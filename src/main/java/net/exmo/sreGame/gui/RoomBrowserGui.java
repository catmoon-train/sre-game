package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class RoomBrowserGui {
   private static final int[] SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34
   };

   private RoomBrowserGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, int page) {
      SimpleContainer container = new SimpleContainer(54);
      int safePage = Math.max(0, page);
      fill(ctx, player, container, safePage);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, safePage),
         TextUtil.color("&b⌂ &f公开房间")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, int page) {
      ItemStackPane.fill(container);
      List<GameRoom> rooms = ctx.rooms().publicRooms();
      int from = page * SLOTS.length;
      for (int i = 0; i < SLOTS.length; i++) {
         int index = from + i;
         if (index >= rooms.size()) {
            break;
         }
         GameRoom room = rooms.get(index);
         MiniGame game = ctx.games().get(room.miniGameId());
         List<String> lore = new ArrayList<>();
         lore.add("&7房主： &f" + ctx.name(room.host()));
         lore.add("&7人数： &f" + room.size() + "&7/&f" + room.maxPlayers());
         lore.add("&7游戏： &f" + (game != null ? game.displayName() : room.miniGameId()));
         if (room.isBuildWar()) {
            lore.add("&7建筑战争 &8| &f" + room.buildWarSettings().rounds() + " 轮");
         } else if (room.isDrawWar()) {
            lore.add("&7绘画战争 &8| &f" + room.buildWarSettings().rounds() + " 轮");
         } else if (room.isYouGuess()) {
            lore.add("&7你建我猜");
         } else if (room.isDrawGuess()) {
            lore.add("&7你画我猜");
         } else if (room.isCaveGuess()) {
            lore.add("&7洞穴猜猜乐 &8| &f" + room.caveSettings().totalRounds() + " 轮");
         } else if (room.isChickenHorse()) {
            lore.add("&7超级鸡马 &8| &f" + room.chickenHorseSettings().rounds() + " 轮");
         } else if (room.isDontDo()) {
            lore.add("&7不要做挑战 &8| &f生命 " + room.dontDoSettings().lives());
         } else if (room.isLuckyPillar()) {
            lore.add("&7幸运之柱 &8| &f" + room.luckyPillarSettings().borderSize() + " 边界");
         } else if (room.isPillarPummel()) {
            lore.add("&7柱联壁合 &8| &f" + room.pillarPummelSettings().teamCount() + " 队 "
               + room.pillarPummelSettings().durationMinutes() + " 分钟");
         } else if (room.duelSettings().gamemode() != null) {
            lore.add("&7模式： &f" + room.duelSettings().gamemode());
         }
         lore.add(room.hasPassword() ? "&c需要密码" : "&a公开加入");
         lore.add("&e点击加入");
         String icon = room.hasPassword() ? "iron_door" : "oak_door";
         container.setItem(SLOTS[i], GuiItems.action(icon, "&f" + room.displayName() + " &8[" + room.id() + "]", lore, "join", "code", room.id()));
      }
      if (page > 0) {
         container.setItem(45, GuiItems.action("arrow", "&e上一页", List.of(), "prev"));
      }
      container.setItem(49, GuiItems.action("barrier", "&c返回大厅", List.of(), "back"));
      if (from + SLOTS.length < rooms.size()) {
         container.setItem(53, GuiItems.action("arrow", "&e下一页", List.of(), "next"));
      }
      if (rooms.isEmpty()) {
         container.setItem(22, GuiItems.named("painting", "&7暂无公开房间", List.of("&e点击创建", "&7或使用 &f/room create")));
      }
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final int page;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, int page) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.page = page;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         var stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            if (slotId == 22) {
               CreateRoomGui.open(this.ctx, player);
            }
            return;
         }
         switch (action) {
            case "back" -> MainMenuGui.open(this.ctx, player);
            case "prev" -> open(this.ctx, player, this.page - 1);
            case "next" -> open(this.ctx, player, this.page + 1);
            case "join" -> {
               String code = GuiItems.extraTag(stack, "code");
               GameRoom room = this.ctx.rooms().get(code);
               if (room == null) {
                  this.ctx.send(player, "&c房间已消失。");
                  open(this.ctx, player, this.page);
                  return;
               }
               if (room.hasPassword()) {
                  this.ctx.send(player, "&e该房间有密码，请输入：&f/room join " + room.id() + " <密码>");
                  player.closeContainer();
                  return;
               }
               if (this.ctx.rooms().join(player, room.id(), null)) {
                  RoomPanelGui.open(this.ctx, player);
               }
            }
            default -> {
            }
         }
      }
   }
}
