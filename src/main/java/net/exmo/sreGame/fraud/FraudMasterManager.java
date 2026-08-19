package net.exmo.sreGame.fraud;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class FraudMasterManager {
   private final GameContext ctx;
   private final Map<UUID, FraudMasterMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, FraudMasterMatch> byId = new ConcurrentHashMap<>();

   public FraudMasterManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public FraudMasterMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public FraudMasterMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      List<Plot> claimed = this.ctx.plots().acquire(seats.size());
      if (claimed.size() != seats.size()) {
         return null;
      }
      FraudMasterMatch match = new FraudMasterMatch(this.ctx, room, seats, claimed);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.ctx.plots().prepare(claimed, false, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         ServerLevel level = this.ctx.plots().level();
         if (level != null) {
            for (int i = 0; i < seats.size(); i++) {
               BoothHut.build(level, claimed.get(i), ColorCode.ofIndex(i));
            }
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在搭建小屋，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      for (FraudMasterMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      FraudMasterMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(FraudMasterMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (FraudMasterMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      FraudMasterMatch match = this.get(player.getUUID());
      return match != null && match.handleChat(player, message);
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      FraudMasterMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      FraudMasterMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7诈骗大师进行中。拨号需对方接听。接通后聊天仅通话对象可见。");
      return true;
   }

   public boolean isRestrictedPos(ServerPlayer player, BlockPos pos) {
      return this.isPlaying(player);
   }

   public Plot boundPlot(ServerPlayer player) {
      FraudMasterMatch match = this.get(player.getUUID());
      return match == null ? null : match.plot(player.getUUID());
   }
}
