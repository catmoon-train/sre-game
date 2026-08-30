package net.exmo.sreGame.games.rhythm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 一张节奏谱面：由一串带时间戳的音符组成。
 *
 * <p>JSON 格式：
 * <pre>
 * {
 *   "id": "flow",
 *   "name": "流光",
 *   "bpm": 120,
 *   "notes": [
 *     { "t": 1000, "lane": "RED",  "type": "tap" },
 *     { "t": 1400, "lane": "BLUE", "type": "hold", "duration": 800 }
 *   ]
 * }
 * </pre>
 * t 为距歌曲开始（第一个音符）的毫秒数。长按音符（hold）用 duration 表示尾部长度。
 */
public final class RhythmChart {

   public enum Lane {
      RED,
      BLUE
   }

   public enum NoteType {
      TAP,
      HOLD,
      GOLD,
      ACCENT,
      MOVING
   }

   /** 单个音符的静态定义（谱面数据，运行时每个玩家各自维护击打状态）。 */
   public static final class Note {
      public final long timeMs;
      public final Lane lane;
      public final NoteType type;
      public final long durationMs;

      public Note(long timeMs, Lane lane, NoteType type, long durationMs) {
         this.timeMs = timeMs;
         this.lane = lane;
         this.type = type;
         this.durationMs = type == NoteType.HOLD ? Math.max(120L, durationMs) : 0L;
      }
   }

   public final String id;
   public final String name;
   public final int bpm;
   public final long lengthMs;
   private final List<Note> notes;

   private RhythmChart(String id, String name, int bpm, List<Note> notes) {
      this.id = id;
      this.name = name;
      this.bpm = bpm;
      this.notes = List.copyOf(notes);
      long last = 0L;
      for (Note n : this.notes) {
         last = Math.max(last, n.timeMs + n.durationMs);
      }
      this.lengthMs = last + 1500L;
   }

   public List<Note> notes() {
      return this.notes;
   }

   public int noteCount() {
      return this.notes.size();
   }

   /**
    * 纯左键模式的谱面变体：长按音符拆成连续短音块，避免生成连接长条；
    * 同时把少量普通音块标记为移动音块，让模式有更明显的节奏变化。
    */
   public RhythmChart asPureLeft() {
      List<Note> expanded = new ArrayList<>();
      int stepMs = Math.max(140, 60000 / Math.max(40, this.bpm) / 4);
      int ordinal = 0;
      for (Note note : this.notes) {
         Lane lane = note.type == NoteType.GOLD ? Lane.BLUE : note.lane;
         if (note.type == NoteType.HOLD) {
            long end = note.timeMs + note.durationMs;
            for (long t = note.timeMs; t <= end; t += stepMs) {
               expanded.add(new Note(t, lane, NoteType.TAP, 0L));
            }
         } else {
            NoteType type = note.type;
            if (type == NoteType.TAP && ordinal % 8 == 3) {
               type = NoteType.MOVING;
            }
            expanded.add(new Note(note.timeMs, lane, type, 0L));
         }
         ordinal++;
      }
      return new RhythmChart(this.id, this.name + " · 纯左键", this.bpm, expanded);
   }

   /** 解析 JSON 谱面；失败返回 null。 */
   public static RhythmChart parse(String id, String json) {
      if (json == null || json.isBlank()) {
         return null;
      }
      try {
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         String name = optString(root, "name", id);
         int bpm = optInt(root, "bpm", 120);
         List<Note> notes = new ArrayList<>();
         JsonArray arr = root.getAsJsonArray("notes");
         if (arr == null) {
            return null;
         }
         for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
               continue;
            }
            JsonObject o = el.getAsJsonObject();
            long t = Math.max(0L, optLong(o, "t", 0L));
            String laneRaw = optString(o, "lane", "RED").toUpperCase(Locale.ROOT);
            Lane lane = "BLUE".equals(laneRaw) ? Lane.BLUE : Lane.RED;
            String typeRaw = optString(o, "type", "tap").toUpperCase(Locale.ROOT);
            NoteType type = switch (typeRaw) {
               case "HOLD" -> NoteType.HOLD;
               case "GOLD" -> NoteType.GOLD;
               case "ACCENT", "BEAT", "SPECIAL" -> NoteType.ACCENT;
               case "MOVING", "MOVE", "SWING" -> NoteType.MOVING;
               default -> NoteType.TAP;
            };
            long duration = optLong(o, "duration", 0L);
            notes.add(new Note(t, lane, type, duration));
         }
         if (notes.isEmpty()) {
            return null;
         }
         notes.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
         return new RhythmChart(id, name, bpm, notes);
      } catch (Exception e) {
         return null;
      }
   }

   /** 程序化生成一张可播放的谱面（兜底 / 随机曲目）。 */
   public static RhythmChart procedural(String id, String name, int bpm, int noteCount, long seed) {
      Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
      List<Note> notes = new ArrayList<>();
      long t = 1200L;
      int stepMs = Math.max(180, 60000 / Math.max(40, bpm) / 2);
      Lane lastLane = Lane.RED;
      for (int i = 0; i < noteCount; i++) {
         Lane lane;
         if (rng.nextDouble() < 0.15) {
            lane = lastLane;
         } else {
            lane = lastLane == Lane.RED ? Lane.BLUE : Lane.RED;
            lastLane = lane;
         }
         NoteType type;
         double r = rng.nextDouble();
         if (r < 0.12) {
            type = NoteType.HOLD;
         } else if (r < 0.20) {
            type = NoteType.GOLD;
         } else if (r < 0.30) {
            type = NoteType.ACCENT;
         } else if (r < 0.40) {
            type = NoteType.MOVING;
         } else {
            type = NoteType.TAP;
         }
         long duration = type == NoteType.HOLD ? 400L + rng.nextInt(5) * 150L : 0L;
         notes.add(new Note(t, lane, type, duration));
         int variance = (int) (stepMs * (rng.nextDouble() - 0.5));
         t += stepMs + variance;
      }
      return new RhythmChart(id, name, bpm, notes);
   }

   private static String optString(JsonObject o, String key, String def) {
      JsonElement e = o.get(key);
      return e == null || e.isJsonNull() ? def : e.getAsString();
   }

   private static int optInt(JsonObject o, String key, int def) {
      JsonElement e = o.get(key);
      if (e == null || e.isJsonNull()) {
         return def;
      }
      try {
         return e.getAsInt();
      } catch (NumberFormatException ex) {
         return def;
      }
   }

   private static long optLong(JsonObject o, String key, long def) {
      JsonElement e = o.get(key);
      if (e == null || e.isJsonNull()) {
         return def;
      }
      try {
         return e.getAsLong();
      } catch (NumberFormatException ex) {
         return def;
      }
   }
}
