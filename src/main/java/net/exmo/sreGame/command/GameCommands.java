package net.exmo.sreGame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.MainMenuGui;
import net.exmo.sreGame.gui.RoomBrowserGui;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GameCommands {
   private GameCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(Commands.literal("sregame")
         .executes(c -> openMenu(c.getSource(), ctx))
         .then(Commands.literal("luckyitems").requires(src -> src.hasPermission(2)).executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            net.exmo.sreGame.gui.LuckyItemGui.open(ctx, player, 0);
            return 1;
         }))
         .then(Commands.literal("words").requires(src -> src.hasPermission(2)).executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            net.exmo.sreGame.gui.WordBankGui.open(ctx, player, 0);
            return 1;
         })));
      dispatcher.register(Commands.literal("sg").executes(c -> openMenu(c.getSource(), ctx)));
      dispatcher.register(Commands.literal("menu").executes(c -> openMenu(c.getSource(), ctx)));

      var room = Commands.literal("room")
         .executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            GameRoom current = ctx.rooms().getByPlayer(player.getUUID());
            if (current != null) {
               RoomPanelGui.open(ctx, player);
            } else {
               RoomBrowserGui.open(ctx, player, 0);
            }
            return 1;
         })
         .then(Commands.literal("create").executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            GameRoom created = ctx.rooms().create(
               player,
               player.getGameProfile().getName() + " 的房间",
               2,
               true,
               null,
               "mcrpvp_duel"
            );
            if (created != null) {
               RoomPanelGui.open(ctx, player);
            }
            return 1;
         }))
         .then(Commands.literal("join")
            .then(Commands.argument("code", StringArgumentType.word())
               .executes(c -> join(ctx, c.getSource(), StringArgumentType.getString(c, "code"), null))
               .then(Commands.argument("password", StringArgumentType.word())
                  .executes(c -> join(ctx, c.getSource(),
                     StringArgumentType.getString(c, "code"),
                     StringArgumentType.getString(c, "password"))))))
         .then(Commands.literal("leave").executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            if (ctx.rooms().getByPlayer(player.getUUID()) == null) {
               ctx.send(player, "&c你不在任何房间中。");
               return 0;
            }
            ctx.rooms().leave(player);
            ctx.send(player, "&7已离开房间。");
            return 1;
         }))
         .then(Commands.literal("list").executes(c -> {
            ServerPlayer player = player(c.getSource());
            if (player == null) {
               return 0;
            }
            var rooms = ctx.rooms().publicRooms();
            if (rooms.isEmpty()) {
               ctx.send(player, "&7当前没有公开房间。用 &f/room create &7创建一个。");
               return 1;
            }
            ctx.send(player, "&6公开房间：");
            for (GameRoom listed : rooms) {
               ctx.send(player, "&8• &f" + listed.displayName() + " &8[" + listed.id() + "] &7"
                  + listed.size() + "/" + listed.maxPlayers()
                  + (listed.hasPassword() ? " &c锁" : ""));
            }
            return 1;
         }));
      dispatcher.register(room);
   }

   private static int join(GameContext ctx, CommandSourceStack source, String code, String password) {
      ServerPlayer player = player(source);
      if (player == null) {
         return 0;
      }
      if (ctx.rooms().join(player, code, password)) {
         RoomPanelGui.open(ctx, player);
         return 1;
      }
      return 0;
   }

   private static int openMenu(CommandSourceStack source, GameContext ctx) {
      ServerPlayer player = player(source);
      if (player == null) {
         return 0;
      }
      MainMenuGui.open(ctx, player);
      return 1;
   }

   private static ServerPlayer player(CommandSourceStack source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         return player;
      }
      source.sendFailure(Component.literal("该命令仅玩家可用。"));
      return null;
   }
}
