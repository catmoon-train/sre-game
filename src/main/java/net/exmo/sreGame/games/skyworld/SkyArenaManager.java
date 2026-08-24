package net.exmo.sreGame.games.skyworld;

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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

public final class SkyArenaManager {
   public static final int MAX_ARENAS = 6;
   private static final int BLOCKS_PER_TICK = 6000;

   private final GameContext ctx;
   private final List<SkyArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public SkyArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().skyWorldPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("SkyWorld arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public SkyArena acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (SkyArena arena : this.arenas) {
         if (arena.state() == SkyArena.State.IDLE) {
            arena.setState(SkyArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      SkyArena created = this.ensure(this.arenas.size());
      created.setState(SkyArena.State.IN_USE);
      return created;
   }

   public boolean prepare(SkyArena arena, Runnable whenReady) {
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
      return true;
   }

   public void tick() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int budget = BLOCKS_PER_TICK;
      while (budget > 0 && !this.queue.isEmpty()) {
         FillTask task = this.queue.peekFirst();
         if (task.arena.state() != SkyArena.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.arena);
            task.batch.completeOne();
         }
      }
   }

   public void release(SkyArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena);
      }
      arena.setState(SkyArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (SkyArena arena : this.arenas) {
         if (arena.state() == SkyArena.State.IN_USE && arena.contains(entity.getX(), entity.getY(), entity.getZ())) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public void clearEntities(ServerLevel level, SkyArena arena) {
      AABB box = new AABB(
         arena.origin().getX(), arena.fillMinY() - 2, arena.origin().getZ(),
         arena.origin().getX() + SkyArena.STRIDE, arena.islandY() + 64,
         arena.origin().getZ() + SkyArena.STRIDE
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private SkyArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().skyWorldOriginX() + index * SkyArena.STRIDE,
            this.ctx.config().originY(),
            this.ctx.config().skyWorldOriginZ()
         );
         this.arenas.add(new SkyArena(index, origin));
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
      final SkyArena arena;
      final Batch batch;
      final int minX;
      final int maxX;
      final int minY;
      final int maxY;
      final int minZ;
      final int maxZ;
      final int minCx;
      final int maxCx;
      final int minCz;
      final int maxCz;
      final int minSy;
      final int maxSy;
      boolean done;
      boolean placing;
      boolean inBlocks;
      int index;
      int cx;
      int cz;
      int sy;
      int lx;
      int ly;
      int lz;
      int skipped;

      FillTask(SkyArena arena, Batch batch) {
         this.arena = arena;
         this.batch = batch;
         this.minX = arena.origin().getX();
         this.maxX = arena.origin().getX() + SkyArena.STRIDE - 1;
         this.minY = arena.fillMinY() - 2;
         this.maxY = arena.islandY() + 64;
         this.minZ = arena.origin().getZ();
         this.maxZ = arena.origin().getZ() + SkyArena.STRIDE - 1;
         this.minCx = this.minX >> 4;
         this.maxCx = this.maxX >> 4;
         this.minCz = this.minZ >> 4;
         this.maxCz = this.maxZ >> 4;
         this.minSy = this.minY >> 4;
         this.maxSy = this.maxY >> 4;
         this.cx = this.minCx;
         this.cz = this.minCz;
         this.sy = this.minSy;
      }

      int step(ServerLevel level, int budget) {
         int used = 0;
         if (!this.placing) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (used < budget && !this.placing) {
               used += this.clearStep(level, pos);
            }
            return Math.max(1, used);
         }
         List<SkyArena.Voxel> voxels = this.arena.voxels();
         while (used < budget && this.index < voxels.size()) {
            SkyArena.Voxel voxel = voxels.get(this.index);
            level.setBlock(voxel.pos(), voxel.state(), 3);
            this.index++;
            used++;
         }
         if (this.index >= voxels.size()) {
            this.arena.ensureChests(level);
            this.done = true;
         }
         return Math.max(1, used);
      }

      private int clearStep(ServerLevel level, BlockPos.MutableBlockPos pos) {
         if (!this.inBlocks) {
            LevelChunk chunk = level.getChunk(this.cx, this.cz);
            int y = this.sy << 4;
            int sectionIndex = chunk.getSectionIndex(y);
            if (sectionIndex >= 0 && sectionIndex < chunk.getSectionsCount()) {
               LevelChunkSection section = chunk.getSection(sectionIndex);
               if (section.hasOnlyAir()) {
                  this.nextSection();
                  return 1;
               }
            } else {
               this.nextSection();
               return 1;
            }
            this.inBlocks = true;
            this.lx = 0;
            this.ly = 0;
            this.lz = 0;
         }
         int wx = (this.cx << 4) + this.lx;
         int wy = (this.sy << 4) + this.ly;
         int wz = (this.cz << 4) + this.lz;
         int cost = 0;
         if (wx >= this.minX && wx <= this.maxX && wy >= this.minY && wy <= this.maxY && wz >= this.minZ && wz <= this.maxZ) {
            pos.set(wx, wy, wz);
            if (!level.getBlockState(pos).isAir()) {
               level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
               cost = 1;
            } else {
               this.skipped++;
               if (this.skipped >= 24) {
                  this.skipped = 0;
                  cost = 1;
               }
            }
         } else {
            this.skipped++;
            if (this.skipped >= 24) {
               this.skipped = 0;
               cost = 1;
            }
         }
         this.advanceLocal();
         return cost;
      }

      private void advanceLocal() {
         this.ly++;
         if (this.ly >= 16) {
            this.ly = 0;
            this.lz++;
            if (this.lz >= 16) {
               this.lz = 0;
               this.lx++;
               if (this.lx >= 16) {
                  this.nextSection();
               }
            }
         }
      }

      private void nextSection() {
         this.inBlocks = false;
         this.sy++;
         if (this.sy > this.maxSy) {
            this.sy = this.minSy;
            this.cz++;
            if (this.cz > this.maxCz) {
               this.cz = this.minCz;
               this.cx++;
               if (this.cx > this.maxCx) {
                  this.placing = true;
                  this.index = 0;
               }
            }
         }
      }
   }
}
