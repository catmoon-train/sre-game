package net.exmo.sreGame.buildwar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PlotManager {
   public static final int MAX_PLOTS = 20;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<Plot> plots = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public PlotManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().pregen();
      for (int i = 0; i < count; i++) {
         this.ensurePlot(i, level);
      }
      SreGame.LOGGER.info("Build-war plots ready: {} in {}", this.plots.size(), level.dimension().location());
   }

   public List<Plot> acquire(int count) {
      ServerLevel level = this.level();
      if (level == null || count < 1 || count > MAX_PLOTS) {
         return List.of();
      }
      List<Plot> claimed = new ArrayList<>(count);
      for (Plot plot : this.plots) {
         if (plot.state() == Plot.State.IDLE) {
            plot.setState(Plot.State.IN_USE);
            claimed.add(plot);
            if (claimed.size() == count) {
               return claimed;
            }
         }
      }
      while (claimed.size() < count && this.plots.size() < MAX_PLOTS) {
         Plot created = this.ensurePlot(this.plots.size(), level);
         created.setState(Plot.State.IN_USE);
         claimed.add(created);
      }
      if (claimed.size() < count) {
         for (Plot plot : claimed) {
            plot.setState(Plot.State.IDLE);
         }
         return List.of();
      }
      return claimed;
   }

   public boolean prepare(List<Plot> claimed, boolean canvas, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      for (Plot plot : claimed) {
         if (plot.dirty()) {
            this.queue.addLast(FillTask.clear(plot, canvas, batch));
            batch.left++;
         } else if (canvas) {
            this.queue.addLast(FillTask.canvas(plot, batch));
            batch.left++;
         }
         plot.markDirty();
      }
      boolean queued = batch.left > 0;
      batch.finishIfEmpty();
      return queued;
   }

   public void tick() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int budget = BLOCKS_PER_TICK;
      while (budget > 0 && !this.queue.isEmpty()) {
         FillTask task = this.queue.peekFirst();
         if (task.plot.state() != Plot.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            if (task.kind == FillTask.Kind.CLEAR) {
               this.clearEntities(level, task.plot);
            }
            task.batch.completeOne();
         }
      }
   }

   public void release(List<Plot> claimed) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (Plot plot : claimed) {
         this.clearInterior(level, plot);
         this.clearEntities(level, plot);
         plot.markClean();
         plot.setState(Plot.State.IDLE);
      }
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public boolean isBuildWarLevel(ServerLevel level) {
      ServerLevel ours = this.level();
      return ours != null && level == ours;
   }

   private Plot ensurePlot(int slot, ServerLevel level) {
      while (this.plots.size() <= slot) {
         int index = this.plots.size();
         int size = this.ctx.config().plotSize();
         int gap = this.ctx.config().gap();
         int height = this.ctx.config().height();
         int stride = size + gap;
         BlockPos origin = new BlockPos(
            this.ctx.config().originX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().originZ()
         );
         Plot plot = new Plot(index, origin, size, height);
         this.buildShell(level, plot);
         plot.markClean();
         this.plots.add(plot);
      }
      return this.plots.get(slot);
   }

   private void buildShell(ServerLevel level, Plot plot) {
      BlockPos o = plot.origin();
      int size = plot.size();
      int height = plot.height();
      BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
      BlockState wall = Blocks.BLACK_CONCRETE.defaultBlockState();
      BlockState light = Blocks.GLOWSTONE.defaultBlockState();
      int minX = o.getX();
      int minY = o.getY();
      int minZ = o.getZ();
      int maxX = minX + size - 1;
      int maxZ = minZ + size - 1;
      int topY = minY + height;
      int thick = 2;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            put(level, pos.set(x, minY, z), floor);
            put(level, pos.set(x, topY, z), wall);
         }
      }
      for (int t = 1; t <= thick; t++) {
         for (int y = minY; y <= topY; y++) {
            for (int x = minX - t; x <= maxX + t; x++) {
               put(level, pos.set(x, y, minZ - t), wall);
               put(level, pos.set(x, y, maxZ + t), wall);
            }
            for (int z = minZ - t + 1; z <= maxZ + t - 1; z++) {
               put(level, pos.set(minX - t, y, z), wall);
               put(level, pos.set(maxX + t, y, z), wall);
            }
         }
      }
      put(level, pos.set(minX, minY, minZ), light);
      put(level, pos.set(maxX, minY, minZ), light);
      put(level, pos.set(minX, minY, maxZ), light);
      put(level, pos.set(maxX, minY, maxZ), light);
      this.clearInterior(level, plot);
   }

   public void clearInterior(ServerLevel level, Plot plot) {
      BlockPos o = plot.origin();
      int size = plot.size();
      int height = plot.height();
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
      BlockState light = Blocks.GLOWSTONE.defaultBlockState();
      int minX = o.getX();
      int minY = o.getY();
      int minZ = o.getZ();
      int maxX = minX + size - 1;
      int maxZ = minZ + size - 1;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
            put(level, pos.set(x, minY, z), corner ? light : floor);
            for (int y = minY + 1; y < minY + height; y++) {
               put(level, pos.set(x, y, z), air);
            }
         }
      }
      this.clearEntities(level, plot);
      plot.markClean();
   }

   public static void put(ServerLevel level, BlockPos pos, BlockState state) {
      if (level.getBlockState(pos).getBlock() == state.getBlock()) {
         return;
      }
      level.setBlock(pos, state, 2);
   }

   private void clearEntities(ServerLevel level, Plot plot) {
      BlockPos o = plot.origin();
      AABB box = new AABB(
         o.getX(), o.getY(), o.getZ(),
         o.getX() + plot.size(), o.getY() + plot.height() + 1, o.getZ() + plot.size()
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private static final class Batch {
      int left;
      Runnable done;
      boolean fired;

      Batch(Runnable done) {
         this.done = done;
      }

      void finishIfEmpty() {
         if (this.left <= 0) {
            this.fire();
         }
      }

      void completeOne() {
         this.left--;
         if (this.left <= 0) {
            this.fire();
         }
      }

      private void fire() {
         if (this.fired) {
            return;
         }
         this.fired = true;
         if (this.done != null) {
            this.done.run();
         }
      }
   }

   private static final class FillTask {
      enum Kind {
         CLEAR,
         CANVAS
      }

      final Plot plot;
      final Kind kind;
      final boolean canvas;
      final Batch batch;
      boolean done;
      int x;
      int z;
      int y;

      private FillTask(Plot plot, Kind kind, boolean canvas, Batch batch) {
         this.plot = plot;
         this.kind = kind;
         this.canvas = canvas;
         this.batch = batch;
         this.x = plot.origin().getX();
         this.z = plot.origin().getZ();
         this.y = plot.origin().getY();
      }

      static FillTask clear(Plot plot, boolean canvas, Batch batch) {
         return new FillTask(plot, Kind.CLEAR, canvas, batch);
      }

      static FillTask canvas(Plot plot, Batch batch) {
         FillTask task = new FillTask(plot, Kind.CANVAS, true, batch);
         task.x = plot.origin().getX() + 1;
         task.y = plot.origin().getY() + 2;
         task.z = plot.canvasWallZ();
         return task;
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         if (this.kind == Kind.CANVAS) {
            BlockState wall = canvasBlock();
            int maxX = this.plot.origin().getX() + this.plot.size() - 2;
            int maxY = this.plot.origin().getY() + this.plot.height() - 2;
            while (used < budget && !this.done) {
               put(level, pos.set(this.x, this.y, this.z), wall);
               used++;
               this.y++;
               if (this.y > maxY) {
                  this.y = this.plot.origin().getY() + 2;
                  this.x++;
                  if (this.x > maxX) {
                     this.done = true;
                  }
               }
            }
            return used;
         }
         BlockState air = Blocks.AIR.defaultBlockState();
         BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
         BlockState light = Blocks.GLOWSTONE.defaultBlockState();
         BlockState canvasWall = this.canvas ? canvasBlock() : air;
         int minX = this.plot.origin().getX();
         int minY = this.plot.origin().getY();
         int minZ = this.plot.origin().getZ();
         int maxX = minX + this.plot.size() - 1;
         int maxZ = minZ + this.plot.size() - 1;
         int top = minY + this.plot.height();
         int wallZ = this.plot.canvasWallZ();
         int cMinX = minX + 1;
         int cMaxX = maxX - 1;
         int cMinY = minY + 2;
         int cMaxY = top - 2;
         while (used < budget && !this.done) {
            boolean corner = (this.x == minX || this.x == maxX) && (this.z == minZ || this.z == maxZ);
            BlockState want;
            if (this.y == minY) {
               want = corner ? light : floor;
            } else if (this.canvas && this.z == wallZ && this.x >= cMinX && this.x <= cMaxX
               && this.y >= cMinY && this.y <= cMaxY) {
               want = canvasWall;
            } else {
               want = air;
            }
            put(level, pos.set(this.x, this.y, this.z), want);
            used++;
            this.y++;
            if (this.y >= top) {
               this.y = minY;
               this.z++;
               if (this.z > maxZ) {
                  this.z = minZ;
                  this.x++;
                  if (this.x > maxX) {
                     this.done = true;
                  }
               }
            }
         }
         return used;
      }

      private static BlockState canvasBlock() {
         return Blocks.WHITE_CONCRETE.defaultBlockState();
      }
   }
}
