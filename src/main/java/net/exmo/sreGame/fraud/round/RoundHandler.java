package net.exmo.sreGame.fraud.round;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.draw.Canvas;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public interface RoundHandler {
   RoundType type();

   String rules();

   void onPrepare(FraudMasterMatch match);

   default void onCallStart(FraudMasterMatch match) {
   }

   default void onCallTick(FraudMasterMatch match) {
   }

   default void onActionStart(FraudMasterMatch match) {
   }

   default void onActionTick(FraudMasterMatch match) {
   }

   default void onActionTimeout(FraudMasterMatch match) {
   }

   void onSettle(FraudMasterMatch match);

   default int actionSeconds(FraudMasterMatch match) {
      return 30;
   }

   default boolean handleChat(FraudMasterMatch match, ServerPlayer player, String message) {
      return false;
   }

   default boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      return false;
   }

   default void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
   }

   default List<String> privateInfo(FraudMasterMatch match, UUID player) {
      return List.of();
   }

   default List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return List.of();
   }

   default String actionBar(FraudMasterMatch match, UUID player) {
      return null;
   }

   default boolean canPaint(FraudMasterMatch match, UUID player) {
      return false;
   }

   default Canvas canvas(FraudMasterMatch match, UUID player) {
      return null;
   }

   default void onLeave(FraudMasterMatch match, UUID player) {
   }
}
