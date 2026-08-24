package net.exmo.sreGame.games.quakechasm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.quakechasm.match.MatchMode;
import net.exmo.sreGame.games.quakechasm.match.QMap;
import net.exmo.sreGame.games.quakechasm.match.TDMMatch;
import net.exmo.sreGame.room.GameRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Team Deathmatch entry. */
public final class QuakeTDMMiniGame implements MiniGame {
    public static final String ID = "quake_tdm";
    private final GameContext ctx;

    public QuakeTDMMiniGame(GameContext ctx) { this.ctx = ctx; }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Quake 团队死斗"; }
    @Override public String icon() { return "iron_sword"; }
    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return 16; }

    @Override public void openSetup(ServerPlayer host, GameRoom room) {
        ctx.send(host, "&6[Quake TDM] &7红蓝两队对抗，人数自动平衡。准备后开始。");
    }

    @Override
    public boolean canStart(GameRoom room, ServerPlayer actor) {
        if (room.size() < minPlayers()) { ctx.send(actor, "&cQuake TDM 需要至少 " + minPlayers() + " 人。"); return false; }
        if (!room.allReady()) { ctx.send(actor, "&c还有玩家未准备。"); return false; }
        return true;
    }

    @Override
    public void start(GameRoom room, ServerPlayer actor) {
        ServerLevel level = actor.serverLevel();
        Vec3 p = actor.position();
        QMap map = new QMap("auto_tdm", "Quake TDM", level.dimension().location().getPath(),
                p.x - 32, p.y - 5, p.z - 32, p.x + 32, p.y + 20, p.z + 32,
                new ArrayList<>(), new ArrayList<>(List.of(MatchMode.TDM)), 2);
        TDMMatch match = new TDMMatch(map);
        QuakeManager.INSTANCE.matches.add(match);
        QuakeMiniGame.spawnPickups(level, p);
        for (UUID id : room.members()) {
            ServerPlayer mp = ctx.server().getPlayerList().getPlayer(id);
            if (mp != null) match.join(mp, null);
        }
        match.warmup();
    }
}
