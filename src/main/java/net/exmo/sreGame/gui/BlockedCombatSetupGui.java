package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.blockedcombat.BlockedCombatSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class BlockedCombatSetupGui {
   private BlockedCombatSetupGui() { }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, ignored) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⛏ &f疯狂惊天矿工团设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      BlockedCombatSettings s = room.blockedCombatSettings();
      container.setItem(10, GuiItems.action("player_head", "&f每队人数 &e" + s.teamSize(), List.of(
         "&7自动分成至多四支队伍", "&e点击切换 1–6 人"), "team"));
      container.setItem(11, GuiItems.action("totem_of_undying", "&f最大死亡次数 &e" + s.deathLimit(), List.of(
         "&7达到次数后出局并旁观", "&e点击切换 1 / 3 / 5 / 7 / 10"), "deaths"));
      container.setItem(12, GuiItems.action("map", "&f矿坑边长 &e" + s.arenaSize(), List.of(
         "&7随机方块填充的正方形矿坑", "&e点击切换 48 / 60 / 72 / 84"), "size"));
      container.setItem(13, GuiItems.action("tnt", "&fTNT 缺省率 &e" + s.tntScarcity() + "%", List.of(
         "&7越高越多 TNT 被玻璃替代", "&e点击切换 25 / 40 / 50 / 60%"), "tnt"));
      container.setItem(14, GuiItems.action("bedrock", "&f出生点范围 &e" + s.spawnSpread() + "×" + s.spawnSpread(), List.of(
         "&7矿坑内部出生舱的分散范围", "&e点击切换 1×1 / 3×3 / 5×5"), "spawn"));
      container.setItem(15, GuiItems.action(s.friendlyFire() ? "iron_axe" : "wooden_sword",
         "&f友军伤害 &e" + (s.friendlyFire() ? "开" : "关"), List.of(
         "&7关闭时队友不能造成伤害", "&e点击开关"), "ff"));
      container.setItem(20, GuiItems.action("clock", "&f准备时间 &e" + s.prepareSeconds() + " 秒", List.of(
         "&7矿坑生成后、开战前的等待时间", "&e点击切换 5 / 10 / 15 秒"), "prepare"));
      container.setItem(22, GuiItems.action(s.richStarterKit() ? "golden_apple" : "bread", "&f强化初始物资 &e" + (s.richStarterKit() ? "开" : "关"), List.of(
         "&7开：更多木材、食物和石斧", "&e点击开关"), "kit"));

      container.setItem(31, GuiItems.named("diamond_pickaxe", "&f玩法说明", List.of(
         "&7挖掘随机方块，获得装备和资源", "&7死亡会回到队伍出生点并计数", "&7出界会被送回出生点", "&7最后仍有成员存活的队伍获胜"
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
         BlockedCombatSettings s = room.blockedCombatSettings();
         switch (action) {
            case "team" -> s.cycleTeamSize();
            case "deaths" -> s.cycleDeathLimit();
            case "size" -> s.cycleArenaSize();
            case "tnt" -> s.cycleTntScarcity();
            case "spawn" -> s.cycleSpawnSpread();
            case "ff" -> s.toggleFriendlyFire();
            case "prepare" -> s.cyclePrepareSeconds();
            case "kit" -> s.toggleRichStarterKit();
            default -> { return; }
         }
         fill(this.container, room);
      }
   }
}
