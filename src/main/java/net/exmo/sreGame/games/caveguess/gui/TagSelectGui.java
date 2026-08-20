package net.exmo.sreGame.games.caveguess.gui;

import java.util.List;
import net.exmo.sreGame.games.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.games.caveguess.CaveTag;
import net.exmo.sreGame.games.caveguess.mode.BreakItDownMode;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class TagSelectGui {
   private TagSelectGui() {
   }

   public static void open(CaveGuessersMatch match, ServerPlayer player) {
      if (player == null || match == null || !(match.handler() instanceof BreakItDownMode mode)) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(container, mode);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&e拆解描述 &8· &f选择标签")
      ));
   }

   private static void fill(SimpleContainer container, BreakItDownMode mode) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      int slot = 0;
      String lastGroup = "";
      for (CaveTag tag : CaveTag.values()) {
         if (slot == 45 || slot == 49 || slot == 53) {
            slot++;
         }
         if (slot >= 45) {
            break;
         }
         if (!tag.group().equals(lastGroup)) {
            lastGroup = tag.group();
            container.setItem(slot, GuiItems.named("paper", "&6" + tag.group(), List.of("&7最多 5 个")));
            slot++;
            if (slot == 45 || slot == 49 || slot == 53) {
               slot++;
            }
            if (slot >= 45) {
               break;
            }
         }
         boolean on = mode.selected().contains(tag);
         container.setItem(slot, GuiItems.action(
            on ? "lime_concrete" : tag.icon(),
            (on ? "&a" : "&f") + tag.display(),
            List.of("&7分类 " + tag.group(), on ? "&a已选 &e点击取消" : "&e点击选择"),
            "tag", "value", tag.name()
         ));
         slot++;
      }
      container.setItem(49, GuiItems.named("book", "&f已选 &e" + mode.selected().size() + "&7/" + BreakItDownMode.MAX_TAGS,
         mode.selected().stream().map(tag -> "&7- &f" + tag.display()).toList()));
      container.setItem(53, GuiItems.action("lime_concrete", "&a锁定标签", List.of("&7选定后不能修改"), "confirm"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final CaveGuessersMatch match;

      Menu(int syncId, Inventory inv, SimpleContainer container, CaveGuessersMatch match, ServerPlayer viewer) {
         super(syncId, inv, container, 6, viewer);
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         ItemStack stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         this.match.handleGui(player, action, GuiItems.extraTag(stack, "value"));
      }
   }
}
