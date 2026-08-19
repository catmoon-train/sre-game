package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.input.ChatPrompt;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class WordBankGui {
   private static final int[] SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34
   };

   private WordBankGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, int page) {
      SimpleContainer container = new SimpleContainer(54);
      int safe = Math.max(0, page);
      fill(ctx, container, safe);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, safe),
         TextUtil.color("&d✦ &f主题词库")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, int page) {
      ItemStackPane.fill(container);
      List<String> words = ctx.words().all();
      int from = page * SLOTS.length;
      for (int i = 0; i < SLOTS.length; i++) {
         int index = from + i;
         if (index >= words.size()) {
            break;
         }
         String word = words.get(index);
         container.setItem(SLOTS[i], GuiItems.action("paper", "&f" + word, List.of("&c点击删除"), "del", "word", word));
      }
      container.setItem(45, page > 0 ? GuiItems.action("arrow", "&e上一页", List.of(), "prev") : GuiItems.filler());
      container.setItem(49, GuiItems.action("emerald", "&a添加词语", List.of("&e点击后在聊天框输入"), "add"));
      container.setItem(53, from + SLOTS.length < words.size()
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
         switch (action) {
            case "prev" -> open(this.ctx, player, this.page - 1);
            case "next" -> open(this.ctx, player, this.page + 1);
            case "add" -> {
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.WORD_ADD, null, "&a请在聊天框输入要添加的主题词");
            }
            case "del" -> {
               String word = GuiItems.extraTag(stack, "word");
               if (word != null && this.ctx.words().remove(word)) {
                  this.ctx.send(player, "&c已删除： &f" + word);
               }
               open(this.ctx, player, this.page);
            }
            default -> {
            }
         }
      }
   }
}
