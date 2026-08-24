package net.exmo.sreGame.games.fillinthewall.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallSettings;
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

public final class FillWallSetupGui {
   private FillWallSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f填墙游戏设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      FillInTheWallSettings s = room.fillInTheWallSettings();
      container.setItem(10, GuiItems.action("clock", "&f模式 &e" + s.mode().label(),
         List.of("&7无尽难度 / 限时计分", "&e点击切换"), "mode"));
      container.setItem(11, GuiItems.action("white_concrete", "&f墙面长度 &e" + s.length(),
         List.of("&7墙面沿 Z 方向的格数", "&e点击切换"), "length"));
      container.setItem(12, GuiItems.action("white_concrete", "&f墙面高度 &e" + s.height(),
         List.of("&7墙面沿 Y 方向的格数", "&e点击切换"), "height"));
      container.setItem(19, GuiItems.action("clock", "&f墙体行进时间 &e" + s.wallActiveTime() + "t",
         List.of("&7越大墙体越慢", "&e点击切换"), "walltime"));
      container.setItem(20, GuiItems.action("clock", "&f限时秒数 &e" + s.durationSeconds() + "s",
         List.of("&7仅限时模式生效", "&e点击切换"), "duration"));
      container.setItem(21, GuiItems.action("air", "&f随机洞数 &e" + s.randomHoles(),
         List.of("&7每墙随机散布的洞", "&e点击切换"), "rholes"));
      container.setItem(22, GuiItems.action("air", "&f连通洞数 &e" + s.connectedHoles(),
         List.of("&7紧邻已有洞的额外洞", "&e点击切换"), "choles"));
      container.setItem(28, GuiItems.action("paper", "&f随机进一步 &e" + s.onOff(s.randomizeFurther()),
         List.of("&7在范围内再随机洞数", "&e点击开关"), "rfurther"));
      container.setItem(29, GuiItems.action("player_head", "&f站立距离 &e" + s.standingDistance(),
         List.of("&7玩家与墙面的距离", "&e点击切换"), "stand"));
      container.setItem(30, GuiItems.action("nether_star", "&f完美墙上限 &e" + s.perfectWallCap(),
         List.of("&70 = 不限制完美墙", "&e点击切换"), "perfectcap"));
      container.setItem(40, GuiItems.named("quartz_pillar", "&f规则摘要", List.of(
         "&7" + s.length() + "×" + s.height() + " 墙面 · " + s.mode().label(),
         "&7用白色混凝土填满墙上的洞",
         "&7完美：消除 2 垃圾墙 · 失败：+1 垃圾墙",
         "&7垃圾墙堆满 20 格即游戏结束"
      )));
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
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
         FillInTheWallSettings s = room.fillInTheWallSettings();
         switch (action) {
            case "mode" -> s.cycleMode();
            case "length" -> s.cycleLength();
            case "height" -> s.cycleHeight();
            case "walltime" -> s.cycleWallTime();
            case "duration" -> s.cycleDuration();
            case "rholes" -> s.cycleRandomHoles();
            case "choles" -> s.cycleConnectedHoles();
            case "rfurther" -> s.toggleRandomizeFurther();
            case "stand" -> s.cycleStandingDistance();
            case "perfectcap" -> s.cyclePerfectWallCap();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
