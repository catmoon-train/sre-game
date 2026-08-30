package net.exmo.sreGame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.quakechasm.QuakeCommands;
import net.exmo.sreGame.gui.MainMenuGui;
import net.exmo.sreGame.gui.PartyMapGui;
import net.exmo.sreGame.gui.PartyGameAdminGui;
import net.exmo.sreGame.gui.RoomBrowserGui;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GameCommands {
   private GameCommands() { }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(Commands.literal("sregame")
         .executes(c -> openMenu(c.getSource(), ctx))
         .then(joinLeaveNotificationCommands(ctx))
         .then(Commands.literal("luckyitems").requires(src -> src.hasPermission(2)).executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; net.exmo.sreGame.gui.LuckyItemGui.open(ctx, p, 0); return 1; }))
         .then(Commands.literal("words").requires(src -> src.hasPermission(2)).executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; net.exmo.sreGame.gui.WordBankGui.open(ctx, p, 0); return 1; }))
         .then(Commands.literal("partyadmin").requires(src -> src.hasPermission(2)).executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; PartyGameAdminGui.open(ctx, p); return 1; }))
         .then(mapCommands(ctx)));
      AiCommands.register(dispatcher, ctx);
      QuakeCommands.register(dispatcher, ctx);
      SituationPuzzleCommands.register(dispatcher, ctx);
      PlayerCommands.register(dispatcher, ctx);
      WhitelistCommands.register(dispatcher, ctx);
      dispatcher.register(joinLeaveNotificationCommands(ctx));
      dispatcher.register(Commands.literal("sg").executes(c -> openMenu(c.getSource(), ctx)));
      dispatcher.register(Commands.literal("menu").executes(c -> openMenu(c.getSource(), ctx)));
      dispatcher.register(roomCommands(ctx));
      registerParkour(dispatcher, ctx);
   }

   private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mapCommands(GameContext ctx) {
      var root = Commands.literal("maps").requires(src -> src.hasPermission(2))
         .executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; PartyMapGui.open(ctx, p); return 1; });
      var create = Commands.literal("create");
      var game = Commands.argument("game", StringArgumentType.word());
      game.then(Commands.argument("id", StringArgumentType.word()).executes(c -> mapCreate(ctx, c.getSource(), StringArgumentType.getString(c, "game"), StringArgumentType.getString(c, "id"))));
      create.then(game); root.then(create);
      root.then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).executes(c -> mapDelete(ctx, c.getSource(), StringArgumentType.getString(c, "id")))));
      root.then(Commands.literal("rebuild").then(Commands.argument("id", StringArgumentType.word()).executes(c -> mapRebuild(ctx, c.getSource(), StringArgumentType.getString(c, "id")))));
      root.then(Commands.literal("default").then(Commands.argument("id", StringArgumentType.word()).executes(c -> mapDefault(ctx, c.getSource(), StringArgumentType.getString(c, "id")))));
      root.then(Commands.literal("toggle").then(Commands.argument("id", StringArgumentType.word()).executes(c -> mapToggle(ctx, c.getSource(), StringArgumentType.getString(c, "id")))));
      var seed = Commands.literal("seed");
      var seedId = Commands.argument("id", StringArgumentType.word());
      seedId.then(Commands.argument("value", LongArgumentType.longArg()).executes(c -> mapSeed(ctx, c.getSource(), StringArgumentType.getString(c, "id"), LongArgumentType.getLong(c, "value"))));
      seed.then(seedId); root.then(seed);
      var set = Commands.literal("set");
      var setId = Commands.argument("id", StringArgumentType.word());
      var setKey = Commands.argument("key", StringArgumentType.word());
      setKey.then(Commands.argument("value", IntegerArgumentType.integer()).executes(c -> mapParameter(ctx, c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "key"), IntegerArgumentType.getInteger(c, "value"))));
      setId.then(setKey); set.then(setId); root.then(set);
      return root;
   }

   private static int mapCreate(GameContext ctx, CommandSourceStack source, String game, String id) { PartyGameType type = PartyGameType.byId(game); boolean ok = ctx.partyGames().maps().create(type, id); source.sendSuccess(() -> Component.literal(ok ? "已创建地图模板：" + id : "创建失败：检查游戏 ID 或模板 ID。"), false); return ok ? 1 : 0; }
   private static int mapDelete(GameContext ctx, CommandSourceStack source, String id) { boolean ok = ctx.partyGames().maps().delete(id); source.sendSuccess(() -> Component.literal(ok ? "已删除地图模板：" + id : "删除失败（默认模板不可删除）。"), false); return ok ? 1 : 0; }
   private static int mapRebuild(GameContext ctx, CommandSourceStack source, String id) { boolean ok = ctx.partyGames().maps().get(id) != null; source.sendSuccess(() -> Component.literal(ok ? "模板会在下次分配场地时按当前参数重建：" + id : "找不到地图模板。"), false); return ok ? 1 : 0; }
   private static int mapDefault(GameContext ctx, CommandSourceStack source, String id) { boolean ok = ctx.partyGames().maps().setDefault(id); source.sendSuccess(() -> Component.literal(ok ? "已设为该小游戏默认地图：" + id : "找不到地图模板。"), false); return ok ? 1 : 0; }
   private static int mapToggle(GameContext ctx, CommandSourceStack source, String id) { boolean ok = ctx.partyGames().maps().toggle(id); source.sendSuccess(() -> Component.literal(ok ? "已切换模板启用状态：" + id : "找不到地图模板。"), false); return ok ? 1 : 0; }
   private static int mapSeed(GameContext ctx, CommandSourceStack source, String id, long value) { boolean ok = ctx.partyGames().maps().setSeed(id, value); source.sendSuccess(() -> Component.literal(ok ? "已更新地图种子：" + id : "找不到地图模板。"), false); return ok ? 1 : 0; }
   private static int mapParameter(GameContext ctx, CommandSourceStack source, String id, String key, int value) { boolean ok = ctx.partyGames().maps().setParameter(id, key, value); source.sendSuccess(() -> Component.literal(ok ? "已更新地图参数 " + key + "：" + id : "参数无效（可用 size、height、difficulty、weight）。"), false); return ok ? 1 : 0; }

   private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> roomCommands(GameContext ctx) {
      return Commands.literal("room").executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; GameRoom room = ctx.rooms().getByPlayer(p.getUUID()); if (room != null) RoomPanelGui.open(ctx, p); else RoomBrowserGui.open(ctx, p, 0); return 1; })
         .then(Commands.literal("create").executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; if (ctx.rooms().create(p, p.getGameProfile().getName() + " 的房间", 2, true, null, "mcrpvp_duel") != null) RoomPanelGui.open(ctx, p); return 1; }))
         .then(Commands.literal("join").then(Commands.argument("code", StringArgumentType.word()).executes(c -> join(ctx, c.getSource(), StringArgumentType.getString(c, "code"), null)).then(Commands.argument("password", StringArgumentType.word()).executes(c -> join(ctx, c.getSource(), StringArgumentType.getString(c, "code"), StringArgumentType.getString(c, "password"))))))
         .then(Commands.literal("leave").executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; if (ctx.rooms().getByPlayer(p.getUUID()) == null) { ctx.send(p, "&c你不在任何房间中。"); return 0; } ctx.rooms().leave(p); ctx.send(p, "&7已离开房间。"); return 1; }))
         .then(Commands.literal("end").executes(c -> { ServerPlayer p = player(c.getSource()); return p != null && ctx.rooms().endActiveMatch(p) ? 1 : 0; }))
         .then(Commands.literal("list").executes(c -> {
            ServerPlayer p = player(c.getSource()); if (p == null) return 0;
            var rooms = ctx.rooms().publicRooms();
            if (rooms.isEmpty()) { ctx.send(p, "&7当前没有公开房间。用 &f/room create &7创建一个。"); return 1; }
            ctx.send(p, "&6公开房间：");
            for (GameRoom listed : rooms) ctx.send(p, "&8• &f" + listed.displayName() + " &8[" + listed.id() + "] &7" + listed.size() + "/" + listed.maxPlayers() + (listed.hasPassword() ? " &c锁" : ""));
            return 1;
         }));
   }

   private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> joinLeaveNotificationCommands(GameContext ctx) {
      return Commands.literal("hidejoinleave").requires(src -> src.hasPermission(2))
         .executes(c -> setJoinLeaveNotifications(ctx, c.getSource(), !ctx.config().hideJoinLeaveNotifications()))
         .then(Commands.literal("on").executes(c -> setJoinLeaveNotifications(ctx, c.getSource(), true)))
         .then(Commands.literal("off").executes(c -> setJoinLeaveNotifications(ctx, c.getSource(), false)))
         .then(Commands.literal("status").executes(c -> {
            boolean hidden = ctx.config().hideJoinLeaveNotifications();
            c.getSource().sendSuccess(() -> Component.literal("进服和退服通知当前" + (hidden ? "已隐藏。" : "正常显示。")), false);
            return 1;
         }));
   }

   private static int setJoinLeaveNotifications(GameContext ctx, CommandSourceStack source, boolean hide) {
      ctx.config().setHideJoinLeaveNotifications(hide);
      source.sendSuccess(() -> Component.literal(hide ? "已隐藏进服和退服通知。" : "已恢复进服和退服通知。"), true);
      return 1;
   }

   private static void registerParkour(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      var parkour = Commands.literal("parkour").executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null) return 0; if (ctx.parkour().isPlaying(p)) net.exmo.sreGame.gui.ParkourMenuGui.open(ctx, p); else ctx.parkour().join(p); return 1; })
         .then(Commands.literal("join").executes(c -> { ServerPlayer p = player(c.getSource()); if (p != null) ctx.parkour().join(p); return p == null ? 0 : 1; }))
         .then(Commands.literal("leave").executes(c -> { ServerPlayer p = player(c.getSource()); if (p == null || !ctx.parkour().isPlaying(p)) return 0; ctx.parkour().leave(p, true); return 1; }));
      dispatcher.register(parkour);
      dispatcher.register(Commands.literal("ip").executes(c -> { ServerPlayer p = player(c.getSource()); if (p != null) ctx.parkour().join(p); return p == null ? 0 : 1; }));
   }

   private static int join(GameContext ctx, CommandSourceStack source, String code, String password) { ServerPlayer p = player(source); if (p != null && ctx.rooms().join(p, code, password)) { RoomPanelGui.open(ctx, p); return 1; } return 0; }
   private static int openMenu(CommandSourceStack source, GameContext ctx) { ServerPlayer p = player(source); if (p == null) return 0; MainMenuGui.open(ctx, p); return 1; }
   private static ServerPlayer player(CommandSourceStack source) { if (source.getEntity() instanceof ServerPlayer p) return p; source.sendFailure(Component.literal("该命令仅玩家可用。")); return null; }
}
