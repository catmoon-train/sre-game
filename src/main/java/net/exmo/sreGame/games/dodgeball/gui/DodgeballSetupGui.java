package net.exmo.sreGame.games.dodgeball.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.dodgeball.DodgeballSettings;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.SettingsArchive;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class DodgeballSetupGui {
   private DodgeballSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f躲避球设置")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      DodgeballSettings s = room.dodgeballSettings();
      container.setItem(10, GuiItems.action("clock", "&f每局时长 &e" + s.roundSeconds() + "s",
         List.of("&7默认 180 秒", "&e点击切换 60 / 120 / 180 / 240"), "round"));
      container.setItem(11, GuiItems.action("gold_ingot", "&f获胜局数 &e" + s.winsNeeded(),
         List.of("&7先赢 " + s.winsNeeded() + " 局者获胜（" + s.totalRounds() + " 局两胜制）",
            "&e点击切换 1 / 2 / 3"), "wins"));
      container.setItem(12, GuiItems.action(s.powerups() ? "ender_chest" : "chest",
         "&f道具刷新 &e" + s.onOff(s.powerups()),
         List.of("&7每 30 秒刷新 2 个场地道具", "&e点击开关"), "powerups"));
      container.setItem(13, GuiItems.action(s.frenzy() ? "blaze_powder" : "gunpowder",
         "&f绝杀时刻 &e" + s.onOff(s.frenzy()),
         List.of("&7最后 30 秒雪球速度 +20%", "&e点击开关"), "frenzy"));
      container.setItem(14, GuiItems.action(s.catchUp() ? "sugar" : "paper",
         "&f逆境加成 &e" + s.onOff(s.catchUp()),
         List.of("&7落后队伍移动速度 +10%", "&e点击开关"), "catchUp"));
      container.setItem(22, GuiItems.named("snowball", "&f规则摘要", List.of(
         "&72–16 人，自动均分红蓝，不能越中线",
         "&7前线中点每 2 秒补 1 个雪球",
         "&7投掷冷却 0.2 秒，空手左键接球反弹",
         "&7每局不复活，三局两胜"
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
            fill(this.ctx, player, this.container, room);
            return;
         }
         DodgeballSettings s = room.dodgeballSettings();
         switch (action) {
            case "round" -> s.cycleRoundSeconds();
            case "wins" -> s.cycleWinsNeeded();
            case "powerups" -> s.togglePowerups();
            case "frenzy" -> s.toggleFrenzy();
            case "catchUp" -> s.toggleCatchUp();
            default -> {
               return;
            }
         }
         fill(this.ctx, player, this.container, room);
      }
   }
}
