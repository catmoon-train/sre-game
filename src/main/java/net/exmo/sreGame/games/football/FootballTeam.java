package net.exmo.sreGame.games.football;

import net.minecraft.ChatFormatting;

public enum FootballTeam {
   RED("红", "&c", ChatFormatting.RED),
   BLUE("蓝", "&9", ChatFormatting.BLUE);

   private final String label;
   private final String code;
   private final ChatFormatting formatting;

   FootballTeam(String label, String code, ChatFormatting formatting) {
      this.label = label;
      this.code = code;
      this.formatting = formatting;
   }

   public String label() { return this.label; }
   public String code() { return this.code; }
   public ChatFormatting formatting() { return this.formatting; }
   public String display() { return this.code + this.label + "队"; }
   public FootballTeam other() { return this == RED ? BLUE : RED; }
}
