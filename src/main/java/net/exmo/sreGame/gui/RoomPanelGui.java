package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class RoomPanelGui {
   private static final int[] TEAM1_SLOTS = {10, 11, 12, 13, 14, 15, 16};
   private static final int[] TEAM2_SLOTS = {19, 20, 21, 22, 23, 24, 25};
   private static final int[] MEMBER_SLOTS = {
      9, 10, 11, 12, 13, 14, 15, 16, 17,
      18, 19, 20, 21, 22, 23, 24, 25, 26,
      27, 28, 29, 30, 31, 32, 33, 34, 35,
      43, 44, 46
   };

   private RoomPanelGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      if (room == null) {
         ctx.send(player, "&c你不在任何房间中。");
         MainMenuGui.open(ctx, player);
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&e⌂ &f" + room.displayName() + " &8[" + room.id() + "]")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      MiniGame game = ctx.games().get(room.miniGameId());
      boolean host = room.isHost(player.getUUID());
      List<String> info = new ArrayList<>();
      info.add("&7编号： &f" + room.id());
      info.add("&7状态： &f" + switch (room.state()) {
         case WAITING -> "&a等待中";
         case STARTING -> "&e开局中";
         case PLAYING -> "&c对局中";
      });
      info.add("&7人数： &f" + room.size() + "&7/&f" + room.maxPlayers());
      info.add("&7游戏： &f" + (game != null ? game.displayName() : room.miniGameId()));
      if (room.isBuildWar()) {
         var bw = room.buildWarSettings();
         info.add("&7轮数 &f" + bw.rounds() + " &8| &7主题 &f" + bw.themeCountLabel()
            + " &8| &7多余 &f" + bw.extraPlayersLabel()
            + " &8| &7自定 &f" + bw.customThemeLabel()
            + " &8| &7三选 &f" + bw.pickFromThreeLabel()
            + " &8| &7建造 &f" + bw.buildTimesSummary() + " &8| &7猜词 &f" + bw.guessSeconds() + "s");
         info.add("&7词库 &e" + room.wordPackLabel() + " &8(" + room.resolvedWords(ctx).size() + ")");
      } else if (room.isYouGuess() || room.isDrawGuess()) {
         var yg = room.youGuessSettings();
         info.add("&7轮数 &f" + yg.roundsLabel(room.size()) + (room.isDrawGuess() ? " &8| &7绘画 &f" : " &8| &7建造 &f") + yg.buildSeconds() + "s"
            + " &8| &7自定 &f" + yg.customThemeLabel()
            + " &8| &7三选 &f" + yg.pickFromThreeLabel());
         info.add("&7词库 &e" + room.wordPackLabel() + " &8(" + room.resolvedWords(ctx).size() + ")");
      } else if (room.isDrawWar()) {
         var bw = room.buildWarSettings();
         info.add("&7轮数 &f" + bw.rounds() + " &8| &7主题 &f" + bw.themeCountLabel()
            + " &8| &7多余 &f" + bw.extraPlayersLabel()
            + " &8| &7自定 &f" + bw.customThemeLabel()
            + " &8| &7三选 &f" + bw.pickFromThreeLabel()
            + " &8| &7绘画 &f" + bw.buildTimesSummary() + " &8| &7猜词 &f" + bw.guessSeconds() + "s");
         info.add("&7词库 &e" + room.wordPackLabel() + " &8(" + room.resolvedWords(ctx).size() + ")");
      } else if (room.isFakeHuman()) {
         var fh = room.fakeHumanSettings();
         info.add("&7天数 &f" + fh.days() + " &8| &7白天 &f" + fh.daySeconds() + "s");
         info.add("&74–8 人 · 房间房主即屋主 · 昼夜社交推理");
      } else if (room.isFraudMaster()) {
         var fm = room.fraudSettings();
         info.add("&7通话 &f" + fm.callModeLabel()
            + " &8| &7双倍 &f" + fm.onOff(fm.doubleRound())
            + " &8| &7通话税 &f" + fm.onOff(fm.callTax())
            + " &8| &7匿名投票 &f" + fm.onOff(fm.anonymousVote()));
         info.add("&74–8 人 · 8 回合 + 终局 · 仅电话说话");
      } else if (room.isCaveGuess()) {
         var cg = room.caveSettings();
         info.add("&7" + cg.scheduleSummary());
         info.add("&7难度 &f" + cg.difficulty().label() + " &8| &7曲调 &f" + cg.freeTuneLabel());
         info.add("&72–16 人 · 五种猜词模式锦标赛");
      } else if (room.isChickenHorse()) {
         var ch = room.chickenHorseSettings();
         info.add("&7轮数 &f" + ch.rounds()
            + " &8| &7摆机关 &f" + ch.placeSeconds() + "s"
            + " &8| &7冲关 &f" + ch.raceSeconds() + "s"
            + " &8| &7金蛋 &f" + ch.goldEggLabel());
         info.add("&7赛道 &f" + ch.lengthLabel() + " &8· &e" + ch.laneWidth() + " 格宽"
            + " &8| &72–30 人 · 随机 1 或 2 个机关");
      } else if (room.isDontDo()) {
         var dd = room.dontDoSettings();
         info.add("&7生命 &f" + dd.lives()
            + " &8| &7事项 &f" + dd.ruleSeconds() + "s"
            + " &8| &7组队 &f" + dd.teamsLabel()
            + (dd.teams() ? " &8| &7每队 &f" + dd.teamSize() : "")
            + " &8| &7事件 &f" + dd.eventsLabel());
         info.add("&72–16 人 · 256×256 生存岛 · 挖钻石回血");
      } else if (room.isLuckyPillar()) {
         var lp = room.luckyPillarSettings();
         info.add("&7刷新 &f" + lp.refreshSeconds() + "s×" + lp.refreshCount()
            + " &8| &7幸运方块 &f" + lp.onOff(lp.luckyBlockMode())
            + " &8| &7组队 &f" + lp.onOff(lp.teams())
            + " &8| &7钓鱼 &f" + lp.onOff(lp.fishingMode()));
         info.add("&7边界 &f" + (lp.border() ? lp.borderSize() + " / " + lp.shrinkDelaySeconds() + "s" : "关")
            + " &8| &7柱子 &f" + lp.pillar().label() + " " + lp.pillarHeight());
      } else if (room.isPillarPummel()) {
         var pp = room.pillarPummelSettings();
         info.add("&7" + pp.teamCount() + " 队×" + pp.teamSize()
            + " &8| &7" + pp.durationMinutes() + " 分钟"
            + " &8| &7" + pp.arenaShape().label() + " " + pp.grid() + "×" + pp.grid()
            + "（" + pp.plotCount() + " 台）"
            + " &8| &7" + pp.winMode().label());
         info.add("&7铺桥成台 · 产分 &f" + pp.scorePerPlot() + "/" + pp.scoreInterval() + "s"
            + " &8| &7死亡 &f" + pp.deathScore());
      } else if (room.duelSettings().gamemode() != null) {
         info.add("&7决斗模式： &f" + room.duelSettings().gamemode()
            + " &8| &7" + room.duelSettings().queueType().name()
            + " FT" + room.duelSettings().rounds());
      }
      info.add(room.publicRoom() ? "&a公开" : "&8私密");
      info.add("&7聊天： &f" + room.chatMode().label());
      if (room.hasPassword()) {
         info.add("&c已设密码");
      }
      container.setItem(4, GuiItems.named("beacon", "&f" + room.displayName(), info));
      if (room.isBuildStyle()) {
         placeMembers(ctx, container, room, player, host);
      } else {
         container.setItem(9, GuiItems.named("red_wool", "&c红队", List.of("&7左键头颅换边")));
         container.setItem(18, GuiItems.named("blue_wool", "&9蓝队", List.of("&7左键头颅换边")));
         placeTeam(ctx, container, room, player, room.duelSettings().team1(), TEAM1_SLOTS, host);
         placeTeam(ctx, container, room, player, room.duelSettings().team2(), TEAM2_SLOTS, host);
      }

      boolean ready = room.isReady(player.getUUID());
      container.setItem(37, GuiItems.action(ready ? "lime_dye" : "gray_dye",
         ready ? "&a已准备" : "&e点击准备",
         List.of(room.isHost(player.getUUID()) ? "&7房主默认准备" : "&7切换准备状态"),
         "ready"));
      if (host) {
         container.setItem(36, GuiItems.action(game != null ? game.icon() : "chest", "&d更换游戏",
            List.of("&7当前： &f" + (game != null ? game.displayName() : room.miniGameId()),
               room.state() == RoomState.WAITING ? "&e点击选择其他小游戏" : "&c对局中无法更换"),
            "minigame"));
         container.setItem(38, GuiItems.action("comparator", "&e房间设置", List.of("&7改名 / 密码 / 公开 / 聊天范围"), "settings"));
         container.setItem(39, GuiItems.action(game != null ? game.icon() : "chest", "&6小游戏设置", List.of("&7选择模式与回合"), "setup"));
         container.setItem(40, GuiItems.action("emerald_block", "&a&l开始对局",
            List.of(room.isBuildStyle() ? "&7需全员准备" : "&7需全员准备且分队正确"), "start"));
         container.setItem(41, GuiItems.action(room.publicRoom() ? "lime_dye" : "gray_dye",
            room.publicRoom() ? "&a公开" : "&8私密", List.of("&e点击切换可见性"), "visibility"));
         container.setItem(42, GuiItems.action("player_head", "&f人数上限 &e" + room.maxPlayers(),
            List.of(room.isChickenHorse() ? "&e点击切换 2/4/8/12/16/20/24/30"
               : room.isDontDo() ? "&e点击切换 2/4/8/12/16"
               : room.isLuckyPillar() ? "&e点击切换 2/4/8/12/16"
               : room.isPillarPummel() ? "&e点击切换 4/8/12/16"
               : room.isFraudMaster() || room.isFakeHuman() ? "&e点击切换 4–8"
               : room.isCaveGuess() ? "&e点击切换 2/3/8/12/16"
               : "&e点击切换 2/3/8/12/16/20"), "max"));
      }
      container.setItem(45, GuiItems.action("oak_door", "&c离开房间", List.of(), "leave"));
      container.setItem(49, GuiItems.action("barrier", "&7返回大厅", List.of(), "back"));
      if (host) {
         container.setItem(53, GuiItems.action("tnt", "&4解散房间", List.of("&c将踢出所有成员"), "disband"));
      }
   }

   private static void placeMembers(GameContext ctx, SimpleContainer container, GameRoom room, ServerPlayer viewer, boolean host) {
      placeTeam(ctx, container, room, viewer, room.members(), MEMBER_SLOTS, host);
   }

   private static void placeTeam(GameContext ctx, SimpleContainer container, GameRoom room, ServerPlayer viewer,
                                 List<UUID> team, int[] slots, boolean hostViewer) {
      for (int i = 0; i < slots.length && i < team.size(); i++) {
         UUID uuid = team.get(i);
         ServerPlayer member = ctx.player(uuid);
         List<String> lore = new ArrayList<>();
         lore.add(room.isHost(uuid) ? "&6房主" : "&7成员");
         lore.add(room.isReady(uuid) ? "&a已准备" : "&e未准备");
         if (!room.isBuildStyle()) {
            lore.add("&e左键换边");
         }
         if (hostViewer && !uuid.equals(viewer.getUUID())) {
            lore.add("&c右键踢出");
         }
         String name = "&f" + ctx.name(uuid);
         ItemStack head = GuiItems.action("player_head", name, lore, "member", "uuid", uuid.toString());
         if (member != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slots[i], head);
      }
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
         var stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if (action == null || room == null) {
            return;
         }
         switch (action) {
            case "back" -> MainMenuGui.open(this.ctx, player);
            case "leave" -> {
               this.ctx.rooms().leave(player);
               player.closeContainer();
            }
            case "disband" -> {
               if (room.isHost(player.getUUID())) {
                  if (room.activeMatchId() != null) {
                     var bw = this.ctx.buildWar().getById(room.activeMatchId());
                     if (bw != null) {
                        bw.endNow();
                     }
                     var yg = this.ctx.youGuess().getById(room.activeMatchId());
                     if (yg != null) {
                        yg.endNow();
                     }
                     var fm = this.ctx.fraudMaster().getById(room.activeMatchId());
                     if (fm != null) {
                        fm.endNow();
                     }
                     var fh = this.ctx.fakeHuman().getById(room.activeMatchId());
                     if (fh != null) {
                        fh.endNow();
                     }
                     var cg = this.ctx.caveGuess().getById(room.activeMatchId());
                     if (cg != null) {
                        cg.endNow();
                     }
                     var ch = this.ctx.chickenHorse().getById(room.activeMatchId());
                     if (ch != null) {
                        ch.endNow();
                     }
                     var dd = this.ctx.dontDo().getById(room.activeMatchId());
                     if (dd != null) {
                        dd.endNow();
                     }
                     var lp = this.ctx.luckyPillar().getById(room.activeMatchId());
                     if (lp != null) {
                        lp.endNow();
                     }
                     var pp = this.ctx.pillarPummel().getById(room.activeMatchId());
                     if (pp != null) {
                        pp.endNow();
                     }
                  }
                  this.ctx.rooms().disband(room, "&c房主解散了房间。");
                  MainMenuGui.open(this.ctx, player);
               }
            }
            case "ready" -> {
               if (room.state() != RoomState.WAITING) {
                  return;
               }
               room.toggleReady(player.getUUID());
               fill(this.ctx, player, this.container, room);
            }
            case "start" -> {
               if (this.ctx.rooms().start(player)) {
                  player.closeContainer();
               } else {
                  fill(this.ctx, player, this.container, room);
               }
            }
            case "minigame" -> {
               if (room.isHost(player.getUUID()) && room.state() == RoomState.WAITING && room.activeMatchId() == null) {
                  MinigameSelectGui.open(this.ctx, player, false);
               }
            }
            case "setup" -> {
               MiniGame game = this.ctx.games().get(room.miniGameId());
               if (game != null && room.isHost(player.getUUID())) {
                  game.openSetup(player, room);
               }
            }
            case "settings" -> {
               if (!room.isHost(player.getUUID())) {
                  return;
               }
               RoomSettingsGui.open(this.ctx, player);
            }
            case "visibility" -> {
               if (room.isHost(player.getUUID()) && room.state() == RoomState.WAITING) {
                  room.setPublicRoom(!room.publicRoom());
                  fill(this.ctx, player, this.container, room);
               }
            }
            case "max" -> {
               if (room.isHost(player.getUUID()) && room.state() == RoomState.WAITING) {
                  int[] cycle = (room.isFraudMaster() || room.isFakeHuman())
                     ? new int[] {4, 5, 6, 7, 8}
                     : room.isChickenHorse() ? new int[] {2, 4, 8, 12, 16, 20, 24, 30}
                     : room.isDontDo() || room.isLuckyPillar() ? new int[] {2, 4, 8, 12, 16}
                     : room.isPillarPummel() ? new int[] {4, 8, 12, 16}
                     : room.isCaveGuess() ? new int[] {2, 3, 8, 12, 16} : new int[] {2, 3, 8, 12, 16, 20};
                  int next = 2;
                  for (int i = 0; i < cycle.length; i++) {
                     if (cycle[i] == room.maxPlayers()) {
                        next = cycle[(i + 1) % cycle.length];
                        break;
                     }
                  }
                  if (next < room.size()) {
                     this.ctx.send(player, "&c当前人数多于 &f" + next + " &c，无法下调上限。");
                  } else {
                     room.setMaxPlayers(next);
                  }
                  fill(this.ctx, player, this.container, room);
               }
            }
            case "member" -> {
               if (room.state() != RoomState.WAITING) {
                  return;
               }
               String raw = GuiItems.extraTag(stack, "uuid");
               if (raw == null) {
                  return;
               }
               UUID target = UUID.fromString(raw);
               if (button == 1 && room.isHost(player.getUUID()) && !target.equals(player.getUUID())) {
                  this.ctx.rooms().kick(player, target);
                  GameRoom updated = this.ctx.rooms().getByPlayer(player.getUUID());
                  if (updated != null) {
                     fill(this.ctx, player, this.container, updated);
                  }
                  return;
               }
               if (!room.isBuildStyle()) {
                  room.duelSettings().swapTeam(target);
               }
               fill(this.ctx, player, this.container, room);
            }
            default -> {
            }
         }
      }
   }
}
