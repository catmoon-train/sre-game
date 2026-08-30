package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.tunnelrats.TunnelRatsSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class TunnelRatsSetupGui {
   private TunnelRatsSetupGui() { }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, ignored) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⛏ &f地道战设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      TunnelRatsSettings s = room.tunnelRatsSettings();
      container.setItem(10, GuiItems.action("map", "&f矿层长度 &e" + s.arenaLength(), List.of(
         "&7两队基地之间的随机可挖掘矿层", "&e点击切换 56 / 72 / 88"), "length"));
      container.setItem(11, GuiItems.action("clock", "&f开战倒计时 &e" + s.countdownSeconds() + " 秒", List.of(
         "&7期间不可挖掘或战斗", "&e点击切换 5 / 10 / 15 秒"), "countdown"));
      container.setItem(12, GuiItems.action("totem_of_undying", "&f床位复活延迟 &e" + s.respawnSeconds() + " 秒", List.of(
         "&7己方床存在时，阵亡后可复活", "&e点击切换 3 / 5 / 8 秒"), "respawn"));
      container.setItem(13, GuiItems.action("wither_rose", "&f孤军时限 &e" + (s.lastStandSeconds() == 0 ? "关闭" : s.lastStandSeconds() / 60 + " 分钟"), List.of(
         "&7仅剩一人时开始倒计时，到时淘汰", "&e点击切换 关闭 / 3 / 5 分钟"), "laststand"));
      container.setItem(14, GuiItems.action(s.friendlyFire() ? "iron_sword" : "wooden_sword", "&f友军伤害 &e" + s.onOff(s.friendlyFire()), List.of(
         "&7关闭时同队玩家不会互相伤害", "&e点击开关"), "friendly"));
      container.setItem(15, GuiItems.action("ender_eye", "&f夜视 &e" + s.onOff(s.nightVision()), List.of(
         "&7持续获得夜视，便于探索地道", "&e点击开关"), "night"));
      container.setItem(16, GuiItems.action("sugar", "&f速度 I &e" + s.onOff(s.speed()), List.of("&7持续速度效果", "&e点击开关"), "speed"));
      container.setItem(19, GuiItems.action("golden_pickaxe", "&f急迫 I &e" + s.onOff(s.haste()), List.of("&7持续挖掘加速", "&e点击开关"), "haste"));
      container.setItem(20, GuiItems.action("leather_chestplate", "&f队伍皮甲 &e" + s.onOff(s.teamArmor()), List.of(
         "&7红、蓝皮甲用于辨识队友", "&e点击开关"), "armor"));
      container.setItem(31, GuiItems.named("iron_pickaxe", "&f玩法说明", List.of(
         "&7红蓝两队分别守护自己的床", "&7挖掘随机矿层，收集资源并攻入敌方基地", "&7床存在时，阵亡后可等待复活；床毁后死亡即淘汰", "&7最后仍有成员的队伍获胜"
      )));
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      Menu(int syncId, Inventory inventory, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, inventory, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
      }

      @Override protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if ("back".equals(action)) { RoomPanelGui.open(this.ctx, player); return; }
         if (action == null || room == null || !room.isHost(player.getUUID())) return;
         if (SettingsArchive.handle(this.ctx, player, room, action)) { fill(this.container, room); return; }
         TunnelRatsSettings s = room.tunnelRatsSettings();
         switch (action) {
            case "length" -> s.cycleArenaLength();
            case "countdown" -> s.cycleCountdownSeconds();
            case "respawn" -> s.cycleRespawnSeconds();
            case "laststand" -> s.cycleLastStandSeconds();
            case "friendly" -> s.toggleFriendlyFire();
            case "night" -> s.toggleNightVision();
            case "speed" -> s.toggleSpeed();
            case "haste" -> s.toggleHaste();
            case "armor" -> s.toggleTeamArmor();
            default -> { return; }
         }
         fill(this.container, room);
      }
   }
}
