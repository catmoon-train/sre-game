package net.exmo.sreGame.pillarpummel;

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
      Blocks.GREEN_STAINED_GLASS, DyeColor.GREEN, 0x5E7C16);

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
      };
   }

   public static PummelColor of(int team) {
      PummelColor[] values = values();
      if (team < 0 || team >= values.length) {
         return RED;
      }
      return values[team];
   }
}
