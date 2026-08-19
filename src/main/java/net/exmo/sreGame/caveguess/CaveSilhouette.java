package net.exmo.sreGame.caveguess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class CaveSilhouette {
   private CaveSilhouette() {
   }

   public static void update(ServerLevel level, CaveArena arena) {
      if (level == null || arena == null) {
         return;
      }
      int width = arena.wallMaxX() - arena.wallMinX() + 1;
      int height = arena.wallMaxY() - arena.wallMinY() + 1;
      BlockState[][] cells = new BlockState[width][height];
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      AABB box = arena.stageBox();
      for (int x = arena.wallMinX(); x <= arena.wallMaxX(); x++) {
         for (int y = arena.wallMinY(); y <= arena.wallMaxY(); y++) {
            for (int z = arena.wallZ() + 2; z <= arena.oz() + arena.size() - 4; z++) {
               BlockState state = level.getBlockState(pos.set(x, y, z));
               if (occupies(state)) {
                  stamp(cells, arena, x, y, silhouetteOf(state));
                  break;
               }
            }
         }
      }
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
         if (entity instanceof Mob mob) {
            freeze(mob);
         }
         AABB bb = entity.getBoundingBox();
         int minX = Mth.floor(bb.minX);
         int maxX = Mth.floor(bb.maxX - 1.0E-4);
         int minY = Mth.floor(bb.minY);
         int maxY = Mth.floor(bb.maxY - 1.0E-4);
         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               stamp(cells, arena, x, y, Blocks.BLACK_CONCRETE.defaultBlockState());
            }
         }
      }
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            BlockState want = cells[x][y] == null ? Blocks.WHITE_CONCRETE.defaultBlockState() : cells[x][y];
            arena.paintWallCell(level, arena.wallMinX() + x, arena.wallMinY() + y, want);
         }
      }
   }

   public static void freeze(Mob mob) {
      mob.setNoAi(true);
      mob.setSilent(true);
      mob.setInvulnerable(true);
      mob.setPersistenceRequired();
   }

   private static void stamp(BlockState[][] cells, CaveArena arena, int x, int y, BlockState state) {
      int cx = x - arena.wallMinX();
      int cy = y - arena.wallMinY();
      if (cx < 0 || cy < 0 || cx >= cells.length || cy >= cells[0].length) {
         return;
      }
      cells[cx][cy] = state;
   }

   private static boolean occupies(BlockState state) {
      if (state == null || state.isAir()) {
         return false;
      }
      Block block = state.getBlock();
      return block != Blocks.BARRIER
         && block != Blocks.LIGHT
         && block != Blocks.STRUCTURE_VOID
         && block != Blocks.CAVE_AIR
         && block != Blocks.VOID_AIR;
   }

   private static BlockState silhouetteOf(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof StainedGlassBlock stained) {
         return concrete(stained.getColor());
      }
      if (block instanceof StainedGlassPaneBlock pane) {
         return concrete(pane.getColor());
      }
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
      DyeColor color = colorOf(path);
      if (color != null && color != DyeColor.WHITE && color != DyeColor.LIGHT_GRAY) {
         return concrete(color);
      }
      return Blocks.BLACK_CONCRETE.defaultBlockState();
   }

   private static BlockState concrete(DyeColor color) {
      return switch (color) {
         case ORANGE -> Blocks.ORANGE_CONCRETE.defaultBlockState();
         case MAGENTA -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
         case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
         case YELLOW -> Blocks.YELLOW_CONCRETE.defaultBlockState();
         case LIME -> Blocks.LIME_CONCRETE.defaultBlockState();
         case PINK -> Blocks.PINK_CONCRETE.defaultBlockState();
         case GRAY -> Blocks.GRAY_CONCRETE.defaultBlockState();
         case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
         case CYAN -> Blocks.CYAN_CONCRETE.defaultBlockState();
         case PURPLE -> Blocks.PURPLE_CONCRETE.defaultBlockState();
         case BLUE -> Blocks.BLUE_CONCRETE.defaultBlockState();
         case BROWN -> Blocks.BROWN_CONCRETE.defaultBlockState();
         case GREEN -> Blocks.GREEN_CONCRETE.defaultBlockState();
         case RED -> Blocks.RED_CONCRETE.defaultBlockState();
         case BLACK -> Blocks.BLACK_CONCRETE.defaultBlockState();
         default -> Blocks.BLACK_CONCRETE.defaultBlockState();
      };
   }

   private static DyeColor colorOf(String path) {
      if (path == null) {
         return null;
      }
      for (DyeColor color : DyeColor.values()) {
         if (path.startsWith(color.getName() + "_")) {
            return color;
         }
      }
      return null;
   }
}
