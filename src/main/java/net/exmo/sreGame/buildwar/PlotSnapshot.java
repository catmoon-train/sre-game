package net.exmo.sreGame.buildwar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PlotSnapshot {
   private final BlockState[] blocks;
   private final int size;
   private final int innerHeight;

   private PlotSnapshot(BlockState[] blocks, int size, int innerHeight) {
      this.blocks = blocks;
      this.size = size;
      this.innerHeight = innerHeight;
   }

   public static PlotSnapshot capture(ServerLevel level, Plot plot) {
      int size = plot.size();
      int innerHeight = Math.max(1, plot.height() - 1);
      BlockState[] data = new BlockState[size * innerHeight * size];
      BlockPos origin = plot.origin();
      int i = 0;
      for (int y = 1; y <= innerHeight; y++) {
         for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
               data[i++] = level.getBlockState(origin.offset(x, y, z));
            }
         }
      }
      return new PlotSnapshot(data, size, innerHeight);
   }

   public void restore(ServerLevel level, Plot plot) {
      BlockPos origin = plot.origin();
      int i = 0;
      BlockState air = Blocks.AIR.defaultBlockState();
      int maxY = Math.min(this.innerHeight, Math.max(1, plot.height() - 1));
      int maxSize = Math.min(this.size, plot.size());
      for (int y = 1; y <= maxY; y++) {
         for (int z = 0; z < maxSize; z++) {
            for (int x = 0; x < maxSize; x++) {
               BlockState state = i < this.blocks.length && this.blocks[i] != null ? this.blocks[i] : air;
               i++;
               level.setBlock(origin.offset(x, y, z), state, 2);
            }
         }
      }
   }
}
