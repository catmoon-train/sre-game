package net.exmo.sreGame.mixin;

import io.wifi.starrailexpress.PlayerJoinUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复「重进游戏重置玩家物品栏」（mixin 仅实现在 sre-game，不修改 StarRailExpress 本体）。
 *
 * StarRailExpress 的 {@link PlayerJoinUtils#adjustPlayerPosition} 会在玩家每次加入/重进时
 * 无差别调用 {@link SREItemUtils#clearItem(Player, Predicate)}（谓词 {@code a -> true}），
 * 把背包连同盔甲/副手一并清空；该路径在加入时（placeNewPlayer）、以及加入后 500ms/3000ms
 * 的 tick 中会各执行一次，相当于重连一次清空三次。
 *
 * Minecraft 已通过玩家 NBT 自动持久化背包内容，重连本应保留物品。
 * 本 mixin 将该清包调用重定向为空操作（保留出生点传送与游戏模式设置），
 * 从而在 sre-game 侧删除 StarRailExpress 的 isLobby 模式清包重置行为。
 */
@Mixin(PlayerJoinUtils.class)
public abstract class KeepInventoryOnJoinMixin {
   @Redirect(
      method = "adjustPlayerPosition",
      at = @At(
         value = "INVOKE",
         target = "Lio/wifi/starrailexpress/util/SREItemUtils;clearItem(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Predicate;)I"))
   private static int sre$keepInventoryOnJoin(Player player, Predicate<ItemStack> predicate) {
      // 背包由玩家 NBT 自动恢复，重进游戏不再清空。
      return 0;
   }
}
