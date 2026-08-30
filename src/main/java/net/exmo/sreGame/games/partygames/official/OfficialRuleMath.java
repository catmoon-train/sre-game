package net.exmo.sreGame.games.partygames.official;

/** Pure timing/physics rules shared by controllers and deterministic tests. */
public final class OfficialRuleMath {
   private OfficialRuleMath() { }

   public static int bellThreshold(int remainingTicks) {
      return remainingTicks <= 400 ? 5 : remainingTicks <= 800 ? 10 : remainingTicks <= 1200 ? 15 : 20;
   }

   public static int buttonThreshold(int elapsedTicks) {
      return elapsedTicks >= 600 ? 6 : elapsedTicks >= 450 ? 7 : elapsedTicks >= 300 ? 8 : 9;
   }

   public static double cannonWindAcceleration(int wind) {
      if (wind < -10 || wind > 10) throw new IllegalArgumentException("wind must be -10..10");
      // Original function stores Motion[2] at 1:100000 and changes it by wind*100.
      return -wind * 0.001;
   }

   public record Projectile(double x, double y, double z, double vx, double vy, double vz) { }

   /** One source-rule trajectory step: velocity, gravity and lateral wind, then vanilla-like drag. */
   public static Projectile cannonStep(Projectile value, int wind) {
      double vx = value.vx() * 0.99;
      double vy = (value.vy() - 0.05) * 0.99;
      double vz = (value.vz() + cannonWindAcceleration(wind)) * 0.99;
      return new Projectile(value.x() + value.vx(), value.y() + value.vy(), value.z() + value.vz(), vx, vy, vz);
   }
}
