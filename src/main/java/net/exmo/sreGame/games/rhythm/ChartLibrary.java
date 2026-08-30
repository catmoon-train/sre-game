package net.exmo.sreGame.games.rhythm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.exmo.sreGame.SreGame;

/**
 * 谱面库：内置 5 首（jar 内资源）+ 服主可放在 config/sre-game/rhythm/ 下的自定义谱面。
 * 外部同名 id 覆盖内置。
 */
public final class ChartLibrary {
   private static final String[] BUILT_IN = {
      "warmup", "beginner", "serenade", "flow", "groove",
      "pulse", "rain", "storm", "blaze", "climax"
   };

   private final Path dir;
   private final Map<String, RhythmChart> charts = new LinkedHashMap<>();

   public ChartLibrary(Path configDir) {
      this.dir = configDir.resolve("rhythm");
   }

   public void load() {
      this.charts.clear();
      for (String id : BUILT_IN) {
         RhythmChart chart = loadBuiltIn(id);
         if (chart != null) {
            this.charts.put(chart.id, chart);
         }
      }
      this.loadExternal();
      if (this.charts.isEmpty()) {
         // 极端情况：资源全部缺失，仍保证有曲目可玩。
         this.charts.put("demo", RhythmChart.procedural("demo", "演示", 120, 60, 42L));
      }
      SreGame.LOGGER.info("Rhythm charts loaded: {}", this.charts.size());
   }

   private RhythmChart loadBuiltIn(String id) {
      String path = "/assets/sre-game/rhythm/" + id + ".json";
      try (InputStream in = SreGame.class.getResourceAsStream(path)) {
         if (in == null) {
            return null;
         }
         String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         return RhythmChart.parse(id, json);
      } catch (IOException e) {
         return null;
      }
   }

   private void loadExternal() {
      try {
         Files.createDirectories(this.dir);
      } catch (IOException e) {
         return;
      }
      try (Stream<Path> files = Files.list(this.dir)) {
         files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
            try {
               String id = p.getFileName().toString();
               id = id.substring(0, id.length() - ".json".length());
               String json = Files.readString(p, StandardCharsets.UTF_8);
               RhythmChart chart = RhythmChart.parse(id, json);
               if (chart != null) {
                  this.charts.put(chart.id, chart);
               }
            } catch (IOException ignored) {
            }
         });
      } catch (IOException ignored) {
      }
   }

   public RhythmChart get(String id) {
      return this.charts.get(id);
   }

   public List<RhythmChart> all() {
      return new ArrayList<>(this.charts.values());
   }

   public RhythmChart random() {
      List<RhythmChart> list = this.all();
      if (list.isEmpty()) {
         return RhythmChart.procedural("demo", "演示", 120, 60, System.nanoTime());
      }
      return list.get(ThreadLocalRandom.current().nextInt(list.size()));
   }

   public RhythmChart resolve(String selection) {
      if (selection == null || selection.isBlank() || "random".equalsIgnoreCase(selection)) {
         return this.random();
      }
      RhythmChart chart = this.get(selection);
      return chart != null ? chart : this.random();
   }

   public String firstId() {
      return this.charts.isEmpty() ? "demo" : this.charts.keySet().iterator().next();
   }
}
