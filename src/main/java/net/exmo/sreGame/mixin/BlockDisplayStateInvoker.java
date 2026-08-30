package net.exmo.sreGame.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Display.BlockDisplay} 的私有 {@code setBlockState}，用于设置音符方块。
 */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayStateInvoker {
   @Invoker("setBlockState")
   void sre$setBlockState(BlockState state);
}
