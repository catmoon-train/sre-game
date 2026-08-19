package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.fakehuman.FakeHumanMatch;
import net.exmo.sreGame.fakehuman.FakeHumanPlayer;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class FakeHumanPickGui {
   public enum Kind {
      ADMIT,
      STONE,
      GUN,
      ROPE,
      INSPECT,
      ID_ASK,
      ACCUSE,
      VOUCH,
      REFUSE
   }

   private FakeHumanPickGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, FakeHumanMatch match, Kind kind) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, match, player, kind);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, kind),
         TextUtil.color(title(kind))
      ));
   }

   private static String title(Kind kind) {
      return switch (kind) {
         case ADMIT -> "&a选择请进的人";
         case STONE -> "&6选择驱逐目标";
         case GUN -> "&c选择击毙目标";
         case ROPE -> "&e选择捆绑目标";
         case INSPECT -> "&b选择查验目标";
         case ID_ASK -> "&f选择出示证件的人";
         case ACCUSE -> "&c选择指认目标";
         case VOUCH -> "&a选择担保对象";
         case REFUSE -> "&c选择打发回门外的人";
      };
   }

   private static void fill(GameContext ctx, SimpleContainer container, FakeHumanMatch match, ServerPlayer viewer, Kind kind) {
      ItemStackPane.fill(container);
      List<UUID> targets = match.pickTargets(viewer.getUUID(), kind);
      int slot = 10;
      for (UUID uuid : targets) {
         FakeHumanPlayer state = match.player(uuid);
         List<String> lore = new ArrayList<>();
         lore.add("&7位置： &f" + zoneLabel(state));
         if (state != null && state.bound()) {
            lore.add("&c已被捆绑");
         }
         lore.add("&e点击选择");
         String label = state == null ? ctx.name(uuid) : "访客 " + state.alias();
         ItemStack head = GuiItems.action("player_head", "&f" + label, lore, "pick", "uuid", uuid.toString());
         ServerPlayer online = ctx.player(uuid);
         if (online != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(online.getGameProfile()));
         }
         container.setItem(slot, head);
         slot++;
         if (slot % 9 == 8) {
            slot += 2;
         }
         if (slot >= 44) {
            break;
         }
      }
      container.setItem(49, GuiItems.action("barrier", "&c取消", List.of(), "close"));
   }

   private static String zoneLabel(FakeHumanPlayer state) {
      if (state == null) {
         return "?";
      }
      return switch (state.zone()) {
         case SPECTATE -> "未到访";
         case DOOR -> "门口";
         case INSIDE -> "屋内";
         case DEAD -> "揭晓中";
      };
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final Kind kind;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer, Kind kind) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.kind = kind;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         if ("close".equals(action)) {
            player.closeContainer();
            return;
         }
         if (!"pick".equals(action)) {
            return;
         }
         String raw = GuiItems.extraTag(this.getSlot(slotId).getItem(), "uuid");
         if (raw == null) {
            return;
         }
         FakeHumanMatch match = this.ctx.fakeHuman().get(player.getUUID());
         if (match == null) {
            player.closeContainer();
            return;
         }
         UUID target = UUID.fromString(raw);
         player.closeContainer();
         match.handlePick(player, this.kind, target);
      }
   }
}
