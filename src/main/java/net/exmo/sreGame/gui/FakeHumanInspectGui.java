package net.exmo.sreGame.gui;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.fakehuman.FakeHumanMatch;
import net.exmo.sreGame.games.fakehuman.FakeHumanPlayer;
import net.exmo.sreGame.games.fakehuman.InspectType;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class FakeHumanInspectGui {
   private FakeHumanInspectGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, UUID target) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, target);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, target),
         TextUtil.color("&b查验 &f" + aliasOf(ctx, target))
      ));
   }

   private static String aliasOf(GameContext ctx, UUID target) {
      FakeHumanMatch match = ctx.fakeHuman().get(target);
      FakeHumanPlayer state = match == null ? null : match.player(target);
      return state == null ? ctx.name(target) : state.alias();
   }

   private static void fill(GameContext ctx, SimpleContainer container, UUID target) {
      ItemStackPane.fill(container);
      FakeHumanMatch match = ctx.fakeHuman().get(target);
      boolean rain = match != null && match.tempDisabled();
      boolean blackout = match != null && match.inspectDisabled();
      int slot = 11;
      for (InspectType type : InspectType.values()) {
         boolean blocked = blackout || type == InspectType.TEMP && rain;
         container.setItem(slot, GuiItems.action(type.icon(),
            (blocked ? "&8" : "&f") + type.display(),
            List.of(
               "&7" + type.hint(),
               "&7误报率 &f" + (int) Math.round(type.missRate() * 100) + "%",
               blocked ? "&c当前不可用" : "&e点击查验（结果仅你可见）"
            ),
            blocked ? "blocked" : "type",
            "id", type.name()));
         slot += 2;
      }
      container.setItem(49, GuiItems.action("barrier", "&c取消", List.of(), "close"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final UUID target;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, UUID target) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.target = target;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         if ("close".equals(action) || "blocked".equals(action)) {
            if ("close".equals(action)) {
               player.closeContainer();
            }
            return;
         }
         if (!"type".equals(action)) {
            return;
         }
         String id = GuiItems.extraTag(this.getSlot(slotId).getItem(), "id");
         InspectType type;
         try {
            type = InspectType.valueOf(id);
         } catch (Exception e) {
            return;
         }
         FakeHumanMatch match = this.ctx.fakeHuman().get(player.getUUID());
         player.closeContainer();
         if (match != null) {
            match.inspect(player, this.target, type);
         }
      }
   }
}
