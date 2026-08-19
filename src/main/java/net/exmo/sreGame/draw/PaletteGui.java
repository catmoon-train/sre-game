package net.exmo.sreGame.draw;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;

public final class PaletteGui {
   private static final int[] SLOTS = {
      10, 11, 12, 13, 14, 15, 16, 17,
      19, 20, 21, 22, 23, 24, 25, 26
   };

   private PaletteGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, player);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&e✦ &f调色板")
      ));
   }

   private static void fill(SimpleContainer container, ServerPlayer player) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      DyeColor current = DrawKit.state(player.getUUID()).color;
      DyeColor bg = DrawKit.state(player.getUUID()).background;
      DyeColor[] colors = DyeColor.values();
      for (int i = 0; i < SLOTS.length && i < colors.length; i++) {
         DyeColor color = colors[i];
         boolean sel = color == current;
         container.setItem(SLOTS[i], GuiItems.action(
            color.getName() + "_concrete",
            (sel ? "&a" : "&f") + DrawKit.colorName(color)
               + (sel ? " &7(笔刷)" : "")
               + (color == bg ? " &b(背景)" : ""),
            List.of("&7左键：设为笔刷颜色", "&7右键：设为画布背景", "&e点击选择"),
            "pick",
            "color",
            color.getName()
         ));
      }
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         if (action == null) {
            return;
         }
         if ("close".equals(action)) {
            player.closeContainer();
            return;
         }
         if (!"pick".equals(action)) {
            return;
         }
         String raw = GuiItems.extraTag(this.getSlot(slotId).getItem(), "color");
         DyeColor color = DyeColor.BLACK;
         for (DyeColor value : DyeColor.values()) {
            if (value.getName().equals(raw)) {
               color = value;
               break;
            }
         }
         if (button == 1) {
            DrawKit.applyBackground(this.ctx, player, color);
            this.ctx.send(player, "&a已改背景： &e" + DrawKit.colorName(color));
         } else {
            DrawKit.state(player.getUUID()).color = color;
            DrawKit.refreshColor(player);
            this.ctx.send(player, "&a已选笔刷颜色： &e" + DrawKit.colorName(color));
         }
         fill(this.container, player);
      }
   }
}
