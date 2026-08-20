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

public final class PtbNominateGui {
   private PtbNominateGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, player, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&c气闸提名")
      ));
   }

   private static void fill(PushTheButtonMatch match, ServerPlayer viewer, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("hopper", "&c选出 &e" + match.neededNominees() + " &c名外星人", List.of(
         "&7已选 &f" + match.nominees().size()
      )));
      int slot = 19;
      for (UUID uuid : match.aliveSeats()) {
         if (slot >= 44 || uuid.equals(viewer.getUUID())) {
            continue;
         }
         boolean on = match.nominees().contains(uuid);
         List<String> lore = new ArrayList<>();
         lore.add(on ? "&a已提名" : "&e点击提名");
         ItemStack head = GuiItems.action(on ? "red_concrete" : "player_head",
            (on ? "&c" : "&f") + match.ctx().name(uuid), lore, "nominate", "uuid", uuid.toString());
         ServerPlayer member = match.ctx().player(uuid);
         if (member != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slot++, head);
      }
      boolean ready = match.nominees().size() == match.neededNominees();
      container.setItem(49, GuiItems.action(ready ? "tnt" : "coal_block",
         ready ? "&c确认送入气闸" : "&7需要恰好 " + match.neededNominees() + " 人",
         List.of(), ready ? "confirm_noms" : "none"));
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
         if (action == null || "none".equals(action)) {
            return;
         }
         this.match.handleGuiAction(player, action, GuiItems.extraTag(stack, "uuid"));
      }
   }
}
