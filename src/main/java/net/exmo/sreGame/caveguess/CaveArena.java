package net.exmo.sreGame.caveguess;

import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.buildwar.PlotManager;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CaveArena {
   public static final String TEXT_TAG = "sre_cave_text";
   private static final int INNER_H = 12;

   private final Plot plot;

   public CaveArena(Plot plot) {
      this.plot = plot;
   }

   public Plot plot() {
      return this.plot;
   }

   public int ox() {
      return this.plot.origin().getX();
   }

   public int oy() {
      return this.plot.origin().getY();
   }

   public int oz() {
      return this.plot.origin().getZ();
   }

   public int size() {
      return this.plot.size();
   }

   public int wallZ() {
      return this.oz() + 16;
   }

   public int wallMinX() {
      return this.ox() + 8;
   }

   public int wallMaxX() {
      return this.ox() + this.size() - 9;
   }

   public int wallMinY() {
      return this.oy() + 2;
   }

   public int wallMaxY() {
      return this.oy() + 10;
   }

   public int boothMinZ() {
      return this.oz() + this.size() - 8;
   }

   public void build(ServerLevel level, CaveMode mode) {
      if (level == null) {
         return;
      }
      this.buildShell(level);
      if (mode == CaveMode.SHADOW) {
         this.buildShadow(level);
      } else {
         this.buildBooth(level);
         this.buildDisplayWall(level);
      }
   }

   public void resetShadow(ServerLevel level) {
      if (level == null) {
         return;
      }
      this.clearStage(level);
      this.paintWall(level, Blocks.WHITE_CONCRETE.defaultBlockState());
   }

   public void showText(ServerLevel level, String text) {
      this.clearText(level);
      if (level == null || text == null || text.isBlank()) {
         return;
      }
      Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
      if (display == null) {
         return;
      }
      Vec3 pos = this.textPos();
      display.setPos(pos.x, pos.y, pos.z);
      display.setYRot(180.0F);
      display.setText(TextUtil.color("&f" + CaveWords.wrap(text, 18)));
      display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
      display.setBackgroundColor(0xC8000000);
      display.setLineWidth(200);
      display.setBrightnessOverride(new Brightness(15, 15));
      display.setViewRange(2.0F);
      display.addTag(TEXT_TAG);
      level.addFreshEntity(display);
   }

   public void clearText(ServerLevel level) {
      if (level == null) {
         return;
      }
      AABB box = this.plotBox();
      for (Entity entity : level.getEntities((Entity) null, box, e -> e.getTags().contains(TEXT_TAG))) {
         entity.discard();
      }
   }

   public Vec3 viewingSpawn(int index, int total) {
      int n = Math.max(1, total);
      double span = Math.max(4.0, this.wallMaxX() - this.wallMinX() - 2.0);
      double t = n <= 1 ? 0.5 : Math.floorMod(index, n) / (double) (n - 1);
      double x = this.wallMinX() + 1.0 + t * span + 0.5;
      double y = this.oy() + 1.1;
      double z = this.oz() + 8.5;
      return new Vec3(x, y, z);
   }

   public Vec3 boothSpawn() {
      return new Vec3(this.ox() + this.size() / 2.0 + 0.5, this.oy() + 1.1, this.boothMinZ() + 3.5);
   }

   public Vec3 stageSpawn() {
      return new Vec3(
         (this.wallMinX() + this.wallMaxX()) / 2.0 + 0.5,
         this.oy() + 1.1,
         this.wallZ() + 8.5
      );
   }

   public Vec3 textPos() {
      return new Vec3(this.ox() + this.size() / 2.0 + 0.5, this.oy() + 5.5, this.oz() + 16.35);
   }

   public boolean inStage(BlockPos pos) {
      if (pos == null) {
         return false;
      }
      return pos.getX() >= this.wallMinX()
         && pos.getX() <= this.wallMaxX()
         && pos.getY() >= this.wallMinY()
         && pos.getY() <= this.wallMaxY() + 3
         && pos.getZ() >= this.wallZ() + 2
         && pos.getZ() <= this.oz() + this.size() - 4;
   }

   public boolean inViewing(double x, double y, double z) {
      return x >= this.ox() + 2
         && x < this.ox() + this.size() - 2
         && z >= this.oz() + 2
         && z <= this.wallZ() - 1.2
         && y >= this.oy()
         && y <= this.oy() + INNER_H + 1;
   }

   public boolean inBooth(double x, double y, double z) {
      double cx = this.ox() + this.size() / 2.0 + 0.5;
      return Math.abs(x - cx) <= 5.0
         && z >= this.boothMinZ() - 0.2
         && z <= this.oz() + this.size() - 1.5
         && y >= this.oy()
         && y <= this.oy() + INNER_H + 1;
   }

   public boolean inStageArea(double x, double y, double z) {
      return x >= this.wallMinX() - 0.5
         && x <= this.wallMaxX() + 1.5
         && z >= this.wallZ() + 1.2
         && z <= this.oz() + this.size() - 2.5
         && y >= this.oy()
         && y <= this.oy() + INNER_H + 1;
   }

   public AABB stageBox() {
      return new AABB(
         this.wallMinX(), this.wallMinY(), this.wallZ() + 2,
         this.wallMaxX() + 1, this.wallMaxY() + 4, this.oz() + this.size() - 3
      );
   }

   public void teleportViewing(ServerPlayer player, ServerLevel level, int index, int total) {
      Vec3 spawn = this.viewingSpawn(index, total);
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, 0.0F, 8.0F);
   }

   public void teleportBooth(ServerPlayer player, ServerLevel level) {
      Vec3 spawn = this.boothSpawn();
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, 180.0F, 8.0F);
   }

   public void teleportStage(ServerPlayer player, ServerLevel level) {
      Vec3 spawn = this.stageSpawn();
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, 180.0F, 12.0F);
   }

   public void paintWallCell(ServerLevel level, int x, int y, BlockState state) {
      if (level == null || x < this.wallMinX() || x > this.wallMaxX() || y < this.wallMinY() || y > this.wallMaxY()) {
         return;
      }
      PlotManager.put(level, new BlockPos(x, y, this.wallZ()), state);
   }

   public void paintWall(ServerLevel level, BlockState state) {
      for (int x = this.wallMinX(); x <= this.wallMaxX(); x++) {
         for (int y = this.wallMinY(); y <= this.wallMaxY(); y++) {
            PlotManager.put(level, new BlockPos(x, y, this.wallZ()), state);
         }
      }
   }

   private void buildShell(ServerLevel level) {
      int ox = this.ox();
      int oy = this.oy();
      int oz = this.oz();
      int s = this.size();
      int top = oy + INNER_H;
      BlockState floor = Blocks.DEEPSLATE.defaultBlockState();
      BlockState wall = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
      BlockState ceil = Blocks.DEEPSLATE_TILES.defaultBlockState();
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState lantern = Blocks.LANTERN.defaultBlockState();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = ox; x < ox + s; x++) {
         for (int z = oz; z < oz + s; z++) {
            boolean edge = x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
            PlotManager.put(level, pos.set(x, oy, z), floor);
            for (int y = oy + 1; y < top; y++) {
               PlotManager.put(level, pos.set(x, y, z), edge ? wall : air);
            }
            PlotManager.put(level, pos.set(x, top, z), ceil);
         }
      }
      PlotManager.put(level, new BlockPos(ox + 2, top, oz + 2), lantern);
      PlotManager.put(level, new BlockPos(ox + s - 3, top, oz + 2), lantern);
      PlotManager.put(level, new BlockPos(ox + 2, top, oz + s - 3), lantern);
      PlotManager.put(level, new BlockPos(ox + s - 3, top, oz + s - 3), lantern);
   }

   private void buildBooth(ServerLevel level) {
      int cx = this.ox() + this.size() / 2;
      int minX = cx - 4;
      int maxX = cx + 4;
      int minZ = this.boothMinZ();
      int maxZ = this.oz() + this.size() - 3;
      int minY = this.oy() + 1;
      int maxY = this.oy() + 5;
      BlockState barrier = Blocks.BARRIER.defaultBlockState();
      BlockState glass = Blocks.TINTED_GLASS.defaultBlockState();
      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            PlotManager.put(level, new BlockPos(x, y, minZ), y == minY + 2 && x > minX && x < maxX ? glass : barrier);
            PlotManager.put(level, new BlockPos(x, y, maxZ), barrier);
         }
      }
      for (int z = minZ; z <= maxZ; z++) {
         for (int y = minY; y <= maxY; y++) {
            PlotManager.put(level, new BlockPos(minX, y, z), barrier);
            PlotManager.put(level, new BlockPos(maxX, y, z), barrier);
         }
      }
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            PlotManager.put(level, new BlockPos(x, maxY, z), barrier);
         }
      }
   }

   private void buildDisplayWall(ServerLevel level) {
      int z = this.oz() + 17;
      BlockState board = Blocks.SMOOTH_QUARTZ.defaultBlockState();
      for (int x = this.wallMinX(); x <= this.wallMaxX(); x++) {
         for (int y = this.oy() + 3; y <= this.oy() + 8; y++) {
            PlotManager.put(level, new BlockPos(x, y, z), board);
         }
      }
   }

   private void buildShadow(ServerLevel level) {
      int wz = this.wallZ();
      BlockState partition = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
      BlockState barrier = Blocks.BARRIER.defaultBlockState();
      for (int x = this.ox() + 1; x < this.ox() + this.size() - 1; x++) {
         for (int y = this.oy() + 1; y <= this.oy() + INNER_H - 1; y++) {
            boolean screen = x >= this.wallMinX() && x <= this.wallMaxX()
               && y >= this.wallMinY() && y <= this.wallMaxY();
            PlotManager.put(level, new BlockPos(x, y, wz), screen
               ? Blocks.WHITE_CONCRETE.defaultBlockState()
               : partition);
         }
      }
      for (int z = this.oz() + 2; z < wz; z++) {
         for (int y = this.oy() + 1; y <= this.oy() + 4; y++) {
            PlotManager.put(level, new BlockPos(this.ox() + 1, y, z), barrier);
            PlotManager.put(level, new BlockPos(this.ox() + this.size() - 2, y, z), barrier);
         }
      }
      this.clearStage(level);
   }

   private void clearStage(ServerLevel level) {
      BlockState air = Blocks.AIR.defaultBlockState();
      for (int x = this.wallMinX(); x <= this.wallMaxX(); x++) {
         for (int z = this.wallZ() + 1; z <= this.oz() + this.size() - 4; z++) {
            for (int y = this.oy() + 1; y <= this.wallMaxY() + 3; y++) {
               PlotManager.put(level, new BlockPos(x, y, z), air);
            }
         }
      }
      AABB box = this.stageBox();
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         entity.discard();
      }
   }

   private AABB plotBox() {
      BlockPos o = this.plot.origin();
      return new AABB(
         o.getX(), o.getY(), o.getZ(),
         o.getX() + this.plot.size(), o.getY() + this.plot.height() + 1, o.getZ() + this.plot.size()
      );
   }
}
