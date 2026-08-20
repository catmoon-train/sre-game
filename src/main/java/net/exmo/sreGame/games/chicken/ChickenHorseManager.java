package net.exmo.sreGame.games.chicken;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class ChickenHorseManager {
   private final GameContext ctx;
   private final TrackManager tracks;
   private final Map<UUID, ChickenHorseMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, ChickenHorseMatch> byId = new ConcurrentHashMap<>();

   public ChickenHorseManager(GameContext ctx) {
      this.ctx = ctx;
      this.tracks = new TrackManager(ctx);
   }

   public TrackManager tracks() {
      return this.tracks;
   }

   public ChickenHorseMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public ChickenHorseMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      Track track = this.tracks.acquire();
      if (track == null) {
         return null;
      }
      track.setLayout(room.chickenHorseSettings().layout());
      ChickenHorseMatch match = new ChickenHorseMatch(this.ctx, room, seats, track);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.tracks.prepare(track, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在铺设鸡马赛道，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.tracks.tick();
      for (ChickenHorseMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      ChickenHorseMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(ChickenHorseMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (ChickenHorseMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      ChickenHorseMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public boolean tryPlace(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      ChickenHorseMatch match = this.get(player.getUUID());
      return match != null && match.tryPlace(player, hit, stack);
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      ChickenHorseMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public boolean handleDeath(ServerPlayer player) {
      ChickenHorseMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean handleDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
      ChickenHorseMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      ChickenHorseMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7超级鸡马进行中。创造改造只能放方块，冲关时受伤会出局旁观。");
      return true;
   }
}
