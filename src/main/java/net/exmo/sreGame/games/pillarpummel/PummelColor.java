package net.exmo.sreGame.games.pillarpummel;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum PummelColor {
   RED("红", ChatFormatting.RED, "&c", Blocks.RED_WOOL, Blocks.RED_CONCRETE, Blocks.RED_CONCRETE_POWDER,
      Blocks.RED_STAINED_GLASS, DyeColor.RED, 0xB02E26),
   BLUE("蓝", ChatFormatting.BLUE, "&9", Blocks.BLUE_WOOL, Blocks.BLUE_CONCRETE, Blocks.BLUE_CONCRETE_POWDER,
      Blocks.BLUE_STAINED_GLASS, DyeColor.BLUE, 0x3C44AA),
   YELLOW("黄", ChatFormatting.YELLOW, "&e", Blocks.YELLOW_WOOL, Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CONCRETE_POWDER,
      Blocks.YELLOW_STAINED_GLASS, DyeColor.YELLOW, 0xFED83D),
   GREEN("绿", ChatFormatting.DARK_GREEN, "&2", Blocks.GREEN_WOOL, Blocks.GREEN_CONCRETE, Blocks.GREEN_CONCRETE_POWDER,
      Blocks.GREEN_STAINED_GLASS, DyeColor.GREEN, 0x5E7C16),
   ORANGE("橙", ChatFormatting.GOLD, "&6", Blocks.ORANGE_WOOL, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CONCRETE_POWDER,
      Blocks.ORANGE_STAINED_GLASS, DyeColor.ORANGE, 0xF9801D),
   PURPLE("紫", ChatFormatting.DARK_PURPLE, "&5", Blocks.PURPLE_WOOL, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE_POWDER,
      Blocks.PURPLE_STAINED_GLASS, DyeColor.PURPLE, 0x8932B8),
   CYAN("青", ChatFormatting.AQUA, "&b", Blocks.CYAN_WOOL, Blocks.CYAN_CONCRETE, Blocks.CYAN_CONCRETE_POWDER,
      Blocks.CYAN_STAINED_GLASS, DyeColor.CYAN, 0x169C9C),
   PINK("粉", ChatFormatting.LIGHT_PURPLE, "&d", Blocks.PINK_WOOL, Blocks.PINK_CONCRETE, Blocks.PINK_CONCRETE_POWDER,
      Blocks.PINK_STAINED_GLASS, DyeColor.PINK, 0xF38BAA);

   private final String label;
   private final ChatFormatting formatting;
   private final String code;
   private final Block wool;
   private final Block concrete;
   private final Block powder;
   private final Block glass;
   private final DyeColor dye;
   private final int rgb;

   PummelColor(String label, ChatFormatting formatting, String code, Block wool, Block concrete, Block powder,
      Block glass, DyeColor dye, int rgb) {
      this.label = label;
      this.formatting = formatting;
      this.code = code;
      this.wool = wool;
      this.concrete = concrete;
      this.powder = powder;
      this.glass = glass;
      this.dye = dye;
      this.rgb = rgb;
   }

   public String label() {
      return this.label;
   }

   public ChatFormatting formatting() {
      return this.formatting;
   }

   public String code() {
      return this.code;
   }

   public Block wool() {
      return this.wool;
   }

   public Item woolItem() {
      return this.wool.asItem();
   }

   public BlockState woolState() {
      return this.wool.defaultBlockState();
   }

   public Block concrete() {
      return this.concrete;
   }

   public BlockState concreteState() {
      return this.concrete.defaultBlockState();
   }

   public Block powder() {
      return this.powder;
   }

   public Item powderItem() {
      return this.powder.asItem();
   }

   public BlockState powderState() {
      return this.powder.defaultBlockState();
   }

   public BlockState glassState() {
      return this.glass.defaultBlockState();
   }

   public DyeColor dye() {
      return this.dye;
   }

   public int rgb() {
      return this.rgb;
   }

   public Block generatorBlock() {
      return switch (this) {
         case RED -> Blocks.DIAMOND_BLOCK;
         case BLUE -> Blocks.EMERALD_BLOCK;
         case YELLOW -> Blocks.GOLD_BLOCK;
         case GREEN -> Blocks.COAL_BLOCK;
         case ORANGE -> Blocks.REDSTONE_BLOCK;
         case PURPLE -> Blocks.AMETHYST_BLOCK;
         case CYAN -> Blocks.LAPIS_BLOCK;
         case PINK -> Blocks.QUARTZ_BLOCK;
      };
   }

   public static PummelColor of(int team) {
      PummelColor[] values = values();
      return values[Math.floorMod(team, values.length)];
   }
}
