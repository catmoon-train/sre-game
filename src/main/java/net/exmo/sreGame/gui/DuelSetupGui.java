package net.exmo.sreGame.gui;

import com.mcrpvp.duel.fabric.api.DuelApi;
import com.mcrpvp.duel.fabric.queue.QueueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.DuelSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class DuelSetupGui {
   private DuelSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, player, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⚔ &f决斗设置")
      ));
   }

   private static void fill(GameContext ctx, ServerPlayer player, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      DuelSettings settings = room.duelSettings();
      int slot = 10;
      for (Map.Entry<String, Object> entry : DuelApi.getGameModes().entrySet()) {
         if (!(entry.getValue() instanceof Map<?, ?> meta)) {
            continue;
         }
         String id = entry.getKey();
         if (!DuelApi.isModeEnabled(id)) {
            continue;
         }
         String name = meta.get("name") != null ? String.valueOf(meta.get("name")) : id;
         String icon = meta.get("icon") != null ? String.valueOf(meta.get("icon")) : "diamond_sword";
         int teamSize = DuelApi.getTeamSize(id);
         boolean selected = id.equals(settings.gamemode());
         List<String> lore = new ArrayList<>();
         lore.add("&7" + teamSize + "v" + teamSize);
         lore.add(DuelApi.isRankedEnabled(id) ? "&a可排位" : "&7仅休闲");
         lore.add(selected ? "&6✔ 已选择" : "&e点击选择");
         container.setItem(slot, GuiItems.action(icon, (selected ? "&6&l✔ " : "&f") + name, lore, "mode", "id", id));
         slot++;
         if (slot % 9 == 8) {
            slot += 2;
         }
         if (slot >= 44) {
            break;
         }
      }
      boolean ranked = settings.queueType() == QueueType.RANKED;
      container.setItem(45, GuiItems.action(ranked ? "diamond_sword" : "iron_sword",
         ranked ? "&6排位" : "&7休闲",
         List.of("&e点击切换队列类型"), "queue"));
      container.setItem(47, GuiItems.action("clock", "&f回合 FT" + settings.rounds(), List.of("&e点击 +1（最大 10，循环）"), "rounds"));
      SettingsArchive.paint(container, 48, 50);
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
         var stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if (action == null || room == null || !room.isHost(player.getUUID())) {
            if ("back".equals(action)) {
               RoomPanelGui.open(this.ctx, player);
            }
            return;
         }
         if (SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.ctx, player, this.container, room);
            return;
         }
         DuelSettings settings = room.duelSettings();
         switch (action) {
            case "back" -> RoomPanelGui.open(this.ctx, player);
            case "queue" -> {
               settings.setQueueType(settings.queueType() == QueueType.RANKED ? QueueType.UNRANKED : QueueType.RANKED);
               fill(this.ctx, player, this.container, room);
            }
            case "rounds" -> {
               int next = settings.rounds() >= 10 ? 1 : settings.rounds() + 1;
               settings.setRounds(next);
               fill(this.ctx, player, this.container, room);
            }
            case "mode" -> {
               String id = GuiItems.extraTag(stack, "id");
               if (id != null) {
                  settings.setGamemode(id);
                  fill(this.ctx, player, this.container, room);
               }
            }
            default -> {
            }
         }
      }
   }
}
