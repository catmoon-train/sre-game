package net.exmo.sreGame.games.partygames.api;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Stable team identity used by every party-game surface, entity and HUD. */
public enum PartyColor {
   BLUE(1, "蓝方", ChatFormatting.BLUE, DyeColor.BLUE, 0x3366FF, Blocks.BLUE_CONCRETE, Blocks.BLUE_WOOL, Blocks.BLUE_STAINED_GLASS),
   RED(2, "红方", ChatFormatting.RED, DyeColor.RED, 0xFF3333, Blocks.RED_CONCRETE, Blocks.RED_WOOL, Blocks.RED_STAINED_GLASS);

   private final int team;
   private final String display;
   private final ChatFormatting formatting;
   private final DyeColor dye;
   private final int rgb;
   private final Block concrete;
   private final Block wool;
   private final Block glass;

   PartyColor(int team, String display, ChatFormatting formatting, DyeColor dye, int rgb, Block concrete, Block wool, Block glass) {
      this.team = team; this.display = display; this.formatting = formatting; this.dye = dye; this.rgb = rgb;
      this.concrete = concrete; this.wool = wool; this.glass = glass;
   }

   public int team() { return team; }
   public String display() { return display; }
   public ChatFormatting formatting() { return formatting; }
   public DyeColor dye() { return dye; }
   public int rgb() { return rgb; }
   public BlockState concrete() { return concrete.defaultBlockState(); }
   public BlockState wool() { return wool.defaultBlockState(); }
   public BlockState glass() { return glass.defaultBlockState(); }
   public static PartyColor ofTeam(int team) { return team == 2 ? RED : BLUE; }
}
