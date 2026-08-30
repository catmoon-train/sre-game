package net.exmo.sreGame.games.tunnelrats;

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

public final class TunnelRatsManager {
   private final GameContext ctx;
   private final TunnelRatsArenaManager arenas;
   private final Map<UUID, TunnelRatsMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, TunnelRatsMatch> byId = new ConcurrentHashMap<>();

   public TunnelRatsManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new TunnelRatsArenaManager(ctx);
   }

   public TunnelRatsArenaManager arenas() { return this.arenas; }
   public TunnelRatsMatch get(UUID uuid) { return this.byPlayer.get(uuid); }
   public TunnelRatsMatch getById(UUID id) { return this.byId.get(id); }
   public boolean isPlaying(ServerPlayer player) { return player != null && this.byPlayer.containsKey(player.getUUID()); }

   public UUID start(GameRoom room) {
      TunnelRatsArena arena = this.arenas.acquire();
      if (arena == null) return null;
      TunnelRatsMatch match = new TunnelRatsMatch(this.ctx, room, new ArrayList<>(room.members()), arena);
      this.byId.put(match.id(), match);
      for (UUID uuid : room.members()) this.byPlayer.put(uuid, match);
      room.setActiveMatchId(match.id());
      this.arenas.prepare(arena, room.tunnelRatsSettings(), () -> {
         if (this.byId.containsKey(match.id())) {
            room.setState(RoomState.PLAYING);
            match.start();
         }
      });
      this.ctx.broadcast(room, "&e正在生成随机矿层与两队基地，请稍候…");
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (TunnelRatsMatch match : List.copyOf(this.byId.values())) match.tick();
   }

   public void onLeave(ServerPlayer player) {
      if (player == null) return;
      TunnelRatsMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) match.onLeave(player.getUUID());
   }

   public void remove(TunnelRatsMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(entry -> entry.getValue() == match);
   }

   public void endAll() { for (TunnelRatsMatch match : List.copyOf(this.byId.values())) match.endNow(); }
   public boolean tryBreak(ServerPlayer player, BlockPos pos) { TunnelRatsMatch match = get(player.getUUID()); return match != null && match.tryBreak(player, pos); }
   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) { TunnelRatsMatch match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack); }
   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) { TunnelRatsMatch match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack); }
   public boolean handleDamage(ServerPlayer player, DamageSource source) { TunnelRatsMatch match = get(player.getUUID()); return match != null && match.handleDamage(player, source); }
   public boolean handleDeath(ServerPlayer player, DamageSource source) { TunnelRatsMatch match = get(player.getUUID()); return match != null && match.handleDeath(player, source); }
   public boolean openIfPlaying(ServerPlayer player) {
      if (!isPlaying(player)) return false;
      this.ctx.send(player, "&7地道战进行中。完成对局后可打开大厅菜单。");
      return true;
   }
}
