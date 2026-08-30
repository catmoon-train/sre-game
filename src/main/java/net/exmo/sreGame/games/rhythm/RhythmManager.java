package net.exmo.sreGame.games.rhythm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;

public final class RhythmManager {
   private static final int SLOT_STRIDE = 128;

   private final GameContext ctx;
   private final ChartLibrary charts;
   private final Map<UUID, RhythmMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, RhythmMatch> byId = new ConcurrentHashMap<>();
   private final AtomicInteger slotCounter = new AtomicInteger();

   public RhythmManager(GameContext ctx) {
      this.ctx = ctx;
      this.charts = new ChartLibrary(ctx.configDir());
   }

   public ChartLibrary charts() {
      return this.charts;
   }

   public void load() {
      this.charts.load();
   }

   public RhythmMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public RhythmMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      RhythmSettings settings = room.rhythmSettings();
      RhythmChart chart = this.charts.resolve(settings.chart());
      double[][] origins = this.allocateOrigins(settings.mode(), seats.size());
      RhythmMatch match = new RhythmMatch(this.ctx, room, seats, chart, settings, origins);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      room.setState(RoomState.PLAYING);
      match.start();
      return match.id();
   }

   private double[][] allocateOrigins(RhythmSettings.Mode mode, int count) {
      int baseX = this.ctx.config().rhythmOriginX();
      int baseY = this.ctx.config().rhythmOriginY();
      int baseZ = this.ctx.config().rhythmOriginZ();
      double[][] origins = new double[count][];
      if (mode == RhythmSettings.Mode.VERSUS) {
         for (int i = 0; i < count; i++) {
            int slot = this.slotCounter.getAndIncrement();
            origins[i] = new double[]{baseX + (long) slot * SLOT_STRIDE, baseY, baseZ};
         }
         return origins;
      }
      int slot = this.slotCounter.getAndIncrement();
      double centerX = baseX + (long) slot * SLOT_STRIDE;
      for (int i = 0; i < count; i++) {
         double x = centerX;
         if (count == 2) {
            x = i == 0 ? centerX - 3 : centerX + 3;
         }
         origins[i] = new double[]{x, baseY, baseZ};
      }
      return origins;
   }

   public void tick() {
      for (RhythmMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      RhythmMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(RhythmMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (RhythmMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public void handleLeftClick(ServerPlayer player) {
      RhythmMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handleLeftClick(player);
      }
   }

   public void handleRightClick(ServerPlayer player) {
      RhythmMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handleRightClick(player);
      }
   }

   public boolean openIfPlaying(ServerPlayer player) {
      RhythmMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7节奏大师进行中：" + match.inputHint() + "。");
      return true;
   }
}
