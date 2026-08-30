package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.rhythm.RhythmChart;
import net.exmo.sreGame.games.rhythm.RhythmSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class RhythmSetupGui {
   private RhythmSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&d♪ &f节奏大师设置")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      RhythmSettings s = room.rhythmSettings();
      container.setItem(11, GuiItems.action("note_block", "&f模式 &e" + s.mode().label(), List.of(
         "&7单人：自己打红蓝双轨",
         "&7合作：2 人分轨（左=红 右=蓝）",
         "&7对战：2–4 人同谱竞分 + 干扰道具",
         "&7纯左键：钻石剑打红 / 金剑打金，连续单块 + 移动块",
         "&e点击切换"
      ), "mode"));
      container.setItem(13, GuiItems.action("clock", "&f下落速度 &e" + s.speed() + "x", List.of(
         "&7音符下落速度（不影响判定窗口）",
         "&e点击切换 1x / 2x / 3x"
      ), "speed"));
      container.setItem(15, GuiItems.action("target", "&f判定 &e" + s.strictness().label(), List.of(
         "&7新手 ±320ms · 普通 ±220ms",
         "&7困难 ±150ms · 专家 ±100ms",
         "&7（Perfect 窗口）",
         "&e点击切换（四档）"
      ), "strict"));
      container.setItem(17, GuiItems.action("compass", "&f轨道方向 &e" + s.orientation().label(), List.of(
         "&7纵向：音符自上而下，红左蓝右",
         "&7横向：音符自左向右，红上蓝下",
         "&7由远及近：音符迎面飞来，红左蓝右",
         "&e点击切换"
      ), "orientation"));
      container.setItem(29, GuiItems.action("music_disc_13", "&f曲目 &e" + s.chartLabel(ctx.rhythm().charts()), List.of(
         "&7随机：开局抽一首",
         "&7内置 " + ctx.rhythm().charts().all().size() + " 首，或指定自定义谱面",
         "&e点击切换"
      ), "chart"));
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
         RhythmSettings s = room.rhythmSettings();
         switch (action) {
            case "mode" -> {
               s.cycleMode();
               fill(this.ctx, this.container, room);
            }
            case "speed" -> {
               s.cycleSpeed();
               fill(this.ctx, this.container, room);
            }
            case "strict" -> {
               s.cycleStrictness();
               fill(this.ctx, this.container, room);
            }
            case "orientation" -> {
               s.cycleOrientation();
               fill(this.ctx, this.container, room);
            }
            case "chart" -> {
               cycleChart(s);
               fill(this.ctx, this.container, room);
            }
            default -> {
            }
         }
      }

      private void cycleChart(RhythmSettings s) {
         List<String> ids = new ArrayList<>();
         ids.add("random");
         for (RhythmChart chart : this.ctx.rhythm().charts().all()) {
            ids.add(chart.id);
         }
         int idx = ids.indexOf(s.chart());
         int next = (idx + 1) % Math.max(1, ids.size());
         s.setChart(ids.get(next));
      }
   }
}
