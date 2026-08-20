package net.exmo.sreGame.games.pillarpummel;

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

public final class PillarPummelManager {
   private final GameContext ctx;
   private final PummelArenaManager arenas;
   private final Map<UUID, PillarPummelMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, PillarPummelMatch> byId = new ConcurrentHashMap<>();

   public PillarPummelManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new PummelArenaManager(ctx);
   }

   public PummelArenaManager arenas() {
      return this.arenas;
   }

   public PillarPummelMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public PillarPummelMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      PummelArena arena = this.arenas.acquire();
      if (arena == null) {
         return null;
      }
      PillarPummelMatch match = new PillarPummelMatch(this.ctx, room, seats, arena);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.arenas.prepare(arena, match.layout(), () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在生成柱联壁合场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (PillarPummelMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      PillarPummelMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(PillarPummelMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (PillarPummelMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      PillarPummelMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack);
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      PillarPummelMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack);
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      PillarPummelMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public boolean handleDeath(ServerPlayer player) {
      PillarPummelMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      PillarPummelMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public boolean containsEntity(net.minecraft.world.entity.Entity entity) {
      return this.arenas.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      PillarPummelMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7柱联壁合进行中。");
      return true;
   }
}
