package net.exmo.sreGame.games.partygames.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.regex.Pattern;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.api.PartyGameAction.Type;
import org.junit.jupiter.api.Test;

/** Contract tests for the room-facing 201-214 and 301-314 catalogues. */
final class TeamPartyGamesTest {
   private static final Pattern HAN = Pattern.compile(".*\\p{IsHan}.*");

   @Test void exposesExactlyTheFourteenTeamGames() {
      var games = Arrays.stream(PartyGameType.values()).filter(TeamPartyGames::contains).filter(game -> catalogueId(game) >= 201 && catalogueId(game) <= 214).toList();
      assertEquals(14, games.size());
      for (PartyGameType game : games) {
         var definition = TeamPartyGames.definition(game);
         assertNotNull(definition);
         assertEquals(game, definition.type());
         assertEquals(2, definition.minPlayers());
         assertTrue(definition.maxPlayers() >= definition.minPlayers());
         assertTrue(definition.fixedDurationTicks() > 0);
         assertFalse(definition.rules().isEmpty());
         assertTrue(definition.rules().stream().allMatch(rule -> HAN.matcher(rule).matches()), game.id());
         assertNotNull(TeamPartyGames.create(game));
      }
   }

   @Test void exposesExactlyTheFourteenAdvancedGames() {
      var games = Arrays.stream(PartyGameType.values()).filter(TeamPartyGames::contains).filter(game -> catalogueId(game) >= 301 && catalogueId(game) <= 314).toList();
      assertEquals(14, games.size());
      for (PartyGameType game : games) {
         var definition = TeamPartyGames.definition(game);
         assertNotNull(definition);
         assertEquals(2, definition.minPlayers());
         assertTrue(definition.fixedDurationTicks() > 0);
         assertTrue(definition.rules().stream().allMatch(rule -> HAN.matcher(rule).matches()), game.id());
         assertNotNull(TeamPartyGames.create(game));
      }
   }

   @Test void interactiveRulesDeclareTheirServerInputs() {
      assertTrue(TeamPartyGames.definition(PartyGameType.RPSC).inputs().contains(Type.DROP_ITEM));
      assertTrue(TeamPartyGames.definition(PartyGameType.SPACE_JUMPERS).inputs().contains(Type.USE_ITEM));
      assertTrue(TeamPartyGames.definition(PartyGameType.RECRUITMENT_ROYALE).inputs().contains(Type.ATTACK_ENTITY));
      assertTrue(TeamPartyGames.definition(PartyGameType.BOOM_CARTS).inputs().contains(Type.USE_BLOCK));
      assertTrue(TeamPartyGames.definition(PartyGameType.HIDE_AND_SEEK).inputs().contains(Type.DROP_ITEM));
      assertTrue(TeamPartyGames.definition(PartyGameType.EGGCELLENCE).inputs().contains(Type.USE_ENTITY));
      assertTrue(TeamPartyGames.definition(PartyGameType.MOUSE_TRAP).inputs().contains(Type.JUMP));
   }

   @Test void advancedRulesKeepSourceChoiceAndDisguiseContracts() {
      assertTrue(TeamPartyGames.definition(PartyGameType.GAME_THEORY).rules().stream().anyMatch(rule -> rule.contains("A、B、C")));
      assertTrue(TeamPartyGames.definition(PartyGameType.BLOCK_BUSTER).rules().stream().anyMatch(rule -> rule.contains("伪装")));
      assertTrue(TeamPartyGames.definition(PartyGameType.BOSS_BRAWL).rules().stream().anyMatch(rule -> rule.contains("8 秒")));
   }

   private static int catalogueId(PartyGameType game) { return Integer.parseInt(game.id().substring(4, 7)); }
}
