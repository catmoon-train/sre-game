package net.exmo.sreGame.caveguess.mode;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.caveguess.CaveMode;
import net.exmo.sreGame.caveguess.CaveSilhouette;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShadowOnWallMode implements CaveModeHandler {
   @Override
   public CaveMode type() {
      return CaveMode.SHADOW;
   }

   @Override
   public void onPrepare(CaveGuessersMatch match) {
      ServerPlayer performer = match.ctx().player(match.performer());
      if (performer != null) {
         giveKit(performer);
      }
      match.broadcast("&7描述者正在影子剧场摆造型。猜测者只能看见墙上的剪影。");
   }

   @Override
   public void onDescribeTick(CaveGuessersMatch match) {
      if (match.describeTicks() % 10 == 0) {
         CaveSilhouette.update(match.level(), match.arena());
      }
   }

   @Override
   public boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      if (match.isPerformer(player.getUUID())) {
         match.ctx().send(player, "&7影子模式不能说话或打字。");
         return true;
      }
      return match.tryGuess(player, message);
   }

   @Override
   public boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return match.isPerformer(player);
   }

   @Override
   public void onCleanup(CaveGuessersMatch match) {
      if (match.level() != null) {
         match.arena().resetShadow(match.level());
      }
   }

   @Override
   public String actionBar(CaveGuessersMatch match, UUID player) {
      return match.isPerformer(player) ? "&e摆造型，调整剪影" : "&b看剪影抢答";
   }

   private static void giveKit(ServerPlayer player) {
      String[] blocks = {
         "black_wool", "white_wool", "stone", "oak_planks",
         "red_stained_glass", "blue_stained_glass", "green_stained_glass",
         "yellow_stained_glass", "purple_stained_glass", "black_concrete"
      };
      int slot = 0;
      for (String block : blocks) {
         player.getInventory().setItem(slot++, GuiItems.named(block, "&f" + block, List.of("&7影子舞台用方块")));
      }
      player.getInventory().setItem(slot++, new ItemStack(Items.ARMOR_STAND, 8));
      player.getInventory().setItem(slot++, new ItemStack(Items.CREEPER_SPAWN_EGG, 2));
      player.getInventory().setItem(slot, new ItemStack(Items.PIG_SPAWN_EGG, 2));
   }
}
