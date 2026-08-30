package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 节奏大师的输入检测：
 * 普通模式：左键（挥臂）= 红轨；右键（使用物品/方块）= 蓝轨。
 * 纯左键模式：服务端再根据主手的钻石剑 / 金剑选择红轨 / 金轨。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RhythmInputMixin {
   @Shadow
   public ServerPlayer player;

   @Inject(method = "handleAnimate", at = @At("TAIL"))
   private void sre$rhythmLeftClick(ServerboundSwingPacket packet, CallbackInfo ci) {
      if (packet.getHand() != InteractionHand.MAIN_HAND || this.player == null) {
         return;
      }
      var ctx = SreGame.getContext();
      if (ctx != null) {
         ctx.rhythm().handleLeftClick(this.player);
      }
   }

}
