package net.exmo.sreGame.games.partygames.official;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class OfficialRuleMathTest {
   @Test void bellThresholdChangesAtExactSourceTimes() {
      assertEquals(20, OfficialRuleMath.bellThreshold(1201));
      assertEquals(15, OfficialRuleMath.bellThreshold(1200));
      assertEquals(10, OfficialRuleMath.bellThreshold(800));
      assertEquals(5, OfficialRuleMath.bellThreshold(400));
   }

   @Test void buttonThresholdChangesAt15_22_5_30Seconds() {
      assertEquals(9, OfficialRuleMath.buttonThreshold(299));
      assertEquals(8, OfficialRuleMath.buttonThreshold(300));
      assertEquals(7, OfficialRuleMath.buttonThreshold(450));
      assertEquals(6, OfficialRuleMath.buttonThreshold(600));
   }

   @Test void cannonWindHasMirroredExtremeDeflection() {
      assertEquals(-0.01, OfficialRuleMath.cannonWindAcceleration(10), 1e-12);
      assertEquals(-OfficialRuleMath.cannonWindAcceleration(-10), OfficialRuleMath.cannonWindAcceleration(10), 1e-12);
      var still = new OfficialRuleMath.Projectile(0, 0, 0, 1, 1, 0);
      assertTrue(OfficialRuleMath.cannonStep(still, -10).vz() > 0);
      assertTrue(OfficialRuleMath.cannonStep(still, 10).vz() < 0);
      assertEquals(0, OfficialRuleMath.cannonStep(still, 0).z(), 1e-12);
   }
}
