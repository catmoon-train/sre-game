package net.exmo.sreGame.games.quakechasm.match;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;
import net.exmo.sreGame.games.quakechasm.entity.spawner.CTFFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Capture the Flag. Ported from quakechasm's CTFMatch. */
public class CTFMatch extends QuakeMatch {
    @QManageable(name = "capturelimit", min = 1, max = 100, description = "Captures needed to win")
    public int capturelimit = 10;

    @QManageable(name = "needPlayers", min = 1, max = 100)
    private int needPlayers = 2;

    private final HashMap<ServerPlayer, Integer> scores = new HashMap<>();
    private final int[] captures = {0, 0};
    private final ServerPlayer[] flagCarriers = {null, null};
    private CTFFlag redFlag;
    private CTFFlag blueFlag;
    private boolean started = false;

    public CTFMatch(QMap map) { super(map); initCTF(); }
    public CTFMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) { super(map, ownerId, privacy, password); initCTF(); }

    private void initCTF() {
        ServerLevel level = map.getWorld(QuakeManager.INSTANCE.server());
        if (level == null) return;
        Vec3 c = new Vec3((map.minX + map.maxX) / 2, map.maxY - 1, (map.minZ + map.maxZ) / 2);
        redFlag = new CTFFlag(Team.RED, false, this, level, c.add(15, 0, 0));
        blueFlag = new CTFFlag(Team.BLUE, false, this, level, c.add(-15, 0, 0));
    }

    @Override public String getNameKey() { return "match.ctf.name"; }
    @Override public void setScoreLimit(int v) { this.capturelimit = v; }
    @Override public void setNeedPlayers(int v) { this.needPlayers = v; }
    @Override public List<Team> allowedTeams() { return List.of(Team.RED, Team.BLUE); }

    @Override
    public Team assignTeam(ServerPlayer player) {
        return getPlayersInTeam(Team.RED).size() >= getPlayersInTeam(Team.BLUE).size() ? Team.BLUE : Team.RED;
    }

    @Override
    public void join(ServerPlayer player, Team team) {
        super.join(player, team);
        scores.put(player, 0);
        broadcast(QuakeTranslator.t("match.team.joined", player.getName().getString(), getTeamOfPlayer(player).name()));
        if (players.size() >= needPlayers && !started) warmup();
    }

    @Override
    public void leave(ServerPlayer player) {
        super.leave(player);
        scores.remove(player);
        if (players.isEmpty()) end();
    }

    public void warmup() {
        broadcast(QuakeTranslator.t("match.countdown", 10));
        QuakeManager.INSTANCE.schedule(200, this::start);
    }

    public void start() {
        for (ServerPlayer p : players.keySet()) {
            scores.put(p, 0);
            QuakeUserState st = QuakeManager.INSTANCE.getUserState(p);
            if (st != null) { st.reset(); st.initRespawn(); }
        }
        started = true;
        broadcast(QuakeTranslator.t("match.start"));
        broadcast(QuakeTranslator.t("match.ctf.startMessage", capturelimit));
    }

    public Team getCarryingFlagTeam(ServerPlayer carrier) {
        if (flagCarriers[0] == carrier) return Team.RED;
        if (flagCarriers[1] == carrier) return Team.BLUE;
        return null;
    }

    public void grabFlag(Team flagTeam, ServerPlayer player) {
        int idx = flagTeam == Team.RED ? 0 : 1;
        flagCarriers[idx] = player;
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(flagTeam == Team.RED ? Items.RED_BANNER : Items.BLUE_BANNER));
        CTFFlag flag = flagTeam == Team.RED ? redFlag : blueFlag;
        if (flag != null) flag.hideFlag();
        broadcast("&e" + player.getName().getString() + " 抢到了 " + (flagTeam == Team.RED ? "红" : "蓝") + "旗!");
    }

    public void returnFlag(Team team, ServerPlayer returner) {
        CTFFlag flag = team == Team.RED ? redFlag : blueFlag;
        if (flag != null) flag.respawn();
        broadcast("&e" + (team == Team.RED ? "红" : "蓝") + "旗已归还");
    }

    public void captureFlag(Team belongingFlagTeam, ServerPlayer carrier) {
        // carrier touched own base while carrying enemy flag → capture enemy flag
        CTFFlag enemyFlag = belongingFlagTeam == Team.RED ? blueFlag : redFlag;
        int enemyIdx = belongingFlagTeam == Team.RED ? 1 : 0;
        flagCarriers[enemyIdx] = null;
        if (enemyFlag != null) enemyFlag.respawn();
        carrier.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        scoreCapture(belongingFlagTeam, carrier);
    }

    public void scoreCapture(Team team, ServerPlayer capturer) {
        if (started) {
            int idx = team == Team.RED ? 0 : 1;
            captures[idx]++;
            scores.merge(capturer, 5, Integer::sum);
        }
        broadcast("&e" + capturer.getName().getString() + " 夺旗得分! 红" + captures[0] + " 蓝" + captures[1]);
        if (captures[0] >= capturelimit || captures[1] >= capturelimit) {
            Team w = captures[0] > captures[1] ? Team.RED : Team.BLUE;
            broadcast("&6" + w.name() + " 队获胜!");
            end();
        }
    }

    @Override
    public void onDeath(ServerPlayer victim, Entity attacker, DamageCause cause) {
        super.onDeath(victim, attacker, cause);
        broadcast(getDeathMessage(victim, attacker, cause));
        if (started && attacker instanceof ServerPlayer ap && victim != attacker) {
            boolean sameTeam = getPlayersInTeam(players.get(ap)).contains(victim);
            scores.merge(ap, sameTeam ? -1 : 1, Integer::sum);
        }
        Team carried = getCarryingFlagTeam(victim);
        if (carried != null) {
            // drop flag at victim location
            ServerLevel level = victim.serverLevel();
            Vec3 loc = victim.position().add(0, 0.5, 0);
            new CTFFlag(carried, true, this, level, loc);
            int idx = carried == Team.RED ? 0 : 1;
            flagCarriers[idx] = null;
            victim.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }
}
