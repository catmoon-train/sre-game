package net.exmo.sreGame.games.quakechasm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.quakechasm.combat.WeaponType;
import net.exmo.sreGame.games.quakechasm.combat.powerup.PowerupType;
import net.exmo.sreGame.games.quakechasm.entity.spawner.AmmoSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.ArmorSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.HealthSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.PowerupSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.WeaponSpawner;
import net.exmo.sreGame.games.quakechasm.entity.trigger.Jumppad;
import net.exmo.sreGame.games.quakechasm.match.FFAMatch;
import net.exmo.sreGame.games.quakechasm.match.MatchMode;
import net.exmo.sreGame.games.quakechasm.match.QMap;
import net.exmo.sreGame.room.GameRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quakechasm MiniGame entry. Registers the Quake arena as an SRE-GAME minigame;
 * starting it builds a temporary arena around the host and launches an FFA match.
 */
public final class QuakeMiniGame implements MiniGame {
    public static final String ID = "quake";
    private final GameContext ctx;

    public QuakeMiniGame(GameContext ctx) {
        this.ctx = ctx;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Quake 竞技场"; }
    @Override public String icon() { return "carrot_on_a_stick"; }
    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return 24; }

    @Override
    public void openSetup(ServerPlayer host, GameRoom room) {
        ctx.send(host, "&6[Quake] &7在房主位置自动生成临时竞技场。准备后开始即可。");
    }

    @Override
    public boolean canStart(GameRoom room, ServerPlayer actor) {
        if (room.size() < minPlayers() || room.size() > maxPlayers()) {
            ctx.send(actor, "&cQuake 需要 &f" + minPlayers() + "&c 人。");
            return false;
        }
        if (!room.allReady()) {
            ctx.send(actor, "&c还有玩家未准备。");
            return false;
        }
        return true;
    }

    @Override
    public void start(GameRoom room, ServerPlayer actor) {
        ServerLevel level = actor.serverLevel();
        Vec3 p = actor.position();
        QMap map = new QMap("auto", "Quake 竞技场", level.dimension().location().getPath(),
                p.x - 32, p.y - 5, p.z - 32, p.x + 32, p.y + 20, p.z + 32,
                new ArrayList<>(), new ArrayList<>(List.of(MatchMode.FFA)), 2);

        FFAMatch match = new FFAMatch(map);
        QuakeManager.INSTANCE.matches.add(match);
        room.setActiveMatchId(match.matchId);
        room.setState(net.exmo.sreGame.room.RoomState.PLAYING);
        for (UUID id : room.members()) {
            ServerPlayer mp = ctx.server().getPlayerList().getPlayer(id);
            if (mp != null) match.join(mp, null);
        }
        match.cleanupTriggers(); // 清上局残留 spawner，避免堆积
        spawnPickups(level, p);
        match.warmup();
    }

    /** Scatter Quake pickups around the host position so all 7 weapons are obtainable. */
    public static void spawnPickups(ServerLevel level, Vec3 c) {
        new WeaponSpawner(WeaponType.SHOTGUN, level, c.add(8, 0, 0));
        new WeaponSpawner(WeaponType.ROCKET_LAUNCHER, level, c.add(-8, 0, 0));
        new WeaponSpawner(WeaponType.RAILGUN, level, c.add(0, 0, 8));
        new WeaponSpawner(WeaponType.LIGHTNING_GUN, level, c.add(0, 0, -8));
        new WeaponSpawner(WeaponType.PLASMA_GUN, level, c.add(6, 0, 6));
        new WeaponSpawner(WeaponType.BFG, level, c.add(0, 5, 0));
        for (int i = 0; i < 7; i++) new AmmoSpawner(i, level, c.add((i - 3) * 3, 0, 12));
        new ArmorSpawner(100, level, c.add(12, 0, -6));
        new ArmorSpawner(50, level, c.add(-12, 0, 6));
        new ArmorSpawner(5, level, c.add(14, 0, 14));
        new HealthSpawner(20, level, c.add(0, 0, -12));
        new HealthSpawner(10, level, c.add(10, 0, 10));
        new PowerupSpawner(PowerupType.QUAD_DAMAGE, level, c.add(-10, 0, -10), false, 30);
        new PowerupSpawner(PowerupType.REGENERATION, level, c.add(10, 2, -10), false, 30);
        new PowerupSpawner(PowerupType.PROTECTION, level, c.add(0, 3, 0), false, 30);
        new Jumppad(level, c.add(15, 0, 0), new Vec3(0, 1.5, 0));
    }
}
