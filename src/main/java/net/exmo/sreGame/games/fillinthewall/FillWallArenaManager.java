package net.exmo.sreGame.games.fillinthewall;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class FillWallArenaManager {
   public static final int MAX_ARENAS = 6;
   private static final int STRIDE = 48;

   private final GameContext ctx;
   private final List<FillWallArena> arenas = new ArrayList<>();

   public FillWallArenaManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().fillInTheWallPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Fill-in-the-wall arenas ready: {} in {}", this.arenas.size(), level.dimension().location());
   }

   public FillWallArena acquire() {
      for (FillWallArena arena : this.arenas) {
         if (arena.state() == FillWallArena.State.IDLE) {
            arena.setState(FillWallArena.State.IN_USE);
            return arena;
         }
      }
      if (this.arenas.size() >= MAX_ARENAS) {
         return null;
      }
      FillWallArena created = this.ensure(this.arenas.size());
      created.setState(FillWallArena.State.IN_USE);
      return created;
   }

   public boolean prepare(FillWallArena arena, FillWallArena.Layout layout, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || arena == null || layout == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      arena.prepare(level, layout, whenReady);
      return false;
   }

   public void release(FillWallArena arena) {
      if (arena == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, arena);
         arena.release(level);
      }
      arena.setState(FillWallArena.State.IDLE);
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (FillWallArena arena : this.arenas) {
         if (arena.state() == FillWallArena.State.IN_USE && arena.builtLayout() != null
            && arena.contains(entity.getX(), entity.getY(), entity.getZ(), arena.builtLayout())) {
            return true;
         }
      }
      return false;
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public void tick() {
   }

   private FillWallArena ensure(int slot) {
      while (this.arenas.size() <= slot) {
         int index = this.arenas.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().fillInTheWallOriginX() + index * STRIDE,
            this.ctx.config().originY(),
            this.ctx.config().fillInTheWallOriginZ()
         );
         this.arenas.add(new FillWallArena(index, origin));
      }
      return this.arenas.get(slot);
   }

   private void clearEntities(ServerLevel level, FillWallArena arena) {
      FillWallArena.Layout layout = arena.builtLayout();
      if (layout == null) {
         return;
      }
      int fx = arena.fieldX();
      int fy = arena.fieldY();
      int fz = arena.fieldZ();
      int len = layout.length();
      int h = layout.height();
      int stand = layout.standingDistance();
      int track = layout.trackLength();
      AABB box = new AABB(
         fx - track - 3, fy - 4, fz - 4,
         fx + stand + 4, fy + h + 6, fz + len + 5
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }
}
