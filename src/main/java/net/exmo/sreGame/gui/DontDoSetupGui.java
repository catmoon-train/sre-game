package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.dontdo.DontDoSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class DontDoSetupGui {
   private DontDoSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&c⌂ &f不要做挑战设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      DontDoSettings s = room.dontDoSettings();
      container.setItem(10, GuiItems.action("redstone", "&f生命 &e" + s.lives(), List.of(
         "&7开局每人持有的挑战生命",
         "&7违规 -1，挖钻石矿 +1，死亡不扣",
         "&e点击切换 10 / 15 / 20 / 25 / 30"
      ), "lives"));
      container.setItem(12, GuiItems.action("clock", "&f事项刷新 &e" + s.ruleSeconds() + "s", List.of(
         "&7到期后全员换一条不要做",
         "&7违规会立刻换新，不等倒计时",
         "&e点击切换 60 / 90 / 120"
      ), "rule"));
      container.setItem(14, GuiItems.action(s.teams() ? "shield" : "iron_sword",
         "&f组队 &e" + s.teamsLabel(), List.of(
            "&7关闭为混战，最后一人获胜",
            "&7开启后随机分队，关闭友伤",
            "&e点击开关"
         ), "teams"));
      container.setItem(16, GuiItems.action("player_head", "&f每队人数 &e" + s.teamSize(), List.of(
         "&7仅组队开启时生效",
         "&7开局至少需要两队",
         "&e点击切换 2 / 3 / 4"
      ), "size"));
      container.setItem(28, GuiItems.action(s.randomEvents() ? "fire_charge" : "gunpowder",
         "&f随机事件 &e" + s.eventsLabel(), List.of(
            "&7开启后周期性刷新全局事件",
            "&7如天降铁砧、闪电、矿脉觉醒",
            "&e点击开关"
         ), "events"));
      container.setItem(30, GuiItems.action("ender_eye", "&f事件间隔 &e" + s.eventSeconds() + "s", List.of(
         "&7仅随机事件开启时生效",
         "&e点击切换 60 / 90 / 120"
      ), "eventtime"));
      container.setItem(32, GuiItems.named("diamond_pickaxe", "&f规则摘要", List.of(
         "&72–16 人，256×256 仿原版岛",
         "&7地表约 y=0，地下矿洞与矿石",
         "&7边界基岩不可挖，挖钻石矿回血",
         "&7右侧计分板显示他人事项与生命",
         "&7自己看不见自己的事项，死亡不扣生命"
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
         DontDoSettings s = room.dontDoSettings();
         switch (action) {
            case "lives" -> s.cycleLives();
            case "rule" -> s.cycleRuleSeconds();
            case "teams" -> s.toggleTeams();
            case "size" -> s.cycleTeamSize();
            case "events" -> s.toggleEvents();
            case "eventtime" -> s.cycleEventSeconds();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
