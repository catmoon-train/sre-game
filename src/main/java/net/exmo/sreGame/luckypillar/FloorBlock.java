package net.exmo.sreGame.luckypillar;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum FloorBlock {
   WHITE_WOOL("白色羊毛", "white_wool", Blocks.WHITE_WOOL),
   ORANGE_WOOL("橙色羊毛", "orange_wool", Blocks.ORANGE_WOOL),
   MAGENTA_WOOL("品红羊毛", "magenta_wool", Blocks.MAGENTA_WOOL),
   YELLOW_WOOL("黄色羊毛", "yellow_wool", Blocks.YELLOW_WOOL),
   LIME_WOOL("黄绿羊毛", "lime_wool", Blocks.LIME_WOOL),
   BLUE_WOOL("蓝色羊毛", "blue_wool", Blocks.BLUE_WOOL),
   BROWN_WOOL("棕色羊毛", "brown_wool", Blocks.BROWN_WOOL),
   BLACK_WOOL("黑色羊毛", "black_wool", Blocks.BLACK_WOOL),
   STONE("石头", "stone", Blocks.STONE),
   GRASS("草方块", "grass_block", Blocks.GRASS_BLOCK),
   GLASS("玻璃", "glass", Blocks.GLASS),
   ICE("冰", "ice", Blocks.ICE),
   SAND("沙子", "sand", Blocks.SAND);

   private final String label;
   private final String icon;
   private final Block block;

   FloorBlock(String label, String icon, Block block) {
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

   public FloorBlock next() {
      FloorBlock[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public static FloorBlock fromName(String raw) {
      if (raw == null || raw.isBlank()) {
         return WHITE_WOOL;
      }
      try {
         return valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return WHITE_WOOL;
      }
   }
}
