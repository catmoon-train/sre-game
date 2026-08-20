package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMatch;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class PtbViewGui {
   private PtbViewGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, player, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&d查看答案")
      ));
   }

   private static void fill(PushTheButtonMatch match, ServerPlayer viewer, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("spyglass", "&f人类题面", List.of("&7" + match.lastHumanPrompt())));
      int slot = 19;
      for (PushTheButtonMatch.Answer answer : match.lastAnswers()) {
         if (slot >= 35) {
            break;
         }
         List<String> lore = new ArrayList<>();
         lore.add("&7答案：&f" + answer.text);
         lore.add("&8可疑标记 &e" + answer.sus);
         lore.add(answer.player.equals(viewer.getUUID()) ? "&8这是你" : "&e点击标记可疑");
         ItemStack head = GuiItems.action("player_head", "&f" + match.ctx().name(answer.player), lore,
            "sus", "uuid", answer.player.toString());
         ServerPlayer member = match.ctx().player(answer.player);
         if (member != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slot, head);
         slot += 2;
      }
   }

   public static final class Menu extends ProtectedChestMenu {
      private final PushTheButtonMatch match;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, PushTheButtonMatch match, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         ItemStack stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         this.match.handleGuiAction(player, action, GuiItems.extraTag(stack, "uuid"));
      }
   }
}
