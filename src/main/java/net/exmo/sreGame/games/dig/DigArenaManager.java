package net.exmo.sreGame.games.dig;

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

public final class DigArenaManager {
   public static final int MAX_ARENAS = 8;
   private static final int BLOCKS_PER_TICK = 9000;
   private static final int STRIDE = DigArena.SIZE + 24;

   private final GameContext ctx;
   private final List<DigArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public DigArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().digToDeathPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Dig-to-death arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public DigArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (DigArena arena : this.arenas) {
         if (arena.state() == DigArena.State.IDLE) {
            arena.setState(DigArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      DigArena created = this.ensure(this.arenas.size());
      created.setState(DigArena.State.IN_USE);
      return created;
   }

   public boolean prepare(DigArena arena, int layers, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || arena == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(arena, layers, batch));
      batch.left = 1;
      arena.markDirty();
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
         if (task.arena.state() != DigArena.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena, task.layers);
            task.arena.remember(task.layers);
            task.arena.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(DigArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena, arena.lastLayers());
      }
      arena.setState(DigArena.State.IDLE);
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (DigArena arena : this.arenas) {
         if (arena.state() == DigArena.State.IN_USE
            && arena.contains(entity.getX(), entity.getY(), entity.getZ(), arena.lastLayers())) {
            return true;
         }
      }
      return false;
   }

   private DigArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().digToDeathOriginX() + index * STRIDE,
            this.ctx.config().originY(),
            this.ctx.config().digToDeathOriginZ()
         );
         this.arenas.add(new DigArena(index, origin));
      }
      return this.arenas.get(slot);
   }

   private void clearEntities(ServerLevel level, DigArena arena, int layers) {
      AABB box = new AABB(
         arena.minX() - 2, arena.lavaY() - 1, arena.minZ() - 2,
         arena.maxX() + 3, arena.wallTop(layers) + 2, arena.maxZ() + 3
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
      final DigArena arena;
      final int layers;
      final Batch batch;
      boolean done;
      boolean placing;
      int x;
      int y;
      int z;
      int x0;
      int z0;
      int x1;
      int z1;
      int minY;
      int maxY;

      FillTask(DigArena arena, int layers, Batch batch) {
         this.arena = arena;
         this.layers = Math.max(2, Math.min(6, layers));
         this.batch = batch;
         int usedLayers = Math.max(this.layers, arena.lastLayers());
         this.x0 = arena.minX() - DigArena.PAD;
         this.z0 = arena.minZ() - DigArena.PAD;
         this.x1 = arena.maxX() + DigArena.PAD;
         this.z1 = arena.maxZ() + DigArena.PAD;
         this.minY = arena.lavaY();
         this.maxY = arena.wallTop(usedLayers);
         this.x = this.x0;
         this.y = this.minY;
         this.z = this.z0;
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         while (used < budget && !this.done) {
            if (!this.placing) {
               level.setBlock(pos.set(this.x, this.y, this.z), Blocks.AIR.defaultBlockState(), 2);
               used++;
               this.advance(false);
            } else {
               BlockState want = this.want();
               if (level.getBlockState(pos.set(this.x, this.y, this.z)).getBlock() != want.getBlock()) {
                  level.setBlock(pos, want, 2);
               }
               used++;
               this.advance(true);
            }
         }
         return used;
      }

      private void advance(boolean placing) {
         this.y++;
         if (this.y > this.maxY) {
            this.y = this.minY;
            this.z++;
            if (this.z > this.z1) {
               this.z = this.z0;
               this.x++;
               if (this.x > this.x1) {
                  if (!placing) {
                     this.placing = true;
                     this.x = this.x0;
                     this.y = this.minY;
                     this.z = this.z0;
                     this.maxY = this.arena.wallTop(this.layers);
                  } else {
                     this.done = true;
                  }
               }
            }
         }
      }

      private BlockState want() {
         boolean wall = this.x == this.x0 || this.x == this.x1 || this.z == this.z0 || this.z == this.z1;
         if (wall) {
            return DigArena.wall();
         }
         if (this.y == this.arena.lavaY()) {
            return DigArena.lava();
         }
         if (this.arena.isSnowLayer(this.y, this.layers)) {
            return DigArena.snow();
         }
         return Blocks.AIR.defaultBlockState();
      }
   }
}
