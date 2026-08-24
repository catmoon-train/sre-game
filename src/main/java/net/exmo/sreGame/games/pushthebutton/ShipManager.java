package net.exmo.sreGame.games.pushthebutton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

public final class ShipManager {
   public static final int MAX_SHIPS = 8;
   private static final int BLOCKS_PER_TICK = 9000;
   private static final int STRIDE = Ship.SIZE_X + 24;

   private final GameContext ctx;
   private final List<Ship> ships = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public ShipManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().pushTheButtonPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i);
      }
      SreGame.LOGGER.info("Push-the-button ships ready: {} in {}", this.ships.size(), level.dimension().location());
   }

   public Ship acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (Ship ship : this.ships) {
         if (ship.state() == Ship.State.IDLE) {
            ship.setState(Ship.State.IN_USE);
            return ship;
         }
      }
      if (this.ships.size() >= MAX_SHIPS) {
         return null;
      }
      Ship created = this.ensure(this.ships.size());
      created.setState(Ship.State.IN_USE);
      return created;
   }

   public boolean prepare(Ship ship, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || ship == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(ship, batch));
      batch.left = 1;
      ship.markDirty();
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
         if (task.ship.state() != Ship.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.ship);
            task.ship.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(Ship ship) {
      if (ship == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, ship);
      }
      ship.setState(Ship.State.IDLE);
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   public boolean contains(Entity entity) {
      if (entity == null) {
         return false;
      }
      for (Ship ship : this.ships) {
         if (ship.state() == Ship.State.IN_USE && ship.contains(entity.getX(), entity.getY(), entity.getZ())) {
            return true;
         }
      }
      return false;
   }

   private Ship ensure(int slot) {
      while (this.ships.size() <= slot) {
         int index = this.ships.size();
         BlockPos origin = new BlockPos(
            this.ctx.config().pushTheButtonOriginX() + index * STRIDE,
            this.ctx.config().originY(),
            this.ctx.config().pushTheButtonOriginZ()
         );
         this.ships.add(new Ship(index, origin));
      }
      return this.ships.get(slot);
   }

   private void clearEntities(ServerLevel level, Ship ship) {
      AABB box = new AABB(
         ship.minX() - 2, ship.minY() - 1, ship.minZ() - 2,
         ship.maxX() + 3, ship.maxY() + 3, ship.maxZ() + 3
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   static BlockState palette(int lx, int ly, int lz) {
      int w = Ship.SIZE_X;
      int d = Ship.SIZE_Z;
      int h = Ship.HEIGHT;
      if (ly < 0 || ly >= h) {
         return Blocks.AIR.defaultBlockState();
      }
      if (ly == 0) {
         if (lx >= 1 && lx <= 7) {
            return Blocks.RED_CONCRETE.defaultBlockState();
         }
         if (lx >= 9 && lx <= 30 && lz >= 1 && lz <= 7) {
            return Blocks.LIME_TERRACOTTA.defaultBlockState();
         }
         if (lx >= 9 && lx <= 30 && lz >= 25 && lz <= d - 2) {
            return Blocks.WHITE_TERRACOTTA.defaultBlockState();
         }
         if (lx >= 9 && lx <= 30 && lz >= 8 && lz <= 23) {
            return ((lx + lz) % 2 == 0 ? Blocks.CYAN_TERRACOTTA : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
         }
         if (lx >= 32) {
            return Blocks.DARK_PRISMARINE.defaultBlockState();
         }
         return Blocks.GRAY_CONCRETE.defaultBlockState();
      }
      if (ly == h - 1) {
         if (lx % 6 == 3 && lz % 6 == 3) {
            return Blocks.SEA_LANTERN.defaultBlockState();
         }
         return Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
      }
      boolean outer = lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1;
      if (outer) {
         if (ly >= 3 && ly <= 4 && !isCorner(lx, lz, w, d) && (lx + lz) % 4 == 0) {
            return (lx <= 8 ? Blocks.RED_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS).defaultBlockState();
         }
         return (lx <= 8 ? Blocks.RED_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE).defaultBlockState();
      }
      if (lx == 8 && !(lz >= 14 && lz <= 17 && ly >= 1 && ly <= 3)) {
         return Blocks.CYAN_CONCRETE.defaultBlockState();
      }
      if (lx == 31 && !(lz >= 14 && lz <= 17 && ly >= 1 && ly <= 3)) {
         return Blocks.CYAN_CONCRETE.defaultBlockState();
      }
      if (lz == 8 && lx >= 9 && lx <= 30 && !(lx >= 18 && lx <= 21 && ly <= 3)) {
         return Blocks.QUARTZ_PILLAR.defaultBlockState();
      }
      if (lz == 24 && lx >= 9 && lx <= 30 && !(lx >= 18 && lx <= 21 && ly <= 3)) {
         return Blocks.WHITE_CONCRETE.defaultBlockState();
      }
      if (lx >= 1 && lx <= 7) {
         if (isAirlockGlass(lx, ly, lz)) {
            return Blocks.IRON_BARS.defaultBlockState();
         }
      }
      if (lx == 20 && lz == 16 && ly == 1) {
         return Blocks.REDSTONE_BLOCK.defaultBlockState();
      }
      if (lx == 20 && lz == 16 && ly == 2) {
         return Blocks.STONE_BUTTON.defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.FLOOR)
            .setValue(ButtonBlock.FACING, Direction.NORTH);
      }
      if (lx >= 9 && lx <= 30 && lz >= 8 && lz <= 23 && ly == 1
         && (lx + lz) % 5 == 0 && !(lx == 20 && lz == 16)
         && !((lx == 12 || lx == 15 || lx == 25 || lx == 28) && (lz == 11 || lz == 20))) {
         return Blocks.CYAN_CARPET.defaultBlockState();
      }
      if (lx >= 18 && lx <= 22 && lz >= 14 && lz <= 18 && ly == 1
         && !(lx == 20 && lz == 16)) {
         return Blocks.QUARTZ_SLAB.defaultBlockState();
      }
      if (ly == 1 && ((lx == 12 || lx == 15 || lx == 25 || lx == 28) && (lz == 11 || lz == 20))) {
         return Blocks.OAK_STAIRS.defaultBlockState();
      }
      if (ly == 1 && ((lx == 13 || lx == 27) && (lz == 12 || lz == 19))) {
         return Blocks.OAK_SLAB.defaultBlockState();
      }
      if (lx >= 33 && lx <= 36 && lz >= 15 && lz <= 17 && ly == 1) {
         return Blocks.GOLD_BLOCK.defaultBlockState();
      }
      if (lx == 35 && lz == 16 && ly == 2) {
         return Blocks.LECTERN.defaultBlockState();
      }
      if (lz == d - 2 && ly >= 2 && ly < 2 + Ship.CANVAS_H) {
         for (int i = 0; i < Ship.CANVAS_COUNT; i++) {
            int min = Ship.canvasMinLx(i);
            if (lx == min - 1 || lx == min + Ship.CANVAS_W) {
               return Blocks.BLACK_CONCRETE.defaultBlockState();
            }
            if (lx >= min && lx < min + Ship.CANVAS_W) {
               return Blocks.WHITE_CONCRETE.defaultBlockState();
            }
         }
      }
      if (lz == 2 && ly >= 2 && ly <= 4) {
         for (int i = 0; i < Ship.BIO_STATIONS; i++) {
            int t = Ship.bioTemplateMinLx(i);
            int c = Ship.bioCopyMinLx(i);
            if (lx >= t && lx < t + 3 || lx >= c && lx < c + 3) {
               return Blocks.WHITE_CONCRETE.defaultBlockState();
            }
         }
      }
      if (ly == 1 && ((lx == 12 || lx == 28) && (lz == 10 || lz == 21))) {
         return Blocks.SEA_LANTERN.defaultBlockState();
      }
      if (ly == 2 && lx == 16 && lz == 9) {
         return Blocks.SMOOTH_QUARTZ.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private static boolean isCorner(int lx, int lz, int w, int d) {
      return (lx == 0 || lx == w - 1) && (lz == 0 || lz == d - 1);
   }

   private static boolean isAirlockGlass(int lx, int ly, int lz) {
      if (ly < 1 || ly > 4) {
         return false;
      }
      for (int i = 0; i < 3; i++) {
         int z0 = 9 + i * 5;
         int z1 = z0 + 3;
         boolean wall = lx == 2 || lx == 6 || lz == z0 || lz == z1;
         if (wall && lx >= 2 && lx <= 6 && lz >= z0 && lz <= z1) {
            return true;
         }
      }
      return false;
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
      final Ship ship;
      final Batch batch;
      boolean done;
      int x;
      int y;
      int z;

      FillTask(Ship ship, Batch batch) {
         this.ship = ship;
         this.batch = batch;
         this.x = ship.origin().getX();
         this.y = ship.origin().getY();
         this.z = ship.origin().getZ();
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         int minX = this.ship.origin().getX();
         int minY = this.ship.origin().getY();
         int minZ = this.ship.origin().getZ();
         int maxX = minX + Ship.SIZE_X - 1;
         int maxY = minY + Ship.HEIGHT - 1;
         int maxZ = minZ + Ship.SIZE_Z - 1;
         while (used < budget && !this.done) {
            int lx = this.x - minX;
            int ly = this.y - minY;
            int lz = this.z - minZ;
            BlockState want = palette(lx, ly, lz);
            pos.set(this.x, this.y, this.z);
            if (!level.getBlockState(pos).equals(want)) {
               level.setBlock(pos, want, 2);
            }
            used++;
            this.x++;
            if (this.x > maxX) {
               this.x = minX;
               this.z++;
               if (this.z > maxZ) {
                  this.z = minZ;
                  this.y++;
                  if (this.y > maxY) {
                     this.done = true;
                  }
               }
            }
         }
         return used;
      }
   }
}
