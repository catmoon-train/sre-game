package net.exmo.sreGame.games.chicken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 运行时解析 StarRailExpress / noellesroles 的场景方块。未加载 SRE 时全部返回空。
 */
public final class SreSceneBlocks {
   public static final String NAMESPACE = "noellesroles";

   private SreSceneBlocks() {
   }

   public static boolean loaded() {
      FabricLoader loader = FabricLoader.getInstance();
      return loader.isModLoaded("starrailexpress")
         || loader.isModLoaded("noellesroles")
         || present("fake_block");
   }

   public static boolean present(String path) {
      if (path == null || path.isBlank()) {
         return false;
      }
      return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
   }

   public static Block block(String path) {
      if (!present(path)) {
         return null;
      }
      return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
   }

   public static BlockState prepared(String path) {
      Block block = block(path);
      if (block == null) {
         return Blocks.AIR.defaultBlockState();
      }
      BlockState state = block.defaultBlockState();
      if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
         state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
      }
      if (state.hasProperty(SlabBlock.TYPE)) {
         state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
      }
      return state;
   }
}
