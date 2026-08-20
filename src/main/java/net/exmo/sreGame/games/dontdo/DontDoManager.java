package net.exmo.sreGame.games.dontdo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DontDoManager {
   private final GameContext ctx;
   private final IslandManager islands;
   private final Map<UUID, DontDoMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, DontDoMatch> byId = new ConcurrentHashMap<>();

   public DontDoManager(GameContext ctx) {
      this.ctx = ctx;
      this.islands = new IslandManager(ctx);
   }

   public IslandManager islands() {
      return this.islands;
   }

   public DontDoMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public DontDoMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      Island island = this.islands.acquire();
      if (island == null) {
         return null;
      }
      DontDoMatch match = new DontDoMatch(this.ctx, room, seats, island);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.islands.prepare(island, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在生成 256×256 生存岛，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.islands.tick();
      float progress = this.islands.progress();
      if (progress < 1.0F) {
         for (DontDoMatch match : List.copyOf(this.byId.values())) {
            if (match.begun()) {
               continue;
            }
            for (UUID uuid : this.byPlayer.keySet()) {
               if (this.byPlayer.get(uuid) != match) {
                  continue;
               }
               ServerPlayer player = this.ctx.player(uuid);
               if (player != null) {
                  player.displayClientMessage(net.exmo.sreGame.util.TextUtil.color(
                     "&e生成地图 &f" + (int) (progress * 100) + "%"), true);
               }
            }
         }
      }
      for (DontDoMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      DontDoMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(DontDoMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (DontDoMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      DontDoMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public boolean handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      DontDoMatch match = this.get(player.getUUID());
      return match != null && match.handleUseBlock(player, hit, stack);
   }

   public boolean handleBreak(ServerPlayer player, BlockPos pos, BlockState state) {
      DontDoMatch match = this.get(player.getUUID());
      return match != null && match.handleBreak(player, pos, state);
   }

   public void handleAttack(ServerPlayer player, Entity target) {
      DontDoMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handleAttack(player, target);
      }
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source, float amount) {
      DontDoMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source, amount);
   }

   public boolean handleDeath(ServerPlayer player) {
      DontDoMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public void handleDrop(ServerPlayer player) {
      DontDoMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handleDrop(player);
      }
   }

   public void handleJump(ServerPlayer player) {
      DontDoMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handleJump(player);
      }
   }

   public boolean openIfPlaying(ServerPlayer player) {
      DontDoMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7不要做挑战进行中。右侧计分板可看别人的事项和生命。");
      return true;
   }
}
