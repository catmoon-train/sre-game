package net.exmo.sreGame.game;

import com.mcrpvp.duel.fabric.api.DuelApi;
import com.mcrpvp.duel.fabric.api.MatchEvents;
import com.mcrpvp.duel.fabric.api.MatchLifecycleListener;
import com.mcrpvp.duel.fabric.match.DuelMatch;
import com.mcrpvp.duel.fabric.queue.QueueType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.DuelSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;

public final class DuelMiniGame implements MiniGame, MatchLifecycleListener {
   public static final String ID = "mcrpvp_duel";
   private final GameContext ctx;

   public DuelMiniGame(GameContext ctx) {
      this.ctx = ctx;
      MatchEvents.addListener(this);
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "决斗";
   }

   @Override
   public String icon() {
      return "diamond_sword";
   }

   @Override
   public int minPlayers() {
      return 2;
   }

   @Override
   public int maxPlayers() {
      return 16;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      DuelSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      DuelSettings settings = room.duelSettings();
      String gamemode = settings.gamemode();
      if (gamemode == null || !DuelApi.isModeEnabled(gamemode)) {
         this.ctx.send(actor, "&c请先在房间设置里选择决斗模式。");
         return false;
      }
      if (settings.queueType() == QueueType.RANKED && !DuelApi.isRankedEnabled(gamemode)) {
         this.ctx.send(actor, "&c该模式未开放排位。");
         return false;
      }
      int teamSize = DuelApi.getTeamSize(gamemode);
      int need = teamSize * 2;
      if (room.size() != need) {
         this.ctx.send(actor, "&c该模式需要 &f" + teamSize + "v" + teamSize + " &c（当前 &f" + room.size() + "&c 人）。");
         return false;
      }
      if (settings.team1().size() != teamSize || settings.team2().size() != teamSize) {
         this.ctx.send(actor, "&c请在房内面板把玩家分成两队，每队 &f" + teamSize + " &c人。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) {
            if (!room.isReady(uuid)) {
               waiting.add(this.ctx.name(uuid));
            }
         }
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      for (UUID uuid : room.members()) {
         ServerPlayer member = this.ctx.player(uuid);
         if (member == null) {
            this.ctx.send(actor, "&c有玩家不在线。");
            return false;
         }
         if (DuelApi.isInMatch(member)) {
            this.ctx.send(actor, "&c" + member.getGameProfile().getName() + " 已在其他对局中。");
            return false;
         }
      }
      if (room.size() < this.minPlayers()) {
         this.ctx.send(actor, "&c至少需要 &f" + this.minPlayers() + " &c人。");
         return false;
      }
      return true;
   }

   @Override
   public void start(GameRoom room, ServerPlayer actor) {
      DuelSettings settings = room.duelSettings();
      UUID matchId = DuelApi.startTeams(
         List.copyOf(settings.team1()),
         List.copyOf(settings.team2()),
         settings.gamemode(),
         settings.queueType(),
         settings.rounds()
      );
      if (matchId != null) {
         room.setActiveMatchId(matchId);
         room.setState(RoomState.PLAYING);
      }
   }

   @Override
   public void onMatchStarted(DuelMatch match) {
      for (UUID uuid : allPlayers(match)) {
         GameRoom room = this.ctx.rooms().getByPlayer(uuid);
         if (room != null && room.state() == RoomState.STARTING) {
            room.setActiveMatchId(match.id());
            room.setState(RoomState.PLAYING);
            return;
         }
      }
   }

   @Override
   public void onMatchEnded(DuelMatch match, UUID winnerUuid) {
      this.ctx.rooms().onMatchEnded(match.id());
   }

   private static List<UUID> allPlayers(DuelMatch match) {
      List<UUID> all = new ArrayList<>();
      all.addAll(match.team1Members());
      all.addAll(match.team2Members());
      if (all.isEmpty()) {
         all.add(match.player1());
         all.add(match.player2());
      }
      return all;
   }
}
