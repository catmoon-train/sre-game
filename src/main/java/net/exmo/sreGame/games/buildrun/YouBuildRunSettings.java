package net.exmo.sreGame.games.buildrun;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class YouBuildRunSettings {
   private static final int[] BUILD_SECONDS = {60, 120, 180, 240};
   private static final int[] SELF_SECONDS = {60, 90, 120};
   private static final int[] BLOCKS = {32, 64, 128, 256};
   private static final int[] LIVES = {1, 2, 3, 5};

   private BuildRunScene scene = BuildRunScene.PLOT;
   private int buildSeconds = 180;
   private int selfSeconds = 90;
   private int blockLimit = 64;
   private int lives = 2;

   public BuildRunScene scene() {
      return this.scene;
   }

   public void cycleScene() {
      this.scene = this.scene.next();
   }

   public int buildSeconds() {
      return this.buildSeconds;
   }

   public void cycleBuildSeconds() {
      this.buildSeconds = next(BUILD_SECONDS, this.buildSeconds, 180);
   }

   public int selfSeconds() {
      return this.selfSeconds;
   }

   public void cycleSelfSeconds() {
      this.selfSeconds = next(SELF_SECONDS, this.selfSeconds, 90);
   }

   public int blockLimit() {
      return this.blockLimit;
   }

   public void cycleBlockLimit() {
      this.blockLimit = next(BLOCKS, this.blockLimit, 64);
   }

   public int lives() {
      return this.lives;
   }

   public void cycleLives() {
      this.lives = next(LIVES, this.lives, 2);
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("scene", this.scene.name());
      data.put("buildSeconds", this.buildSeconds);
      data.put("selfSeconds", this.selfSeconds);
      data.put("blockLimit", this.blockLimit);
      data.put("lives", this.lives);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.scene = BuildRunScene.fromName(SettingsIo.asString(data, "scene", this.scene.name()));
      this.buildSeconds = clamp(BUILD_SECONDS, SettingsIo.asInt(data, "buildSeconds", this.buildSeconds), 180);
      this.selfSeconds = clamp(SELF_SECONDS, SettingsIo.asInt(data, "selfSeconds", this.selfSeconds), 90);
      this.blockLimit = clamp(BLOCKS, SettingsIo.asInt(data, "blockLimit", this.blockLimit), 64);
      this.lives = clamp(LIVES, SettingsIo.asInt(data, "lives", this.lives), 2);
   }

   private static int next(int[] cycle, int current, int fallback) {
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return fallback;
   }

   private static int clamp(int[] cycle, int current, int fallback) {
      for (int value : cycle) {
         if (value == current) {
            return current;
         }
      }
      return fallback;
   }
}
