package net.exmo.sreGame.games.quakechasm;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.quakechasm.combat.ProjectileUtil;
import net.exmo.sreGame.games.quakechasm.combat.WeaponUtil;
import net.exmo.sreGame.games.quakechasm.entity.Trigger;
import net.exmo.sreGame.games.quakechasm.match.QuakeMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quakechasm runtime singleton: schedules ticks, tracks projectiles, timed tasks,
 * user states, active matches and world triggers. Replaces quakechasm's QuakePlugin
 * static state + BukkitRunnable scheduling.
 */
public final class QuakeManager {
    public static QuakeManager INSTANCE;

    private final GameContext ctx;
    private final Map<UUID, QuakeUserState> userStates = new HashMap<>();
    public final List<QuakeMatch> matches = new ArrayList<>();
    public final List<Trigger> triggers = new ArrayList<>();
    private final List<ProjectileTracker> projectiles = new ArrayList<>();
    private final List<ScheduledTask> scheduled = new ArrayList<>();

    public QuakeManager(GameContext ctx) {
        this.ctx = ctx;
        INSTANCE = this;
    }

    public MinecraftServer server() {
        return ctx.server();
    }

    // ---- user state ----
    public QuakeUserState getUserState(ServerPlayer player) {
        return userStates.get(player.getUUID());
    }

    public QuakeUserState getOrCreate(ServerPlayer player) {
        return userStates.computeIfAbsent(player.getUUID(), id -> new QuakeUserState(player));
    }

    public void initPlayer(ServerPlayer player) {
        userStates.computeIfAbsent(player.getUUID(), id -> new QuakeUserState(player));
    }

    public void removePlayer(ServerPlayer player) {
        QuakeUserState st = userStates.remove(player.getUUID());
        if (st != null) {
            for (QuakeMatch m : matches) {
                if (m.players.containsKey(player)) m.leave(player);
            }
            st.reset();
        }
    }

    // ---- projectile tracking ----
    public void trackProjectile(Entity proj, String type, Entity attacker) {
        projectiles.add(new ProjectileTracker(proj, type, attacker.getUUID(), proj.position()));
    }

    // ---- scheduling ----
    public void schedule(int delayTicks, Runnable runnable) {
        scheduled.add(new ScheduledTask(delayTicks, runnable));
    }

    // ---- tick ----
    public void tick() {
        MinecraftServer server = ctx.server();
        if (server == null) return;

        // scheduled tasks
        for (int i = scheduled.size() - 1; i >= 0; i--) {
            ScheduledTask t = scheduled.get(i);
            t.delay--;
            if (t.delay <= 0) {
                scheduled.remove(i);
                try { t.runnable.run(); } catch (Exception e) { /* swallow */ }
            }
        }

        // projectiles
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            ProjectileTracker tr = projectiles.get(i);
            Entity proj = tr.proj;
            if (proj.isRemoved() || proj.tickCount > 200) {
                ServerLevel level = (ServerLevel) proj.level();
                Vec3 pos = proj.isRemoved() ? tr.lastPos : proj.position();
                ServerPlayer attacker = server.getPlayerList().getPlayer(tr.attackerId);
                switch (tr.type) {
                    case "rocket" -> ProjectileUtil.explodeRocket(level, pos, attacker, null);
                    case "plasma" -> ProjectileUtil.explodePlasma(level, pos, attacker, null, true);
                    case "bfg" -> ProjectileUtil.explodeBFG(level, pos, attacker);
                }
                proj.discard();
                projectiles.remove(i);
            } else {
                tr.lastPos = proj.position();
                // 飞行物尾迹粒子
                ServerLevel lvl = (ServerLevel) proj.level();
                Vec3 p = tr.lastPos;
                switch (tr.type) {
                    case "rocket" -> lvl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.02);
                    case "plasma" -> lvl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0.06);
                    case "bfg" -> lvl.sendParticles(ParticleTypes.COMPOSTER, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.5);
                }
            }
        }

        // trigger collision scan (picks up spawners, jumppads, portals)
        try {
            net.exmo.sreGame.games.quakechasm.listener.TriggerHandler.checkTriggers(server);
        } catch (Throwable ignored) {}

        // user states
        for (Map.Entry<UUID, QuakeUserState> e : new ArrayList<>(userStates.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                userStates.remove(e.getKey());
                continue;
            }
            try { e.getValue().tick(); } catch (Exception ex) { /* swallow per-player */ }
        }
    }

    // ---- match lookup ----
    public QuakeMatch getMatchByOwner(UUID ownerId) {
        for (QuakeMatch m : matches) {
            if (ownerId.equals(m.getOwnerId())) return m;
        }
        return null;
    }

    public QuakeMatch getById(UUID id) {
        for (QuakeMatch m : matches) {
            if (id.equals(m.matchId)) return m;
        }
        return null;
    }

    public void onServerStopping() {
        projectiles.clear();
        scheduled.clear();
        triggers.clear();
        matches.clear();
        userStates.clear();
    }

    private static final class ProjectileTracker {
        final Entity proj;
        final String type;
        final UUID attackerId;
        Vec3 lastPos;

        ProjectileTracker(Entity proj, String type, UUID attackerId, Vec3 start) {
            this.proj = proj;
            this.type = type;
            this.attackerId = attackerId;
            this.lastPos = start;
        }
    }

    private static final class ScheduledTask {
        int delay;
        final Runnable runnable;

        ScheduledTask(int delay, Runnable runnable) {
            this.delay = delay;
            this.runnable = runnable;
        }
    }
}
