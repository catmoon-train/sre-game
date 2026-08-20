package net.exmo.sreGame.games.luckypillar;

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

public final class PillarArenaManager {
   public static final int MAX_ARENAS = 8;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<PillarArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public PillarArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().luckyPillarPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Lucky-pillar arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public PillarArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (PillarArena arena : this.arenas) {
         if (arena.state() == PillarArena.State.IDLE) {
            arena.setState(PillarArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      PillarArena created = this.ensure(this.arenas.size());
      created.setState(PillarArena.State.IN_USE);
      return created;
   }

   public boolean prepare(PillarArena arena, PillarArena.Layout layout, Runnable whenReady) {
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
         if (task.arena.state() != PillarArena.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena, task.layout);
            task.arena.remember(task.layout.borderSize(), task.layout.pillarHeight());
            task.arena.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(PillarArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena, null);
      }
      arena.setState(PillarArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (PillarArena arena : this.arenas) {
         if (arena.state() != PillarArena.State.IN_USE) {
            continue;
         }
         if (entity.getX() >= arena.origin().getX() && entity.getX() < arena.origin().getX() + PillarArena.MAX_SIZE
            && entity.getZ() >= arena.origin().getZ() && entity.getZ() < arena.origin().getZ() + PillarArena.MAX_SIZE
            && entity.getY() >= arena.basinY() - 2 && entity.getY() <= arena.wallTop(80) + 4) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private PillarArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         int stride = PillarArena.MAX_SIZE + 32;
         BlockPos origin = new BlockPos(
            this.ctx.config().luckyPillarOriginX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().luckyPillarOriginZ()
         );
         this.arenas.add(new PillarArena(index, origin));
      }
      return this.arenas.get(slot);
   }

   private void clearEntities(ServerLevel level, PillarArena arena, PillarArena.Layout layout) {
      int border = layout != null ? layout.borderSize() : arena.lastBorder();
      int height = layout != null ? layout.pillarHeight() : arena.lastHeight();
      int x0 = arena.minX(Math.max(border, arena.lastBorder()));
      int z0 = arena.minZ(Math.max(border, arena.lastBorder()));
      int size = Math.max(border, arena.lastBorder());
      AABB box = new AABB(
         x0, arena.basinY() - 1, z0,
         x0 + size, arena.wallTop(height) + 2, z0 + size
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
      final PillarArena arena;
      final PillarArena.Layout layout;
      final Batch batch;
      boolean done;
      boolean placing;
      int x;
      int y;
      int z;
      int clearX0;
      int clearZ0;
      int clearSize;
      int clearMinY;
      int clearMaxY;

      FillTask(PillarArena arena, PillarArena.Layout layout, Batch batch) {
         this.arena = arena;
         this.layout = layout;
         this.batch = batch;
         this.clearSize = Math.max(layout.borderSize(), arena.lastBorder());
         this.clearX0 = arena.minX(this.clearSize);
         this.clearZ0 = arena.minZ(this.clearSize);
         this.clearMinY = arena.basinY();
         this.clearMaxY = arena.wallTop(Math.max(layout.pillarHeight(), arena.lastHeight()));
         this.x = this.clearX0;
         this.y = this.clearMinY;
         this.z = this.clearZ0;
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         while (used < budget && !this.done) {
            if (!this.placing) {
               level.setBlock(pos.set(this.x, this.y, this.z), Blocks.AIR.defaultBlockState(), 2);
               used++;
               this.advanceClear();
            } else {
               BlockState want = this.want();
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
         if (this.y > this.arena.wallTop(this.layout.pillarHeight())) {
            this.y = this.arena.basinY();
            this.z++;
            if (this.z >= z0 + size) {
               this.z = z0;
               this.x++;
               if (this.x >= x0 + size) {
                  this.done = true;
               }
            }
         }
      }

      private BlockState want() {
         int size = this.layout.borderSize();
         int x0 = this.arena.minX(size);
         int z0 = this.arena.minZ(size);
         int x1 = this.arena.maxX(size);
         int z1 = this.arena.maxZ(size);
         int ly = this.y;
         if (ly == this.arena.basinY()) {
            return PillarArena.BASIN;
         }
         boolean wall = this.x == x0 || this.x == x1 || this.z == z0 || this.z == z1;
         if (wall) {
            return PillarArena.WALL;
         }
         if (ly == this.arena.floorY()) {
            if (this.layout.fishing()) {
               return Blocks.WATER.defaultBlockState();
            }
            return this.layout.floor();
         }
         if (ly > this.arena.floorY() && ly <= this.arena.floorY() + this.layout.pillarHeight()
            && this.isPillarColumn()) {
            return this.layout.pillar();
         }
         return Blocks.AIR.defaultBlockState();
      }

      private boolean isPillarColumn() {
         for (BlockPos base : this.arena.pillarBases(this.layout.players(), this.layout.borderSize())) {
            if (base.getX() == this.x && base.getZ() == this.z) {
               return true;
            }
         }
         return false;
      }
   }
}
