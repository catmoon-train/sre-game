package net.exmo.sreGame.games.chicken;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class TrackManager {
   public static final int MAX_TRACKS = 8;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<Track> tracks = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public TrackManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().chickenHorsePregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i, level);
      }
      SreGame.LOGGER.info("Chicken-horse tracks ready: {} in {}", this.tracks.size(), level.dimension().location());
   }

   public Track acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (Track track : this.tracks) {
         if (track.state() == Track.State.IDLE) {
            track.setState(Track.State.IN_USE);
            return track;
         }
      }
      if (this.tracks.size() >= MAX_TRACKS) {
         return null;
      }
      Track created = this.ensure(this.tracks.size(), level);
      created.setState(Track.State.IN_USE);
      return created;
   }

   public boolean prepare(Track track, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || track == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(track, batch));
      batch.left = 1;
      track.markDirty();
      return true;
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
         if (task.track.state() != Track.State.IN_USE) {
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

   public void release(Track track) {
      if (track == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, track);
      }
      track.setState(Track.State.IDLE);
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private Track ensure(int slot, ServerLevel level) {
      while (this.tracks.size() <= slot) {
         int index = this.tracks.size();
         int stride = TrackLayout.MAX_SIZE_X + 24;
         BlockPos origin = new BlockPos(
            this.ctx.config().chickenHorseOriginX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().chickenHorseOriginZ()
         );
         this.tracks.add(new Track(index, origin));
      }
      return this.tracks.get(slot);
   }

   private void clearEntities(ServerLevel level, Track track) {
      BlockPos o = track.origin();
      AABB box = new AABB(
         o.getX(), o.getY() - Track.PIT, o.getZ(),
         o.getX() + TrackLayout.MAX_SIZE_X, o.getY() + Track.HEIGHT + 2, o.getZ() + TrackLayout.MAX_SIZE_Z
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
      final Track track;
      final Batch batch;
      boolean done;
      int x;
      int y;
      int z;

      FillTask(Track track, Batch batch) {
         this.track = track;
         this.batch = batch;
         this.x = track.origin().getX();
         this.y = track.origin().getY() - Track.PIT;
         this.z = track.origin().getZ();
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         int minX = this.track.origin().getX();
         int minY = this.track.origin().getY() - Track.PIT;
         int minZ = this.track.origin().getZ();
         int maxX = minX + TrackLayout.MAX_SIZE_X - 1;
         int maxY = this.track.origin().getY() + Track.HEIGHT - 1;
         int maxZ = minZ + TrackLayout.MAX_SIZE_Z - 1;
         while (used < budget && !this.done) {
            int lx = this.x - minX;
            int ly = this.y - this.track.origin().getY();
            int lz = this.z - minZ;
            BlockState want = this.track.palette(lx, ly, lz);
            if (level.getBlockState(pos.set(this.x, this.y, this.z)).getBlock() != want.getBlock()) {
               level.setBlock(pos, want, 2);
            }
            used++;
            this.y++;
            if (this.y > maxY) {
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
   }
}
