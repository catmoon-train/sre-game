package net.exmo.sreGame.youguess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.buildwar.WordBank;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class YouGuessManager {
   private final GameContext ctx;
   private final Map<UUID, YouGuessMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, YouGuessMatch> byId = new ConcurrentHashMap<>();

   public YouGuessManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public YouGuessMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public YouGuessMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return this.byPlayer.containsKey(player.getUUID());
   }

   public boolean isBuilder(ServerPlayer player) {
      YouGuessMatch match = this.get(player.getUUID());
      return match != null && match.isBuilder(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      List<Plot> claimed = this.ctx.plots().acquire(1);
      if (claimed.size() != 1) {
         return null;
      }
      List<String> words = WordBank.pickFrom(room.resolvedWords(this.ctx),
         Math.max(seats.size(), room.youGuessSettings().resolvedRounds(seats.size())));
      YouGuessMatch match = new YouGuessMatch(this.ctx, room, seats, claimed.get(0), words, room.isDrawGuess());
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.ctx.plots().prepare(claimed, room.isDrawGuess(), () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在构建场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      for (YouGuessMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      YouGuessMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(YouGuessMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (YouGuessMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      YouGuessMatch match = this.get(player.getUUID());
      return match != null && match.handleGuess(player, message);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      YouGuessMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, match.drawing()
         ? "&7你画我猜进行中。画手用笔刷在白色画布上作画，其他人聊天猜词。"
         : "&7你建我猜进行中。建造者看主题，其他人聊天猜词。");
      return true;
   }

   public Plot boundPlot(ServerPlayer player) {
      YouGuessMatch match = this.get(player.getUUID());
      return match == null ? null : match.plot();
   }

   public boolean isRestrictedPos(ServerPlayer player, BlockPos pos) {
      YouGuessMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      if (match.drawing()) {
         return true;
      }
      if (match.phase() != YouGuessMatch.Phase.PLAYING) {
         return true;
      }
      return !match.isBuilder(player.getUUID()) || match.plot() == null || !match.plot().contains(pos);
   }
}
