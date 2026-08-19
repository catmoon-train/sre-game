package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.BuildGroup;
import net.exmo.sreGame.buildwar.BuildWarMatch;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class BuildWarVoteGui {
   private static final String[] MATS = {
      "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "lime_wool"
   };
   private static final int[] SLOTS = {20, 21, 22, 23, 24};

   private BuildWarVoteGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, BuildWarMatch match) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, match, player);
      BuildGroup group = match.reviewingGroup();
      String title = group == null
         ? "&6✦ &f评分"
         : "&6✦ &f主题 " + (group.id() + 1) + "  开局「" + group.startWord() + "」";
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, match, player),
         TextUtil.color(title)
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, BuildWarMatch match, ServerPlayer player) {
      ItemStackPane.fill(container);
      BuildGroup group = match.reviewingGroup();
      if (group == null) {
         return;
      }
      int current = match.voteScore(player.getUUID(), group.id());
      container.setItem(4, GuiItems.named("book", "&f主题 &e" + (group.id() + 1), List.of(
         "&7开局： &e" + group.startWord(),
         "&7演变： &f" + group.chainText(),
         "&7最终： &a" + group.finalWord(),
         current == 0 ? "&e尚未打分" : "&a当前： &f" + current + " 分"
      )));
      for (int i = 0; i < 5; i++) {
         int score = i + 1;
         boolean selected = current == score;
         container.setItem(SLOTS[i], GuiItems.action(
            MATS[i],
            (selected ? "&a&l" : "&f") + score + " 分",
            List.of(selected ? "&a已选择" : "&e点击打 " + score + " 分", "&7也可聊天输入数字"),
            "score",
            "score",
            String.valueOf(score)
         ));
      }
      container.setItem(49, GuiItems.named("clock", "&7也可右键热键栏羊毛，或聊天输入 1–5", List.of()));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private final BuildWarMatch match;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, BuildWarMatch match, ServerPlayer viewer) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         if (this.match.phase() != BuildWarMatch.Phase.SCORING) {
            player.closeContainer();
            return;
         }
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         if (!"score".equals(action)) {
            return;
         }
         String raw = GuiItems.extraTag(this.getSlot(slotId).getItem(), "score");
         if (raw == null) {
            return;
         }
         try {
            this.match.acceptScore(player, Integer.parseInt(raw));
            fill(this.ctx, this.container, this.match, player);
         } catch (NumberFormatException ignored) {
         }
      }
   }
}
