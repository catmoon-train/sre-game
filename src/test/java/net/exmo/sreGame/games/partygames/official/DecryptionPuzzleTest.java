package net.exmo.sreGame.games.partygames.official;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class DecryptionPuzzleTest {
   @Test void mappingIsBijectionAndAnswerMatchesCode() {
      DecryptionPuzzle puzzle = DecryptionPuzzle.generate(new Random(113));
      assertEquals(10, puzzle.mapping().size());
      assertEquals(10, new HashSet<>(puzzle.mapping().values()).size());
      assertEquals(6, puzzle.code().length());
      assertEquals(6, puzzle.answer().length());
      StringBuilder decoded = new StringBuilder();
      for (char ch : puzzle.code().toCharArray()) decoded.append(puzzle.mapping().get(ch));
      assertEquals(puzzle.answer(), decoded.toString());
      assertEquals(puzzle, DecryptionPuzzle.generate(new Random(113)));
   }
}
