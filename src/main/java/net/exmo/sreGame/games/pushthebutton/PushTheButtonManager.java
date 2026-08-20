package net.exmo.sreGame.games.pushthebutton;

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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class PushTheButtonManager {
   private final GameContext ctx;
   private final ShipManager ships;
   private final Map<UUID, PushTheButtonMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, PushTheButtonMatch> byId = new ConcurrentHashMap<>();

   public PushTheButtonManager(GameContext ctx) {
      this.ctx = ctx;
      this.ships = new ShipManager(ctx);
   }

   public ShipManager ships() {
      return this.ships;
   }

   public PushTheButtonMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public PushTheButtonMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      Ship ship = this.ships.acquire();
      if (ship == null) {
         return null;
      }
      PushTheButtonMatch match = new PushTheButtonMatch(this.ctx, room, seats, ship);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.ships.prepare(ship, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在组装飞船，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.ships.tick();
      for (PushTheButtonMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      PushTheButtonMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(PushTheButtonMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (PushTheButtonMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      PushTheButtonMatch match = this.get(player.getUUID());
      return match != null && match.handleChat(player, message);
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      PushTheButtonMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      PushTheButtonMatch match = this.get(player.getUUID());
      if (match == null) {
         return InteractionResult.PASS;
      }
      return match.handleUseBlock(player, hit, stack);
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      PushTheButtonMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public boolean tryPlace(ServerPlayer player, BlockPos pos, ItemStack stack) {
      PushTheButtonMatch match = this.get(player.getUUID());
      return match != null && match.tryPlace(player, pos, stack);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      return this.isPlaying(player);
   }

   public boolean handleDeath(ServerPlayer player) {
      PushTheButtonMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean containsEntity(net.minecraft.world.entity.Entity entity) {
      return this.ships.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      PushTheButtonMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7拍下按钮进行中。");
      return true;
   }
}
