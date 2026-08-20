package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.fakehuman.FakeHumanPlayer;
import net.exmo.sreGame.games.fakehuman.IdCard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class FakeHumanIdGui {
   private FakeHumanIdGui() {
   }

   public static void openOwn(GameContext ctx, ServerPlayer player, FakeHumanPlayer state) {
      open(ctx, player, state, true);
   }

   public static void openShown(GameContext ctx, ServerPlayer viewer, FakeHumanPlayer state) {
      open(ctx, viewer, state, false);
   }

   private static void open(GameContext ctx, ServerPlayer viewer, FakeHumanPlayer state, boolean own) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, state);
      String alias = state == null || state.card() == null ? "访客" : state.card().name();
      viewer.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container),
         TextUtil.color(own ? "&f你的证件" : "&f证件：" + alias)
      ));
   }

   private static void fill(SimpleContainer container, FakeHumanPlayer state) {
      ItemStackPane.fill(container);
      IdCard card = state == null ? null : state.card();
      List<String> cardLore = new ArrayList<>();
      if (card != null) {
         cardLore.addAll(card.lore());
      }
      container.setItem(13, GuiItems.named("written_book", "&f证件", cardLore));
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
   }

   private static final class Menu extends ProtectedChestMenu {
      Menu(int syncId, Inventory playerInv, SimpleContainer container) {
         super(syncId, playerInv, container, 6, viewerOf(playerInv));
      }

      private static ServerPlayer viewerOf(Inventory inv) {
         return inv.player instanceof ServerPlayer sp ? sp : null;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         if ("close".equals(GuiItems.actionTag(this.getSlot(slotId).getItem()))) {
            player.closeContainer();
         }
      }
   }
}
