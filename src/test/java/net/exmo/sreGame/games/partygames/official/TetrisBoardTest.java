package net.exmo.sreGame.games.partygames.official;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TetrisBoardTest {
   @Test void sevenBagProducesEveryPieceExactlyOnce() {
      var board = new OfficialControllers.Board(); Set<Integer> types = new HashSet<>(); Random random = new Random(111);
      for (int i = 0; i < 7; i++) { assertTrue(board.next(random)); types.add(board.currentType()); }
      assertEquals(Set.of(0,1,2,3,4,5,6), types);
   }

   @Test void holdCanOnlyBeUsedOncePerPiece() {
      var board = new OfficialControllers.Board(); Random random = new Random(1); board.next(random); int first = board.currentType();
      assertTrue(board.hold(random)); assertEquals(first, board.heldType()); assertFalse(board.hold(random));
      board.next(random); assertTrue(board.hold(random));
   }

   @Test void movementStopsAtWallsAndGarbageHasOneHole() {
      var board = new OfficialControllers.Board(); Random random = new Random(7); board.next(random);
      int moves = 0; while (board.move(-1, 0)) moves++; assertTrue(moves <= 3); assertFalse(board.move(-1, 0));
      board.garbage(new Random(5), false); int filled = 0; for (int x = 0; x < 10; x++) if (board.cell(19, x) != 0) filled++;
      assertEquals(9, filled);
   }

   @Test void fullNormalRowClearsAndSendsOneGarbageLine() {
      var board = new OfficialControllers.Board(); for (int x = 0; x < 10; x++) board.setCell(19, x, 1);
      assertEquals(1, board.clearRows()); for (int x = 0; x < 10; x++) assertEquals(0, board.cell(0, x));
   }

   @Test void clearingGarbageDoesNotSendItBack() {
      var board = new OfficialControllers.Board(); for (int x = 0; x < 9; x++) board.setCell(19, x, 2); board.setCell(19, 9, 1);
      assertEquals(0, board.clearRows());
   }

   @Test void permanentObstacleLineCannotBeCleared() {
      var board = new OfficialControllers.Board(); for (int x = 0; x < 10; x++) board.setCell(19, x, 4);
      assertEquals(0, board.clearRows()); for (int x = 0; x < 10; x++) assertEquals(4, board.cell(19, x));
   }
}
