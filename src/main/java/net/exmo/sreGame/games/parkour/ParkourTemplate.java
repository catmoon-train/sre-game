package net.exmo.sreGame.games.parkour;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Relative parkour snippet. Local +X is along the current heading.
 */
public final class ParkourTemplate {
   public enum Kind {
      STYLE,
      ICE,
      SLAB,
      PANE,
      FENCE,
      SLIME,
      LADDER,
      END
   }

   public record Cell(int dx, int dy, int dz, Kind kind) {
   }

   private final String id;
   private final String label;
   private final double difficulty;
   private final List<Cell> cells;

   public ParkourTemplate(String id, String label, double difficulty, List<Cell> cells) {
      this.id = id;
      this.label = label;
      this.difficulty = difficulty;
      this.cells = List.copyOf(cells);
   }

   public String id() {
      return this.id;
   }

   public String label() {
      return this.label;
   }

   public double difficulty() {
      return this.difficulty;
   }

   public List<Cell> cells() {
      return this.cells;
   }

   public BlockPos world(BlockPos origin, int hx, int hz, Cell cell) {
      return new BlockPos(
         origin.getX() + hx * cell.dx + (-hz) * cell.dz,
         origin.getY() + cell.dy,
         origin.getZ() + hz * cell.dx + hx * cell.dz
      );
   }

   public BlockState state(Cell cell, ParkourStyle style, int hx, int hz) {
      return switch (cell.kind) {
         case ICE -> Blocks.PACKED_ICE.defaultBlockState();
         case SLAB -> Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
         case PANE -> Blocks.GLASS_PANE.defaultBlockState();
         case FENCE -> Blocks.OAK_FENCE.defaultBlockState();
         case SLIME -> Blocks.SLIME_BLOCK.defaultBlockState();
         case LADDER -> facingLadder(hx, hz, cell);
         case STYLE, END -> style.randomBlock().defaultBlockState();
      };
   }

   public boolean walkable(Cell cell) {
      return cell.kind != Kind.LADDER;
   }

   public boolean jumpsReachable() {
      int px = 0;
      int py = 0;
      int pz = 0;
      ParkourJumps.Special from = null;
      boolean any = false;
      for (Cell cell : this.cells) {
         if (!this.walkable(cell)) {
            continue;
         }
         if (!ParkourJumps.reachable(cell.dx - px, cell.dy - py, cell.dz - pz, from)) {
            return false;
         }
         px = cell.dx;
         py = cell.dy;
         pz = cell.dz;
         from = switch (cell.kind) {
            case ICE -> ParkourJumps.Special.ICE;
            case SLAB -> ParkourJumps.Special.SLAB;
            case PANE -> ParkourJumps.Special.PANE;
            case FENCE -> ParkourJumps.Special.FENCE;
            case SLIME -> ParkourJumps.Special.SLIME;
            default -> null;
         };
         any = true;
      }
      return any;
   }

   private static BlockState facingLadder(int hx, int hz, Cell cell) {
      int fx;
      int fz;
      if (cell.dz != 0) {
         int s = Integer.signum(cell.dz);
         fx = (-hz) * s;
         fz = hx * s;
      } else {
         fx = -hx;
         fz = -hz;
      }
      Direction facing = Direction.NORTH;
      if (fx > 0) {
         facing = Direction.EAST;
      } else if (fx < 0) {
         facing = Direction.WEST;
      } else if (fz > 0) {
         facing = Direction.SOUTH;
      }
      return Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
   }

   public static Cell c(int dx, int dy, int dz) {
      return new Cell(dx, dy, dz, Kind.STYLE);
   }

   public static Cell c(int dx, int dy, int dz, Kind kind) {
      return new Cell(dx, dy, dz, kind);
   }

   public static ParkourTemplate of(String id, String label, double difficulty, Cell... cells) {
      List<Cell> list = new ArrayList<>();
      for (Cell cell : cells) {
         list.add(cell);
      }
      return new ParkourTemplate(id, label, difficulty, list);
   }
}
