package net.exmo.sreGame.games.parkour;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum ParkourStyle {
   RED("红", List.of(Blocks.RED_WOOL, Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA)),
   BLUE("蓝", List.of(Blocks.BLUE_WOOL, Blocks.BLUE_CONCRETE, Blocks.LIGHT_BLUE_WOOL)),
   GREEN("绿", List.of(Blocks.LIME_WOOL, Blocks.GREEN_CONCRETE, Blocks.LIME_CONCRETE)),
   RAINBOW("彩虹", List.of(
      Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
      Blocks.LIGHT_BLUE_WOOL, Blocks.BLUE_WOOL, Blocks.PURPLE_WOOL, Blocks.PINK_WOOL
   )),
   NETHER("下界", List.of(Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.NETHER_BRICKS, Blocks.BASALT));

   private final String label;
   private final List<Block> blocks;

   ParkourStyle(String label, List<Block> blocks) {
      this.label = label;
      this.blocks = blocks;
   }

   public String label() {
      return this.label;
   }

   public Block randomBlock() {
      return this.blocks.get(ThreadLocalRandom.current().nextInt(this.blocks.size()));
   }

   public ParkourStyle next() {
      ParkourStyle[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public static ParkourStyle fromName(String name) {
      if (name == null) {
         return RED;
      }
      try {
         return valueOf(name);
      } catch (IllegalArgumentException e) {
         return RED;
      }
   }
}
