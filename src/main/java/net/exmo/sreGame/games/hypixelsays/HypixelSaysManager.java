package net.exmo.sreGame.games.hypixelsays;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class HypixelSaysManager {
   private final GameContext ctx;
   private final HypixelSaysArenaManager arenas;
   private final Map<UUID, HypixelSaysMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, HypixelSaysMatch> byId = new ConcurrentHashMap<>();
   public HypixelSaysManager(GameContext ctx) { this.ctx = ctx; this.arenas = new HypixelSaysArenaManager(ctx); }
   public HypixelSaysArenaManager arenas() { return arenas; }
   public HypixelSaysMatch get(UUID uuid) { return byPlayer.get(uuid); }
   public HypixelSaysMatch getById(UUID id) { return byId.get(id); }
   public boolean isPlaying(ServerPlayer player) { return player != null && byPlayer.containsKey(player.getUUID()); }
   public UUID start(GameRoom room) {
      HypixelSaysArena arena = arenas.acquire(); if (arena == null) return null;
      List<UUID> seats = new ArrayList<>(room.members()); HypixelSaysMatch match = new HypixelSaysMatch(ctx, room, seats, arena);
      byId.put(match.id(), match); for (UUID uuid : seats) byPlayer.put(uuid, match); room.setActiveMatchId(match.id()); room.setState(RoomState.PLAYING); match.start(); return match.id();
   }
   public void pregen() { arenas.pregen(); }
   public void tick() { for (HypixelSaysMatch match : List.copyOf(byId.values())) match.tick(); }
   public void endAll() { for (HypixelSaysMatch match : List.copyOf(byId.values())) match.endNow(); }
   public void remove(HypixelSaysMatch match) { byId.remove(match.id()); byPlayer.entrySet().removeIf(entry -> entry.getValue() == match); }
   public void onLeave(ServerPlayer player) { HypixelSaysMatch match = get(player.getUUID()); if (match != null) match.onLeave(player.getUUID()); }
   public void handleJump(ServerPlayer player) { HypixelSaysMatch match = get(player.getUUID()); if (match != null) match.handleJump(player); }
   public boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state) { HypixelSaysMatch match = get(player.getUUID()); return match != null && match.tryBreak(player, pos, state); }
   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) { HypixelSaysMatch match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack); }
   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) { HypixelSaysMatch match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack); }
   public InteractionResult handleUseEntity(ServerPlayer player, Entity entity) { HypixelSaysMatch match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseEntity(player, entity); }
   public boolean handleAttack(ServerPlayer player, Entity entity) { HypixelSaysMatch match = get(player.getUUID()); return match != null && match.handleAttack(player, entity); }
   public boolean handleDamage(ServerPlayer player, DamageSource source) { HypixelSaysMatch match = get(player.getUUID()); return match != null && match.handleDamage(player, source); }
   public boolean handleMobDamage(Entity entity, DamageSource source) { for (HypixelSaysMatch match : byId.values()) if (match.handleMobDamage(entity, source)) return true; return false; }
   public boolean handleDeath(ServerPlayer player) { HypixelSaysMatch match = get(player.getUUID()); return match != null && match.handleDeath(player); }
}
