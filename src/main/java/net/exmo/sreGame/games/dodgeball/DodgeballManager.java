package net.exmo.sreGame.games.dodgeball;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class DodgeballManager {
   private final GameContext ctx;
   private final DodgeballArenaManager arenas;
   private final Map<UUID, DodgeballMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, DodgeballMatch> byId = new ConcurrentHashMap<>();

   public DodgeballManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new DodgeballArenaManager(ctx);
   }

   public DodgeballArenaManager arenas() {
      return this.arenas;
   }

   public DodgeballMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public DodgeballMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      DodgeballArena arena = this.arenas.acquire();
      if (arena == null) {
         return null;
      }
      DodgeballMatch match = new DodgeballMatch(this.ctx, room, seats, arena);
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
         this.ctx.broadcast(room, "&e正在生成躲避球场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (DodgeballMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      DodgeballMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(DodgeballMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (DodgeballMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      DodgeballMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack);
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      DodgeballMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack);
   }

   public boolean handleDeath(ServerPlayer player) {
      DodgeballMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      DodgeballMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public boolean handleSnowballHit(Snowball ball, Entity hit) {
      if (ball == null || hit == null) {
         return false;
      }
      for (DodgeballMatch match : this.byId.values()) {
         if (match.handleSnowballHit(ball, hit)) {
            return true;
         }
      }
      return false;
   }

   public boolean containsEntity(Entity entity) {
      return this.arenas.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      DodgeballMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7躲避球进行中。");
      return true;
   }
}
