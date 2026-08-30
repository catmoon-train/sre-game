package net.exmo.sreGame.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Display} 的私有 {@code setTransformation}，用于音符墙的缩放/拉伸渲染。
 */
@Mixin(Display.class)
public interface DisplayTransformationInvoker {
   @Invoker("setTransformation")
   void sre$setTransformation(Transformation transformation);
}
