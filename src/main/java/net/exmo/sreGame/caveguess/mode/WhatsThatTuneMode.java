package net.exmo.sreGame.caveguess.mode;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.caveguess.CaveMode;
import net.exmo.sreGame.caveguess.gui.TuneGui;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class WhatsThatTuneMode implements CaveModeHandler {
   @Override
   public CaveMode type() {
      return CaveMode.TUNE;
   }

   @Override
   public void onPrepare(CaveGuessersMatch match) {
      boolean free = match.settings().freeTuneGuess();
      ServerPlayer performer = match.ctx().player(match.performer());
      if (performer != null) {
         performer.getInventory().setItem(0, match.reopenItem("note_block", "&e音符键盘", "cave-tune"));
         TuneGui.openKeyboard(match, performer);
      }
      for (UUID uuid : match.remaining()) {
         if (match.isPerformer(uuid)) {
            continue;
         }
         ServerPlayer guesser = match.ctx().player(uuid);
         if (guesser == null) {
            continue;
         }
         if (free) {
            match.send(uuid, "&b自由猜：在聊天输入歌名/旋律相关词。");
         } else {
            guesser.getInventory().setItem(0, match.reopenItem("jukebox", "&e四选一", "cave-choices"));
            TuneGui.openChoices(match, guesser);
         }
      }
      match.broadcast("&7描述者请用音符或哼唱表达目标，不能说话。");
   }

   @Override
   public boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      if (match.isPerformer(player.getUUID())) {
         match.ctx().send(player, "&7本模式不能打字或说话，请用音符键盘。");
         return true;
      }
      if (!match.settings().freeTuneGuess()) {
         match.ctx().send(player, "&7请用四选一菜单作答。");
         return true;
      }
      return match.tryGuess(player, message);
   }

   @Override
   public boolean handleGui(CaveGuessersMatch match, ServerPlayer player, String action, String extra) {
      if ("note".equals(action) && match.isPerformer(player.getUUID())) {
         try {
            int note = Integer.parseInt(extra);
            play(match.level(), player, note);
         } catch (NumberFormatException ignored) {
         }
         TuneGui.openKeyboard(match, player);
         return true;
      }
      if ("pick".equals(action) && !match.isPerformer(player.getUUID())) {
         if (match.settings().freeTuneGuess()) {
            return false;
         }
         player.closeContainer();
         if (match.word().word().equals(extra)) {
            match.tryGuess(player, extra);
         } else {
            match.markWrongChoice(player);
         }
         return true;
      }
      return false;
   }

   @Override
   public boolean handleUseItem(CaveGuessersMatch match, ServerPlayer player, String action) {
      if ("cave-tune".equals(action) && match.isPerformer(player.getUUID())) {
         TuneGui.openKeyboard(match, player);
         return true;
      }
      if ("cave-choices".equals(action) && !match.isPerformer(player.getUUID()) && !match.settings().freeTuneGuess()) {
         TuneGui.openChoices(match, player);
         return true;
      }
      return false;
   }

   @Override
   public boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return false;
   }

   @Override
   public String actionBar(CaveGuessersMatch match, UUID player) {
      if (match.isPerformer(player)) {
         return "&e弹奏或哼唱，不要说话";
      }
      return match.settings().freeTuneGuess() ? "&b聊天抢答" : "&b四选一";
   }

   private static void play(ServerLevel level, ServerPlayer player, int note) {
      int clamped = Math.max(0, Math.min(24, note));
      float pitch = (float) Math.pow(2.0, (clamped - 12) / 12.0);
      if (level != null) {
         level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
            SoundSource.RECORDS, 3.0F, pitch);
      }
   }
}
