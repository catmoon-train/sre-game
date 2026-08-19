package net.exmo.sreGame.pillarpummel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class Bridge {
   public enum Axis {
      X, Z
   }

   final Axis axis;
   final int a;
   final int b;
   int owner = -1;
   boolean spawn;
   boolean disabled;

   Bridge(Axis axis, int a, int b) {
      this.axis = axis;
      this.a = a;
      this.b = b;
   }

   public boolean empty() {
      return this.owner < 0;
   }

   public boolean missing() {
      return this.disabled;
   }

   public List<BlockPos> blocks(PummelArena arena, int pillars) {
      List<BlockPos> out = new ArrayList<>(5);
      int y = arena.platformY();
      if (this.axis == Axis.X) {
         int z = arena.pillarZ(this.b, pillars);
         int x0 = arena.pillarX(this.a, pillars) + 1;
         for (int i = 0; i < 5; i++) {
            out.add(new BlockPos(x0 + i, y, z));
         }
      } else {
         int x = arena.pillarX(this.a, pillars);
         int z0 = arena.pillarZ(this.b, pillars) + 1;
         for (int i = 0; i < 5; i++) {
            out.add(new BlockPos(x, y, z0 + i));
         }
      }
      return out;
   }

   public BlockPos center(PummelArena arena, int pillars) {
      if (this.axis == Axis.X) {
         return new BlockPos(arena.pillarX(this.a, pillars) + 3, arena.platformY(), arena.pillarZ(this.b, pillars));
      }
      return new BlockPos(arena.pillarX(this.a, pillars), arena.platformY(), arena.pillarZ(this.b, pillars) + 3);
   }
}
