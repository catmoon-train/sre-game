package net.exmo.sreGame.games.blockedcombat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class BlockedCombatArenaManager {
   private static final int MAX_ARENAS = 4;
   private static final int BLOCKS_PER_TICK = 8000;
   private final GameContext ctx;
   private final List<BlockedCombatArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public BlockedCombatArenaManager(GameContext ctx) { this.ctx = ctx; }
   public ServerLevel level() { return this.ctx.config().world(this.ctx.server()); }
   public void pregen() { }

   public BlockedCombatArena acquire() {
      if (this.level() == null) return null;
      for (BlockedCombatArena arena : this.arenas) if (arena.state() == BlockedCombatArena.State.IDLE) {
         arena.setState(BlockedCombatArena.State.IN_USE); return arena;
      }
      if (this.arenas.size() >= MAX_ARENAS) return null;
      int index = this.arenas.size();
      BlockedCombatArena arena = new BlockedCombatArena(index, new BlockPos(
         this.ctx.config().blockedCombatOriginX() + index * BlockedCombatArena.STRIDE,
         this.ctx.config().originY(), this.ctx.config().blockedCombatOriginZ()));
      arena.setState(BlockedCombatArena.State.IN_USE);
      this.arenas.add(arena);
      return arena;
   }

   public void prepare(BlockedCombatArena arena, int size, int tntScarcity, Runnable ready) {
      arena.configure(size, tntScarcity, System.nanoTime() ^ ((long) arena.index() << 32));
      this.queue.addLast(new FillTask(arena, ready));
   }

   public void tick() {
      ServerLevel level = this.level();
      if (level == null) return;
      int budget = BLOCKS_PER_TICK;
      while (budget > 0 && !this.queue.isEmpty()) {
         FillTask task = this.queue.peekFirst();
         if (task.arena.state() != BlockedCombatArena.State.IN_USE) { this.queue.removeFirst(); continue; }
         budget -= task.step(level, budget);
         if (task.done) { this.queue.removeFirst(); clearEntities(level, task.arena); if (task.ready != null) task.ready.run(); }
      }
   }

   public void release(BlockedCombatArena arena) {
      if (arena == null) return;
      this.queue.removeIf(task -> task.arena == arena);
      ServerLevel level = this.level();
      if (level != null) clearEntities(level, arena);
      arena.setState(BlockedCombatArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) return false;
      for (BlockedCombatArena arena : this.arenas) if (arena.state() == BlockedCombatArena.State.IN_USE
         && arena.contains(entity.getX(), entity.getY(), entity.getZ())) return true;
      return false;
   }

   private static void clearEntities(ServerLevel level, BlockedCombatArena arena) {
      AABB box = new AABB(arena.minX() - 2, arena.floorY() - 2, arena.minZ() - 2,
         arena.maxX() + 3, arena.topY() + 20, arena.maxZ() + 3);
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) entity.discard();
   }

   private static final class FillTask {
      final BlockedCombatArena arena;
      final Runnable ready;
      final int minX, maxX, minY, maxY, minZ, maxZ;
      int x, y, z;
      boolean done;
      FillTask(BlockedCombatArena arena, Runnable ready) {
         this.arena = arena; this.ready = ready;
         this.minX = arena.minX() - 1; this.maxX = arena.maxX() + 1;
         this.minY = arena.floorY() - 1; this.maxY = arena.topY() + 18;
         this.minZ = arena.minZ() - 1; this.maxZ = arena.maxZ() + 1;
         this.x = minX; this.y = minY; this.z = minZ;
      }
      int step(ServerLevel level, int budget) {
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
         int used = 0;
         while (used < budget && !done) {
            pos.set(x, y, z);
            boolean core = x >= arena.minX() && x <= arena.maxX() && z >= arena.minZ() && z <= arena.maxZ()
               && y >= arena.floorY() && y <= arena.topY() + 1;
            BlockStateChange.set(level, pos, core ? arena.blockAt(x, y, z) : Blocks.AIR.defaultBlockState());
            advance(); used++;
         }
         return Math.max(1, used);
      }
      void advance() {
         if (++y > maxY) { y = minY; if (++z > maxZ) { z = minZ; if (++x > maxX) done = true; } }
      }
   }

   /** Centralizes clearing container contents before replacing the block. */
   private static final class BlockStateChange {
      static void set(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
         BlockEntity entity = level.getBlockEntity(pos);
         if (entity instanceof Container container) container.clearContent();
         level.setBlock(pos, state, 2);
      }
   }
}
