package net.exmo.sreGame.games.partygames.api;

import java.util.List;
import java.util.Set;
import net.exmo.sreGame.games.partygames.PartyGameType;

/** Immutable, player-visible definition of one official Minecraft Party 2 game. */
public record PartyGameDefinition(
   PartyGameType type,
   String sceneId,
   int minPlayers,
   int maxPlayers,
   int fixedDurationTicks,
   List<String> rules,
   Set<PartyGameAction.Type> inputs
) {
   public PartyGameDefinition {
      rules = List.copyOf(rules);
      inputs = Set.copyOf(inputs);
   }
}
