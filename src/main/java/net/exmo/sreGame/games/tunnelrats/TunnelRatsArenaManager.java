package net.exmo.sreGame.games.tunnelrats;

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

/** Builds and reuses isolated Tunnel Rats mining fields without blocking a server tick. */
public final class TunnelRatsArenaManager {
   private static final int MAX_ARENAS = 4;
   private static final int BLOCKS_PER_TICK = 8000;
   private final GameContext ctx;
   private final List<TunnelRatsArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public TunnelRatsArenaManager(GameContext ctx) { this.ctx = ctx; }
   public ServerLevel level() { return this.ctx.config().world(this.ctx.server()); }
   public void pregen() { }

   public TunnelRatsArena acquire() {
      if (this.level() == null) return null;
      for (TunnelRatsArena arena : this.arenas) {
         if (arena.state() == TunnelRatsArena.State.IDLE) {
            arena.setState(TunnelRatsArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) return null;
      int index = this.arenas.size();
      TunnelRatsArena arena = new TunnelRatsArena(index, new BlockPos(
         this.ctx.config().tunnelRatsOriginX() + index * TunnelRatsArena.STRIDE,
         this.ctx.config().originY(), this.ctx.config().tunnelRatsOriginZ()
      ));
      arena.setState(TunnelRatsArena.State.IN_USE);
      this.arenas.add(arena);
      return arena;
   }

   public void prepare(TunnelRatsArena arena, TunnelRatsSettings settings, Runnable ready) {
      arena.configure(settings.arenaLength(), System.nanoTime() ^ ((long) arena.index() << 32));
      this.queue.addLast(new FillTask(arena, ready));
   }

   public void tick() {
      ServerLevel level = this.level();
      if (level == null) return;
      int budget = BLOCKS_PER_TICK;
      while (budget > 0 && !this.queue.isEmpty()) {
         FillTask task = this.queue.peekFirst();
         if (task.arena.state() != TunnelRatsArena.State.IN_USE) {
            this.queue.removeFirst();
            continue;
         }
         budget -= task.step(level, budget);
         if (task.done) {
            this.queue.removeFirst();
            clearEntities(level, task.arena);
            task.arena.populateSupplies(level);
            if (task.ready != null) task.ready.run();
         }
      }
   }

   public void release(TunnelRatsArena arena) {
      if (arena == null) return;
      this.queue.removeIf(task -> task.arena == arena);
      ServerLevel level = this.level();
      if (level != null) clearEntities(level, arena);
      arena.setState(TunnelRatsArena.State.IDLE);
   }

   private static void clearEntities(ServerLevel level, TunnelRatsArena arena) {
      AABB box = new AABB(arena.minX() - 2, arena.floorY() - 8, arena.minZ() - 2,
         arena.maxX() + 3, arena.topY() + 13, arena.maxZ() + 3);
      for (Entity entity : level.getEntities((Entity) null, box, entity -> !(entity instanceof Player))) entity.discard();
   }

   private static final class FillTask {
      final TunnelRatsArena arena;
      final Runnable ready;
      final int minX, maxX, minY, maxY, minZ, maxZ;
      int x, y, z;
      boolean done;

      FillTask(TunnelRatsArena arena, Runnable ready) {
         this.arena = arena;
         this.ready = ready;
         this.minX = arena.minX() - 1;
         this.maxX = arena.maxX() + 1;
         this.minY = arena.floorY() - 1;
         this.maxY = arena.topY() + 7;
         this.minZ = arena.minZ() - 1;
         this.maxZ = arena.maxZ() + 1;
         this.x = this.minX;
         this.y = this.minY;
         this.z = this.minZ;
      }

      int step(ServerLevel level, int budget) {
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
         int used = 0;
         while (used < budget && !this.done) {
            pos.set(this.x, this.y, this.z);
            boolean core = this.x >= this.arena.minX() && this.x <= this.arena.maxX()
               && this.z >= this.arena.minZ() && this.z <= this.arena.maxZ()
               && this.y >= this.arena.floorY() && this.y <= this.arena.topY();
            set(level, pos, core ? this.arena.blockAt(this.x, this.y, this.z) : Blocks.AIR.defaultBlockState());
            this.advance();
            used++;
         }
         return Math.max(1, used);
      }

      private void advance() {
         if (++this.y > this.maxY) {
            this.y = this.minY;
            if (++this.z > this.maxZ) {
               this.z = this.minZ;
               if (++this.x > this.maxX) this.done = true;
            }
         }
      }

      private static void set(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
         BlockEntity entity = level.getBlockEntity(pos);
         if (entity instanceof Container container) container.clearContent();
         level.setBlock(pos, state, 2);
      }
   }
}
