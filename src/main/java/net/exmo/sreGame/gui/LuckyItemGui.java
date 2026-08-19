package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.luckypillar.LuckyItemPool;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class LuckyItemGui {
   private static final int[] SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34
   };

   private LuckyItemGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, int page) {
      SimpleContainer container = new SimpleContainer(54);
      int safe = Math.max(0, page);
      fill(ctx, player, container, safe);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, safe),
         TextUtil.color("&d✦ &f幸运物品池")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, int page) {
      ItemStackPane.fill(container);
      List<LuckyItemPool.Entry> extras = ctx.luckyPillar().items().extras();
      int from = page * SLOTS.length;
      for (int i = 0; i < SLOTS.length; i++) {
         int index = from + i;
         if (index >= extras.size()) {
            break;
         }
         LuckyItemPool.Entry entry = extras.get(index);
         ItemStack display = entry.toStack(player.registryAccess());
         if (display.isEmpty()) {
            display = GuiItems.named("barrier", "&c" + entry.id(), List.of("&7无法解析", "&c点击删除"));
         }
         int captured = index;
         display.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> {
            tag.putString("sre_action", "del");
            tag.putString("index", String.valueOf(captured));
         }));
         container.setItem(SLOTS[i], display);
      }
      container.setItem(45, page > 0 ? GuiItems.action("arrow", "&e上一页", List.of(), "prev") : GuiItems.filler());
      container.setItem(49, GuiItems.action("emerald", "&a添加手持物品", List.of(
         "&7把要加入的物品拿在主手",
         "&7支持模组物品与 NBT/组件",
         "&e点击添加（全局）"
      ), "add"));
      container.setItem(47, GuiItems.action("barrier", "&c返回", List.of(), "back"));
      container.setItem(53, from + SLOTS.length < extras.size()
         ? GuiItems.action("arrow", "&e下一页", List.of(), "next")
         : GuiItems.filler());
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final int page;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, int page) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.page = page;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         var stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         if (!player.hasPermissions(2)) {
            this.ctx.send(player, "&c仅管理员可编辑幸运物品池。");
            return;
         }
         switch (action) {
            case "prev" -> open(this.ctx, player, this.page - 1);
            case "next" -> open(this.ctx, player, this.page + 1);
            case "back" -> {
               var room = this.ctx.rooms().getByPlayer(player.getUUID());
               if (room != null && room.isLuckyPillar()) {
                  LuckyPillarSetupGui.open(this.ctx, player, room);
               } else {
                  player.closeContainer();
               }
            }
            case "add" -> {
               ItemStack held = player.getMainHandItem();
               if (held.isEmpty()) {
                  this.ctx.send(player, "&c请将要添加的物品拿在主手。");
                  return;
               }
               if (this.ctx.luckyPillar().items().add(held, player.registryAccess())) {
                  this.ctx.send(player, "&a已加入全局物品池。");
               } else {
                  this.ctx.send(player, "&c添加失败。");
               }
               open(this.ctx, player, this.page);
            }
            case "del" -> {
               String raw = GuiItems.extraTag(stack, "index");
               if (raw != null) {
                  try {
                     if (this.ctx.luckyPillar().items().remove(Integer.parseInt(raw))) {
                        this.ctx.send(player, "&c已从全局物品池删除。");
                     }
                  } catch (NumberFormatException ignored) {
                  }
               }
               open(this.ctx, player, this.page);
            }
            default -> {
            }
         }
      }
   }
}
