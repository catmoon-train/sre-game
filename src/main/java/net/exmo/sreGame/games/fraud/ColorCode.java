package net.exmo.sreGame.games.fraud;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;

public enum ColorCode {
   RED("红", "&c", "red_wool", DyeColor.RED, ChatFormatting.RED, true),
   ORANGE("橙", "&6", "orange_wool", DyeColor.ORANGE, ChatFormatting.GOLD, true),
   YELLOW("黄", "&e", "yellow_wool", DyeColor.YELLOW, ChatFormatting.YELLOW, true),
   GREEN("绿", "&a", "green_wool", DyeColor.LIME, ChatFormatting.GREEN, false),
   CYAN("青", "&b", "cyan_wool", DyeColor.CYAN, ChatFormatting.AQUA, false),
   BLUE("蓝", "&9", "blue_wool", DyeColor.BLUE, ChatFormatting.BLUE, false),
   PURPLE("紫", "&d", "purple_wool", DyeColor.PURPLE, ChatFormatting.DARK_PURPLE, false),
   PINK("粉", "&d", "pink_wool", DyeColor.PINK, ChatFormatting.LIGHT_PURPLE, true);

   private final String display;
   private final String chat;
   private final String wool;
   private final DyeColor dye;
   private final ChatFormatting formatting;
   private final boolean warm;

   ColorCode(String display, String chat, String wool, DyeColor dye, ChatFormatting formatting, boolean warm) {
      this.display = display;
      this.chat = chat;
      this.wool = wool;
      this.dye = dye;
      this.formatting = formatting;
      this.warm = warm;
   }

   public String display() {
      return this.display;
   }

   public String chat() {
      return this.chat;
   }

   public String wool() {
      return this.wool;
   }

   public DyeColor dye() {
      return this.dye;
   }

   public ChatFormatting formatting() {
      return this.formatting;
   }

   public boolean warm() {
      return this.warm;
   }

   public String tagged() {
      return this.chat + "[" + this.display + "]";
   }

   public static ColorCode ofIndex(int index) {
      ColorCode[] all = values();
      return all[Math.floorMod(index, all.length)];
   }
}
