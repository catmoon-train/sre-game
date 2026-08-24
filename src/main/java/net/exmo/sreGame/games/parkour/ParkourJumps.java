package net.exmo.sreGame.games.parkour;

import java.util.concurrent.ThreadLocalRandom;

public final class ParkourJumps {
   private ParkourJumps() {
   }

   public static int distance() {
      return weighted(new int[] {1, 2, 3}, new int[] {15, 55, 30});
   }

   public static int height() {
      return weighted(new int[] {1, 0, -1, -2}, new int[] {18, 67, 10, 5});
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

   public static int clampDistance(int height, int distance) {
      if (height >= 1) {
         return Math.min(distance, 2);
      }
      return Math.min(distance, 3);
   }

   public static int sideOffset(int height, int distance) {
      int max = maxOffset(height, distance);
      if (distance >= 4 || height >= 1 && distance >= 3) {
         return 0;
      }
      double u1 = Math.max(1e-9, ThreadLocalRandom.current().nextDouble());
      double u2 = ThreadLocalRandom.current().nextDouble();
      double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2.0 * u2);
      int offset = (int) Math.round(gaussian);
      return Math.max(-max, Math.min(max, offset));
   }

   public static boolean reachable(int forward, int up, int side, Special from) {
      int fx = Math.abs(forward);
      int fz = Math.abs(side);
      if (fx == 0 && fz == 0) {
         return up != 0;
      }
      double horiz = Math.hypot(fx, fz);
      if (from == Special.SLIME) {
         if (fz > 1) {
            return false;
         }
         return up >= 2 && up <= 4 && fx >= 2 && fx <= 3;
      }
      if (from == Special.ICE) {
         if (up > 0) {
            return horiz <= 3.1;
         }
         return up >= -2 && horiz <= 5.1;
      }
      if (from == Special.PANE) {
         if (up > 0) {
            return false;
         }
         return up >= -1 && horiz <= 3.1;
      }
      if (from == Special.FENCE) {
         if (up > 1) {
            return false;
         }
         return up >= -1 && horiz <= 3.1;
      }
      if (from == Special.SLAB && up > 0) {
         return false;
      }
      if (up > 1) {
         return false;
      }
      if (up == 1) {
         return horiz <= 3.05;
      }
      if (up == 0) {
         return horiz <= 4.05;
      }
      if (up == -1) {
         return horiz <= 5.15;
      }
      if (up == -2) {
         return horiz <= 5.6;
      }
      if (up <= -3 && up >= -5) {
         return horiz <= 2.1;
      }
      return false;
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
      ICE, SLAB, PANE, FENCE, SLIME
   }
}
