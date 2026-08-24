package net.exmo.sreGame.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.ai.AnswerType;
import net.exmo.sreGame.games.situationpuzzle.Question;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleMatch;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 海龟汤对局内指令：/sp say 发言、/sp list 查看记录、/sp answer 修改回答。
 * 对局期间聊天会被游戏拦截，这些指令提供额外输入与回看能力。
 */
public final class SituationPuzzleCommands {
   private SituationPuzzleCommands() {
   }

   public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(
         Commands.literal("sp")
            .executes(c -> help(c.getSource()))
            .then(
               Commands.literal("say")
                  .then(Commands.argument("text", StringArgumentType.greedyString())
                     .executes(c -> say(ctx, c))))
            .then(
               Commands.literal("list")
                     .executes(c -> list(ctx, c)))
            .then(
               Commands.literal("answer")
                  .then(Commands.argument("index", IntegerArgumentType.integer(1))
                     .then(Commands.argument("type", StringArgumentType.word())
                        .executes(c -> answer(ctx, c))))));
   }

   private static int help(CommandSourceStack src) {
      src.sendSuccess(() -> TextUtil.color(
         "&6海龟汤指令：\n"
            + "&f/sp say <文本> &7— 对局内向房间发言\n"
            + "&f/sp list &7— 查看提问与回答记录\n"
            + "&f/sp answer <序号> <是|不是|是或不是|无关> &7— 设置/修改某条提问的回答"), false);
      return 1;
   }

   private static int say(GameContext ctx, CommandContext<CommandSourceStack> c) {
      ServerPlayer player = player(c.getSource());
      if (player == null) return 0;
      SituationPuzzleMatch match = ctx.situationPuzzle().get(player.getUUID());
      if (match == null) {
         ctx.send(player, "&c你不在海龟汤对局中。");
         return 0;
      }
      String text = StringArgumentType.getString(c, "text");
      if (text == null || text.trim().isEmpty()) {
         ctx.send(player, "&c内容不能为空。");
         return 0;
      }
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      if (room == null) {
         return 0;
      }
      ctx.broadcast(room, "&b" + player.getGameProfile().getName() + " &7（发言）&f" + text);
      return 1;
   }

   private static int list(GameContext ctx, CommandContext<CommandSourceStack> c) {
      ServerPlayer player = player(c.getSource());
      if (player == null) return 0;
      SituationPuzzleMatch match = ctx.situationPuzzle().get(player.getUUID());
      if (match == null) {
         ctx.send(player, "&c你不在海龟汤对局中。");
         return 0;
      }
      var qs = match.questions();
      if (qs.isEmpty()) {
         ctx.send(player, "&7还没有提问。");
         return 1;
      }
      ctx.send(player, "&6海龟汤提问记录：");
      for (int i = 0; i < qs.size(); i++) {
         Question q = qs.get(i);
         String ans = q.isAnswered() ? q.answerType().label() : "&7待答";
         ctx.send(player, "&8" + (i + 1) + ". &b" + q.askerName() + " &7： &f" + q.question() + " &8→ " + ans);
      }
      return 1;
   }

   private static int answer(GameContext ctx, CommandContext<CommandSourceStack> c) {
      ServerPlayer player = player(c.getSource());
      if (player == null) return 0;
      SituationPuzzleMatch match = ctx.situationPuzzle().get(player.getUUID());
      if (match == null) {
         ctx.send(player, "&c你不在海龟汤对局中。");
         return 0;
      }
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      if (room == null || !room.isHost(player.getUUID())) {
         ctx.send(player, "&c只有房主可以回答。");
         return 0;
      }
      int index = IntegerArgumentType.getInteger(c, "index");
      AnswerType type = AnswerType.fromLabel(StringArgumentType.getString(c, "type"));
      if (type == null) {
         ctx.send(player, "&c回答类型无效。用 &f是/不是/是或不是/无关 &7或 &f1/2/4/3&7。");
         return 0;
      }
      if (match.setAnswer(index, type, player)) {
         return 1;
      }
      ctx.send(player, "&c无法设置回答：序号超出范围，或当前不是多人对局的提问阶段。用 &f/sp list &c查看序号。");
      return 0;
   }

   private static ServerPlayer player(CommandSourceStack source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         return player;
      }
      source.sendFailure(Component.literal("该命令仅玩家可用。"));
      return null;
   }
}
