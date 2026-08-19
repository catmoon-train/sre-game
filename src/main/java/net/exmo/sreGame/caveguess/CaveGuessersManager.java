package net.exmo.sreGame.caveguess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class CaveGuessersManager {
   private final GameContext ctx;
   private final Map<UUID, CaveGuessersMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, CaveGuessersMatch> byId = new ConcurrentHashMap<>();

   public CaveGuessersManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public CaveGuessersMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public CaveGuessersMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      List<Plot> claimed = this.ctx.plots().acquire(1);
      if (claimed.size() != 1) {
         return null;
      }
      List<CaveWord> words = this.ctx.caveWords().resolved(room);
      CaveGuessersMatch match = new CaveGuessersMatch(this.ctx, room, seats, claimed.get(0), words);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.ctx.plots().prepare(claimed, false, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在开凿洞穴，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      for (CaveGuessersMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      CaveGuessersMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(CaveGuessersMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (CaveGuessersMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      CaveGuessersMatch match = this.get(player.getUUID());
      return match != null && match.handleChat(player, message);
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      CaveGuessersMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public boolean canBuild(ServerPlayer player) {
      CaveGuessersMatch match = this.get(player.getUUID());
      return match != null && match.canBuild(player.getUUID());
   }

   public boolean isRestrictedPos(ServerPlayer player, BlockPos pos) {
      CaveGuessersMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      if (!match.canBuild(player.getUUID())) {
         return true;
      }
      return pos == null || !match.stageContains(pos);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      CaveGuessersMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7洞穴猜猜乐进行中。按当前模式描述或在聊天抢答。");
      return true;
   }

   public Plot boundPlot(ServerPlayer player) {
      CaveGuessersMatch match = this.get(player.getUUID());
      return match == null ? null : match.plot();
   }
}
