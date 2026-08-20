package net.exmo.sreGame.games.dontdo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class IslandManager {
   public static final int MAX_ISLANDS = 4;
   private static final int BLOCKS_PER_TICK = 14000;
   private static final int STRIDE = 512;

   private final GameContext ctx;
   private final List<Island> islands = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public IslandManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public Island acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (Island island : this.islands) {
         if (island.state() == Island.State.IDLE) {
            island.setState(Island.State.IN_USE);
            return island;
         }
      }
      if (this.islands.size() >= MAX_ISLANDS) {
         return null;
      }
      Island created = this.ensure(this.islands.size(), level);
      created.setState(Island.State.IN_USE);
      return created;
   }

   public boolean prepare(Island island, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || island == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      island.resetSeed(ThreadLocalRandom.current().nextLong());
      this.preloadChunks(level, island);
      this.queue.addLast(new FillTask(island, whenReady));
      return true;
   }

   public float progress() {
      FillTask task = this.queue.peekFirst();
      return task == null ? 1.0F : task.progress();
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
         if (task.island.state() != Island.State.IN_USE) {
            this.queue.removeFirst();
            task.fire();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.island);
            task.fire();
         }
      }
   }

   public void release(Island island) {
      if (island == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, island);
      }
      island.setState(Island.State.IDLE);
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private Island ensure(int slot, ServerLevel level) {
      while (this.islands.size() <= slot) {
         int index = this.islands.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().dontDoOriginX() + index * STRIDE,
            0,
            this.ctx.config().dontDoOriginZ()
         );
         this.islands.add(new Island(index, origin));
         SreGame.LOGGER.info("Dont-do island slot {} at {}", index, origin);
      }
      return this.islands.get(slot);
   }

   private void preloadChunks(ServerLevel level, Island island) {
      int minCx = island.origin().getX() >> 4;
      int minCz = island.origin().getZ() >> 4;
      int maxCx = (island.origin().getX() + IslandGenerator.TOTAL - 1) >> 4;
      int maxCz = (island.origin().getZ() + IslandGenerator.TOTAL - 1) >> 4;
      for (int cx = minCx; cx <= maxCx; cx++) {
         for (int cz = minCz; cz <= maxCz; cz++) {
            LevelChunk chunk = level.getChunk(cx, cz);
            chunk.setUnsaved(true);
         }
      }
   }

   private void clearEntities(ServerLevel level, Island island) {
      BlockPos o = island.origin();
      AABB box = new AABB(
         o.getX(), IslandGenerator.MIN_Y, o.getZ(),
         o.getX() + IslandGenerator.TOTAL, IslandGenerator.MAX_Y + 4, o.getZ() + IslandGenerator.TOTAL
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private static final class FillTask {
      final Island island;
      final Runnable doneCallback;
      boolean done;
      boolean fired;
      int phase;
      int x;
      int y = IslandGenerator.MIN_Y;
      int z;
      int treeX;
      int treeZ;
      final int totalBlocks = IslandGenerator.TOTAL * IslandGenerator.TOTAL
         * (IslandGenerator.MAX_Y - IslandGenerator.MIN_Y + 1);
      int placed;

      FillTask(Island island, Runnable doneCallback) {
         this.island = island;
         this.doneCallback = doneCallback;
         this.x = island.origin().getX();
         this.z = island.origin().getZ();
      }

      float progress() {
         return Math.min(1.0F, this.placed / (float) Math.max(1, this.totalBlocks));
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         if (this.phase == 0) {
            int used = this.stepTerrain(level, budget, pos);
            if (this.phase == 1) {
               this.treeX = 4;
               this.treeZ = 4;
            }
            return used;
         }
         return this.stepTrees(level, budget);
      }

      private int stepTerrain(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         int minX = this.island.origin().getX();
         int minZ = this.island.origin().getZ();
         int maxX = minX + IslandGenerator.TOTAL - 1;
         int maxZ = minZ + IslandGenerator.TOTAL - 1;
         while (used < budget && this.phase == 0) {
            BlockState want = IslandGenerator.blockAt(this.island, this.x, this.y, this.z);
            if (level.getBlockState(pos.set(this.x, this.y, this.z)).getBlock() != want.getBlock()) {
               level.setBlock(pos, want, 2);
            }
            used++;
            this.placed++;
            this.y++;
            if (this.y > IslandGenerator.MAX_Y) {
               this.y = IslandGenerator.MIN_Y;
               this.z++;
               if (this.z > maxZ) {
                  this.z = minZ;
                  this.x++;
                  if (this.x > maxX) {
                     this.phase = 1;
                  }
               }
            }
         }
         return used;
      }

      private int stepTrees(ServerLevel level, int budget) {
         int used = 0;
         while (used < budget && this.phase == 1) {
            if ((this.treeX + this.treeZ * 3) % 11 == 0
               && ((this.island.seed() >> ((this.treeX * 13 + this.treeZ) & 31)) & 3) != 0) {
               used += Math.max(1, IslandGenerator.placeTree(level, this.island, this.treeX, this.treeZ));
            } else {
               used++;
            }
            this.treeX += 6;
            if (this.treeX >= IslandGenerator.PLAY - 4) {
               this.treeX = 4 + (this.treeZ & 1) * 3;
               this.treeZ += 6;
               if (this.treeZ >= IslandGenerator.PLAY - 4) {
                  this.phase = 2;
                  this.done = true;
               }
            }
         }
         return Math.max(1, used);
      }

      void fire() {
         if (this.fired) {
            return;
         }
         this.fired = true;
         if (this.doneCallback != null) {
            this.doneCallback.run();
         }
      }
   }
}
