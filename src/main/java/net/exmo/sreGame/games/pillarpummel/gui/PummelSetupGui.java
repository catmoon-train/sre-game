package net.exmo.sreGame.games.pillarpummel.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.SettingsArchive;
import net.exmo.sreGame.games.pillarpummel.PillarPummelSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class PummelSetupGui {
   public enum Page {
      HOME, BASIC, TERRITORY, RESOURCE, COMBAT, BUILD, SHOP, CATCHUP, WIN
   }

   private PummelSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      open(ctx, player, room, Page.HOME);
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room, Page page) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room, page);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, page),
         TextUtil.color("&6⌂ &f柱联壁合设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room, Page page) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      PillarPummelSettings s = room.pillarPummelSettings();
      if (page == Page.HOME) {
         container.setItem(10, GuiItems.action("clock", "&f基础", List.of("&7时长 / 队伍 / 地图"), "page", "p", Page.BASIC.name()));
         container.setItem(11, GuiItems.action("red_concrete_powder", "&f领土", List.of("&7铺桥成台 / 产分"), "page", "p", Page.TERRITORY.name()));
         container.setItem(12, GuiItems.action("iron_sword", "&f战斗", List.of("&7武器 / 死亡 / 复活"), "page", "p", Page.COMBAT.name()));
         container.setItem(13, GuiItems.action("gold_ingot", "&f军火价格", List.of("&7TNT / 堡垒 / 核弹"), "page", "p", Page.SHOP.name()));
         container.setItem(14, GuiItems.action("golden_apple", "&f追赶", List.of("&7落后补助"), "page", "p", Page.CATCHUP.name()));
         container.setItem(19, GuiItems.action("nether_star", "&f胜利", List.of("&7胜负与平局"), "page", "p", Page.WIN.name()));
         container.setItem(40, GuiItems.named("quartz_pillar", "&f规则摘要", List.of(
            "&7" + s.teamCount() + " 队 × " + s.teamSize() + " 人",
            "&7" + s.durationMinutes() + " 分钟 · " + s.arenaShape().label() + " " + s.grid() + "×" + s.grid()
               + "（" + s.plotCount() + " 台）",
            "&7桥上放粉末铺桥，四面同色成台",
            "&7TNT 拆台 · 占地产分 · 死亡 -1"
         )));
         SettingsArchive.paint(container);
         container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
         return;
      }
      switch (page) {
         case BASIC -> {
            container.setItem(10, GuiItems.action("clock", "&f时长 &e" + s.durationMinutes() + " 分钟",
               List.of("&e点击切换 1–15"), "duration"));
            container.setItem(11, GuiItems.action("white_banner", "&f队伍数量 &e" + s.teamCount(),
               List.of("&e点击切换 2 / 3 / 4"), "teams"));
            container.setItem(12, GuiItems.action("player_head", "&f每队人数 &e" + s.teamSize(),
               List.of("&e点击切换 1–4"), "size"));
            container.setItem(13, GuiItems.action("quartz_pillar", "&f地图大小 &e" + s.grid() + "×" + s.grid()
                  + "（" + s.plotCount() + " 台）",
               List.of("&7每边柱子数量，间距 6", "&e点击切换 4–11"), "grid"));
            container.setItem(14, GuiItems.action("filled_map", "&f地图样式 &e" + s.arenaShape().label(),
               List.of("&7方形 / 圆形 / 环形 / 十字 / 斜十字 / 随机缺柱", "&e点击切换"), "shape"));
         }
         case TERRITORY -> {
            container.setItem(10, GuiItems.action("clock", "&f产分间隔 &e" + s.scoreInterval() + "s",
               List.of("&7每座非出生台 +分", "&e点击切换"), "interval"));
            container.setItem(11, GuiItems.action("gold_nugget", "&f单台产分 &e" + s.scorePerPlot(),
               List.of("&e点击切换 1–5"), "perplot"));
         }
         case COMBAT -> {
            container.setItem(10, GuiItems.action("iron_sword", "&f初始武器 &e" + s.startWeapon().label(),
               List.of("&e点击切换 无/木/石/铁"), "weapon"));
            container.setItem(11, GuiItems.action("iron_sword", "&f击杀得分 &e+" + s.killScore(),
               List.of("&7原版为 0", "&e点击切换"), "kill"));
            container.setItem(12, GuiItems.action("skeleton_skull", "&f死亡扣分 &e" + s.deathScore(),
               List.of("&e点击切换"), "death"));
            container.setItem(13, GuiItems.action("clock", "&f复活等待 &e" + s.respawnInvuln() + "s",
               List.of("&7旁观倒数后回出生台"), "invuln"));
            container.setItem(14, GuiItems.action("red_concrete_powder", "&f死亡掉落粉末 &e" + s.woolDrop().label(),
               List.of("&e点击切换 全部/一半/不掉"), "drop"));
         }
         case SHOP -> {
            container.setItem(10, GuiItems.action("tnt", "&fTNT &e" + s.priceTnt(), List.of("&e点击改价"), "ptnt"));
            container.setItem(11, GuiItems.action("stone_bricks", "&f堡垒 &e" + s.priceDefense(), List.of("&e点击改价"), "pdef"));
            container.setItem(12, GuiItems.action("iron_block", "&f防御塔 &e" + s.priceLaser(), List.of("&e点击改价"), "plaser"));
            container.setItem(13, GuiItems.action("diamond_block", "&f方块生成器 &e" + s.priceResource(), List.of("&e点击改价"), "pres"));
            container.setItem(14, GuiItems.action("bow", "&f弓 &e" + s.priceBow(), List.of("&e点击改价"), "pbow"));
            container.setItem(15, GuiItems.action("shield", "&f盾牌 &e" + s.priceShield(), List.of("&e点击改价"), "pshield"));
            container.setItem(16, GuiItems.action("potion", "&f速度药水 &e" + s.priceSpeed(), List.of("&e点击改价"), "pspeed"));
            container.setItem(19, GuiItems.action("snow_block", "&f维修 &e" + s.priceRepair(), List.of("&e点击改价"), "prepair"));
            container.setItem(20, GuiItems.action("carrot_on_a_stick", "&f核弹 &e" + s.priceNuke(), List.of("&e点击改价"), "pnuke"));
            container.setItem(21, GuiItems.action("potion", "&f跳跃药水 &e" + s.priceJump(), List.of("&e点击改价"), "pjump"));
            container.setItem(22, GuiItems.action("potion", "&f治疗药水 &e" + s.priceHeal(), List.of("&e点击改价"), "pheal"));
            container.setItem(23, GuiItems.action("cobblestone", "&f圆石 ×16 &e" + s.priceStone(), List.of("&e点击改价"), "pstone"));
            container.setItem(24, GuiItems.action("iron_block", "&f铁块 ×2 &e" + s.priceIron(), List.of("&e点击改价"), "piron"));
            container.setItem(25, GuiItems.action("obsidian", "&f黑曜石 ×4 &e" + s.priceDiamond(), List.of("&e点击改价"), "pdiamond"));
         }
         case CATCHUP -> {
            container.setItem(10, GuiItems.action(s.catchUp() ? "golden_apple" : "apple",
               "&f追赶机制 &e" + s.onOff(s.catchUp()), List.of("&e点击开关"), "catch"));
            container.setItem(11, GuiItems.action("gold_ingot", "&f触发分差 &e" + s.catchGap(),
               List.of("&e点击切换"), "gap"));
            container.setItem(12, GuiItems.action("clock", "&f首次触发 &e" + s.firstTriggerMinutes() + " 分钟",
               List.of("&e点击切换"), "first"));
            container.setItem(13, GuiItems.action("clock", "&f补助间隔 &e" + s.assistIntervalMinutes() + " 分钟",
               List.of("&e点击切换"), "aint"));
            container.setItem(14, GuiItems.action("white_wool", "&f补助羊毛 &e" + s.assistWool(),
               List.of("&e点击切换"), "awool"));
            container.setItem(19, GuiItems.action("sugar", "&f速度时长 &e" + s.speedSeconds() + "s",
               List.of("&e点击切换"), "aspeed"));
            container.setItem(20, GuiItems.action("feather", "&f占领加速时长 &e" + s.occupyBoostSeconds() + "s",
               List.of("&e点击切换"), "aboost"));
         }
         case WIN -> {
            container.setItem(10, GuiItems.action("nether_star", "&f胜利方式 &e" + s.winMode().label(),
               List.of("&e点击切换"), "win"));
            container.setItem(11, GuiItems.action("gold_block", "&f目标分数 &e" + s.targetScore(),
               List.of("&7仅目标分模式生效", "&e点击切换"), "target"));
            container.setItem(12, GuiItems.action("iron_sword", "&f平局判定 &e" + s.tieBreak().label(),
               List.of("&e点击切换"), "tie"));
         }
         default -> {
         }
      }
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("arrow", "&e返回分类", List.of(), "home"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private Page page;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, Page page) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
         this.page = page;
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
         if ("home".equals(action)) {
            this.page = Page.HOME;
            fill(this.container, room, this.page);
            return;
         }
         if ("page".equals(action)) {
            String raw = GuiItems.extraTag(this.getSlot(slotId).getItem(), "p");
            try {
               this.page = Page.valueOf(raw);
            } catch (Exception ignored) {
               this.page = Page.HOME;
            }
            fill(this.container, room, this.page);
            return;
         }
         if (SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.container, room, this.page);
            return;
         }
         PillarPummelSettings s = room.pillarPummelSettings();
         switch (action) {
            case "duration" -> s.cycleDuration();
            case "teams" -> s.cycleTeamCount();
            case "size" -> s.cycleTeamSize();
            case "grid" -> s.cycleGrid();
            case "shape" -> s.cycleArenaShape();
            case "occupy" -> s.cycleOccupy();
            case "steal" -> s.cycleSteal();
            case "interval" -> s.cycleScoreInterval();
            case "perplot" -> s.cycleScorePerPlot();
            case "center" -> s.cycleCenterMultiplier();
            case "initial" -> s.cycleInitialPlots();
            case "mines" -> s.cycleMineCount();
            case "mrefresh" -> s.cycleMineRefresh();
            case "mamount" -> s.cycleMineAmount();
            case "iwool" -> s.cycleInitialWool();
            case "hold" -> s.cycleHoldWoolBoost();
            case "weapon" -> s.cycleStartWeapon();
            case "kill" -> s.cycleKillScore();
            case "death" -> s.cycleDeathScore();
            case "invuln" -> s.cycleRespawnInvuln();
            case "drop" -> s.cycleWoolDrop();
            case "safe" -> s.cycleSafeRadius();
            case "place" -> s.cyclePlaceMode();
            case "break" -> s.cycleBreakTime();
            case "bdrop" -> s.toggleBreakDrop();
            case "height" -> s.cycleMaxBuildHeight();
            case "region" -> s.cycleBuildRegion();
            case "pstone" -> s.cyclePriceStone();
            case "piron" -> s.cyclePriceIron();
            case "pbow" -> s.cyclePriceBow();
            case "pshield" -> s.cyclePriceShield();
            case "pspeed" -> s.cyclePriceSpeed();
            case "pjump" -> s.cyclePriceJump();
            case "pheal" -> s.cyclePriceHeal();
            case "pdiamond" -> s.cyclePriceDiamond();
            case "pdef" -> s.cyclePriceDefense();
            case "ptnt" -> s.cyclePriceTnt();
            case "prepair" -> s.cyclePriceRepair();
            case "pnuke" -> s.cyclePriceNuke();
            case "plaser" -> s.cyclePriceLaser();
            case "pres" -> s.cyclePriceResource();
            case "catch" -> s.toggleCatchUp();
            case "gap" -> s.cycleCatchGap();
            case "first" -> s.cycleFirstTrigger();
            case "aint" -> s.cycleAssistInterval();
            case "awool" -> s.cycleAssistWool();
            case "aspeed" -> s.cycleSpeedSeconds();
            case "aboost" -> s.cycleOccupyBoost();
            case "win" -> s.cycleWinMode();
            case "target" -> s.cycleTargetScore();
            case "tie" -> s.cycleTieBreak();
            default -> {
               return;
            }
         }
         fill(this.container, room, this.page);
      }
   }
}
