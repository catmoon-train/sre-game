package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.luckypillar.LuckyPillarSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class LuckyPillarSetupGui {
   private LuckyPillarSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f幸运之柱设置")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      LuckyPillarSettings s = room.luckyPillarSettings();
      container.setItem(10, GuiItems.action(s.teams() ? "shield" : "iron_sword",
         "&f组队 &e" + s.onOff(s.teams()), List.of("&7默认关，开启后按组队人数分组", "&e点击开关"), "teams"));
      container.setItem(11, GuiItems.action("player_head", "&f组队人数 &e" + s.teamSize(),
         List.of("&7每队人数", "&e点击切换 2 / 3 / 4"), "teamSize"));
      container.setItem(12, GuiItems.action("clock", "&f刷新间隔 &e" + s.refreshSeconds() + "s",
         List.of("&7发放随机原版物品或幸运方块", "&e点击切换 3 / 5 / 8 / 10 / 16"), "refresh"));
      container.setItem(13, GuiItems.action("chest", "&f每次数量 &e" + s.refreshCount(),
         List.of("&7每次刷新/钓鱼获得的数量", "&e点击切换 1 / 2 / 3 / 5"), "count"));
      container.setItem(14, GuiItems.action(s.luckyBlockMode() ? "gold_block" : "stone",
         "&f幸运方块 &e" + s.onOff(s.luckyBlockMode()),
         List.of("&7开启后不再发随机物品，改为幸运方块", "&e点击开关"), "lucky"));
      container.setItem(15, GuiItems.action(s.fishingMode() ? "water_bucket" : s.floor().icon(),
         s.fishingMode() ? "&f地板 &b水（钓鱼）" : "&f地板 &e" + s.floor().label(),
         List.of(s.fishingMode() ? "&7钓鱼模式强制水面" : "&7默认白色羊毛", "&e点击切换"), "floor"));
      container.setItem(16, GuiItems.action(s.randomEvents() ? "end_crystal" : "glass",
         "&f随机事件 &e" + s.onOff(s.randomEvents()), List.of("&7约每 30 秒一场全图事件", "&e点击开关"), "events"));

      container.setItem(19, GuiItems.action(s.fishingMode() ? "fishing_rod" : "stick",
         "&f钓鱼模式 &e" + s.onOff(s.fishingMode()),
         List.of("&7地板改水，碰水中毒", "&7开局无限耐久饵钓钓竿", "&e点击开关"), "fish"));
      container.setItem(20, GuiItems.action("enchanted_book", "&f饵钓等级 &e" + s.lureLevel(),
         List.of("&7钓鱼竿饵钓附魔", "&e点击切换 1–5"), "lure"));
      container.setItem(21, GuiItems.action(s.border() ? "bedrock" : "barrier",
         "&f边界 &e" + s.onOff(s.border()), List.of("&7用方块墙挤压，不用世界边界", "&e点击开关"), "border"));
      container.setItem(22, GuiItems.action("map", "&f边界大小 &e" + s.borderSize(),
         List.of("&e点击切换 64 / 96 / 128 / 160 / 192"), "borderSize"));
      container.setItem(23, GuiItems.action("clock", "&f收缩时间 &e" + s.shrinkDelaySeconds() + "s",
         List.of("&7开局后多久开始挤压", "&7默认 120s", "&e点击切换 60 / 90 / 120 / 180"), "shrinkDelay"));
      container.setItem(24, GuiItems.action("piston", "&f收缩速度 &e" + s.shrinkSpeedLabel(),
         List.of("&7默认 2 秒收缩一格", "&e点击切换 2s / 1s / 0.5s / 4s / 8s 一格"), "shrinkSpeed"));
      container.setItem(25, GuiItems.action("stone", "&f柱子高度 &e" + s.pillarHeight(),
         List.of("&e点击切换 32 / 48 / 64 / 80"), "height"));
      container.setItem(26, GuiItems.action("compass", "&f柱子间隔 &e" + s.pillarSpacing(),
         List.of("&7相邻柱心距离（格）", "&7过大时会卡在边界内", "&e点击切换 8 / 12 / 16 / 24 / 32 / 48"), "spacing"));
      container.setItem(28, GuiItems.action(s.pillar().icon(), "&f柱子材质 &e" + s.pillar().label(),
         List.of("&7默认黑曜石", "&e点击切换"), "pillar"));

      boolean op = player.hasPermissions(2);
      container.setItem(31, GuiItems.action(op ? "nether_star" : "paper",
         "&f物品池 &e仅原版",
         List.of(
            "&7只发放原版方块和物品",
            "&7不含模组物品",
            op ? "&e点击查看/编辑（不进入奖池）" : "&8不含自定义追加"
         ), "custom"));
      container.setItem(40, GuiItems.named("gold_block", "&f规则摘要", List.of(
         "&72–16 人，每人一根柱子",
         "&7生存 PvP，地板可站",
         "&7死亡旁观，最后存活/队伍获胜",
         "&7地板、墙、原柱不可拆"
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
         if ("custom".equals(action)) {
            if (player.hasPermissions(2)) {
               LuckyItemGui.open(this.ctx, player, 0);
            } else {
               this.ctx.send(player, "&c自定义物品仅管理员可改。");
            }
            return;
         }
         LuckyPillarSettings s = room.luckyPillarSettings();
         switch (action) {
            case "teams" -> s.toggleTeams();
            case "teamSize" -> s.cycleTeamSize();
            case "refresh" -> s.cycleRefreshSeconds();
            case "count" -> s.cycleRefreshCount();
            case "lucky" -> s.toggleLuckyBlockMode();
            case "floor" -> s.cycleFloor();
            case "events" -> s.toggleRandomEvents();
            case "fish" -> s.toggleFishingMode();
            case "lure" -> s.cycleLureLevel();
            case "border" -> s.toggleBorder();
            case "borderSize" -> s.cycleBorderSize();
            case "shrinkDelay" -> s.cycleShrinkDelay();
            case "shrinkSpeed" -> s.cycleShrinkSpeed();
            case "height" -> s.cyclePillarHeight();
            case "spacing" -> s.cyclePillarSpacing();
            case "pillar" -> s.cyclePillar();
            default -> {
               return;
            }
         }
         fill(this.ctx, player, this.container, room);
      }
   }
}
