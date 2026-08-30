package net.exmo.sreGame.games.nametagwar;

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

public final class NameTagWarArenaManager {
   public static final int MAX_ARENAS = 8;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<NameTagWarArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public NameTagWarArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().nameTagWarPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Name-tag-war arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public NameTagWarArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (NameTagWarArena arena : this.arenas) {
         if (arena.state() == NameTagWarArena.State.IDLE) {
            arena.setState(NameTagWarArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      NameTagWarArena created = this.ensure(this.arenas.size());
      created.setState(NameTagWarArena.State.IN_USE);
      return created;
   }

   public boolean prepare(NameTagWarArena arena, NameTagWarArena.Layout layout, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || arena == null || layout == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      this.queue.addLast(new FillTask(arena, layout, whenReady));
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
         if (task.arena.state() != NameTagWarArena.State.IN_USE) {
            this.queue.removeFirst();
            task.finish();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.doneFlag) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena);
            task.arena.remember(task.layout.borderSize());
            task.arena.markClean();
            task.finish();
         }
      }
   }

   public void release(NameTagWarArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena);
      }
      arena.setState(NameTagWarArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (NameTagWarArena arena : this.arenas) {
         if (arena.state() != NameTagWarArena.State.IN_USE) {
            continue;
         }
         if (entity.getX() >= arena.origin().getX() && entity.getX() < arena.origin().getX() + NameTagWarArena.MAX_SIZE
            && entity.getZ() >= arena.origin().getZ() && entity.getZ() < arena.origin().getZ() + NameTagWarArena.MAX_SIZE
            && entity.getY() >= arena.basinY() - 2 && entity.getY() <= arena.wallTop() + 4) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private NameTagWarArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         int stride = NameTagWarArena.MAX_SIZE + 32;
         BlockPos origin = new BlockPos(
            this.ctx.config().nameTagWarOriginX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().nameTagWarOriginZ()
         );
         this.arenas.add(new NameTagWarArena(index, origin));
      }
      return this.arenas.get(slot);
   }

   private void clearEntities(ServerLevel level, NameTagWarArena arena) {
      int border = arena.lastBorder();
      int x0 = arena.minX(border);
      int z0 = arena.minZ(border);
      AABB box = new AABB(
         x0, arena.basinY() - 1, z0,
         x0 + border, arena.wallTop() + 2, z0 + border
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private static final class FillTask {
      final NameTagWarArena arena;
      final NameTagWarArena.Layout layout;
      final Runnable done;
      boolean fired;
      boolean doneFlag;
      boolean placing;
      int x;
      int y;
      int z;
      int clearX0;
      int clearZ0;
      int clearSize;
      int clearMinY;
      int clearMaxY;

      FillTask(NameTagWarArena arena, NameTagWarArena.Layout layout, Runnable done) {
         this.arena = arena;
         this.layout = layout;
         this.done = done;
         this.clearSize = Math.max(layout.borderSize(), arena.lastBorder());
         this.clearX0 = arena.minX(this.clearSize);
         this.clearZ0 = arena.minZ(this.clearSize);
         this.clearMinY = arena.basinY();
         this.clearMaxY = arena.wallTop();
         this.x = this.clearX0;
         this.y = this.clearMinY;
         this.z = this.clearZ0;
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         while (used < budget && !this.doneFlag) {
            if (!this.placing) {
               level.setBlock(pos.set(this.x, this.y, this.z), Blocks.AIR.defaultBlockState(), 2);
               used++;
               this.advanceClear();
            } else {
               BlockState want = this.arena.structureWant(this.x, this.y, this.z, this.layout.borderSize());
               if (level.getBlockState(pos.set(this.x, this.y, this.z)).getBlock() != want.getBlock()) {
                  level.setBlock(pos, want, 2);
               }
               used++;
               this.advancePlace();
            }
         }
         return used;
      }

      private void advanceClear() {
         this.y++;
         if (this.y > this.clearMaxY) {
            this.y = this.clearMinY;
            this.z++;
            if (this.z >= this.clearZ0 + this.clearSize) {
               this.z = this.clearZ0;
               this.x++;
               if (this.x >= this.clearX0 + this.clearSize) {
                  this.placing = true;
                  this.x = this.arena.minX(this.layout.borderSize());
                  this.y = this.arena.basinY();
                  this.z = this.arena.minZ(this.layout.borderSize());
               }
            }
         }
      }

      private void advancePlace() {
         int size = this.layout.borderSize();
         int x0 = this.arena.minX(size);
         int z0 = this.arena.minZ(size);
         this.y++;
         if (this.y > this.arena.wallTop()) {
            this.y = this.arena.basinY();
            this.z++;
            if (this.z >= z0 + size) {
               this.z = z0;
               this.x++;
               if (this.x >= x0 + size) {
                  this.doneFlag = true;
               }
            }
         }
      }

      void finish() {
         if (this.fired) {
            return;
         }
         this.fired = true;
         if (this.done != null) {
            this.done.run();
         }
      }
   }
}
