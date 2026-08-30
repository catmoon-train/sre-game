package net.exmo.sreGame.games.blockedcombat;

import net.minecraft.ChatFormatting;

/** Team identity colors for 疯狂惊天矿工团 — one per spawn corner, max four teams. */
public enum BlockedCombatColor {
   RED("红", ChatFormatting.RED, "&c", 0xB02E26),
   BLUE("蓝", ChatFormatting.BLUE, "&9", 0x3C44AA),
   YELLOW("黄", ChatFormatting.YELLOW, "&e", 0xFED83D),
   GREEN("绿", ChatFormatting.GREEN, "&a", 0x5E7C16);

   private final String label;
   private final ChatFormatting formatting;
   private final String code;
   private final int rgb;

   BlockedCombatColor(String label, ChatFormatting formatting, String code, int rgb) {
      this.label = label;
      this.formatting = formatting;
      this.code = code;
      this.rgb = rgb;
   }

   public String label() { return this.label; }
   public ChatFormatting formatting() { return this.formatting; }
   public String code() { return this.code; }
   public int rgb() { return this.rgb; }
   public String display() { return this.code + this.label + "队"; }

   public static BlockedCombatColor of(int team) {
      BlockedCombatColor[] values = values();
      return values[Math.floorMod(team, values.length)];
   }
}
