package net.exmo.sreGame.input;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.CreateRoomGui;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.RoomSettingsGui;
import net.exmo.sreGame.gui.RoomWordsGui;
import net.exmo.sreGame.gui.WordBankGui;
import net.exmo.sreGame.gui.WordPackGui;
import net.exmo.sreGame.words.WordLibrary;
import net.exmo.sreGame.words.WordPack;
import net.exmo.sreGame.room.CreateDraft;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class ChatPrompt {
   public enum Kind {
      DRAFT_NAME,
      DRAFT_PASSWORD,
      ROOM_NAME,
      ROOM_PASSWORD,
      WORD_ADD,
      ROOM_WORD_ADD,
      PACK_EXPORT,
      SITUATION_AI_PASSWORD
   }

   public record Pending(Kind kind, String roomId, long since) {
   }

   private static final long TIMEOUT_MILLIS = 60_000L;
   private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

   private ChatPrompt() {
   }

   public static void await(ServerPlayer player, Kind kind, String roomId, String hint) {
      PENDING.put(player.getUUID(), new Pending(kind, roomId, System.currentTimeMillis()));
      player.sendSystemMessage(net.exmo.sreGame.util.TextUtil.color(hint));
      player.sendSystemMessage(net.exmo.sreGame.util.TextUtil.color("&7输入 &f取消 &7可中止（60 秒有效）。"));
   }

   public static boolean handle(GameContext ctx, ServerPlayer player, String message) {
      Pending pending = PENDING.get(player.getUUID());
      if (pending == null) {
         return false;
      }
      if (System.currentTimeMillis() - pending.since() > TIMEOUT_MILLIS) {
         PENDING.remove(player.getUUID());
         return false;
      }
      String input = message == null ? "" : message.trim();
      if (input.equalsIgnoreCase("cancel") || "取消".equals(input)) {
         PENDING.remove(player.getUUID());
         ctx.send(player, "&7已取消输入。");
         return true;
      }
      PENDING.remove(player.getUUID());
      switch (pending.kind()) {
         case DRAFT_NAME -> {
            CreateDraft draft = ctx.rooms().draft(player.getUUID());
            draft.name = input.isBlank() ? draft.name : input;
            ctx.send(player, "&a房间名称已设为 &f" + draft.name);
            CreateRoomGui.open(ctx, player);
         }
         case DRAFT_PASSWORD -> {
            CreateDraft draft = ctx.rooms().draft(player.getUUID());
            draft.password = input.isBlank() ? null : input;
            ctx.send(player, draft.password == null ? "&7已清除密码。" : "&a密码已设置。");
            CreateRoomGui.open(ctx, player);
         }
         case ROOM_NAME -> {
            GameRoom room = ctx.rooms().get(pending.roomId());
            if (room == null || !room.isHost(player.getUUID())) {
               ctx.send(player, "&c无法修改该房间。");
               return true;
            }
            if (!input.isBlank()) {
               room.setDisplayName(input);
               ctx.broadcast(room, "&7房间名称改为 &f" + input);
            }
            RoomSettingsGui.open(ctx, player);
         }
         case ROOM_PASSWORD -> {
            GameRoom room = ctx.rooms().get(pending.roomId());
            if (room == null || !room.isHost(player.getUUID())) {
               ctx.send(player, "&c无法修改该房间。");
               return true;
            }
            room.setPassword(input.isBlank() ? null : input);
            ctx.send(player, room.hasPassword() ? "&a密码已更新。" : "&7已取消密码。");
            RoomSettingsGui.open(ctx, player);
         }
         case WORD_ADD -> {
            if (ctx.words().add(input)) {
               ctx.send(player, "&a已添加主题词： &f" + input.trim());
            } else {
               ctx.send(player, "&c添加失败（空、重复或过长）。");
            }
            WordBankGui.open(ctx, player, 0);
         }
         case ROOM_WORD_ADD -> {
            String[] meta = splitMeta(pending.roomId());
            GameRoom room = ctx.rooms().get(meta[0]);
            if (room == null || !room.isHost(player.getUUID())) {
               ctx.send(player, "&c无法修改该词库。");
               return true;
            }
            room.editableWords(ctx);
            if (room.addWord(input)) {
               ctx.send(player, "&a已添加主题词： &f" + input.trim());
            } else {
               ctx.send(player, "&c添加失败（空、重复或过长）。");
            }
            RoomWordsGui.open(ctx, player, room, meta[1], 0);
         }
         case PACK_EXPORT -> {
            String[] meta = splitMeta(pending.roomId());
            GameRoom room = ctx.rooms().get(meta[0]);
            if (room == null) {
               ctx.send(player, "&c房间已不存在。");
               return true;
            }
            if (ctx.library().packsOf(player.getUUID()).size() >= WordLibrary.MAX_PACKS) {
               ctx.send(player, "&c个人词库已满（最多 10 套）。");
               WordPackGui.open(ctx, player, room, meta[1]);
               return true;
            }
            String name = input.isBlank() ? "未命名词库" : input;
            if (name.length() > 16) {
               name = name.substring(0, 16);
            }
            WordPack pack = ctx.library().saveNew(player.getUUID(), name,
               room.isCaveGuess() && !room.hasCustomWords()
                  ? ctx.caveWords().plainTexts()
                  : room.resolvedWords(ctx));
            if (pack == null) {
               ctx.send(player, "&c保存失败。");
            } else {
               ctx.send(player, "&a已导出词库 &f" + pack.name() + " &7（" + pack.words().size() + " 词）。");
            }
            WordPackGui.open(ctx, player, room, meta[1]);
         }
         case SITUATION_AI_PASSWORD -> {
            GameRoom room = ctx.rooms().get(pending.roomId());
            if (room == null || !room.isHost(player.getUUID())) {
               ctx.send(player, "&c房间已不存在或你不是房主。");
               return true;
            }
            String pw = ctx.aiConfig().aiPassword();
            if (pw == null || pw.isEmpty() || pw.equals(input)) {
               ctx.situationPuzzle().grantAiAuth(player.getUUID());
               ctx.send(player, pw == null || pw.isEmpty() ? "&a开始对局…" : "&a密码正确，开始对局…");
               ctx.rooms().start(player);
            } else {
               ctx.send(player, "&c密码错误，对局未开始。");
            }
         }
      }
      return true;
   }

   private static String[] splitMeta(String raw) {
      if (raw == null || raw.isBlank()) {
         return new String[] {"", "you_guess"};
      }
      int split = raw.indexOf('|');
      if (split < 0) {
         return new String[] {raw, "you_guess"};
      }
      return new String[] {raw.substring(0, split), raw.substring(split + 1)};
   }
}
