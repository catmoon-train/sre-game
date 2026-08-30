package net.exmo.sreGame.games.quakechasm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.quakechasm.match.CTFMatch;
import net.exmo.sreGame.games.quakechasm.match.MatchMode;
import net.exmo.sreGame.games.quakechasm.match.QMap;
import net.exmo.sreGame.room.GameRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Capture the Flag entry. */
public final class QuakeCTFMiniGame implements MiniGame {
    public static final String ID = "quake_ctf";
    private final GameContext ctx;

    public QuakeCTFMiniGame(GameContext ctx) { this.ctx = ctx; }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Quake 夺旗"; }
    @Override public String icon() { return "red_banner"; }
    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return 24; }

    @Override public void openSetup(ServerPlayer host, GameRoom room) {
        ctx.send(host, "&6[Quake CTF] &7抢夺敌方旗帜带回己方基地得分。准备后开始。");
    }

    @Override
    public boolean canStart(GameRoom room, ServerPlayer actor) {
        if (room.size() < minPlayers() || room.size() > maxPlayers()) { ctx.send(actor, "&cQuake CTF 需要 " + minPlayers() + "–" + maxPlayers() + " 人。"); return false; }
        if (!room.allReady()) { ctx.send(actor, "&c还有玩家未准备。"); return false; }
        return true;
    }

    @Override
    public void start(GameRoom room, ServerPlayer actor) {
        ServerLevel level = actor.serverLevel();
        Vec3 p = actor.position();
        QMap map = new QMap("auto_ctf", "Quake CTF", level.dimension().location().getPath(),
                p.x - 32, p.y - 5, p.z - 32, p.x + 32, p.y + 20, p.z + 32,
                new ArrayList<>(), new ArrayList<>(List.of(MatchMode.CTF)), 2);
        CTFMatch match = new CTFMatch(map); // constructor creates red/blue flags at c±15
        QuakeManager.INSTANCE.matches.add(match);
        room.setActiveMatchId(match.matchId);
        room.setState(net.exmo.sreGame.room.RoomState.PLAYING);
        QuakeMiniGame.spawnPickups(level, p);
        for (UUID id : room.members()) {
            ServerPlayer mp = ctx.server().getPlayerList().getPlayer(id);
            if (mp != null) match.join(mp, null);
        }
        match.warmup();
    }
}
