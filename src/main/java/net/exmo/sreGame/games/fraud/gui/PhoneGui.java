package net.exmo.sreGame.games.fraud.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.games.fraud.ColorCode;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class PhoneGui {
   private PhoneGui() {
   }

   public static void open(FraudMasterMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, player, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&b☎ &f电话簿")
      ));
   }

   private static void fill(FraudMasterMatch match, ServerPlayer viewer, SimpleContainer container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      UUID incomingFrom = match.phones().incomingCaller(viewer.getUUID());
      UUID outgoingTo = match.phones().outgoingCallee(viewer.getUUID());
      if (incomingFrom != null) {
         ColorCode fromColor = match.color(incomingFrom);
         boolean conference = match.phones().incomingSwitches(viewer.getUUID());
         container.setItem(4, GuiItems.named(
            fromColor == null ? "bell" : fromColor.wool(),
            "&a来电 " + match.label(incomingFrom),
            List.of(conference ? "&e接听将结束当前通话" : "&7选择接听或拒绝")
         ));
         container.setItem(45, GuiItems.action("lime_concrete", "&a接听", List.of("&7接通后才能说话"), "answer"));
         container.setItem(53, GuiItems.action("barrier", "&c拒绝", List.of("&7对方会收到忙音"), "reject"));
      } else if (outgoingTo != null) {
         ColorCode toColor = match.color(outgoingTo);
         container.setItem(4, GuiItems.named(
            toColor == null ? "clock" : toColor.wool(),
            "&e正在呼叫 " + match.label(outgoingTo),
            List.of("&7等待对方接听", "&7约 15 秒无人接听将挂断")
         ));
      }
      int slot = 10;
      for (UUID uuid : match.alive()) {
         ColorCode color = match.color(uuid);
         boolean self = uuid.equals(viewer.getUUID());
         List<String> lore = new ArrayList<>();
         lore.add(self ? "&7这是你" : "&e点击拨打（对方可拒绝）");
         if (!self && match.phones().sameCall(viewer.getUUID(), uuid)) {
            lore.add("&a已在同一通话");
         } else if (!self && uuid.equals(outgoingTo)) {
            lore.add("&e正在呼叫…");
         } else if (!self && uuid.equals(incomingFrom)) {
            lore.add("&a正在来电");
         } else if (!self && match.phones().inCall(uuid)) {
            lore.add(match.settings().busyMode() ? "&c占线" : "&e通话中 · 接听会切换");
         }
         ItemStack item = GuiItems.action(
            color == null ? "player_head" : color.wool(),
            match.label(uuid),
            lore,
            self ? "self" : "dial",
            "uuid",
            uuid.toString()
         );
         ServerPlayer member = match.player(uuid);
         if (member != null && "player_head".equals(color == null ? "player_head" : null)) {
            item.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slot, item);
         slot++;
         if (slot % 9 == 8) {
            slot += 2;
         }
      }
      List<String> callLore = new ArrayList<>();
      if (incomingFrom != null) {
         callLore.add("&7拒绝 " + match.label(incomingFrom));
      } else if (outgoingTo != null) {
         callLore.add("&7取消呼叫 " + match.label(outgoingTo));
      } else if (match.phones().inCall(viewer.getUUID())) {
         for (UUID mate : match.phones().members(viewer.getUUID())) {
            callLore.add(match.label(mate));
         }
      } else {
         callLore.add("&7当前静音");
      }
      String hangupName = incomingFrom != null ? "&c拒绝来电" : (outgoingTo != null ? "&c取消呼叫" : "&c挂断");
      container.setItem(49, GuiItems.action("red_concrete", hangupName, callLore, "hangup"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final FraudMasterMatch match;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, FraudMasterMatch match, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         ItemStack stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null || "self".equals(action)) {
            return;
         }
         this.match.handleGuiAction(player, action, GuiItems.extraTag(stack, "uuid"));
      }
   }
}
