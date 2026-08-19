package net.exmo.sreGame.caveguess.mode;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.caveguess.CaveMode;
import net.exmo.sreGame.caveguess.CaveWords;
import net.minecraft.server.level.ServerPlayer;

public final class WatchYourWordsMode implements CaveModeHandler {
   private String locked;
   private List<String> banned = List.of();

   @Override
   public CaveMode type() {
      return CaveMode.WATCH_WORDS;
   }

   @Override
   public void onPrepare(CaveGuessersMatch match) {
      this.locked = null;
      this.banned = match.word().bannedForDisplay();
      if (this.banned.size() > 3) {
         this.banned = this.banned.subList(0, 3);
      }
      UUID performer = match.performer();
      match.send(performer, "&c禁用词（不可出现在描述中）： &f" + String.join("、", this.banned));
      match.send(performer, "&e在聊天输入一条描述，发送后锁定，不能修改。");
      match.broadcast("&7描述者正在撰写，请等待告示牌刷新。");
   }

   @Override
   public boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      if (match.isPerformer(player.getUUID())) {
         if (this.locked != null) {
            match.ctx().send(player, "&7描述已锁定： &f" + this.locked);
            return true;
         }
         String text = message == null ? "" : message.trim();
         if (text.isEmpty() || text.length() > 80) {
            match.ctx().send(player, "&c请输入 1–80 字的描述。");
            return true;
         }
         if (CaveWords.containsAny(text, match.word().word(), this.banned)) {
            match.voidWatchWord();
            return true;
         }
         this.locked = text;
         match.ctx().send(player, "&a描述已锁定。");
         match.showDescription(text);
         return true;
      }
      return match.tryGuess(player, message);
   }

   @Override
   public boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return match.isPerformer(player);
   }

   @Override
   public List<String> boardExtra(CaveGuessersMatch match, UUID player) {
      if (match.isPerformer(player)) {
         return List.of("&c禁用 &f" + String.join(",", this.banned));
      }
      return List.of(this.locked == null ? "&7等待描述" : "&b已出题");
   }

   @Override
   public String actionBar(CaveGuessersMatch match, UUID player) {
      if (match.isPerformer(player)) {
         return this.locked == null ? "&e聊天输入描述" : "&a已锁定";
      }
      return this.locked == null ? "&7等待描述" : "&b聊天抢答";
   }
}
