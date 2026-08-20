package net.exmo.sreGame.games.pillarpummel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class PlotCell {
   public static final int BASE_HP = 3;
   public static final int FORT_HP = 6;
   public static final int TURRET_HP = 4;
   public static final int BLOCKGEN_HP = 4;

   final int cx;
   final int cz;
   int owner = -1;
   int hp;
   int maxHp = BASE_HP;
   boolean spawn;
   boolean disabled;
   boolean fort;
   boolean turret;
   boolean blockgen;
   int regenTicks;
   int turretTicks;
   BlockPos generator;

   PlotCell(int cx, int cz) {
      this.cx = cx;
      this.cz = cz;
   }

   public boolean empty() {
      return this.owner < 0;
   }

   public boolean owned() {
      return this.owner >= 0;
   }

   public boolean scores() {
      return this.owned() && !this.spawn;
   }

   public BlockPos center(PummelArena arena, int pillars) {
      return arena.platformCenter(this.cx, this.cz, pillars);
   }

   public List<BlockPos> floorBlocks(PummelArena arena, int pillars) {
      List<BlockPos> out = new ArrayList<>(25);
      BlockPos c = this.center(arena, pillars);
      int y = arena.platformY();
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            out.add(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
         }
      }
      return out;
   }

   public boolean floorPresent(int index) {
      if (this.hp <= 0) {
         return false;
      }
      if (this.hp >= 3) {
         return true;
      }
      if (this.hp == 2) {
         return index % 5 != 0;
      }
      return (index + index / 5) % 2 == 0;
   }

   void reset() {
      this.owner = -1;
      this.hp = 0;
      this.maxHp = BASE_HP;
      this.fort = false;
      this.turret = false;
      this.blockgen = false;
      this.regenTicks = 0;
      this.turretTicks = 0;
      this.generator = null;
   }
}
