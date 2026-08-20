package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.chicken.ChickenHorseSettings;
import net.exmo.sreGame.games.chicken.SreSceneBlocks;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class ChickenHorseSetupGui {
   private ChickenHorseSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f超级鸡马设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      ChickenHorseSettings s = room.chickenHorseSettings();
      container.setItem(10, GuiItems.action("clock", "&f轮数 &e" + s.rounds(), List.of(
         "&7赛道不清空，越往后越毒",
         "&e点击切换 3 / 5 / 7"
      ), "rounds"));
      container.setItem(12, GuiItems.action("brick", "&f改造 &e" + s.placeSeconds() + "s", List.of(
         "&7每轮随机 1 或 2 个额度，从 6 个机关里选",
         "&7另给 2–4 块原石/木头铺路",
         "&7粘液块、蜂蜜块较低概率抽出",
         "&e点击切换 30 / 45 / 60"
      ), "place"));
      container.setItem(14, GuiItems.action("golden_boots", "&f冲关 &e" + s.raceSeconds() + "s", List.of(
         "&7超时未到终点记 DNF",
         "&e点击切换 60 / 75 / 90"
      ), "race"));
      container.setItem(16, GuiItems.action(s.goldEgg() ? "gold_block" : "stone",
         "&f金蛋 &e" + s.goldEggLabel(), List.of(
            "&7侧路金块，过线才 +2",
            "&e点击开关"
         ), "egg"));
      container.setItem(28, GuiItems.action("map", "&f赛道长度 &e" + s.lengthLabel(), List.of(
         "&7缺口、检查点和机关点会按比例缩放",
         "&e点击切换 短120 / 中168 / 长216"
      ), "length"));
      container.setItem(30, GuiItems.action("oak_fence", "&f赛道宽度 &e" + s.laneWidth() + " 格", List.of(
         "&7路面格子数，出生点按道排列",
         "&e点击切换 2 / 3 / 5"
      ), "width"));
      container.setItem(32, GuiItems.named("cooked_chicken", "&f规则摘要", List.of(
         "&72–30 人，共享赛道",
         "&7长度 &e" + s.lengthLabel() + " &8· &7宽度 &e" + s.laneWidth() + " 格",
         "&7创造飞行改造：只能放，不能拆",
         "&7热键栏末格右键：隐藏/显示其他人",
         "&7每轮随机发 1 或 2 个机关，再发原石/木头铺路",
         "&7贴墙潜行攀爬，到顶可翻上去",
         SreSceneBlocks.loaded()
            ? "&d已加载 SRE：部分场景机关会随机关包抽出"
            : "&8加载 SRE 后可抽出部分场景机关"
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
         ChickenHorseSettings s = room.chickenHorseSettings();
         switch (action) {
            case "rounds" -> s.cycleRounds();
            case "place" -> s.cyclePlaceSeconds();
            case "race" -> s.cycleRaceSeconds();
            case "egg" -> s.toggleGoldEgg();
            case "length" -> s.cycleLength();
            case "width" -> s.cycleLaneWidth();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
