package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.partygames.MapTemplate;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.official.OfficialPartyGames;
import net.exmo.sreGame.games.partygames.team.TeamPartyGames;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class PartyGameSetupGui {
   private PartyGameSetupGui() { }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room, PartyGameType type) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room, type);
      player.openMenu(new SimpleMenuProvider((syncId, inv, ignored) -> new Menu(syncId, inv, container, ctx, player, type),
         TextUtil.color("&6⌂ &f" + type.displayName() + " 设置")));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room, PartyGameType type) {
      ItemStackPane.fill(container);
      String locked = room.partyGameSettings().mapId(type);
      MapTemplate selected = ctx.partyGames().maps().get(locked);
      String mapName = selected == null ? "随机可用模板" : selected.id();
      var official = definition(type);
      if (official != null) {
         String time = official.fixedDurationTicks() == 0 ? "按阶段结束" : official.fixedDurationTicks() / 20 + "s（固定）";
         container.setItem(11, GuiItems.named("clock", "&f时限 &e" + time, List.of("&7来自原玩法状态机，不可使用通用时限覆盖")));
         boolean ready = ctx.partyGames().scenes().ready(type);
         container.setItem(13, GuiItems.named(ready ? "filled_map" : "barrier", ready ? "&a官方场景已校验" : "&c官方场景不可用",
            List.of("&7" + ctx.partyGames().scenes().status(type), "&7场景 ID：&f" + type.id().substring(4, 7))));
      } else {
         container.setItem(11, GuiItems.action("clock", "&f时限 &e" + room.partyGameSettings().durationSeconds(type) + "s",
            List.of("&7积分玩法默认 90 秒", "&e点击切换 60 / 90 / 120 / 180 秒"), "duration"));
         container.setItem(13, GuiItems.action("map", "&f地图 &e" + mapName,
            List.of("&7随机或锁定一个启用模板", "&e点击切换"), "map"));
      }
      container.setItem(31, GuiItems.named(type.icon(), "&6" + type.displayName(), rules(type)));
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
   }

   private static List<String> rules(PartyGameType type) {
      List<String> lines = new ArrayList<>();
      var official = definition(type);
      if (official != null) {
         lines.add("&7人数：&f" + official.minPlayers() + "–" + official.maxPlayers() + " &8| &9蓝方 &7对 &c红方");
         for (String rule : official.rules()) lines.add("&7" + rule);
         return lines;
      }
      lines.add("&7模式： &f" + switch (type.mode()) { case ELIMINATION -> "淘汰"; case SCORE -> "限时积分"; case RACE -> "竞速目标"; });
      lines.add(type == PartyGameType.ONE_IN_CHAMBER ? "&7弓箭命中秒杀并返还一箭" : "&7地图参数由 OP 在地图管理中编辑");
      return lines;
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private final PartyGameType type;
      Menu(int syncId, Inventory inventory, SimpleContainer container, GameContext ctx, ServerPlayer viewer, PartyGameType type) {
         super(syncId, inventory, container, 6, viewer); this.ctx = ctx; this.container = container; this.type = type;
      }
      @Override protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(getSlot(slotId).getItem());
         GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
         if ("back".equals(action)) { RoomPanelGui.open(ctx, player); return; }
         if (room == null || !room.isHost(player.getUUID())) return;
         if (SettingsArchive.handle(ctx, player, room, action)) { fill(ctx, container, room, type); return; }
         if ("duration".equals(action) && !isSceneGame(type)) room.partyGameSettings().cycleDuration(type);
         if ("map".equals(action) && !isSceneGame(type)) cycleMap(room);
         fill(ctx, container, room, type);
      }
      private void cycleMap(GameRoom room) {
         List<MapTemplate> maps = ctx.partyGames().maps().list(type).stream().filter(MapTemplate::enabled).toList();
         String current = room.partyGameSettings().mapId(type);
         if (current.isBlank()) { if (!maps.isEmpty()) room.partyGameSettings().setMapId(type, maps.get(0).id()); return; }
         for (int i = 0; i < maps.size(); i++) if (maps.get(i).id().equals(current)) {
            room.partyGameSettings().setMapId(type, i + 1 < maps.size() ? maps.get(i + 1).id() : ""); return;
         }
         room.partyGameSettings().setMapId(type, "");
      }
   }

   private static net.exmo.sreGame.games.partygames.api.PartyGameDefinition definition(PartyGameType type) {
      var definition = OfficialPartyGames.definition(type);
      return definition != null ? definition : TeamPartyGames.definition(type);
   }
   private static boolean isSceneGame(PartyGameType type) { return OfficialPartyGames.contains(type) || TeamPartyGames.contains(type); }
}
