package net.exmo.sreGame.games.parkour;

import java.util.concurrent.ThreadLocalRandom;

public final class ParkourJumps {
   private ParkourJumps() {
   }

   public static int distance() {
      return weighted(new int[] {1, 2, 3, 4}, new int[] {10, 55, 34, 1});
   }

   public static int height() {
      return weighted(new int[] {1, 0, -1, -2}, new int[] {20, 65, 10, 5});
   }

   public static boolean special(boolean enabled) {
      if (!enabled) {
         return false;
      }
      return ThreadLocalRandom.current().nextInt(95) < 10;
   }

   public static Special pickSpecial() {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll < 50) {
         return Special.ICE;
      }
      if (roll < 80) {
         return Special.SLAB;
      }
      if (roll < 90) {
         return Special.PANE;
      }
      return Special.FENCE;
   }

   public static int sideOffset(int height, int distance) {
      int max = maxOffset(height, distance);
      double u1 = Math.max(1e-9, ThreadLocalRandom.current().nextDouble());
      double u2 = ThreadLocalRandom.current().nextDouble();
      double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2.0 * u2);
      int offset = (int) Math.round(gaussian);
      return Math.max(-max, Math.min(max, offset));
   }

   private static int maxOffset(int y, int distance) {
      return switch (y) {
         case 1 -> switch (distance) {
            case 1 -> 4;
            case 2 -> 3;
            default -> 2;
         };
         case 0 -> switch (distance) {
            case 1, 2 -> 4;
            case 3 -> 3;
            default -> 2;
         };
         case -1 -> switch (distance) {
            case 1, 2 -> 5;
            case 3 -> 4;
            default -> 3;
         };
         case -2 -> switch (distance) {
            case 1, 2 -> 5;
            default -> 4;
         };
         default -> 2;
      };
   }

   private static int weighted(int[] keys, int[] weights) {
      int total = 0;
      for (int w : weights) {
         total += w;
      }
      int roll = ThreadLocalRandom.current().nextInt(total);
      int acc = 0;
      for (int i = 0; i < keys.length; i++) {
         acc += weights[i];
         if (roll < acc) {
            return keys[i];
         }
      }
      return keys[0];
   }

   public enum Special {
      ICE, SLAB, PANE, FENCE
   }
}
