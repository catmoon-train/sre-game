package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.input.ChatPrompt;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.words.WordLibrary;
import net.exmo.sreGame.words.WordPack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class WordPackGui {
   private static final int[] SLOTS = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32};

   private WordPackGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room, String backTo) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, backTo),
         TextUtil.color("&d✦ &f词库  " + room.wordPackLabel())
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      List<String> current = room.isCaveGuess() && !room.hasCustomWords()
         ? ctx.caveWords().plainTexts()
         : room.resolvedWords(ctx);
      container.setItem(4, GuiItems.named("book", "&f当前房间词库", List.of(
         "&7来源： &e" + (room.isCaveGuess() && !room.hasCustomWords() ? "洞穴默认" : room.wordPackLabel()),
         "&7词数： &f" + current.size(),
         "&e点击编辑本房间词条"
      )));
      container.setItem(11, GuiItems.action("bookshelf",
         room.isCaveGuess() ? "&a导入洞穴默认" : "&a导入服务器默认", List.of(
         "&7" + (room.isCaveGuess() ? ctx.caveWords().plainTexts().size() : ctx.words().all().size()) + " 个词",
         "&e点击导入到本房间"
      ), "import-server"));
      container.setItem(13, GuiItems.action("writable_book", "&e编辑当前词条", List.of("&7增删本房间正在使用的词"), "edit"));
      List<WordPack> packs = ctx.library().packsOf(player.getUUID());
      container.setItem(15, GuiItems.action("ender_chest", "&b导出到我的词库", List.of(
         "&7个人库存 &f" + packs.size() + "&7/&f" + WordLibrary.MAX_PACKS,
         packs.size() >= WordLibrary.MAX_PACKS ? "&c已满，请先删除一套" : "&e点击后输入名称保存"
      ), "export"));
      for (int i = 0; i < SLOTS.length; i++) {
         if (i < packs.size()) {
            WordPack pack = packs.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + pack.words().size() + " 个词");
            lore.add("&a左键导入到房间");
            lore.add("&c右键删除");
            container.setItem(SLOTS[i], GuiItems.action("paper", "&f" + pack.name(), lore, "pack", "id", pack.id()));
         } else {
            container.setItem(SLOTS[i], GuiItems.named("gray_stained_glass_pane", "&8空槽 " + (i + 1), List.of("&7最多保存 10 套个人词库")));
         }
      }
      container.setItem(49, GuiItems.action("barrier", "&c返回设置", List.of(), "back"));
   }

   private static void back(GameContext ctx, ServerPlayer player, String backTo) {
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      if (room == null) {
         RoomPanelGui.open(ctx, player);
         return;
      }
      if ("build_war".equals(backTo) || "draw_war".equals(backTo)) {
         BuildWarSetupGui.open(ctx, player, room);
      } else if ("cave_guess".equals(backTo)) {
         net.exmo.sreGame.games.caveguess.gui.CaveSetupGui.open(ctx, player, room);
      } else {
         YouGuessSetupGui.open(ctx, player, room);
      }
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private final String backTo;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, String backTo) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
         this.backTo = backTo;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if (action == null || room == null) {
            return;
         }
         switch (action) {
            case "back" -> back(this.ctx, player, this.backTo);
            case "edit" -> RoomWordsGui.open(this.ctx, player, room, this.backTo, 0);
            case "import-server" -> {
               if (!room.isHost(player.getUUID())) {
                  return;
               }
               if (room.isCaveGuess()) {
                  room.importWords(this.ctx.caveWords().plainTexts(), "洞穴默认");
               } else {
                  room.importWords(this.ctx.words().all(), "服务器默认");
               }
               this.ctx.send(player, "&a已导入词库（" + (room.isCaveGuess()
                  ? this.ctx.caveWords().resolved(room).size()
                  : room.resolvedWords(this.ctx).size()) + " 词）。");
               fill(this.ctx, player, this.container, room);
            }
            case "export" -> {
               if (this.ctx.library().packsOf(player.getUUID()).size() >= WordLibrary.MAX_PACKS) {
                  this.ctx.send(player, "&c个人词库已满（最多 10 套），请先删除。");
                  return;
               }
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.PACK_EXPORT, room.id() + "|" + this.backTo,
                  "&a请在聊天框输入要保存的词库名称");
            }
            case "pack" -> {
               String id = GuiItems.extraTag(this.getSlot(slotId).getItem(), "id");
               if (id == null) {
                  return;
               }
               if (button == 1) {
                  if (this.ctx.library().delete(player.getUUID(), id)) {
                     this.ctx.send(player, "&c已删除这套个人词库。");
                  }
                  fill(this.ctx, player, this.container, room);
                  return;
               }
               if (!room.isHost(player.getUUID())) {
                  this.ctx.send(player, "&c只有房主可以把词库导入房间。");
                  return;
               }
               WordPack pack = this.ctx.library().get(player.getUUID(), id);
               if (pack == null || pack.words().isEmpty()) {
                  this.ctx.send(player, "&c这套词库是空的。");
                  return;
               }
               room.importWords(pack.words(), pack.name());
               this.ctx.send(player, "&a已导入 &f" + pack.name() + " &7（" + pack.words().size() + " 词）。");
               fill(this.ctx, player, this.container, room);
            }
            default -> {
            }
         }
      }
   }
}
