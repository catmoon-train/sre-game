package net.exmo.sreGame.games.dodgeball;

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

public final class DodgeballArenaManager {
   public static final int MAX_ARENAS = 6;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<DodgeballArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public DodgeballArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().dodgeballPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Dodgeball arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public DodgeballArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (DodgeballArena arena : this.arenas) {
         if (arena.state() == DodgeballArena.State.IDLE) {
            arena.setState(DodgeballArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      DodgeballArena created = this.ensure(this.arenas.size());
      created.setState(DodgeballArena.State.IN_USE);
      return created;
   }

   public boolean prepare(DodgeballArena arena, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || arena == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(arena, batch));
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
         if (task.arena.state() != DodgeballArena.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena);
            task.arena.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(DodgeballArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena);
      }
      arena.setState(DodgeballArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (DodgeballArena arena : this.arenas) {
         if (arena.state() == DodgeballArena.State.IN_USE && arena.contains(entity.getX(), entity.getY(), entity.getZ())) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public void clearEntities(ServerLevel level, DodgeballArena arena) {
      AABB box = new AABB(
         arena.origin().getX(), arena.lavaY() - 1, arena.origin().getZ(),
         arena.origin().getX() + DodgeballArena.SIZE_X, arena.fillMaxY() + 4,
         arena.origin().getZ() + DodgeballArena.SIZE_Z
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private DodgeballArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().dodgeballOriginX() + index * DodgeballArena.STRIDE,
            this.ctx.config().originY(),
            this.ctx.config().dodgeballOriginZ()
         );
         this.arenas.add(new DodgeballArena(index, origin));
      }
      return this.arenas.get(slot);
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
      final DodgeballArena arena;
      final Batch batch;
      boolean done;
      boolean placing;
      int x;
      int y;
      int z;
      int minY;
      int maxY;

      FillTask(DodgeballArena arena, Batch batch) {
         this.arena = arena;
         this.batch = batch;
         this.minY = arena.lavaY();
         this.maxY = arena.fillMaxY();
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
               BlockState want = this.arena.structureWant(this.x, this.y, this.z);
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
            if (this.z >= this.arena.origin().getZ() + DodgeballArena.SIZE_Z) {
               this.z = this.arena.origin().getZ();
               this.x++;
               if (this.x >= this.arena.origin().getX() + DodgeballArena.SIZE_X) {
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
