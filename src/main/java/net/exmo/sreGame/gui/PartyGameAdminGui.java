package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.partygames.PartyMiniGame;
import net.exmo.sreGame.games.partygames.official.OfficialPartyGames;
import net.exmo.sreGame.games.partygames.team.TeamPartyGames;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

/** Global, persisted enable switches for every registered MiniGame (party games and the rest). */
public final class PartyGameAdminGui {
   private static final int[] SLOTS = {10,11,12,13,14, 15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
   private PartyGameAdminGui() { }

   public static void open(GameContext ctx, ServerPlayer player) { open(ctx, player, 0); }

   private static void open(GameContext ctx, ServerPlayer player, int page) {
      if (!player.hasPermissions(2)) return;
      SimpleContainer container = new SimpleContainer(54);
      ItemStackPane.fill(container);
      List<MiniGame> games = new ArrayList<>(ctx.games().all());
      int pages = Math.max(1, (games.size() + SLOTS.length - 1) / SLOTS.length);
      int safe = Math.max(0, Math.min(page, pages - 1));
      int from = safe * SLOTS.length;
      for (int i = 0; i < SLOTS.length && from + i < games.size(); i++) {
         MiniGame game = games.get(from + i);
         boolean enabled = isEnabled(ctx, game);
         List<String> lore = new ArrayList<>();
         lore.add(enabled ? "&a已启用：玩家可选择并开局" : "&c已禁用：不会出现在选择列表");
         lore.add("&7ID： &f" + game.id());
         lore.add(game instanceof PartyMiniGame ? "&7分类： &e派对游戏" : "&7分类： &9小游戏");
         if (game instanceof PartyMiniGame party && isSceneGame(party.type())) {
            lore.add((ctx.partyGames().scenes().ready(party.type()) ? "&a" : "&c") + ctx.partyGames().scenes().status(party.type()));
         }
         lore.add("&e点击切换");
         container.setItem(SLOTS[i], GuiItems.action(enabled ? game.icon() : "barrier", (enabled ? "&a" : "&c") + game.displayName(), lore, "toggle", "id", game.id()));
      }
      if (safe > 0) container.setItem(47, GuiItems.action("arrow", "&e上一页", List.of(), "prev", "page", String.valueOf(safe - 1)));
      if (safe + 1 < pages) container.setItem(51, GuiItems.action("arrow", "&e下一页", List.of(), "next", "page", String.valueOf(safe + 1)));
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
      player.openMenu(new SimpleMenuProvider((sync, inv, ignored) -> new Menu(sync, inv, container, ctx, player),
         TextUtil.color("&4⚙ &f小游戏管理 &8(" + (safe + 1) + "/" + pages + ")")));
   }

   private static boolean isEnabled(GameContext ctx, MiniGame game) {
      return game instanceof PartyMiniGame party ? ctx.partyGames().isEnabled(party.type()) : ctx.config().isGameEnabled(game.id());
   }

   private static void setEnabled(GameContext ctx, MiniGame game, boolean enabled) {
      if (game instanceof PartyMiniGame party) ctx.partyGames().setEnabled(party.type(), enabled);
      else ctx.config().setGameEnabled(game.id(), enabled);
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      Menu(int sync, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(sync, inv, container, 6, viewer); this.ctx = ctx;
      }
      @Override protected void handleChestClick(ServerPlayer player, int slot, int button, ClickType click) {
         if (!player.hasPermissions(2)) { player.closeContainer(); return; }
         String action = GuiItems.actionTag(getSlot(slot).getItem());
         if ("close".equals(action)) { player.closeContainer(); return; }
         if ("prev".equals(action) || "next".equals(action)) {
            try { open(ctx, player, Integer.parseInt(GuiItems.extraTag(getSlot(slot).getItem(), "page"))); }
            catch (NumberFormatException ignored) { open(ctx, player, 0); }
            return;
         }
         if (!"toggle".equals(action)) return;
         String id = GuiItems.extraTag(getSlot(slot).getItem(), "id");
         MiniGame game = ctx.games().get(id);
         if (game == null) return;
         boolean enabled = !isEnabled(ctx, game);
         if (enabled && game instanceof PartyMiniGame party && isSceneGame(party.type()) && !ctx.partyGames().scenes().ready(party.type())) {
            ctx.send(player, "&c无法启用：" + ctx.partyGames().scenes().status(party.type()));
            return;
         }
         setEnabled(ctx, game, enabled);
         ctx.send(player, (enabled ? "&a已启用：&f" : "&c已禁用：&f") + game.displayName());
         open(ctx, player, 0);
      }
   }

   private static boolean isSceneGame(net.exmo.sreGame.games.partygames.PartyGameType type) { return OfficialPartyGames.contains(type) || TeamPartyGames.contains(type); }
}
