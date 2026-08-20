package net.exmo.sreGame.games.fraud;

import net.exmo.sreGame.games.buildwar.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/** 诈骗大师隔离小屋：建在大地块中央，玩家日常待在这里。 */
public final class BoothHut {
   public static final int SIZE = 11;
   public static final int HEIGHT = 7;

   private BoothHut() {
   }

   public static int originX(Plot plot) {
      return plot.origin().getX() + (plot.size() - SIZE) / 2;
   }

   public static int originY(Plot plot) {
      return plot.origin().getY();
   }

   public static int originZ(Plot plot) {
      return plot.origin().getZ() + (plot.size() - SIZE) / 2;
   }

   public static Vec3 spawn(Plot plot) {
      return new Vec3(
         originX(plot) + SIZE / 2.0 + 0.5,
         originY(plot) + 1.05,
         originZ(plot) + SIZE / 2.0 + 0.5
      );
   }

   public static boolean contains(Plot plot, double x, double y, double z) {
      int ox = originX(plot);
      int oy = originY(plot);
      int oz = originZ(plot);
      return x >= ox
         && x < ox + SIZE
         && z >= oz
         && z < oz + SIZE
         && y >= oy
         && y <= oy + HEIGHT + 0.5;
   }

   public static void teleport(net.minecraft.server.level.ServerPlayer player, ServerLevel level, Plot plot) {
      Vec3 spawn = spawn(plot);
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, 0.0F, 8.0F);
   }

   public static int canvasMinX(Plot plot) {
      return originX(plot) + 1;
   }

   public static int canvasMaxX(Plot plot) {
      return originX(plot) + SIZE - 2;
   }

   public static int canvasMinY(Plot plot) {
      return originY(plot) + 1;
   }

   public static int canvasMaxY(Plot plot) {
      return originY(plot) + HEIGHT - 3;
   }

   public static int canvasWallZ(Plot plot) {
      return originZ(plot) + SIZE - 1;
   }

   public static net.exmo.sreGame.games.draw.Canvas canvas(Plot plot) {
      return net.exmo.sreGame.games.draw.Canvas.of(
         plot,
         canvasMinX(plot),
         canvasMaxX(plot),
         canvasMinY(plot),
         canvasMaxY(plot),
         canvasWallZ(plot)
      );
   }

   public static void teleportCanvas(net.minecraft.server.level.ServerPlayer player, ServerLevel level, Plot plot) {
      double x = originX(plot) + SIZE / 2.0 + 0.5;
      double y = originY(plot) + 1.05;
      double z = canvasWallZ(plot) - 4.2;
      player.teleportTo(level, x, y, z, 0.0F, 6.0F);
   }

   public static void build(ServerLevel level, Plot plot, ColorCode color) {
      if (level == null || plot == null) {
         return;
      }
      int ox = originX(plot);
      int oy = originY(plot);
      int oz = originZ(plot);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int lx = 0; lx < SIZE; lx++) {
         for (int lz = 0; lz < SIZE; lz++) {
            for (int ly = 0; ly < HEIGHT; ly++) {
               put(level, pos.set(ox + lx, oy + ly, oz + lz), shell(lx, ly, lz));
            }
         }
      }
      furnish(level, ox, oy, oz, color == null ? ColorCode.RED : color);
   }

   private static BlockState shell(int lx, int ly, int lz) {
      boolean edgeX = lx == 0 || lx == SIZE - 1;
      boolean edgeZ = lz == 0 || lz == SIZE - 1;
      boolean corner = edgeX && edgeZ;
      boolean wall = edgeX || edgeZ;
      if (ly == 0) {
         if ((lx == 1 || lx == SIZE - 2) && (lz == 1 || lz == SIZE - 2)) {
            return Blocks.GLOWSTONE.defaultBlockState();
         }
         if (lx >= 4 && lx <= 6 && lz >= 4 && lz <= 6) {
            return Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
         }
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (ly == HEIGHT - 1) {
         if (wall) {
            Direction face = edgeZ && lz == 0 ? Direction.NORTH
               : edgeZ && lz == SIZE - 1 ? Direction.SOUTH
               : lx == 0 ? Direction.WEST : Direction.EAST;
            return Blocks.SPRUCE_STAIRS.defaultBlockState()
               .setValue(BlockStateProperties.HORIZONTAL_FACING, face)
               .setValue(BlockStateProperties.HALF, Half.BOTTOM);
         }
         return Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
      }
      if (wall) {
         if (corner) {
            return Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
         }
         if (lz == SIZE - 1 && !corner && ly >= 1 && ly <= HEIGHT - 3) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
         }
         if ((ly == 2 || ly == 3) && isWindow(lx, lz)) {
            return Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState();
         }
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (ly == HEIGHT - 2) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private static boolean isWindow(int lx, int lz) {
      if (lz == SIZE - 1) {
         return false;
      }
      if (lz == 0) {
         return lx >= 4 && lx <= 6;
      }
      if (lx == 0 || lx == SIZE - 1) {
         return lz >= 4 && lz <= 6;
      }
      return false;
   }

   private static void furnish(ServerLevel level, int ox, int oy, int oz, ColorCode color) {
      BlockState wool = named(color.wool());
      BlockState carpet = named(color.dye().getName() + "_carpet");
      BlockState bed = named(color.dye().getName() + "_bed");
      for (int x = 4; x <= 6; x++) {
         for (int z = 4; z <= 6; z++) {
            put(level, new BlockPos(ox + x, oy + 1, oz + z), carpet);
         }
      }
      put(level, new BlockPos(ox + 5, oy + 1, oz + 1), wool);
      put(level, new BlockPos(ox + 5, oy + 2, oz + 1), wool);
      putBed(level, ox + 2, oy + 1, oz + 2, bed, Direction.EAST);
      put(level, new BlockPos(ox + 8, oy + 1, oz + 3), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
      put(level, new BlockPos(ox + 8, oy + 1, oz + 2), Blocks.SPRUCE_STAIRS.defaultBlockState()
         .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
      put(level, new BlockPos(ox + 9, oy + 1, oz + 3), Blocks.FLOWER_POT.defaultBlockState());
      put(level, new BlockPos(ox + 2, oy + 1, oz + 3), Blocks.BOOKSHELF.defaultBlockState());
      put(level, new BlockPos(ox + 1, oy + 1, oz + 3), Blocks.LECTERN.defaultBlockState());
      put(level, new BlockPos(ox + 1, oy + 1, oz + 5), Blocks.BARREL.defaultBlockState());
      put(level, new BlockPos(ox + 5, oy + 4, oz + 5), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
      put(level, new BlockPos(ox + 9, oy + 1, oz + 5), Blocks.POTTED_RED_TULIP.defaultBlockState());
   }

   private static void putBed(ServerLevel level, int x, int y, int z, BlockState bed, Direction facing) {
      BlockState base = bed.hasProperty(BedBlock.FACING)
         ? bed.setValue(BedBlock.FACING, facing)
         : Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
      put(level, new BlockPos(x, y, z), base.setValue(BedBlock.PART, BedPart.FOOT));
      BlockPos head = new BlockPos(x, y, z).relative(facing);
      put(level, head, base.setValue(BedBlock.PART, BedPart.HEAD));
   }

   private static BlockState named(String id) {
      Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(id));
      return block == null || block == Blocks.AIR ? Blocks.WHITE_WOOL.defaultBlockState() : block.defaultBlockState();
   }

   private static void put(ServerLevel level, BlockPos pos, BlockState state) {
      level.setBlock(pos, state, 3);
   }
}
