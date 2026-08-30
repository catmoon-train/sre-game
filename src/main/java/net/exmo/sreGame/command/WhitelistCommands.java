package net.exmo.sreGame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.player.NameManager;
import net.exmo.sreGame.player.PlayerWhitelist.ImportResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WhitelistCommands {
   private static final Component KICK_MESSAGE = Component.literal("你不在该服务器的白名单中。");

   private WhitelistCommands() { }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(Commands.literal("sregame").then(root(ctx)));
   }

   private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(GameContext ctx) {
      return Commands.literal("whitelist").requires(source -> source.hasPermission(2))
         .executes(c -> status(ctx, c.getSource()))
         .then(Commands.literal("status").executes(c -> status(ctx, c.getSource())))
         .then(Commands.literal("on").executes(c -> setEnabled(ctx, c.getSource(), true)))
         .then(Commands.literal("off").executes(c -> setEnabled(ctx, c.getSource(), false)))
         .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
            .executes(c -> add(ctx, c.getSource(), StringArgumentType.getString(c, "name")))))
         .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
            .executes(c -> remove(ctx, c.getSource(), StringArgumentType.getString(c, "name")))))
         .then(Commands.literal("list").executes(c -> list(ctx, c.getSource())))
         .then(Commands.literal("import").executes(c -> importFile(ctx, c.getSource(), false))
            .then(Commands.literal("replace").executes(c -> importFile(ctx, c.getSource(), true))))
         .then(Commands.literal("reload").executes(c -> reload(ctx, c.getSource())));
   }

   private static int status(GameContext ctx, CommandSourceStack source) {
      source.sendSuccess(() -> Component.literal("玩家白名单：" + (ctx.whitelist().isEnabled() ? "已启用" : "已关闭")
         + "，共 " + ctx.whitelist().names().size() + " 人。配置：" + ctx.whitelist().file()), false);
      return 1;
   }

   private static int setEnabled(GameContext ctx, CommandSourceStack source, boolean enabled) {
      ctx.whitelist().setEnabled(enabled);
      if (enabled) enforceOnline(ctx);
      source.sendSuccess(() -> Component.literal(enabled ? "已启用玩家白名单。" : "已关闭玩家白名单。"), true);
      return 1;
   }

   private static int add(GameContext ctx, CommandSourceStack source, String name) {
      boolean added = ctx.whitelist().add(name);
      source.sendSuccess(() -> Component.literal(added ? "已加入白名单：" + name : "添加失败：名称无效或已存在。"), true);
      return added ? 1 : 0;
   }

   private static int remove(GameContext ctx, CommandSourceStack source, String name) {
      boolean removed = ctx.whitelist().remove(name);
      if (removed) enforceOnline(ctx);
      source.sendSuccess(() -> Component.literal(removed ? "已移出白名单：" + name : "该玩家不在白名单中。"), true);
      return removed ? 1 : 0;
   }

   private static int list(GameContext ctx, CommandSourceStack source) {
      List<String> names = ctx.whitelist().names();
      source.sendSuccess(() -> Component.literal(names.isEmpty() ? "白名单为空。" : "白名单（" + names.size() + "）：" + String.join(", ", names)), false);
      return 1;
   }

   private static int importFile(GameContext ctx, CommandSourceStack source, boolean replace) {
      ImportResult result = ctx.whitelist().importFromFile(replace);
      if (result.success()) enforceOnline(ctx);
      source.sendSuccess(() -> Component.literal(result.success()
         ? "白名单导入完成：读取 " + result.read() + "，新增 " + result.added() + "，无效 " + result.invalid() + "。"
         : "找不到导入文件，已创建模板：" + ctx.whitelist().importFile()), true);
      return result.success() ? 1 : 0;
   }

   private static int reload(GameContext ctx, CommandSourceStack source) {
      ctx.whitelist().load();
      enforceOnline(ctx);
      source.sendSuccess(() -> Component.literal("白名单已从文件重新加载。"), true);
      return 1;
   }

   public static boolean allows(GameContext ctx, ServerPlayer player) {
      return player != null && ctx.whitelist().allows(NameManager.originalName(player));
   }

   public static void enforceOnline(GameContext ctx) {
      if (ctx.server() == null || !ctx.whitelist().isEnabled()) return;
      for (ServerPlayer player : ctx.server().getPlayerList().getPlayers()) {
         if (!allows(ctx, player)) player.connection.disconnect(KICK_MESSAGE);
      }
   }

   public static void reject(ServerPlayer player) {
      player.connection.disconnect(KICK_MESSAGE);
   }
}
