package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class SituationPuzzleSetupGui {
   private SituationPuzzleSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room);
      player.openMenu(new SimpleMenuProvider(
            (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
            TextUtil.color("&d⌂ &f海龟汤设置")
      ));
   }

   /** 在「默认」与已配置的提供商之间循环切换。 */
   private static void cycleProvider(GameContext ctx, SituationPuzzleSettings s) {
      java.util.List<String> names = ctx.aiConfig().providerNames();
      if (names.isEmpty()) {
         s.setAiProviderName(null);
         return;
      }
      java.util.List<String> options = new java.util.ArrayList<>(names);
      options.add(null); // 默认
      String current = s.aiProviderName();
      int idx = -1;
      for (int i = 0; i < names.size(); i++) {
         if (names.get(i).equals(current)) {
            idx = i;
            break;
         }
      }
      int next = (idx + 1) % options.size();
      s.setAiProviderName(options.get(next));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      SituationPuzzleSettings s = room.situationPuzzleSettings();
      boolean aiGen = ctx.aiConfig().isGeneratorEnabled();
      boolean aiAns = ctx.aiConfig().isAnswererEnabled();
      java.util.List<String> providers = ctx.aiConfig().providerNames();
      String currentProvider = s.aiProviderName();
      String providerLabel = (currentProvider == null || currentProvider.isBlank()) ? "默认" : currentProvider;
      boolean providerOk = currentProvider == null || ctx.aiConfig().provider(currentProvider) != null;
      container.setItem(11, GuiItems.action("writable_book",
            "&f题目来源 &e" + s.puzzleSource().label(),
            List.of(
                  "&7AI：由 AI 自动生成汤面与汤底",
                  "&7手填：房主聊天输入汤面与汤底",
                  aiGen ? "&aAI 出题已启用" : "&cAI 出题未启用（未配置 key）",
                  "&e点击切换"
            ), "source"));
      container.setItem(13, GuiItems.action("nether_star",
            "&f难度 &e" + s.difficulty().label() + " &7" + s.difficulty().stars(),
            List.of(
                  "&7简单 / 普通 / 困难 / 地狱",
                  "&7仅 AI 出题时影响生成",
                  "&e点击切换"
            ), "difficulty"));
      container.setItem(15, GuiItems.action(s.soloMode() ? "player_head" : "paper",
            "&f单人模式 &e" + (s.soloMode() ? "开" : "关"),
            List.of(
                  "&7开：1 人，AI 出题并当主持人",
                  "&7关：多人，房主持汤底并回答",
                  "&7单人模式需 AI 出题与回答均启用",
                  aiAns ? "&aAI 回答已启用" : "&cAI 回答未启用（未配置 key）",
                  "&e点击切换"
            ), "solo"));
      container.setItem(20, GuiItems.action(s.aiAssistHost() ? "book" : "paper",
            "&fAI 辅助房主 &e" + (s.aiAssistHost() ? "开" : "关"),
            List.of(
                  "&7多人模式下，玩家提问后 AI 私下给房主建议",
                  "&7房主仍可自行决定最终回答",
                  "&e点击切换"
            ), "assist"));
      container.setItem(22, GuiItems.action("compass",
            "&fAI 提供商 &e" + providerLabel,
            List.of(
                  "&7本局使用的 AI 提供商",
                  "&7默认：使用全局出题/回答提供商",
                  providerOk ? "&a当前选择可用" : "&c当前选择无效，将回退默认",
                  "&7可选：&f" + (providers.isEmpty() ? "（无，请先 /sregame ai 配置）" : String.join("&7, &f", providers) + "&7, &f默认"),
                  "&e点击切换"
            ), "provider"));
      String aiPw = ctx.aiConfig().aiPassword();
      boolean pwRequired = !aiPw.isEmpty() && (s.soloMode() || s.puzzleSource() == SituationPuzzleSettings.PuzzleSource.AI);
      container.setItem(31, GuiItems.action("tripwire_hook",
            "&fAI 模式密码 &e" + (aiPw.isEmpty() ? "未设置" : "已设置"),
            List.of(
                  pwRequired ? "&c本局 AI 模式：房主开局需输入密码" : "&a本局无需密码",
                  "&7由 OP 用 &f/sregame ai password <密码> &7设置",
                  "&7手填模式不受密码限制"
            ), "pwinfo"));
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
            fill(this.ctx, this.container, room);
            return;
         }
         SituationPuzzleSettings s = room.situationPuzzleSettings();
         switch (action) {
            case "source" -> {
               s.cyclePuzzleSource();
               fill(this.ctx, this.container, room);
            }
            case "difficulty" -> {
               s.cycleDifficulty();
               fill(this.ctx, this.container, room);
            }
            case "solo" -> {
               s.cycleSoloMode();
               fill(this.ctx, this.container, room);
            }
            case "assist" -> {
               s.cycleAiAssistHost();
               fill(this.ctx, this.container, room);
            }
            case "provider" -> {
               cycleProvider(this.ctx, s);
               fill(this.ctx, this.container, room);
            }
            default -> {
            }
         }
      }
   }
}
