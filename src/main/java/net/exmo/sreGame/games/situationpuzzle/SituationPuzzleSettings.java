package net.exmo.sreGame.games.situationpuzzle;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

/**
 * 海龟汤房间设置：题目来源、难度、单人模式、AI 辅助房主。
 */
public final class SituationPuzzleSettings {
   public enum PuzzleSource {
      AI("AI 出题"),
      MANUAL("房主手填");

      private final String label;

      PuzzleSource(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      public PuzzleSource next() {
         PuzzleSource[] all = values();
         return all[(ordinal() + 1) % all.length];
      }
   }

   private PuzzleSource puzzleSource = PuzzleSource.AI;
   private Difficulty difficulty = Difficulty.NORMAL;
   /** 单人模式：AI 既出题又当主持人。 */
   private boolean soloMode = false;
   /** 多人模式下，AI 辅助房主给出是/不是/无关建议。 */
   private boolean aiAssistHost = false;
   /** 指定本局使用的 AI 提供商；null/空 表示使用全局默认（generator/answerer）。 */
   private String aiProviderName = null;

   public PuzzleSource puzzleSource() {
      return this.puzzleSource;
   }

   public void cyclePuzzleSource() {
      this.puzzleSource = this.puzzleSource.next();
   }

   public Difficulty difficulty() {
      return this.difficulty;
   }

   public void cycleDifficulty() {
      this.difficulty = this.difficulty.next();
   }

   public boolean soloMode() {
      return this.soloMode;
   }

   public void cycleSoloMode() {
      this.soloMode = !this.soloMode;
   }

   public boolean aiAssistHost() {
      return this.aiAssistHost;
   }

   public void cycleAiAssistHost() {
      this.aiAssistHost = !this.aiAssistHost;
   }

   public String aiProviderName() {
      return this.aiProviderName;
   }

   public void setAiProviderName(String name) {
      this.aiProviderName = (name == null || name.isBlank()) ? null : name;
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("puzzleSource", this.puzzleSource.name());
      data.put("difficulty", this.difficulty.name());
      data.put("soloMode", this.soloMode);
      data.put("aiAssistHost", this.aiAssistHost);
      if (this.aiProviderName != null) data.put("aiProviderName", this.aiProviderName);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      String source = SettingsIo.asString(data, "puzzleSource", this.puzzleSource.name());
      try {
         this.puzzleSource = PuzzleSource.valueOf(source);
      } catch (IllegalArgumentException ignored) {
      }
      String diff = SettingsIo.asString(data, "difficulty", this.difficulty.name());
      try {
         this.difficulty = Difficulty.valueOf(diff);
      } catch (IllegalArgumentException ignored) {
      }
      this.soloMode = SettingsIo.asBool(data, "soloMode", this.soloMode);
      this.aiAssistHost = SettingsIo.asBool(data, "aiAssistHost", this.aiAssistHost);
      this.aiProviderName = SettingsIo.asString(data, "aiProviderName", this.aiProviderName == null ? "" : this.aiProviderName);
      if (this.aiProviderName != null && this.aiProviderName.isBlank()) this.aiProviderName = null;
   }
}
