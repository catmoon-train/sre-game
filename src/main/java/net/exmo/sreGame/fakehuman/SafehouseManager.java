package net.exmo.sreGame.fakehuman;

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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;

public final class SafehouseManager {
   public static final int MAX_HOUSES = 8;
   private static final int BLOCKS_PER_TICK = 9000;

   private final GameContext ctx;
   private final List<Safehouse> houses = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   public SafehouseManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public void pregen() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = this.ctx.config().fakeHumanPregen();
      for (int i = 0; i < count; i++) {
         this.ensure(i, level);
      }
      SreGame.LOGGER.info("Fake-human safehouses ready: {} in {}", this.houses.size(), level.dimension().location());
   }

   public Safehouse acquire() {
      ServerLevel level = this.level();
      if (level == null) {
         return null;
      }
      for (Safehouse house : this.houses) {
         if (house.state() == Safehouse.State.IDLE) {
            house.setState(Safehouse.State.IN_USE);
            return house;
         }
      }
      if (this.houses.size() >= MAX_HOUSES) {
         return null;
      }
      Safehouse created = this.ensure(this.houses.size(), level);
      created.setState(Safehouse.State.IN_USE);
      return created;
   }

   public boolean prepare(Safehouse house, Runnable whenReady) {
      ServerLevel level = this.level();
      if (level == null || house == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return false;
      }
      Batch batch = new Batch(whenReady);
      this.queue.addLast(new FillTask(house, batch));
      batch.left = 1;
      house.markDirty();
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
         if (task.house.state() != Safehouse.State.IN_USE) {
            this.queue.removeFirst();
            task.batch.completeOne();
            continue;
         }
         int used = task.step(level, budget, pos);
         budget -= Math.max(1, used);
         if (task.done) {
            this.queue.removeFirst();
            this.clearEntities(level, task.house);
            task.house.markClean();
            task.batch.completeOne();
         }
      }
   }

   public void release(Safehouse house) {
      if (house == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level != null) {
         this.clearEntities(level, house);
      }
      house.setState(Safehouse.State.IDLE);
   }

   public ServerLevel level() {
      return this.ctx.config().world(this.ctx.server());
   }

   private Safehouse ensure(int slot, ServerLevel level) {
      while (this.houses.size() <= slot) {
         int index = this.houses.size();
         int stride = Safehouse.SIZE_X + 24;
         BlockPos origin = new BlockPos(
            this.ctx.config().fakeHumanOriginX() + index * stride,
            this.ctx.config().originY(),
            this.ctx.config().fakeHumanOriginZ()
         );
         Safehouse house = new Safehouse(index, origin);
         this.houses.add(house);
      }
      return this.houses.get(slot);
   }

   private void clearEntities(ServerLevel level, Safehouse house) {
      BlockPos o = house.origin();
      AABB box = new AABB(
         o.getX(), o.getY(), o.getZ(),
         o.getX() + Safehouse.SIZE_X, o.getY() + Safehouse.HEIGHT + 2, o.getZ() + Safehouse.SIZE_Z
      );
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   static BlockState palette(int lx, int ly, int lz) {
      int w = Safehouse.SIZE_X;
      int d = Safehouse.SIZE_Z;
      int h = Safehouse.HEIGHT;
      boolean yard = lz < 12;
      if (ly < 0 || ly >= h) {
         return Blocks.AIR.defaultBlockState();
      }
      boolean path = (lx == 15 || lx == 16) && lz >= 0 && lz <= 11;
      if (yard) {
         if (ly == 0) {
            return path ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
         }
         boolean edge = lx == 0 || lx == w - 1 || lz == 0;
         if (ly == 1 && edge && !(path && lz == 0)) {
            return Blocks.SPRUCE_FENCE.defaultBlockState();
         }
         if (ly == 1 && !edge && !path && (lx + lz * 3) % 5 == 0) {
            return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(lx + lz, 3));
         }
         if (ly == 2 && (lx == 3 || lx == w - 4) && (lz == 3 || lz == 9)) {
            return Blocks.LANTERN.defaultBlockState();
         }
         return Blocks.AIR.defaultBlockState();
      }
      if (ly == 0) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (ly == h - 1) {
         return Blocks.SNOW_BLOCK.defaultBlockState();
      }
      if (isFrontDoor(lx, ly, lz)) {
         return door(Blocks.IRON_DOOR.defaultBlockState(), lx, ly, Direction.SOUTH, lx == 15 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
      }
      if (isInnerDoor(lx, ly, lz)) {
         return door(Blocks.SPRUCE_DOOR.defaultBlockState(), lx, ly, Direction.SOUTH, lx == 15 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
      }
      if (isBackDoor(lx, ly, lz, d)) {
         return door(Blocks.IRON_DOOR.defaultBlockState(), lx, ly, Direction.NORTH, lx == 15 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
      }
      if (lz == d - 2 && (lx == 15 || lx == 16) && ly >= 1 && ly <= 2) {
         return Blocks.IRON_BARS.defaultBlockState();
      }
      if (isWindow(lx, ly, lz, w, d)) {
         return Blocks.GLASS_PANE.defaultBlockState();
      }
      if (isOuterWall(lx, lz, w, d) && !isFrontDoorGap(lx, ly, lz) && !isBackDoorGap(lx, ly, lz, d)) {
         if (isCornerPost(lx, lz, w, d) || ly == 1 && (lx + lz) % 4 == 0) {
            return Blocks.SPRUCE_LOG.defaultBlockState();
         }
         return Blocks.SNOW_BLOCK.defaultBlockState();
      }
      if (lz == 17 && (lx < 15 || lx > 16) && ly <= 4) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (lz == 28 && ly <= 4 && !(lx >= 13 && lx <= 18)) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if ((lx == 12 || lx == 19) && lz >= 29 && lz <= 40 && ly <= 4) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (ly == 1 && isBedFoot(lx, lz)) {
         return Blocks.WHITE_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.EAST)
            .setValue(BedBlock.PART, BedPart.FOOT);
      }
      if (ly == 1 && isBedHead(lx, lz)) {
         return Blocks.WHITE_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.EAST)
            .setValue(BedBlock.PART, BedPart.HEAD);
      }
      if (ly == 1 && lz >= 42 && lz <= 45 && lx >= 3 && lx <= 8) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (ly == h - 2 && ((lx == 2 || lx == w - 3) && (lz == 14 || lz == 22 || lz == 34 || lz == 44)
         || lx == 16 && (lz == 20 || lz == 36))) {
         return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
      }
      return Blocks.AIR.defaultBlockState();
   }

   private static BlockState door(BlockState base, int lx, int ly, Direction facing, DoorHingeSide hinge) {
      return base
         .setValue(DoorBlock.FACING, facing)
         .setValue(DoorBlock.HALF, ly == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
         .setValue(DoorBlock.HINGE, hinge)
         .setValue(DoorBlock.OPEN, false)
         .setValue(DoorBlock.POWERED, false);
   }

   private static boolean isFrontDoor(int lx, int ly, int lz) {
      return lz == 12 && (lx == 15 || lx == 16) && (ly == 1 || ly == 2);
   }

   private static boolean isFrontDoorGap(int lx, int ly, int lz) {
      return lz == 12 && (lx == 15 || lx == 16) && ly >= 1 && ly <= 3;
   }

   private static boolean isInnerDoor(int lx, int ly, int lz) {
      return lz == 17 && (lx == 15 || lx == 16) && (ly == 1 || ly == 2);
   }

   private static boolean isBackDoor(int lx, int ly, int lz, int d) {
      return lz == d - 1 && (lx == 15 || lx == 16) && (ly == 1 || ly == 2);
   }

   private static boolean isBackDoorGap(int lx, int ly, int lz, int d) {
      return lz == d - 1 && (lx == 15 || lx == 16) && ly >= 1 && ly <= 3;
   }

   private static boolean isWindow(int lx, int ly, int lz, int w, int d) {
      if (ly < 2 || ly > 3) {
         return false;
      }
      if (lz == 12 && (lx == 8 || lx == 9 || lx == 22 || lx == 23)) {
         return true;
      }
      if ((lx == 0 || lx == w - 1) && (lz == 20 || lz == 34)) {
         return true;
      }
      return lz == d - 1 && (lx == 8 || lx == 23);
   }

   private static boolean isOuterWall(int lx, int lz, int w, int d) {
      return lx == 0 || lx == w - 1 || lz == 12 || lz == d - 1;
   }

   private static boolean isCornerPost(int lx, int lz, int w, int d) {
      return (lx == 0 || lx == w - 1) && (lz == 12 || lz == d - 1);
   }

   private static boolean isBedFoot(int lx, int lz) {
      if (lz != 31 && lz != 34 && lz != 37 && lz != 40) {
         return false;
      }
      return lx == 5 || lx == 24;
   }

   private static boolean isBedHead(int lx, int lz) {
      if (lz != 31 && lz != 34 && lz != 37 && lz != 40) {
         return false;
      }
      return lx == 6 || lx == 25;
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
      final Safehouse house;
      final Batch batch;
      boolean done;
      int x;
      int y;
      int z;

      FillTask(Safehouse house, Batch batch) {
         this.house = house;
         this.batch = batch;
         this.x = house.origin().getX();
         this.y = house.origin().getY();
         this.z = house.origin().getZ();
      }

      int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         int used = 0;
         int minX = this.house.origin().getX();
         int minY = this.house.origin().getY();
         int minZ = this.house.origin().getZ();
         int maxX = minX + Safehouse.SIZE_X - 1;
         int maxY = minY + Safehouse.HEIGHT - 1;
         int maxZ = minZ + Safehouse.SIZE_Z - 1;
         while (used < budget && !this.done) {
            int lx = this.x - minX;
            int ly = this.y - minY;
            int lz = this.z - minZ;
            BlockState want = palette(lx, ly, lz);
            BlockState have = level.getBlockState(pos.set(this.x, this.y, this.z));
            if (!have.equals(want)) {
               level.setBlock(pos, want, 3);
            }
            used++;
            this.y++;
            if (this.y > maxY) {
               this.y = minY;
               this.z++;
               if (this.z > maxZ) {
                  this.z = minZ;
                  this.x++;
                  if (this.x > maxX) {
                     this.done = true;
                  }
               }
            }
         }
         return used;
      }
   }
}
