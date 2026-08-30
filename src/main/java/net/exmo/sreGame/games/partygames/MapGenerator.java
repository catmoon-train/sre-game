package net.exmo.sreGame.games.partygames;

import java.util.Map;

public interface MapGenerator {
   PartyGameType type();
   Map<String, Integer> defaultParameters();
   boolean validate(MapTemplate template, StringBuilder reason);
}
