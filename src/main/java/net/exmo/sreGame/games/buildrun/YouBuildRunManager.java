package net.exmo.sreGame.games.buildrun;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildwar.Plot;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class YouBuildRunManager {
   private final GameContext ctx;
   private final BuildRunTrackManager tracks;
   private final Map<UUID, YouBuildRunMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, YouBuildRunMatch> byId = new ConcurrentHashMap<>();

   public YouBuildRunManager(GameContext ctx) {
      this.ctx = ctx;
      this.tracks = new BuildRunTrackManager(ctx);
   }

   public BuildRunTrackManager tracks() {
      return this.tracks;
   }

   public YouBuildRunMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public YouBuildRunMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      List<Plot> plots = List.of();
      List<BuildRunTrack> claimedTracks = List.of();
      if (room.youBuildRunSettings().scene() == BuildRunScene.TRACK) {
         claimedTracks = this.tracks.acquire(seats.size());
         if (claimedTracks.size() != seats.size()) {
            this.tracks.release(claimedTracks);
            return null;
         }
      } else {
         plots = this.ctx.plots().acquire(seats.size());
         if (plots.size() != seats.size()) {
            this.ctx.plots().release(plots);
            return null;
         }
      }
      YouBuildRunMatch match = new YouBuildRunMatch(this.ctx, room, seats, plots, claimedTracks);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      List<Plot> plotRef = plots;
      List<BuildRunTrack> trackRef = claimedTracks;
      Runnable ready = () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      };
      boolean queued = room.youBuildRunSettings().scene() == BuildRunScene.TRACK
         ? this.tracks.prepare(trackRef, ready)
         : this.ctx.plots().prepare(plotRef, false, ready);
      if (queued) {
         this.ctx.broadcast(room, "&e正在构建场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.tracks.tick();
      for (YouBuildRunMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      YouBuildRunMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(YouBuildRunMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (YouBuildRunMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      YouBuildRunMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      YouBuildRunMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack);
   }

   public boolean handleDeath(ServerPlayer player) {
      YouBuildRunMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      YouBuildRunMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      YouBuildRunMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7你建我跑进行中。");
      return true;
   }
}
