package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.skyworld.SkyWorldSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class SkyWorldSetupGui {
   private SkyWorldSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&b⌂ &f空岛战争设置")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      SkyWorldSettings s = room.skyWorldSettings();
      container.setItem(10, GuiItems.action(s.teams() ? "shield" : "iron_sword",
         "&f组队 &e" + s.onOff(s.teams()), List.of("&7默认关，开启后按组队人数分组", "&e点击开关"), "teams"));
      container.setItem(11, GuiItems.action("player_head", "&f组队人数 &e" + s.teamSize(),
         List.of("&7每队人数", "&e点击切换 2 / 3 / 4"), "teamSize"));
      container.setItem(12, GuiItems.action(s.friendlyFire() ? "iron_axe" : "wooden_sword",
         "&f友伤 &e" + s.onOff(s.friendlyFire()), List.of("&7组队时队友能否互打", "&e点击开关"), "ff"));
      container.setItem(13, GuiItems.action(s.chestTier().icon(), "&f宝箱 &e" + s.chestTier().label(),
         List.of("&7岛箱与中心箱战利品档", "&e点击切换 普通偏弱 / 标准 / 加强"), "chest"));
      container.setItem(14, GuiItems.action("golden_apple", "&fPVP 保护 &e" + s.pvpGraceSeconds() + "s",
         List.of("&7开笼后无法互相伤害的时间", "&e点击切换 0 / 5 / 10 / 15"), "grace"));
      container.setItem(15, GuiItems.action(s.refill() ? "ender_chest" : "chest",
         "&f补箱 &e" + s.onOff(s.refill()), List.of("&7对局中再填一次全部宝箱", "&e点击开关"), "refill"));
      container.setItem(16, GuiItems.action("clock", "&f补箱间隔 &e" + s.refillSeconds() + "s",
         List.of("&7默认 180 秒", "&e点击切换 60 / 120 / 180 / 300"), "refillTime"));

      container.setItem(19, GuiItems.action(s.border() ? "bedrock" : "barrier",
         "&f缩圈 &e" + s.onOff(s.border()), List.of("&7越出半径即出局", "&e点击开关"), "border"));
      container.setItem(20, GuiItems.action("map", "&f边界大小 &e" + s.borderSize(),
         List.of("&e点击切换 64 / 96 / 128"), "borderSize"));
      container.setItem(21, GuiItems.action("clock", "&f收缩时间 &e" + s.shrinkDelaySeconds() + "s",
         List.of("&7开局后多久开始缩圈", "&e点击切换 60 / 90 / 120 / 180"), "shrinkDelay"));
      container.setItem(22, GuiItems.action("piston", "&f收缩速度 &e" + s.shrinkSpeedLabel(),
         List.of("&7默认 2 秒收缩一格", "&e点击切换"), "shrinkSpeed"));

      container.setItem(40, GuiItems.named("grass_block", "&f规则摘要", List.of(
         "&72–32 人，每人一座空岛",
         "&7笼子倒计时后拆笼填箱",
         "&7死亡掉落旁观，最后存活/队伍获胜",
         "&7虚空即死，开局可选手职业"
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
         SkyWorldSettings s = room.skyWorldSettings();
         switch (action) {
            case "teams" -> s.toggleTeams();
            case "teamSize" -> s.cycleTeamSize();
            case "ff" -> s.toggleFriendlyFire();
            case "chest" -> s.cycleChestTier();
            case "grace" -> s.cyclePvpGrace();
            case "refill" -> s.toggleRefill();
            case "refillTime" -> s.cycleRefillSeconds();
            case "border" -> s.toggleBorder();
            case "borderSize" -> s.cycleBorderSize();
            case "shrinkDelay" -> s.cycleShrinkDelay();
            case "shrinkSpeed" -> s.cycleShrinkSpeed();
            default -> {
               return;
            }
         }
         fill(this.ctx, player, this.container, room);
      }
   }
}
