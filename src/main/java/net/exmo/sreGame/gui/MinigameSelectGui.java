package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.partygames.PartyMiniGame;
import net.exmo.sreGame.room.CreateDraft;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class MinigameSelectGui {
   private static final int[] GAME_SLOTS = {
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
   };

   private MinigameSelectGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, boolean forDraft) {
      open(ctx, player, forDraft, 0);
   }

   private static void open(GameContext ctx, ServerPlayer player, boolean forDraft, int page) {
      SimpleContainer container = new SimpleContainer(54);
      int safePage = clampPage(ctx, page);
      fill(ctx, container, safePage);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, forDraft, safePage),
         TextUtil.color("&d✦ &f选择小游戏 &8(" + (safePage + 1) + "/" + totalPages(ctx) + ")")
      ));
   }

   private static int totalPages(GameContext ctx) {
      int count = selectableGames(ctx).size();
      return Math.max(1, (count + GAME_SLOTS.length - 1) / GAME_SLOTS.length);
   }

   private static List<MiniGame> selectableGames(GameContext ctx) {
      List<MiniGame> games = new ArrayList<>(ctx.games().all());
      games.removeIf(game -> {
         if (game instanceof PartyMiniGame party) {
            return !ctx.partyGames().isEnabled(party.type());
         }
         return !ctx.config().isGameEnabled(game.id());
      });
      return games;
   }

   private static int clampPage(GameContext ctx, int page) {
      return Math.max(0, Math.min(page, totalPages(ctx) - 1));
   }

   private static void fill(GameContext ctx, SimpleContainer container, int page) {
      ItemStackPane.fill(container);
      List<MiniGame> games = selectableGames(ctx);
      int from = page * GAME_SLOTS.length;
      for (int i = 0; i < GAME_SLOTS.length && from + i < games.size(); i++) {
         MiniGame game = games.get(from + i);
         container.setItem(GAME_SLOTS[i], GuiItems.action(game.icon(), "&f" + game.displayName(), List.of(
            "&7人数： &f" + game.minPlayers() + "&7-&f" + game.maxPlayers(),
            "&e点击选择"
         ), "pick", "id", game.id()));
      }
      int pages = totalPages(ctx);
      if (page > 0) {
         container.setItem(47, GuiItems.action("arrow", "&e上一页",
            List.of("&7第 &f" + (page + 1) + "&7/&f" + pages + " &7页"), "prev"));
      }
      container.setItem(49, GuiItems.action("barrier", "&c返回", List.of(), "back"));
      if (page + 1 < pages) {
         container.setItem(51, GuiItems.action("arrow", "&e下一页",
            List.of("&7第 &f" + (page + 1) + "&7/&f" + pages + " &7页"), "next"));
      }
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private final boolean forDraft;
      private int page;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer,
           boolean forDraft, int page) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
         this.forDraft = forDraft;
         this.page = page;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         var stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         if ("back".equals(action)) {
            if (this.forDraft) {
               CreateRoomGui.open(this.ctx, player);
            } else {
               RoomPanelGui.open(this.ctx, player);
            }
            return;
         }
         if ("prev".equals(action)) {
            this.page = clampPage(this.ctx, this.page - 1);
            fill(this.ctx, this.container, this.page);
            return;
         }
         if ("next".equals(action)) {
            this.page = clampPage(this.ctx, this.page + 1);
            fill(this.ctx, this.container, this.page);
            return;
         }
         if ("pick".equals(action)) {
            String id = GuiItems.extraTag(stack, "id");
            MiniGame game = this.ctx.games().get(id);
            if (game == null) {
               return;
            }
            if (this.forDraft) {
               CreateDraft draft = this.ctx.rooms().draft(player.getUUID());
               draft.miniGameId = game.id();
               if (("build_war".equals(game.id()) || "you_guess".equals(game.id())
                  || "draw_war".equals(game.id()) || "draw_guess".equals(game.id())) && draft.maxPlayers < 3) {
                  draft.maxPlayers = 8;
               }
               if (("fraud_master".equals(game.id()) || "who_is_fake".equals(game.id()))
                  && (draft.maxPlayers < 4 || draft.maxPlayers > 32)) {
                  draft.maxPlayers = 24;
               }
               if ("cave_guess".equals(game.id()) && (draft.maxPlayers < 3 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("chicken_horse".equals(game.id()) && draft.maxPlayers > 120) {
                  draft.maxPlayers = 64;
               }
               if ("dont_do".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("lucky_pillar".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("pillar_pummel".equals(game.id()) && (draft.maxPlayers < 4 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("dodgeball".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("football".equals(game.id()) && (draft.maxPlayers < 16 || draft.maxPlayers > 24)) {
                  draft.maxPlayers = 24;
               }
               if ("dig_to_death".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("you_build_run".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 32)) {
                  draft.maxPlayers = 8;
               }
               if ("push_the_button".equals(game.id()) && (draft.maxPlayers < 4 || draft.maxPlayers > 24)) {
                  draft.maxPlayers = 8;
               }
               if ("skyworld".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 32)) {
                  draft.maxPlayers = 8;
               }
               if ("blocked_combat".equals(game.id()) && (draft.maxPlayers < 1 || draft.maxPlayers > 16)) {
                  draft.maxPlayers = 4;
               }
               if ("tunnel_rats".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 32)) {
                  draft.maxPlayers = 8;
               }
               if ("situation_puzzle".equals(game.id()) && (draft.maxPlayers < 1 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("name_tag_war".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 64)) {
                  draft.maxPlayers = 8;
               }
               if ("fill_in_the_wall".equals(game.id()) && (draft.maxPlayers < 1 || draft.maxPlayers > 32)) {
                  draft.maxPlayers = 4;
               }
               if ("rhythm_game".equals(game.id()) && (draft.maxPlayers < 1 || draft.maxPlayers > 4)) {
                  draft.maxPlayers = 1;
               }
               if ("hypixel_says".equals(game.id()) && (draft.maxPlayers < 2 || draft.maxPlayers > 16)) {
                  draft.maxPlayers = 8;
               }
               CreateRoomGui.open(this.ctx, player);
            } else {
               GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
               if (room == null || !room.isHost(player.getUUID())) {
                  RoomPanelGui.open(this.ctx, player);
                  return;
               }
               if (room.state() != RoomState.WAITING || room.activeMatchId() != null) {
                  this.ctx.send(player, "&c对局进行中，无法更换游戏。");
                  RoomPanelGui.open(this.ctx, player);
                  return;
               }
               if (room.size() > game.maxPlayers()) {
                  this.ctx.send(player, "&c当前人数 &f" + room.size() + " &c超过 &f" + game.displayName()
                     + " &c上限 &f" + game.maxPlayers() + "&c。");
                  return;
               }
               String oldId = room.miniGameId();
               room.setMiniGameId(game.id());
               if (!game.id().equals(oldId)) {
                  room.clearReadyExceptHost();
                  if (!room.isBuildStyle()) {
                     for (UUID uuid : room.members()) {
                        if (room.duelSettings().teamOf(uuid) == 0) {
                           room.duelSettings().assignToSmaller(uuid);
                        }
                     }
                  }
                  MiniGame selected = this.ctx.games().get(room.miniGameId());
                  this.ctx.broadcast(room, "&e房主将游戏更换为 &f" + (selected != null ? selected.displayName() : game.displayName())
                     + "&e，请重新准备。");
               }
               RoomPanelGui.open(this.ctx, player);
            }
         }
      }
   }
}
