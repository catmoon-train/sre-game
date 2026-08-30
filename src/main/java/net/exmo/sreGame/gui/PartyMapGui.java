package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.partygames.MapTemplate;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

/** OP-only read/manage entry; edits are deliberately performed by explicit /sregame maps commands. */
public final class PartyMapGui {
   private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
   private PartyMapGui() { }

   public static void open(GameContext ctx, ServerPlayer player) { openTypes(ctx, player); }

   private static void openTypes(GameContext ctx, ServerPlayer player) {
      openTypes(ctx, player, 0);
   }

   private static void openTypes(GameContext ctx, ServerPlayer player, int page) {
      SimpleContainer container = new SimpleContainer(54);
      ItemStackPane.fill(container);
      PartyGameType[] types = PartyGameType.values();
      int pages = Math.max(1, (types.length + SLOTS.length - 1) / SLOTS.length);
      int safePage = Math.max(0, Math.min(page, pages - 1));
      int from = safePage * SLOTS.length;
      for (int i = 0; i < SLOTS.length && from + i < types.length; i++) {
         PartyGameType type = types[from + i];
         int count = ctx.partyGames().maps().list(type).size();
         container.setItem(SLOTS[i], GuiItems.action(type.icon(), "&f" + type.displayName(), List.of("&7模板： &f" + count, "&e点击查看"), "type", "id", type.id()));
      }
      if (safePage > 0) container.setItem(47, GuiItems.action("arrow", "&e上一页", List.of("&7第 &f" + (safePage + 1) + "&7/&f" + pages + " &7页"), "prev", "page", String.valueOf(safePage - 1)));
      if (safePage + 1 < pages) container.setItem(51, GuiItems.action("arrow", "&e下一页", List.of("&7第 &f" + (safePage + 1) + "&7/&f" + pages + " &7页"), "next", "page", String.valueOf(safePage + 1)));
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
      player.openMenu(new SimpleMenuProvider((sync, inv, ignored) -> new Menu(sync, inv, container, ctx, player, null), TextUtil.color("&6⌂ &f派对地图管理 &8(" + (safePage + 1) + "/" + pages + ")")));
   }

   private static void openMaps(GameContext ctx, ServerPlayer player, PartyGameType type) {
      SimpleContainer container = new SimpleContainer(54);
      ItemStackPane.fill(container);
      List<MapTemplate> maps = ctx.partyGames().maps().list(type);
      for (int i = 0; i < maps.size() && i < SLOTS.length; i++) {
         MapTemplate map = maps.get(i);
         container.setItem(SLOTS[i], GuiItems.action(type.icon(), "&f" + map.id(), List.of(
            map.enabled() ? "&a已启用" : "&c已禁用", map.defaultTemplate() ? "&6服务器默认" : "&7普通模板",
            "&7种子： &f" + map.seed(), "&7size： &f" + map.parameter("size", 48), "&7height： &f" + map.parameter("height", 12),
            "&e左键切换启用 · 右键设为默认"), "map", "id", map.id()));
      }
      container.setItem(45, GuiItems.action("arrow", "&e返回游戏列表", List.of(), "back"));
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
      player.openMenu(new SimpleMenuProvider((sync, inv, ignored) -> new Menu(sync, inv, container, ctx, player, type), TextUtil.color("&6⌂ &f" + type.displayName() + " 地图")));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final PartyGameType type;
      Menu(int sync, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer player, PartyGameType type) {
         super(sync, inv, container, 6, player); this.ctx = ctx; this.type = type;
      }
      @Override protected void handleChestClick(ServerPlayer player, int slot, int button, ClickType click) {
         if (!player.hasPermissions(2)) return;
         String action = GuiItems.actionTag(getSlot(slot).getItem());
         if ("close".equals(action)) { player.closeContainer(); return; }
         if ("back".equals(action)) { openTypes(ctx, player); return; }
         if ("prev".equals(action) || "next".equals(action)) {
            try { openTypes(ctx, player, Integer.parseInt(GuiItems.extraTag(getSlot(slot).getItem(), "page"))); }
            catch (NumberFormatException ignored) { openTypes(ctx, player); }
            return;
         }
         if ("type".equals(action)) openMaps(ctx, player, PartyGameType.byId(GuiItems.extraTag(getSlot(slot).getItem(), "id")));
         if ("map".equals(action) && type != null) {
            String id = GuiItems.extraTag(getSlot(slot).getItem(), "id");
            if (button == 1) ctx.partyGames().maps().setDefault(id); else ctx.partyGames().maps().toggle(id);
            openMaps(ctx, player, type);
         }
      }
   }
}
