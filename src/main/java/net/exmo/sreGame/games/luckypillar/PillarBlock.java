package net.exmo.sreGame.games.luckypillar;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum PillarBlock {
   OBSIDIAN("黑曜石", "obsidian", Blocks.OBSIDIAN),
   CRYING_OBSIDIAN("哭泣黑曜石", "crying_obsidian", Blocks.CRYING_OBSIDIAN),
   BEDROCK("基岩", "bedrock", Blocks.BEDROCK),
   STONE("石头", "stone", Blocks.STONE),
   COBBLESTONE("圆石", "cobblestone", Blocks.COBBLESTONE),
   DEEPSLATE("深板岩", "deepslate", Blocks.DEEPSLATE),
   IRON("铁块", "iron_block", Blocks.IRON_BLOCK),
   GOLD("金块", "gold_block", Blocks.GOLD_BLOCK),
   NETHERITE("下界合金块", "netherite_block", Blocks.NETHERITE_BLOCK),
   BLACK_CONCRETE("黑色混凝土", "black_concrete", Blocks.BLACK_CONCRETE),
   WHITE_WOOL("白色羊毛", "white_wool", Blocks.WHITE_WOOL),
   GLASS("玻璃", "glass", Blocks.GLASS);

   private final String label;
   private final String icon;
   private final Block block;

   PillarBlock(String label, String icon, Block block) {
      this.label = label;
      this.icon = icon;
      this.block = block;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public BlockState state() {
      return this.block.defaultBlockState();
   }

   public PillarBlock next() {
      PillarBlock[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public static PillarBlock fromName(String raw) {
      if (raw == null || raw.isBlank()) {
         return OBSIDIAN;
      }
      try {
         return valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return OBSIDIAN;
      }
   }
}
