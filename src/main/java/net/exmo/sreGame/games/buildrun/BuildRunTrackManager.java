package net.exmo.sreGame.games.buildrun;

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

public final class BuildRunTrackManager {
   public static final int MAX = 8;
   private static final int BLOCKS_PER_TICK = 9000;
   private static final int STRIDE = BuildRunTrack.SIZE_Z + 16;

   private final GameContext ctx;
   private final List<BuildRunTrack> tracks = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public BuildRunTrackManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().youBuildRunPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("You-build-run tracks ready: {} in {}", this.tracks.size(), level.dimension().location());
   }

   public List<BuildRunTrack> acquire(int count) {
      ServerLevel level = this.level();
      if (level == null || count < 1) {
         return List.of();
      }
      List<BuildRunTrack> claimed = new ArrayList<>();
      for (BuildRunTrack track : this.tracks) {
         if (track.state() == BuildRunTrack.State.IDLE) {
            track.setState(BuildRunTrack.State.IN_USE);
            claimed.add(track);
            if (claimed.size() == count) {
               return claimed;
            }
         }
      }
      while (claimed.size() < count && this.tracks.size() < MAX) {
         BuildRunTrack created = this.ensure(this.tracks.size());
         created.setState(BuildRunTrack.State.IN_USE);
         claimed.add(created);
      }
      if (claimed.size() < count) {
         for (BuildRunTrack track : claimed) {
            track.setState(BuildRunTrack.State.IDLE);
         }
         return List.of();
      }
      return claimed;
   }

   public boolean prepare(List<BuildRunTrack> claimed, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || claimed.isEmpty()) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      for (BuildRunTrack track : claimed) {
         this.queue.addLast(new FillTask(track, batch));
         batch.left++;
         track.markDirty();
      }
      batch.finishIfEmpty();
      return batch.left > 0;
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
         if (task.track.state() != BuildRunTrack.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.track);
            task.track.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(List<BuildRunTrack> claimed) {
      ServerLevel level = this.level();
      for (BuildRunTrack track : claimed) {
         if (level != null) {
            this.clearEntities(level, track);
         }
         track.setState(BuildRunTrack.State.IDLE);
      }
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private BuildRunTrack ensure(int slot) {
      while (this.tracks.size() <= slot) {
         int index = this.tracks.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().youBuildRunOriginX(),
            this.ctx.config().originY(),
            this.ctx.config().youBuildRunOriginZ() + index * STRIDE
         );
         this.tracks.add(new BuildRunTrack(index, origin));
      }
      return this.tracks.get(slot);
   }

   private void clearEntities(ServerLevel level, BuildRunTrack track) {
      BlockPos o = track.origin();
      AABB box = new AABB(
         o.getX(), o.getY() - BuildRunTrack.PIT, o.getZ(),
         o.getX() + BuildRunTrack.SIZE_X, o.getY() + BuildRunTrack.HEIGHT + 2, o.getZ() + BuildRunTrack.SIZE_Z
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private static final class Batch {
      int left;
      final Runnable done;
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
      final BuildRunTrack track;
      final Batch batch;
      boolean done;
      int lx;
      int ly;
      int lz;

      FillTask(BuildRunTrack track, Batch batch) {
         this.track = track;
         this.batch = batch;
         this.lx = 0;
         this.ly = -BuildRunTrack.PIT;
         this.lz = 0;
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         BlockPos origin = this.track.origin();
         while (used < budget && !this.done) {
            BlockState want = this.track.palette(this.lx, this.ly, this.lz);
            pos.set(origin.getX() + this.lx, origin.getY() + this.ly, origin.getZ() + this.lz);
            if (level.getBlockState(pos).getBlock() != want.getBlock()) {
               level.setBlock(pos, want, 2);
            } else if (want.isAir() && !level.getBlockState(pos).isAir()) {
               level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            used++;
            this.ly++;
            if (this.ly >= BuildRunTrack.HEIGHT) {
               this.ly = -BuildRunTrack.PIT;
               this.lz++;
               if (this.lz >= BuildRunTrack.SIZE_Z) {
                  this.lz = 0;
                  this.lx++;
                  if (this.lx >= BuildRunTrack.SIZE_X) {
                     this.done = true;
                  }
               }
            }
         }
         return used;
      }
   }
}
