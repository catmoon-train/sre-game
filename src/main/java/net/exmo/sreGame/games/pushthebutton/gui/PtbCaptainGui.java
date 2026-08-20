package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.games.pushthebutton.PtbTestType;
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

public final class PtbCaptainGui {
   private PtbCaptainGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&e船长甲板")
      ));
   }

   private static void fill(PushTheButtonMatch match, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      PtbTestType[] types = PtbTestType.values();
      int[] slots = {11, 12, 13, 14, 15};
      for (int i = 0; i < types.length && i < slots.length; i++) {
         PtbTestType type = types[i];
         boolean locked = match.locked(type) || !match.availableTypes().contains(type);
         boolean selected = type == match.testType();
         String name = (selected ? "&a▶ " : locked ? "&8" : "&f") + type.label();
         container.setItem(slots[i], GuiItems.action(locked ? "barrier" : type.icon(), name, List.of(
            locked ? "&c本轮不可用" : "&e点击选择",
            selected ? "&a已选" : ""
         ), locked ? "none" : "pick_test", "value", type.name()));
      }
      container.setItem(31, GuiItems.named("name_tag", "&f受试者 &e" + match.selected().size() + "/" + match.neededTestees(), List.of(
         "&7点击头颅选择 " + match.neededTestees() + " 人"
      )));
      List<UUID> alive = match.aliveSeats();
      int slot = 36;
      for (UUID uuid : alive) {
         if (slot >= 45) {
            break;
         }
         boolean on = match.selected().contains(uuid);
         boolean capt = match.isCaptain(uuid);
         List<String> lore = new ArrayList<>();
         lore.add(capt ? "&6船长" : "&7船员");
         lore.add(on ? "&a已选" : "&e点击选择");
         ItemStack head = GuiItems.action(on ? "lime_wool" : "player_head",
            (on ? "&a" : "&f") + match.ctx().name(uuid), lore, "toggle_testee", "uuid", uuid.toString());
         ServerPlayer member = match.ctx().player(uuid);
         if (member != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slot++, head);
      }
      boolean ready = match.testType() != null && match.selected().size() == match.neededTestees();
      container.setItem(49, GuiItems.action(ready ? "emerald_block" : "coal_block",
         ready ? "&a开始测试" : "&c先选满受试者",
         List.of("&7需要 " + match.neededTestees() + " 人"),
         ready ? "confirm_testees" : "none"));
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
         String extra = GuiItems.extraTag(stack, "value");
         if (extra == null) {
            extra = GuiItems.extraTag(stack, "uuid");
         }
         this.match.handleGuiAction(player, action, extra);
      }
   }
}
