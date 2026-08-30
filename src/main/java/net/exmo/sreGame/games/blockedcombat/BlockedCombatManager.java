package net.exmo.sreGame.games.blockedcombat;

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

public final class BlockedCombatManager {
   private final GameContext ctx;
   private final BlockedCombatArenaManager arenas;
   private final Map<UUID, BlockedCombatMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, BlockedCombatMatch> byId = new ConcurrentHashMap<>();

   public BlockedCombatManager(GameContext ctx) { this.ctx = ctx; this.arenas = new BlockedCombatArenaManager(ctx); }
   public BlockedCombatArenaManager arenas() { return this.arenas; }
   public BlockedCombatMatch get(UUID playerId) { return this.byPlayer.get(playerId); }
   public BlockedCombatMatch getById(UUID id) { return this.byId.get(id); }
   public boolean isPlaying(ServerPlayer player) { return player != null && this.byPlayer.containsKey(player.getUUID()); }

   public UUID start(GameRoom room) {
      BlockedCombatArena arena = this.arenas.acquire();
      if (arena == null) return null;
      BlockedCombatMatch match = new BlockedCombatMatch(this.ctx, room, new ArrayList<>(room.members()), arena);
      this.byId.put(match.id(), match);
      for (UUID uuid : room.members()) this.byPlayer.put(uuid, match);
      room.setActiveMatchId(match.id());
      this.arenas.prepare(arena, room.blockedCombatSettings().arenaSize(), room.blockedCombatSettings().tntScarcity(), () -> {
         if (this.byId.containsKey(match.id())) { room.setState(RoomState.PLAYING); match.start(); }
      });
      this.ctx.broadcast(room, "&e正在填充随机矿坑，请稍候…");
      return match.id();
   }

   public void tick() { this.arenas.tick(); for (BlockedCombatMatch match : List.copyOf(this.byId.values())) match.tick(); }
   public void onLeave(ServerPlayer player) { BlockedCombatMatch match = this.byPlayer.remove(player.getUUID()); if (match != null) match.onLeave(player.getUUID()); }
   public void remove(BlockedCombatMatch match) { this.byId.remove(match.id()); this.byPlayer.entrySet().removeIf(entry -> entry.getValue() == match); }
   public void endAll() { for (BlockedCombatMatch match : List.copyOf(this.byId.values())) match.endNow(); }
   public boolean tryBreak(ServerPlayer player, BlockPos pos) { BlockedCombatMatch match = this.get(player.getUUID()); return match != null && match.tryBreak(player, pos); }
   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) { BlockedCombatMatch match = this.get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack); }
   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) { BlockedCombatMatch match = this.get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack); }
   public boolean handleDeath(ServerPlayer player, DamageSource source) { BlockedCombatMatch match = this.get(player.getUUID()); return match != null && match.handleDeath(player, source); }
   public boolean handleDamage(ServerPlayer player, DamageSource source) { BlockedCombatMatch match = this.get(player.getUUID()); return match != null && match.handleDamage(player, source); }
   public boolean openIfPlaying(ServerPlayer player) { if (!isPlaying(player)) return false; this.ctx.send(player, "&7疯狂惊天矿工团进行中。"); return true; }
}
