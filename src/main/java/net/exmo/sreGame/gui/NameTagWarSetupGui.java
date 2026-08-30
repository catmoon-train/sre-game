package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.nametagwar.NameTagWarSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class NameTagWarSetupGui {
   private NameTagWarSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f撕名牌设置")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      NameTagWarSettings s = room.nameTagWarSettings();
      container.setItem(10, GuiItems.action(s.teams() ? "shield" : "iron_sword",
         "&f组队 &e" + s.onOff(s.teams()),
         List.of("&7开启后按组队人数分组", "&e点击开关"), "teams"));
      container.setItem(11, GuiItems.action("player_head", "&f组队人数 &e" + s.teamSize(),
         List.of("&7每队人数", "&e点击切换 2 / 3 / 4"), "teamSize"));
      container.setItem(12, GuiItems.action(s.friendlyFire() ? "red_dye" : "gray_dye",
         "&f友伤 &e" + s.onOff(s.friendlyFire()),
         List.of("&7组队模式下是否允许队友伤害", "&e点击开关"), "friendlyFire"));
      container.setItem(13, GuiItems.action("shears", "&f默认撕取器 &e" + s.defaultRipMode().label(),
         List.of("&7未标记的剪刀默认模式", "&7速撕 0.25s 背后即可 · 稳撕 0.5s 侧前", "&e点击切换"), "defaultMode"));
      container.setItem(14, GuiItems.action(s.giveBothRippers() ? "chest" : "paper",
         "&f发放双剪 &e" + s.onOff(s.giveBothRippers()),
         List.of("&7开局同时发放速撕+稳撕两把", "&7关则只发默认模式", "&e点击开关"), "bothRippers"));
      container.setItem(15, GuiItems.action("clock", "&f对局时限 &e" + s.maxSeconds() + "s",
         List.of("&7时间到则平局", "&e点击切换 120 / 180 / 240 / 300 / 600"), "maxSeconds"));
      container.setItem(16, GuiItems.action("compass", "&f最大撕取距离 &e" + s.maxDistance() + " 格",
         List.of("&7撕名牌时与目标的最大距离", "&7默认 3.0 格"), "distance"));

      container.setItem(19, GuiItems.action(s.border() ? "bedrock" : "barrier",
         "&f边界 &e" + s.onOff(s.border()),
         List.of("&7用方块墙挤压", "&e点击开关"), "border"));
      container.setItem(20, GuiItems.action("map", "&f边界大小 &e" + s.borderSize(),
         List.of("&e点击切换 48 / 64 / 96 / 128"), "borderSize"));
      container.setItem(21, GuiItems.action("clock", "&f收缩时间 &e" + s.shrinkDelaySeconds() + "s",
         List.of("&7开局后多久开始挤压", "&7默认 120s", "&e点击切换 60 / 90 / 120 / 180"), "shrinkDelay"));
      container.setItem(22, GuiItems.action("piston", "&f收缩速度 &e" + s.shrinkSpeedLabel(),
         List.of("&7默认 2 秒收缩一格", "&e点击切换 2s / 1s / 0.5s / 4s / 8s 一格"), "shrinkSpeed"));
      container.setItem(23, GuiItems.action(s.interruptOnMove() ? "iron_boots" : "leather_boots",
         "&f移动打断 &e" + s.onOff(s.interruptOnMove()),
         List.of("&7撕取中移动是否中断", "&e点击开关"), "interruptMove"));
      container.setItem(24, GuiItems.action(s.interruptOnDamage() ? "shield" : "barrier",
         "&f受击打断 &e" + s.onOff(s.interruptOnDamage()),
         List.of("&7撕取中被攻击是否中断", "&e点击开关"), "interruptDamage"));

      container.setItem(40, GuiItems.named("name_tag", "&f规则摘要", List.of(
         "&72–64 人，每人背部一张名牌",
         "&7用剪刀对准敌人背部持续撕取",
         "&7速撕 0.25s 背后即可 · 稳撕 0.5s 侧前",
         "&7场地为 3 层竞技场建筑，四墙楼梯连通",
         "&7名牌被撕即淘汰，旁观至结束",
         "&7最后存活/队伍获胜"
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
         NameTagWarSettings s = room.nameTagWarSettings();
         switch (action) {
            case "teams" -> s.toggleTeams();
            case "teamSize" -> s.cycleTeamSize();
            case "friendlyFire" -> s.toggleFriendlyFire();
            case "defaultMode" -> s.cycleDefaultRipMode();
            case "bothRippers" -> s.toggleGiveBothRippers();
            case "maxSeconds" -> s.cycleMaxSeconds();
            case "border" -> s.toggleBorder();
            case "borderSize" -> s.cycleBorderSize();
            case "shrinkDelay" -> s.cycleShrinkDelay();
            case "shrinkSpeed" -> s.cycleShrinkSpeed();
            case "interruptMove" -> s.toggleInterruptOnMove();
            case "interruptDamage" -> s.toggleInterruptOnDamage();
            default -> {
               return;
            }
         }
         fill(this.ctx, player, this.container, room);
      }
   }
}
