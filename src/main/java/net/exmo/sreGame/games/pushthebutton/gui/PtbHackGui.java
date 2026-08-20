package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

public final class PtbHackGui {
   private PtbHackGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&c入侵")
      ));
   }

   private static void fill(PushTheButtonMatch match, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("ender_eye", "&c剩余入侵 &e" + match.hacksLeft(), List.of(
         "&7翻转受试者看到的提示",
         "&7人类不会收到通知"
      )));
      int slot = 19;
      for (UUID uuid : match.testees()) {
         if (slot >= 35) {
            break;
         }
         PushTheButtonMatch.Seat seat = match.seat(uuid);
         List<String> lore = new ArrayList<>();
         lore.add(seat != null && seat.hacked ? "&c已被入侵" : "&e点击入侵");
         lore.add(seat != null && seat.submitted ? "&8已提交，无法改提示" : "&7尚未提交");
         ItemStack head = GuiItems.action("player_head", "&f" + match.ctx().name(uuid), lore,
            "hack", "uuid", uuid.toString());
         ServerPlayer member = match.ctx().player(uuid);
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
