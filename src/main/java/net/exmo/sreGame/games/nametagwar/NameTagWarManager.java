package net.exmo.sreGame.games.nametagwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.phys.EntityHitResult;

public final class NameTagWarManager {
   private static final Set<UUID> TAG_ENTITIES = ConcurrentHashMap.newKeySet();

   private final GameContext ctx;
   private final NameTagWarArenaManager arenas;
   private final Map<UUID, NameTagWarMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, NameTagWarMatch> byId = new ConcurrentHashMap<>();

   public NameTagWarManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new NameTagWarArenaManager(ctx);
   }

   public NameTagWarArenaManager arenas() {
      return this.arenas;
   }

   public NameTagWarMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public NameTagWarMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public static boolean isTagEntity(Entity entity) {
      return entity != null && TAG_ENTITIES.contains(entity.getUUID());
   }

   public static void registerTagEntity(UUID uuid) {
      if (uuid != null) {
         TAG_ENTITIES.add(uuid);
      }
   }

   public static void unregisterTagEntity(UUID uuid) {
      if (uuid != null) {
         TAG_ENTITIES.remove(uuid);
      }
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      NameTagWarArena arena = this.arenas.acquire();
      if (arena == null) {
         return null;
      }
      NameTagWarMatch match = new NameTagWarMatch(this.ctx, room, seats, arena);
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
         this.ctx.broadcast(room, "&e正在生成撕名牌场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (NameTagWarMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      NameTagWarMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(NameTagWarMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (NameTagWarMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      NameTagWarMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      NameTagWarMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player, source);
   }

   public InteractionResult handleUseEntity(ServerPlayer player, Entity entity, ItemStack stack) {
      NameTagWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return InteractionResult.PASS;
      }
      if (!(entity instanceof ServerPlayer target)) {
         return InteractionResult.PASS;
      }
      if (stack == null || !stack.is(net.minecraft.world.item.Items.SHEARS)) {
         return InteractionResult.PASS;
      }
      if (match.tryStartRip(player, target)) {
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      NameTagWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return InteractionResult.PASS;
      }
      if (stack == null || !stack.is(net.minecraft.world.item.Items.SHEARS)) {
         return InteractionResult.PASS;
      }
      return InteractionResult.PASS;
   }

   public boolean containsEntity(Entity entity) {
      return this.arenas.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      NameTagWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7撕名牌进行中。");
      return true;
   }
}
