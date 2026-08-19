package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.chicken.ChickenHorseMatch;
import net.exmo.sreGame.chicken.Gadget;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class ChickenHorsePickGui {
   private static final int[] OFFER_SLOTS = {20, 21, 22, 23, 24, 25};

   private ChickenHorsePickGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      ChickenHorseMatch match = ctx.chickenHorse().get(player.getUUID());
      if (match == null || match.phase() != ChickenHorseMatch.Phase.PLACE || match.trapsChosen(player.getUUID())) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(container, match, player);
      int quota = match.trapQuota(player.getUUID());
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&c⌂ &f机关箱 六选" + quota)
      ));
   }

   private static void fill(SimpleContainer container, ChickenHorseMatch match, ServerPlayer player) {
      ItemStackPane.fill(container);
      List<Gadget> offer = match.trapOffer(player.getUUID());
      List<Gadget> picks = match.trapPicks(player.getUUID());
      int quota = match.trapQuota(player.getUUID());
      container.setItem(4, GuiItems.named("chest", "&c机关箱", List.of(
         "&7本轮抽出 6 个机关",
         "&e额度 &f" + quota + " &7：点选后再确认",
         "&7已选 &f" + picks.size() + "&7/&f" + quota
      )));
      for (int i = 0; i < OFFER_SLOTS.length && i < offer.size(); i++) {
         Gadget gadget = offer.get(i);
         boolean picked = picks.contains(gadget);
         List<String> lore = new ArrayList<>();
         lore.add("&7" + (picked ? "已选中，再点取消" : "点击选中"));
         ItemStack stack = GuiItems.action(gadget.icon(), gadget.title(), lore, gadget.action());
         if (picked) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
         }
         container.setItem(OFFER_SLOTS[i], stack);
      }
      if (picks.size() >= 1 && picks.size() <= quota) {
         container.setItem(31, GuiItems.action("lime_dye", "&a确认选择 &f" + picks.size() + "&a/" + quota,
            List.of("&7选好后发到热键栏放置"), "confirm"));
      } else {
         container.setItem(31, GuiItems.named("gray_dye", "&8请点选 1–" + quota + " 个机关",
            List.of("&7本轮额度 &f" + quota)));
      }
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         ChickenHorseMatch match = this.ctx.chickenHorse().get(player.getUUID());
         if (match == null || action == null) {
            return;
         }
         if ("confirm".equals(action)) {
            if (match.confirmTrapPicks(player)) {
               player.closeContainer();
            }
            return;
         }
         Gadget gadget = Gadget.fromStack(this.getSlot(slotId).getItem());
         if (gadget != null) {
            match.toggleTrapPick(player, gadget);
            fill(this.container, match, player);
         }
      }
   }
}
