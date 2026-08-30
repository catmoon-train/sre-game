package net.exmo.sreGame.games.hypixelsays;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class HypixelSaysArenaManager {
   private static final int MAX_ARENAS = 8;
   private final GameContext ctx;
   private final List<HypixelSaysArena> arenas = new ArrayList<>();
   public HypixelSaysArenaManager(GameContext ctx) { this.ctx = ctx; }
   public ServerLevel level() { return ctx.config().hypixelSaysWorld(ctx.server()); }
   public void pregen() { for (int i = 0; i < ctx.config().hypixelSaysPregen(); i++) ensure(i); }
   public HypixelSaysArena acquire() {
      ServerLevel level = level(); if (level == null) return null;
      for (HypixelSaysArena arena : arenas) if (!arena.inUse()) { arena.setInUse(true); arena.build(level); return arena; }
      if (arenas.size() >= MAX_ARENAS) return null;
      HypixelSaysArena arena = ensure(arenas.size()); arena.setInUse(true); arena.build(level); return arena;
   }
   public void release(HypixelSaysArena arena) { if (arena == null) return; ServerLevel level = level(); if (level != null) { arena.clearEntities(level); arena.clear(level); } arena.setInUse(false); }
   public boolean contains(Entity entity) { if (entity == null) return false; for (HypixelSaysArena arena : arenas) if (arena.inUse() && arena.contains(entity.getX(), entity.getY(), entity.getZ())) return true; return false; }
   private HypixelSaysArena ensure(int slot) {
      while (arenas.size() <= slot) { int index = arenas.size(); arenas.add(new HypixelSaysArena(index, new BlockPos(ctx.config().hypixelSaysOriginX() + (index % 4) * HypixelSaysArena.STRIDE, ctx.config().originY(), ctx.config().hypixelSaysOriginZ() + (index / 4) * HypixelSaysArena.STRIDE))); }
      return arenas.get(slot);
   }
}
