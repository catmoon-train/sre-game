package net.exmo.sreGame.games.fillinthewall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A single wall that travels toward the playing field. Holes are stored as (z, y) coordinate
 * pairs where z indexes the length axis and y indexes the height axis.
 */
public final class FillWall {
   public static final class Coord {
      public final int z;
      public final int y;

      public Coord(int z, int y) {
         this.z = z;
         this.y = y;
      }

      @Override
      public boolean equals(Object o) {
         if (!(o instanceof Coord c)) {
            return false;
         }
         return c.z == this.z && c.y == this.y;
      }

      @Override
      public int hashCode() {
         return this.z * 31 + this.y;
      }
   }

   private final int length;
   private final int height;
   private final Set<Coord> holes = new HashSet<>();
   private final List<Display.BlockDisplay> blocks = new ArrayList<>();
   private final List<Display.BlockDisplay> border = new ArrayList<>();
   private double currentX;
   private double startX;
   private double endX;
   private int timeRemaining;
   private int maxTime;
   private boolean spawned;
   private BlockState wallState;

   public FillWall(int length, int height) {
      this.length = length;
      this.height = height;
   }

   public Set<Coord> holes() {
      return this.holes;
   }

   public List<Display.BlockDisplay> blocks() {
      return this.blocks;
   }

   public List<Display.BlockDisplay> border() {
      return this.border;
   }

   public int length() {
      return this.length;
   }

   public int height() {
      return this.height;
   }

   public boolean isEmpty() {
      return this.holes.isEmpty();
   }

   public void setTimeRemaining(int ticks) {
      this.timeRemaining = ticks;
      this.maxTime = ticks;
   }

   public int timeRemaining() {
      return this.timeRemaining;
   }

   public boolean spawned() {
      return this.spawned;
   }

   public void generateHoles(int randomCount, int clusterCount, boolean randomizeFurther, int minimum, Random rng) {
      int total = randomCount + clusterCount;
      if (randomizeFurther) {
         int cap = randomCount + clusterCount;
         if (cap <= minimum) {
            total = rng.nextInt(0, cap + 1);
         } else {
            total = rng.nextInt(minimum, cap + 1);
         }
      }
      insertRandomHoles(Math.min(randomCount, total), rng);
      for (int i = 0; i < total - randomCount; i++) {
         Coord c = randomConnected(rng);
         if (c != null) {
            this.holes.add(c);
         } else {
            break;
         }
      }
   }

   private void insertRandomHoles(int count, Random rng) {
      Set<Coord> possible = new HashSet<>();
      for (int z = 0; z < this.length; z++) {
         for (int y = 0; y < this.height; y++) {
            possible.add(new Coord(z, y));
         }
      }
      possible.removeAll(this.holes);
      List<Coord> pool = new ArrayList<>(possible);
      for (int i = 0; i < count && !pool.isEmpty(); i++) {
         Coord c = pool.get(rng.nextInt(pool.size()));
         this.holes.add(c);
         pool.remove(c);
      }
   }

   private Coord randomConnected(Random rng) {
      if (this.holes.size() >= this.length * this.height) {
         return null;
      }
      List<Coord> existing = new ArrayList<>(this.holes);
      java.util.Collections.shuffle(existing, rng);
      for (Coord base : existing) {
         List<Coord> candidates = new ArrayList<>();
         for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               Coord c = new Coord(base.z + dz, base.y + dy);
               if (inBounds(c) && !this.holes.contains(c)) {
                  candidates.add(c);
               }
            }
         }
         if (!candidates.isEmpty()) {
            return candidates.get(rng.nextInt(candidates.size()));
         }
      }
      return null;
   }

   private boolean inBounds(Coord c) {
      return c.z >= 0 && c.z < this.length && c.y >= 0 && c.y < this.height;
   }

   public FillWall copy() {
      FillWall w = new FillWall(this.length, this.height);
      w.holes.addAll(this.holes);
      w.maxTime = this.maxTime;
      return w;
   }

   /** Spawn the wall as BlockDisplays at the back of the track. */
   public void spawn(ServerLevel level, BlockPos fieldOrigin, double trackStartX, double trackEndX,
                     BlockState wallState, BlockState borderState) {
      this.startX = trackStartX;
      this.endX = trackEndX;
      this.currentX = trackStartX;
      this.wallState = wallState;
      int baseZ = fieldOrigin.getZ();
      int baseY = fieldOrigin.getY();
      for (int z = 0; z < this.length; z++) {
         for (int y = 0; y < this.height; y++) {
            if (this.holes.contains(new Coord(z, y))) {
               continue;
            }
            Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) {
               continue;
            }
            display.setPos(trackStartX, baseY + y, baseZ + z);
            display.setBlockState(wallState);
            level.addFreshEntity(display);
            this.blocks.add(display);
         }
      }
      // Top and bottom borders spanning the length (along Z).
      for (int z = -1; z <= this.length; z++) {
         Display.BlockDisplay top = borderDisplay(level, trackStartX, baseY + this.height, baseZ + z, borderState);
         Display.BlockDisplay bottom = borderDisplay(level, trackStartX, baseY - 1, baseZ + z, borderState);
         this.border.add(top);
         this.border.add(bottom);
      }
      // Left and right borders spanning the height (along Y).
      for (int y = 0; y < this.height; y++) {
         Display.BlockDisplay left = borderDisplay(level, trackStartX, baseY + y, baseZ - 1, borderState);
         Display.BlockDisplay right = borderDisplay(level, trackStartX, baseY + y, baseZ + this.length, borderState);
         this.border.add(left);
         this.border.add(right);
      }
      this.spawned = true;
   }

   private static Display.BlockDisplay borderDisplay(ServerLevel level, double x, int y, int z, BlockState state) {
      Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
      if (display == null) {
         return null;
      }
      display.setPos(x, y, z);
      display.setBlockState(state);
      level.addFreshEntity(display);
      return display;
   }

   /** Advance the wall toward the field. Returns true when it has arrived. */
   public boolean tick() {
      if (!this.spawned || this.maxTime <= 0) {
         return false;
      }
      double travel = (this.endX - this.startX) / this.maxTime;
      this.currentX += travel;
      this.timeRemaining--;
      for (Display.BlockDisplay d : this.blocks) {
         if (d != null && !d.isRemoved()) {
            d.setPos(this.currentX, d.getY(), d.getZ());
         }
      }
      for (Display.BlockDisplay d : this.border) {
         if (d != null && !d.isRemoved()) {
            d.setPos(this.currentX, d.getY(), d.getZ());
         }
      }
      return this.timeRemaining <= 0 || this.currentX >= this.endX - 1.0E-4;
   }

   public void despawn() {
      for (Display.BlockDisplay d : this.blocks) {
         if (d != null && !d.isRemoved()) {
            d.discard();
         }
      }
      for (Display.BlockDisplay d : this.border) {
         if (d != null && !d.isRemoved()) {
            d.discard();
         }
      }
      this.blocks.clear();
      this.border.clear();
      this.spawned = false;
   }

   public BlockState wallState() {
      return this.wallState;
   }
}
