package net.exmo.sreGame.caveguess;

import java.util.function.Supplier;
import net.exmo.sreGame.caveguess.mode.BreakItDownMode;
import net.exmo.sreGame.caveguess.mode.CaveModeHandler;
import net.exmo.sreGame.caveguess.mode.OneOrNoneMode;
import net.exmo.sreGame.caveguess.mode.ShadowOnWallMode;
import net.exmo.sreGame.caveguess.mode.WatchYourWordsMode;
import net.exmo.sreGame.caveguess.mode.WhatsThatTuneMode;

public enum CaveMode {
   WATCH_WORDS("谨言慎语", "writable_book", 90, WatchYourWordsMode::new),
   BREAK_DOWN("拆解描述", "item_frame", 90, BreakItDownMode::new),
   SHADOW("墙上影子", "armor_stand", 60, ShadowOnWallMode::new),
   ONE_OR_NONE("唯一线索", "name_tag", 90, OneOrNoneMode::new),
   TUNE("那是什么调", "note_block", 60, WhatsThatTuneMode::new);

   private final String display;
   private final String icon;
   private final int describeSeconds;
   private final Supplier<CaveModeHandler> factory;

   CaveMode(String display, String icon, int describeSeconds, Supplier<CaveModeHandler> factory) {
      this.display = display;
      this.icon = icon;
      this.describeSeconds = describeSeconds;
      this.factory = factory;
   }

   public String display() {
      return this.display;
   }

   public String icon() {
      return this.icon;
   }

   public int describeSeconds() {
      return this.describeSeconds;
   }

   public CaveModeHandler create() {
      return this.factory.get();
   }

   public static int defaultRounds(CaveMode mode) {
      return switch (mode) {
         case WATCH_WORDS -> 3;
         case BREAK_DOWN, SHADOW, ONE_OR_NONE -> 2;
         case TUNE -> 1;
      };
   }
}
