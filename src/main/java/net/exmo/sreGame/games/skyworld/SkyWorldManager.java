package net.exmo.sreGame.games.skyworld;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class SkyWorldManager {
   private final GameContext ctx;
   private final SkyArenaManager arenas;
   private final Map<UUID, SkyWorldMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, SkyWorldMatch> byId = new ConcurrentHashMap<>();

   public SkyWorldManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new SkyArenaManager(ctx);
   }

   public SkyArenaManager arenas() {
      return this.arenas;
   }

   public SkyWorldMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public SkyWorldMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      SkyArena arena = this.arenas.acquire();
      if (arena == null) {
         return null;
      }
      SkyWorldMatch match = new SkyWorldMatch(this.ctx, room, seats, arena);
      arena.generate(match.islandCount(), room.skyWorldSettings().teams());
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.arenas.prepare(arena, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在生成空岛，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (SkyWorldMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      SkyWorldMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(SkyWorldMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (SkyWorldMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      SkyWorldMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack);
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      SkyWorldMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack);
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      SkyWorldMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      SkyWorldMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player, source);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      SkyWorldMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public boolean containsEntity(Entity entity) {
      return this.arenas.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      SkyWorldMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7空岛战争进行中。");
      return true;
   }
}
