package net.exmo.sreGame.games.quakechasm.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.Chatroom;
import net.exmo.sreGame.games.quakechasm.QuakeConfig;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;
import net.exmo.sreGame.games.quakechasm.combat.DeathMessages;
import net.exmo.sreGame.games.quakechasm.combat.WeaponUtil;
import net.exmo.sreGame.games.quakechasm.entity.Trigger;
import net.exmo.sreGame.games.quakechasm.util.MiscUtil;
import net.exmo.sreGame.util.TextUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract match base. Ported from quakechasm's Match, adapted to Fabric ServerPlayer
 * and driven by QuakeManager.tick() instead of BukkitRunnable.
 */
public abstract class QuakeMatch {
    protected final QMap map;
    public final HashMap<ServerPlayer, Team> players = new HashMap<>();
    public boolean matchEnding = false;

    protected UUID ownerId;
    protected MatchPrivacy privacy = MatchPrivacy.PUBLIC;
    protected String passwordHash;
    protected final HashSet<UUID> invitedPlayers = new HashSet<>();

    /** Unique id for this match; used to link back to the SRE-GAME room via activeMatchId. */
    public final UUID matchId = UUID.randomUUID();

    public UUID getId() { return matchId; }

    public QuakeMatch(QMap map) {
        this(map, null, MatchPrivacy.PUBLIC, null);
    }

    public QuakeMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        this.map = map;
        this.ownerId = ownerId;
        if (privacy != null) this.privacy = privacy;
        if (password != null && !password.isEmpty()) setPassword(password);
        MinecraftServer server = QuakeManager.INSTANCE.server();
        if (server != null) {
            ServerLevel level = map.getWorld(server);
            if (level != null) map.chunkLoad(level);
        }
    }

    public QMap getMap() { return map; }
    public List<ServerPlayer> getPlayers() { return List.copyOf(players.keySet()); }
    public List<ServerPlayer> getPlayersInTeam(Team team) {
        return players.entrySet().stream()
                .filter(e -> e.getValue() == team)
                .map(Map.Entry::getKey)
                .toList();
    }
    public Team getTeamOfPlayer(ServerPlayer player) { return players.get(player); }

    public abstract String getNameKey();
    public abstract Team assignTeam(ServerPlayer player);
    public abstract List<Team> allowedTeams();
    public abstract void setScoreLimit(int scoreLimit);
    public abstract void setNeedPlayers(int needPlayers);

    public boolean isTeamMatch() {
        List<Team> t = allowedTeams();
        return t.contains(Team.RED) && t.contains(Team.BLUE);
    }

    public void join(ServerPlayer player, Team team) {
        Team resolved = team == null ? assignTeam(player) : team;
        if (!allowedTeams().contains(resolved)) return;
        players.put(player, resolved);

        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        st.currentMatch = this;

        Vec3 spawn = map.getRandomSpawnpoint(resolved);
        float yaw = map.getRandomSpawnpointYaw(resolved);
        player.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0);
        MiscUtil.teleEffect(player.serverLevel(), spawn, false);

        st.initForMatch();
        st.currentChat = Chatroom.MATCH;
        if (isTeamMatch()) setTeamArmor(player, resolved);

        broadcast(QuakeTranslator.t("match.player.joined", player.getName().getString()));
    }

    public void leave(ServerPlayer player) {
        players.remove(player);
        cleanup(player);
        broadcast(QuakeTranslator.t("match.player.left", player.getName().getString()));
    }

    public void end() {
        matchEnding = true;
        for (ServerPlayer p : players.keySet()) {
            p.playNotifySound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        }
        QuakeManager.INSTANCE.schedule(200, () -> {
            for (ServerPlayer p : List.copyOf(players.keySet())) {
                cleanup(p);
            }
            cleanupTriggers();
            QuakeManager.INSTANCE.matches.remove(this);
            // 通知房间系统：对局已结束，复位房间到 WAITING
            try {
                net.exmo.sreGame.SreGame.getContext().rooms().onMatchEnded(matchId);
            } catch (Throwable ignored) {}
        });
    }

    /**
     * Remove all Quake triggers (spawners / jumppads / portals / flags) lying inside
     * this match's map bounds, so they don't pile up across repeated matches.
     */
    public void cleanupTriggers() {
        MinecraftServer server = QuakeManager.INSTANCE.server();
        if (server == null) return;
        ServerLevel level = map.getWorld(server);
        if (level == null) return;
        AABB bounds = map.getBounds();
        var it = QuakeManager.INSTANCE.triggers.iterator();
        while (it.hasNext()) {
            Trigger t = it.next();
            try {
                if (t.getLevel() == level && bounds.contains(t.getLocation())) {
                    t.remove();
                    it.remove();
                }
            } catch (Throwable ignored) {
                it.remove();
            }
        }
    }

    public void cleanup(ServerPlayer player) {
        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        if (st != null) {
            st.currentMatch = null;
            st.currentChat = Chatroom.GLOBAL;
            st.reset();
        }
        // teleport to lobby
        QuakeConfig.Lobby lobby = QuakeConfig.get().lobby;
        MinecraftServer server = QuakeManager.INSTANCE.server();
        if (server != null) {
            ServerLevel level = server.overworld();
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().location().getPath().equals(lobby.world) || l.dimension().location().toString().contains(lobby.world)) {
                    level = l;
                    break;
                }
            }
            player.teleportTo(level, lobby.x, lobby.y, lobby.z, lobby.yaw, lobby.pitch);
            MiscUtil.teleEffect(level, new Vec3(lobby.x, lobby.y, lobby.z), true);
        }
    }

    public static void setTeamArmor(ServerPlayer player, Team team) {
        player.setItemSlot(EquipmentSlot.CHEST, WeaponUtil.teamArmor(Items.LEATHER_CHESTPLATE, team));
        player.setItemSlot(EquipmentSlot.LEGS, WeaponUtil.teamArmor(Items.LEATHER_LEGGINGS, team));
        player.setItemSlot(EquipmentSlot.FEET, WeaponUtil.teamArmor(Items.LEATHER_BOOTS, team));
    }

    public void onDeath(ServerPlayer victim, Entity attacker, DamageCause cause) {
        QuakeUserState vst = QuakeManager.INSTANCE.getUserState(victim);
        if (vst != null) {
            vst.consecutiveRailgunHits = 0;
            vst.lastKillTime = 0;
        }
        if (attacker instanceof ServerPlayer ap && attacker != victim) {
            QuakeUserState ast = QuakeManager.INSTANCE.getUserState(ap);
            if (ast != null) {
                ast.checkExcellentMedal();
                ast.lastKillTime = System.currentTimeMillis();
            }
        }
    }

    public static String getDeathMessage(ServerPlayer victim, Entity attacker, DamageCause cause) {
        String vName = victim.getName().getString();
        if (attacker == null || attacker == victim) {
            return DeathMessages.suicide(cause, vName);
        }
        String aName = attacker.getName().getString();
        return DeathMessages.frag(cause, vName, aName);
    }

    public void broadcast(String message) {
        var comp = TextUtil.color(message);
        for (ServerPlayer p : players.keySet()) p.sendSystemMessage(comp);
    }

    // ---- privacy / password ----
    public boolean isOwner(ServerPlayer p) { return ownerId != null && ownerId.equals(p.getUUID()); }
    public UUID getOwnerId() { return ownerId; }
    public MatchPrivacy getPrivacy() { return privacy; }
    public void setPrivacy(MatchPrivacy p) { this.privacy = p; }
    public boolean isPasswordProtected() { return passwordHash != null; }

    public boolean checkPassword(String password) {
        if (passwordHash == null) return true;
        if (password == null) return false;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return passwordHash.equals(bytesToHex(md.digest(password.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) { passwordHash = null; return; }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            passwordHash = bytesToHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            passwordHash = null;
        }
    }

    public boolean isInvited(UUID id) { return invitedPlayers.contains(id); }
    public void invitePlayer(UUID id) { invitedPlayers.add(id); }
    public void clearInvites() { invitedPlayers.clear(); }

    private static String bytesToHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) sb.append('0');
            sb.append(h);
        }
        return sb.toString();
    }
}
