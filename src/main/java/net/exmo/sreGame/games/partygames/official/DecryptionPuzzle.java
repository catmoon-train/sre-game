package net.exmo.sreGame.games.partygames.official;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministic generator for 113's ten-symbol bijection and six-symbol password. */
public record DecryptionPuzzle(Map<Character, Integer> mapping, String code, String answer) {
   public static DecryptionPuzzle generate(Random random) {
      List<Character> letters = new ArrayList<>();
      for (char ch = 'A'; ch <= 'Z'; ch++) letters.add(ch);
      Collections.shuffle(letters, random);
      letters = new ArrayList<>(letters.subList(0, 10));
      List<Integer> digits = new ArrayList<>();
      for (int i = 0; i < 10; i++) digits.add(i);
      Collections.shuffle(digits, random);
      Map<Character, Integer> mapping = new LinkedHashMap<>();
      for (int i = 0; i < 10; i++) mapping.put(letters.get(i), digits.get(i));
      StringBuilder code = new StringBuilder(), answer = new StringBuilder();
      for (int i = 0; i < 6; i++) { char ch = letters.get(random.nextInt(letters.size())); code.append(ch); answer.append(mapping.get(ch)); }
      return new DecryptionPuzzle(Map.copyOf(mapping), code.toString(), answer.toString());
   }
}
