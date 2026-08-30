package net.exmo.sreGame.games.partygames.official;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.exmo.sreGame.games.partygames.PartyGameType;
import org.junit.jupiter.api.Test;

final class OfficialPresentationTest {
   private static final java.util.regex.Pattern HAN = java.util.regex.Pattern.compile(".*\\p{IsHan}.*");

   @Test void officialCatalogueUsesBuiltInChineseTextWithoutResourcePackKeys() {
      List<PartyGameType> games = Arrays.stream(PartyGameType.values())
         .filter(type -> type.id().matches("mp2_1(0[1-9]|1[0-4])_.*"))
         .toList();
      assertEquals(14, games.size());
      for (PartyGameType game : games) {
         assertTrue(HAN.matcher(game.displayName()).matches(), game.id() + " name must default to Chinese");
         var definition = OfficialPartyGames.definition(game);
         assertFalse(definition.rules().isEmpty(), game.id() + " must provide Chinese rules");
         for (String rule : definition.rules()) {
            assertTrue(HAN.matcher(rule).matches(), game.id() + " rule must contain Chinese text: " + rule);
            assertFalse(rule.contains("mcp."), game.id() + " must not depend on original resource-pack translation keys");
         }
      }
   }
}
