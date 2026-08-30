package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.input.ChatPrompt;
import net.exmo.sreGame.room.CreateDraft;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class CreateRoomGui {
   private static final int[] MAX_CYCLE = {2, 8, 16, 32, 48, 64, 80};

   private CreateRoomGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&a✚ &f创建房间")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container) {
      ItemStackPane.fill(container);
      CreateDraft draft = ctx.rooms().draft(player.getUUID());
      MiniGame game = ctx.games().get(draft.miniGameId);
      container.setItem(11, GuiItems.action("name_tag", "&f房间名称", List.of("&7当前： &f" + draft.name, "&e点击后在聊天框输入"), "name"));
      container.setItem(13, GuiItems.action("player_head", "&f人数上限", List.of("&7当前： &f" + draft.maxPlayers, "&e点击切换"), "max"));
      container.setItem(15, GuiItems.action(draft.publicRoom ? "lime_dye" : "gray_dye",
         draft.publicRoom ? "&a公开房间" : "&8私密房间",
         List.of("&7公开房间会出现在房间列表", "&e点击切换"), "visibility"));
      container.setItem(20, GuiItems.action(draft.password == null ? "tripwire_hook" : "iron_door",
         draft.password == null ? "&7无密码" : "&c已设密码",
         List.of("&e点击后在聊天框输入密码", "&7输入空内容可清除"), "password"));
      container.setItem(22, GuiItems.action(game != null ? game.icon() : "chest",
         "&f小游戏",
         List.of("&7当前： &f" + (game != null ? game.displayName() : "未选择"), "&e点击选择"), "minigame"));
      container.setItem(24, GuiItems.action(draft.chatMode.icon(), "&f聊天： &e" + draft.chatMode.label(),
         List.of(draft.chatMode.lore1(), draft.chatMode.lore2(), "&e点击切换 只收房间 / 不外放 / 全部"), "chat"));
      container.setItem(31, GuiItems.action("emerald_block", "&a&l确认创建", List.of("&7创建并进入房间面板"), "confirm"));
      container.setItem(49, GuiItems.action("barrier", "&c返回大厅", List.of(), "back"));
   }

   private static int nextMax(int current, String miniGameId) {
      int[] cycle = "chicken_horse".equals(miniGameId)
         ? new int[] {2, 8, 16, 32, 48, 64, 80, 96, 120}
         : ("dont_do".equals(miniGameId) || "lucky_pillar".equals(miniGameId) || "pillar_pummel".equals(miniGameId)
            || "dodgeball".equals(miniGameId) || "dig_to_death".equals(miniGameId))
            ? new int[] {2, 8, 16, 32, 48, 64}
         : "football".equals(miniGameId) ? new int[] {16, 20, 24}
         : "you_build_run".equals(miniGameId) ? new int[] {2, 8, 16, 24, 32}
         : "skyworld".equals(miniGameId) ? new int[] {2, 8, 16, 24, 32}
         : ("fraud_master".equals(miniGameId) || "who_is_fake".equals(miniGameId))
            ? new int[] {4, 8, 16, 24, 32}
            : "cave_guess".equals(miniGameId) ? new int[] {2, 8, 16, 32, 48, 64}
            : "push_the_button".equals(miniGameId) ? new int[] {4, 8, 16, 24}
            : MAX_CYCLE;
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return cycle[0];
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
         CreateDraft draft = this.ctx.rooms().draft(player.getUUID());
         switch (action) {
            case "back" -> MainMenuGui.open(this.ctx, player);
            case "name" -> {
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.DRAFT_NAME, null, "&a请在聊天框输入房间名称");
            }
            case "max" -> {
               draft.maxPlayers = nextMax(draft.maxPlayers, draft.miniGameId);
               fill(this.ctx, player, this.container);
            }
            case "visibility" -> {
               draft.publicRoom = !draft.publicRoom;
               fill(this.ctx, player, this.container);
            }
            case "password" -> {
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.DRAFT_PASSWORD, null, "&a请在聊天框输入房间密码");
            }
            case "chat" -> {
               draft.chatMode = draft.chatMode.next();
               fill(this.ctx, player, this.container);
            }
            case "minigame" -> MinigameSelectGui.open(this.ctx, player, true);
            case "confirm" -> {
               GameRoom room = this.ctx.rooms().createFromDraft(player);
               if (room != null) {
                  RoomPanelGui.open(this.ctx, player);
               }
            }
            default -> {
            }
         }
      }
   }
}
