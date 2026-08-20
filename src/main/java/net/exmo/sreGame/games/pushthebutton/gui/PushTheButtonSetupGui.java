package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.SettingsArchive;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class PushTheButtonSetupGui {
   private PushTheButtonSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&c⌂ &f拍下按钮设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      PushTheButtonSettings s = room.pushTheButtonSettings();
      container.setItem(11, GuiItems.action("endermite_spawn_egg", "&f外星人数量 &e" + s.alienCountLabel(), List.of(
         "&7自动：人数≤5 为 1，6–8 为 2，9+ 为 3",
         "&7也可强制 1 / 2 / 3（仍受人数上限）",
         "&e点击切换"
      ), "aliens"));
      container.setItem(13, GuiItems.action(s.jesterChance() <= 0 ? "barrier" : "player_head",
         "&f小丑 &e" + s.jesterChanceLabel(), List.of(
            "&7小丑在测试中视为人类",
            "&7若被气闸送走且外星人获胜，小丑也赢",
            "&e点击：关 / +5% / 必出"
         ), "jester"));
      container.setItem(15, GuiItems.action(s.drawing() ? "painting" : "gray_dye",
         "&f绘画板 &e" + s.onOff(s.drawing()), List.of("&7船长可选绘画测试", "&e点击切换"), "drawing"));
      container.setItem(29, GuiItems.action(s.bio() ? "spyglass" : "gray_dye",
         "&f生物扫描 &e" + s.onOff(s.bio()), List.of("&7临摹图案后船长得知真身份", "&e点击切换"), "bio"));
      container.setItem(31, GuiItems.named("stone_button", "&f规则摘要", List.of(
         "&74–10 人，共享飞船大厅",
         "&7船长抽测：写作 / 意见 / 商议 / 绘画 / 扫描",
         "&7外星人看到错误提示，可入侵翻转",
         "&7拍按钮提名恰好 N 名外星人",
         "&7误放人类或超时 → 外星人胜"
      )));
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
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
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if ("back".equals(action)) {
            RoomPanelGui.open(this.ctx, player);
            return;
         }
         if (action == null || room == null || !room.isHost(player.getUUID())) {
            return;
         }
         if (SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.container, room);
            return;
         }
         PushTheButtonSettings s = room.pushTheButtonSettings();
         switch (action) {
            case "aliens" -> s.cycleAlienCount();
            case "jester" -> s.cycleJesterChance();
            case "drawing" -> s.cycleDrawing();
            case "bio" -> s.cycleBio();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
