package net.exmo.sreGame.caveguess.mode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.caveguess.CaveMode;
import net.exmo.sreGame.caveguess.CaveWords;
import net.minecraft.server.level.ServerPlayer;

public final class OneOrNoneMode implements CaveModeHandler {
   private static final int CLUE_TICKS = 200;
   private final Map<UUID, String> clues = new LinkedHashMap<>();
   private boolean closed;
   private boolean guessed;

   @Override
   public CaveMode type() {
      return CaveMode.ONE_OR_NONE;
   }

   @Override
   public void onPrepare(CaveGuessersMatch match) {
      this.clues.clear();
      this.closed = false;
      this.guessed = false;
      match.broadcast("&e线索阶段 10 秒：除猜测者外每人提交一个单词。");
      for (UUID uuid : match.remaining()) {
         if (!match.isIsolatedGuesser(uuid)) {
            match.send(uuid, "&e在聊天输入一个单词线索。不能包含目标词的字。");
         } else {
            match.send(uuid, "&7请等待唯一线索出现。");
         }
      }
   }

   @Override
   public void onDescribeTick(CaveGuessersMatch match) {
      if (!this.closed && match.describeTicks() >= CLUE_TICKS) {
         this.closeClues(match);
      }
   }

   @Override
   public boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      UUID uuid = player.getUUID();
      if (!this.closed) {
         if (match.isIsolatedGuesser(uuid)) {
            match.ctx().send(player, "&7线索尚未公布。");
            return true;
         }
         if (this.clues.containsKey(uuid)) {
            match.ctx().send(player, "&7你已经提交过线索。");
            return true;
         }
         String clue = message == null ? "" : message.trim();
         if (clue.isEmpty() || clue.length() > 16 || clue.contains(" ")) {
            match.ctx().send(player, "&c请输入一个不含空格的单词（1–16 字）。");
            return true;
         }
         if (CaveWords.clueHitsTarget(clue, match.word().word())) {
            match.ctx().send(player, "&c线索不能包含目标词的任何字，已作废。");
            this.clues.put(uuid, "");
            this.maybeClose(match);
            return true;
         }
         this.clues.put(uuid, clue);
         match.ctx().send(player, "&a已提交线索： &f" + clue);
         this.maybeClose(match);
         return true;
      }
      if (match.isIsolatedGuesser(uuid)) {
         if (this.guessed) {
            match.ctx().send(player, "&c你只有一次机会。");
            return true;
         }
         String guess = message == null ? "" : message.trim();
         if (guess.isEmpty()) {
            match.ctx().send(player, "&c请输入答案。");
            return true;
         }
         this.guessed = true;
         return match.tryGuess(player, guess);
      }
      match.ctx().send(player, "&7线索者本轮不能再发言。");
      return true;
   }

   @Override
   public boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return !match.isIsolatedGuesser(player);
   }

   @Override
   public List<String> boardExtra(CaveGuessersMatch match, UUID player) {
      return List.of(this.closed ? "&b根据线索答题" : "&e提交线索");
   }

   @Override
   public String actionBar(CaveGuessersMatch match, UUID player) {
      if (!this.closed) {
         return match.isIsolatedGuesser(player) ? "&7等待线索" : "&e输入一个单词线索";
      }
      return match.isIsolatedGuesser(player) ? "&b只有一次机会" : "&7等待猜测者";
   }

   private void maybeClose(CaveGuessersMatch match) {
      int needed = 0;
      for (UUID uuid : match.remaining()) {
         if (!match.isIsolatedGuesser(uuid)) {
            needed++;
         }
      }
      if (this.clues.size() >= needed) {
         this.closeClues(match);
      }
   }

   private void closeClues(CaveGuessersMatch match) {
      if (this.closed) {
         return;
      }
      this.closed = true;
      Map<String, List<UUID>> groups = new LinkedHashMap<>();
      for (Map.Entry<UUID, String> entry : this.clues.entrySet()) {
         String key = CaveWords.normalize(entry.getValue());
         if (key.isEmpty()) {
            continue;
         }
         groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry.getKey());
      }
      List<String> unique = new ArrayList<>();
      List<UUID> owners = new ArrayList<>();
      for (Map.Entry<String, List<UUID>> entry : groups.entrySet()) {
         if (entry.getValue().size() == 1) {
            String original = this.clues.get(entry.getValue().get(0));
            unique.add(original);
            owners.add(entry.getValue().get(0));
         }
      }
      match.setUniqueClueOwners(owners);
      String shown = unique.isEmpty() ? "none" : String.join(" · ", unique);
      match.showClues(shown);
      match.send(match.isolatedGuesser(), "&e你只有一次答题机会，请在聊天输入答案。");
   }
}
