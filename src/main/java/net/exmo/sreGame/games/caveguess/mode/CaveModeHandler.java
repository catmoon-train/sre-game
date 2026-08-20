package net.exmo.sreGame.games.caveguess.mode;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.games.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.games.caveguess.CaveMode;
import net.minecraft.server.level.ServerPlayer;

public interface CaveModeHandler {
   CaveMode type();

   void onPrepare(CaveGuessersMatch match);

   default void onDescribeTick(CaveGuessersMatch match) {
   }

   default boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      return false;
   }

   default boolean handleGui(CaveGuessersMatch match, ServerPlayer player, String action, String extra) {
      return false;
   }

   default boolean handleUseItem(CaveGuessersMatch match, ServerPlayer player, String action) {
      return false;
   }

   default void onSettle(CaveGuessersMatch match, boolean guessed) {
   }

   default void onCleanup(CaveGuessersMatch match) {
   }

   default boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return false;
   }

   default List<String> boardExtra(CaveGuessersMatch match, UUID player) {
      return List.of();
   }

   default String actionBar(CaveGuessersMatch match, UUID player) {
      return null;
   }
}
