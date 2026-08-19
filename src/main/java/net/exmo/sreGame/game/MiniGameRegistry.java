package net.exmo.sreGame.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MiniGameRegistry {
   private final Map<String, MiniGame> games = new LinkedHashMap<>();

   public void register(MiniGame game) {
      this.games.put(game.id(), game);
   }

   public MiniGame get(String id) {
      return id == null ? null : this.games.get(id);
   }

   public Collection<MiniGame> all() {
      return this.games.values();
   }

   public MiniGame first() {
      return this.games.isEmpty() ? null : this.games.values().iterator().next();
   }
}
