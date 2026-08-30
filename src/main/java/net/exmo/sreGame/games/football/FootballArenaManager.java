package net.exmo.sreGame.games.football;

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

public final class FootballArenaManager {
   private static final int MAX_ARENAS = 6;
   private static final int BLOCKS_PER_TICK = 9000;
   private final GameContext ctx;
   private final List<FootballArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();
   public FootballArenaManager(GameContext ctx) { this.ctx = ctx; }

   public ServerLevel level() { return this.ctx.config().world(this.ctx.server()); }
   public void pregen() { for (int i = 0; i < this.ctx.config().footballPregen(); i++) ensure(i); SreGame.LOGGER.info("Football arenas ready: {}", arenas.size()); }
   public FootballArena acquire() {
      if (level() == null) return null;
      for (FootballArena arena : arenas) if (arena.state() == FootballArena.State.IDLE) { arena.setState(FootballArena.State.IN_USE); return arena; }
      if (arenas.size() >= MAX_ARENAS) return null;
      FootballArena arena = ensure(arenas.size()); arena.setState(FootballArena.State.IN_USE); return arena;
   }
   public boolean prepare(FootballArena arena, Runnable ready) { if (arena == null || level() == null) { if (ready != null) ready.run(); return false; } queue.addLast(new FillTask(arena, ready)); return true; }
   public void release(FootballArena arena) { if (arena == null) return; ServerLevel level = level(); if (level != null) clearEntities(level, arena); arena.setState(FootballArena.State.IDLE); }
   public boolean contains(Entity entity) { return entity != null && arenas.stream().anyMatch(a -> a.state() == FootballArena.State.IN_USE && a.contains(entity.getX(), entity.getY(), entity.getZ())); }
   public void clearEntities(ServerLevel level, FootballArena arena) {
      AABB box = new AABB(arena.origin().getX(), arena.baseY() - 1, arena.origin().getZ(), arena.origin().getX() + FootballArena.SIZE_X, arena.fillMaxY() + 3, arena.origin().getZ() + FootballArena.SIZE_Z);
      for (Entity e : level.getEntities((Entity)null, box, e -> !(e instanceof Player))) e.discard();
   }
   public void tick() {
      ServerLevel level = level(); if (level == null) return;
      int budget = BLOCKS_PER_TICK; BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      while (budget > 0 && !queue.isEmpty()) { FillTask task = queue.peekFirst(); int used = task.step(level, budget, pos); budget -= Math.max(1, used); if (task.done) { queue.removeFirst(); clearEntities(level, task.arena); if (task.ready != null) task.ready.run(); } }
   }
   private FootballArena ensure(int slot) { while (arenas.size() <= slot) { int i = arenas.size(); arenas.add(new FootballArena(i, new BlockPos(ctx.config().footballOriginX() + i * FootballArena.STRIDE, ctx.config().originY(), ctx.config().footballOriginZ()))); } return arenas.get(slot); }
   private static final class FillTask {
      final FootballArena arena; final Runnable ready; int x, y, z; boolean placing, done;
      FillTask(FootballArena arena, Runnable ready) { this.arena = arena; this.ready = ready; x = arena.origin().getX(); y = arena.baseY(); z = arena.origin().getZ(); }
      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) { int used = 0; while (used < budget && !done) { pos.set(x,y,z); if (!placing) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); else { BlockState want = arena.structureWant(x,y,z); if (level.getBlockState(pos).getBlock() != want.getBlock()) level.setBlock(pos, want, 2); } used++; advance(); } return used; }
      void advance() { if (++y > arena.fillMaxY()) { y = arena.baseY(); if (++z >= arena.origin().getZ() + FootballArena.SIZE_Z) { z = arena.origin().getZ(); if (++x >= arena.origin().getX() + FootballArena.SIZE_X) { if (!placing) { placing = true; x = arena.origin().getX(); } else done = true; } } } }
   }
}
