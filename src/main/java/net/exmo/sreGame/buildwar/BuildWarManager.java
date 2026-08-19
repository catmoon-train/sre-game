package net.exmo.sreGame.buildwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.BuildWarVoteGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class BuildWarManager {
   private final GameContext ctx;
   private final Map<UUID, BuildWarMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, BuildWarMatch> byId = new ConcurrentHashMap<>();

   public BuildWarManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public BuildWarMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public BuildWarMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      int themes = room.buildWarSettings().resolvedThemeCount(seats.size());
      List<Plot> claimed = this.ctx.plots().acquire(themes);
      if (claimed.size() != themes) {
         return null;
      }
      List<String> words = WordBank.pickFrom(room.resolvedWords(this.ctx), themes);
      BuildWarMatch match = new BuildWarMatch(this.ctx, room, seats, claimed, words, room.isDrawWar());
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.ctx.plots().prepare(claimed, room.isDrawWar(), () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在构建场地，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      for (BuildWarMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      BuildWarMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(BuildWarMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (BuildWarMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      BuildWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      if (match.phase() == BuildWarMatch.Phase.PICKING) {
         return match.handleThemeChat(player, message);
      }
      if (match.phase() == BuildWarMatch.Phase.GUESSING) {
         return match.handleGuess(player, message);
      }
      if (match.phase() == BuildWarMatch.Phase.SCORING) {
         return match.handleScoreChat(player, message);
      }
      return false;
   }

   public boolean openIfPlaying(ServerPlayer player) {
      BuildWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      if (match.phase() == BuildWarMatch.Phase.SCORING) {
         BuildWarVoteGui.open(this.ctx, player, match);
         return true;
      }
      if (match.phase() == BuildWarMatch.Phase.REVIEW) {
         this.ctx.send(player, match.drawing() ? "&7正在回放画作链，请观看当前绘画组。" : "&7正在回放建筑链，请观看当前建筑组。");
         return true;
      }
      this.ctx.send(player, (match.drawing() ? "&7绘画战争进行中（" : "&7建筑战争进行中（") + match.phase().name() + "）。");
      return true;
   }

   public Plot plotAt(BlockPos pos) {
      BuildWarMatch match = this.matchAt(pos);
      if (match == null) {
         return null;
      }
      for (UUID seat : match.seats()) {
         Plot plot = match.boundPlot(seat);
         if (plot != null && plot.contains(pos) && match.contains(seat)) {
            // not quite - we need the plot that contains pos among all match plots
         }
      }
      return this.plotContaining(pos, match);
   }

   public Plot plotContaining(BlockPos pos, BuildWarMatch match) {
      for (BuildGroup group : match.groups()) {
         if (group.plot().contains(pos)) {
            return group.plot();
         }
      }
      return null;
   }

   public BuildWarMatch matchAt(BlockPos pos) {
      for (BuildWarMatch match : this.byId.values()) {
         for (BuildGroup group : match.groups()) {
            if (group.plot().contains(pos)) {
               return match;
            }
         }
      }
      return null;
   }

   public boolean isRestrictedPos(ServerPlayer player, BlockPos pos) {
      BuildWarMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      Plot plot = match.boundPlot(player.getUUID());
      if (match.drawing()) {
         return true;
      }
      return plot == null || !plot.contains(pos);
   }
}
