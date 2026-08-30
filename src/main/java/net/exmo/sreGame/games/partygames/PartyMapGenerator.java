package net.exmo.sreGame.games.partygames;

import java.util.LinkedHashMap;
import java.util.Map;

/** Common schema for the deliberately parameterized (not hand-built) map catalogue. */
final class PartyMapGenerator implements MapGenerator {
   private final PartyGameType type;

   PartyMapGenerator(PartyGameType type) { this.type = type; }
   @Override public PartyGameType type() { return this.type; }

   @Override public Map<String, Integer> defaultParameters() {
      Map<String, Integer> out = new LinkedHashMap<>();
      out.put("size", switch (this.type) {
         case SURVIVAL_GAMES -> 72;
         case HORSE_RACE -> 92;
         case MINE_FIELD -> 80;
         default -> 48;
      });
      out.put("height", this.type == PartyGameType.DROPPER ? 40 : this.type == PartyGameType.DIG_DOWN ? 48 : 12);
      out.put("difficulty", 1);
      out.put("weight", 100);
      return out;
   }

   @Override public boolean validate(MapTemplate template, StringBuilder reason) {
      if (template == null || template.type() != this.type) {
         reason.append("模板小游戏不匹配");
         return false;
      }
      int size = template.parameter("size", 48);
      if (size < 24 || size > PartyArena.SIZE - 4) {
         reason.append("size 必须介于 24 和 ").append(PartyArena.SIZE - 4);
         return false;
      }
      int height = template.parameter("height", 12);
      if (height < 4 || height > 48) {
         reason.append("height 必须介于 4 和 48");
         return false;
      }
      return true;
   }
}
