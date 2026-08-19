package net.exmo.sreGame.gui;

import com.mcrpvp.duel.fabric.ModContext;
import com.mcrpvp.duel.fabric.api.DuelApi;
import com.mcrpvp.duel.fabric.gui.LeaderboardGui;
import com.mcrpvp.duel.fabric.gui.QueueSelectorGui;
import com.mcrpvp.duel.fabric.gui.RankGui;
import com.mcrpvp.duel.fabric.gui.SettingsGui;
import com.mcrpvp.duel.fabric.gui.TrimGui;
import com.mcrpvp.duel.fabric.queue.QueueType;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class MainMenuGui {
   public enum Tab {
      GAMES,
      PVP
   }

   private MainMenuGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      open(ctx, player, Tab.GAMES);
   }

   public static void open(GameContext ctx, ServerPlayer player, Tab tab) {
      if (ctx.buildWar().openIfPlaying(player) || ctx.youGuess().openIfPlaying(player)
         || ctx.fraudMaster().openIfPlaying(player) || ctx.fakeHuman().openIfPlaying(player)
         || ctx.caveGuess().openIfPlaying(player) || ctx.chickenHorse().openIfPlaying(player)
         || ctx.dontDo().openIfPlaying(player) || ctx.luckyPillar().openIfPlaying(player)
         || ctx.pillarPummel().openIfPlaying(player)) {
         return;
      }
      Tab safe = tab == null ? Tab.GAMES : tab;
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, safe);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color(safe == Tab.PVP ? "&c✦ &fPVP 大厅" : "&6✦ &f小游戏大厅")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, Tab tab) {
      ItemStackPane.fill(container);
      fillTabs(container, tab);
      if (tab == Tab.PVP) {
         fillPvp(container);
         return;
      }
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      container.setItem(11, GuiItems.action("ender_chest", "&b&l房间列表", List.of("&7浏览公开房间并加入"), "browser"));
      container.setItem(13, GuiItems.action("emerald", "&a&l创建房间", List.of("&7设置名称、人数与小游戏"), "create"));
      if (room != null) {
         container.setItem(15, GuiItems.action("oak_door", "&e&l我的房间", List.of(
            "&7" + room.displayName(),
            "&8[" + room.id() + "] &7" + room.size() + "/" + room.maxPlayers()
         ), "mine"));
      } else {
         container.setItem(15, GuiItems.named("barrier", "&8我的房间", List.of("&7你还不在任何房间")));
      }
      container.setItem(49, GuiItems.named("compass", "&f指令", List.of(
         "&e/sregame &7打开本菜单",
         "&e/room create &7快速开房",
         "&e/room join <码> [密码]",
         "&e/room leave"
      )));
   }

   private static void fillTabs(SimpleContainer container, Tab tab) {
      if (tab == Tab.GAMES) {
         container.setItem(45, GuiItems.named("lime_stained_glass_pane", "&a&l小游戏", List.of("&7当前标签")));
         container.setItem(53, GuiItems.action("red_stained_glass_pane", "&cPVP", List.of("&e点击切换"), "tab-pvp"));
      } else {
         container.setItem(45, GuiItems.action("lime_stained_glass_pane", "&a小游戏", List.of("&e点击切换"), "tab-games"));
         container.setItem(53, GuiItems.named("red_stained_glass_pane", "&c&lPVP", List.of("&7当前标签")));
      }
   }

   private static void fillPvp(SimpleContainer container) {
      container.setItem(20, GuiItems.action("diamond_sword", "&6&l排位队列", List.of("&7打开决斗排位模式选择"), "ranked"));
      container.setItem(21, GuiItems.action("iron_sword", "&7&l休闲队列", List.of("&7打开决斗休闲模式选择"), "unranked"));
      container.setItem(22, GuiItems.action("nether_star", "&b&l排位档案", List.of("&7查看排位分与战绩"), "rank"));
      container.setItem(23, GuiItems.action("golden_helmet", "&e&l排行榜", List.of("&7全服排位榜"), "leaderboard"));
      container.setItem(24, GuiItems.action("crafting_table", "&a&lKit 编辑器", List.of("&7编辑个人物品顺序"), "kit"));
      container.setItem(30, GuiItems.action("book", "&c&l个人设置", List.of("&7决斗偏好设置"), "settings"));
      container.setItem(32, GuiItems.action("smithing_table", "&d&l盔甲纹饰", List.of("&7自定义盔甲纹饰"), "trim"));
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
         if (action == null) {
            return;
         }
         ModContext duel = DuelApi.getContext();
         switch (action) {
            case "tab-games" -> open(this.ctx, player, Tab.GAMES);
            case "tab-pvp" -> open(this.ctx, player, Tab.PVP);
            case "browser" -> RoomBrowserGui.open(this.ctx, player, 0);
            case "create" -> CreateRoomGui.open(this.ctx, player);
            case "mine" -> {
               GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
               if (room != null) {
                  RoomPanelGui.open(this.ctx, player);
               }
            }
            case "ranked" -> {
               if (duel != null) {
                  QueueSelectorGui.open(duel, player, QueueType.RANKED);
               }
            }
            case "unranked" -> {
               if (duel != null) {
                  QueueSelectorGui.open(duel, player, QueueType.UNRANKED);
               }
            }
            case "rank" -> {
               if (duel != null) {
                  RankGui.open(duel, player);
               }
            }
            case "leaderboard" -> {
               if (duel != null) {
                  LeaderboardGui.open(duel, player);
               }
            }
            case "kit" -> {
               if (duel != null) {
                  com.mcrpvp.duel.fabric.gui.KitModeSelector.open(duel, player);
               }
            }
            case "settings" -> {
               if (duel != null) {
                  SettingsGui.open(duel, player);
               }
            }
            case "trim" -> {
               if (duel != null) {
                  TrimGui.open(duel, player);
               }
            }
            default -> {
            }
         }
      }
   }
}
