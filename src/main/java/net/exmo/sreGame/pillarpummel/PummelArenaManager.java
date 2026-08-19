package net.exmo.sreGame.pillarpummel;

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

public final class PummelArenaManager {
   public static final int MAX_ARENAS = 6;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<PummelArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public PummelArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().pillarPummelPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Pillar-pummel arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public PummelArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (PummelArena arena : this.arenas) {
         if (arena.state() == PummelArena.State.IDLE) {
            arena.setState(PummelArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      PummelArena created = this.ensure(this.arenas.size());
      created.setState(PummelArena.State.IN_USE);
      return created;
   }

   public boolean prepare(PummelArena arena, PummelArena.Layout layout, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || arena == null || layout == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(arena, layout, batch));
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
         if (task.arena.state() != PummelArena.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena);
            task.arena.remember(task.layout);
            task.arena.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(PummelArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena);
      }
      arena.setState(PummelArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (PummelArena arena : this.arenas) {
         if (arena.state() == PummelArena.State.IN_USE && arena.contains(entity.getX(), entity.getY(), entity.getZ())) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private PummelArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         int stride = PummelArena.MAX_SIZE + 32;
         BlockPos origin = new BlockPos(
            this.ctx.config().pillarPummelOriginX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().pillarPummelOriginZ()
         );
         this.arenas.add(new PummelArena(index, origin));
      }
      return this.arenas.get(slot);
   }

   private void clearEntities(ServerLevel level, PummelArena arena) {
      AABB box = new AABB(
         arena.origin().getX(), arena.basinY() - 1, arena.origin().getZ(),
         arena.origin().getX() + PummelArena.MAX_SIZE, arena.wallTop(32) + 2,
         arena.origin().getZ() + PummelArena.MAX_SIZE
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
      final PummelArena arena;
      final PummelArena.Layout layout;
      final Batch batch;
      boolean done;
      boolean placing;
      int x;
      int y;
      int z;
      int minY;
      int maxY;

      FillTask(PummelArena arena, PummelArena.Layout layout, Batch batch) {
         this.arena = arena;
         this.layout = layout;
         this.batch = batch;
         this.minY = arena.basinY();
         this.maxY = arena.wallTop(16);
         this.x = arena.origin().getX();
         this.y = this.minY;
         this.z = arena.origin().getZ();
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         while (used < budget && !this.done) {
            pos.set(this.x, this.y, this.z);
            if (!this.placing) {
               level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
               used++;
               this.advance();
            } else {
               BlockState want = this.arena.structureWant(this.x, this.y, this.z, this.layout);
               if (level.getBlockState(pos).getBlock() != want.getBlock()) {
                  level.setBlock(pos, want, 2);
               }
               used++;
               this.advance();
            }
         }
         return used;
      }

      private void advance() {
         this.y++;
         if (this.y > this.maxY) {
            this.y = this.minY;
            this.z++;
            if (this.z >= this.arena.origin().getZ() + PummelArena.MAX_SIZE) {
               this.z = this.arena.origin().getZ();
               this.x++;
               if (this.x >= this.arena.origin().getX() + PummelArena.MAX_SIZE) {
                  if (!this.placing) {
                     this.placing = true;
                     this.x = this.arena.origin().getX();
                     this.y = this.minY;
                     this.z = this.arena.origin().getZ();
                  } else {
                     this.done = true;
                  }
               }
            }
         }
      }
   }
}
