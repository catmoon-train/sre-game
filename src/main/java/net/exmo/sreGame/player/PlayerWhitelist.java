package net.exmo.sreGame.player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.exmo.sreGame.SreGame;

/** Server whitelist deliberately keyed by player name, rather than UUID. */
public final class PlayerWhitelist {
   private final Path file;
   private final Path importFile;
   private final Map<String, String> names = new LinkedHashMap<>();
   private boolean enabled;

   public PlayerWhitelist(Path configDir) {
      this.file = configDir.resolve("whitelist.json");
      this.importFile = configDir.resolve("whitelist-import.txt");
   }

   public void load() {
      this.names.clear();
      this.enabled = false;
      try {
         Files.createDirectories(this.file.getParent());
         if (!Files.exists(this.file)) {
            this.save();
            this.writeImportTemplate();
            return;
         }
         boolean readingPlayers = false;
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"enabled\"")) {
               this.enabled = trimmed.contains("true");
            } else if (trimmed.startsWith("\"players\"")) {
               readingPlayers = true;
            } else if (readingPlayers && trimmed.startsWith("\"")) {
               String name = unquote(trimmed.replaceFirst(",\\s*$", ""));
               if (!name.isEmpty()) this.addInternal(name);
            } else if (readingPlayers && trimmed.startsWith("]")) {
               readingPlayers = false;
            }
         }
         this.writeImportTemplate();
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to load whitelist", e);
      }
   }

   public boolean isEnabled() { return this.enabled; }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      this.save();
   }

   public boolean allows(String name) { return !this.enabled || this.names.containsKey(key(name)); }

   public boolean add(String name) {
      String normalized = normalize(name);
      if (normalized == null || this.names.containsKey(key(normalized))) return false;
      this.addInternal(normalized);
      this.save();
      return true;
   }

   public boolean remove(String name) {
      if (this.names.remove(key(name)) == null) return false;
      this.save();
      return true;
   }

   public List<String> names() { return this.names.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(); }

   /** Imports whitelist-import.txt. Each name may be on its own line, comma separated, or whitespace separated. */
   public ImportResult importFromFile(boolean replace) {
      if (!Files.exists(this.importFile)) {
         this.writeImportTemplate();
         return new ImportResult(0, 0, 0, false);
      }
      int invalid = 0;
      int added = 0;
      try {
         List<String> imported = new ArrayList<>();
         for (String line : Files.readAllLines(this.importFile, StandardCharsets.UTF_8)) {
            String content = line.replaceFirst("\\s*#.*$", "");
            for (String candidate : content.split("[,\\s]+")) if (!candidate.isBlank()) imported.add(candidate);
         }
         if (replace) this.names.clear();
         for (String candidate : imported) {
            String normalized = normalize(candidate);
            if (normalized == null) invalid++;
            else if (!this.names.containsKey(key(normalized))) {
               this.addInternal(normalized);
               added++;
            }
         }
         this.save();
         return new ImportResult(imported.size(), added, invalid, true);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to import whitelist", e);
         return new ImportResult(0, 0, 0, false);
      }
   }

   public Path file() { return this.file; }
   public Path importFile() { return this.importFile; }

   private void addInternal(String name) { this.names.put(key(name), name); }

   private void save() {
      try {
         Files.createDirectories(this.file.getParent());
         StringBuilder out = new StringBuilder("{\n  \"enabled\": ").append(this.enabled).append(",\n  \"players\": [");
         List<String> sorted = this.names();
         for (int i = 0; i < sorted.size(); i++) out.append(i == 0 ? "\n" : ",\n").append("    \"").append(sorted.get(i)).append("\"");
         out.append(sorted.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
         Files.writeString(this.file, out.toString(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save whitelist", e);
      }
   }

   private void writeImportTemplate() {
      if (Files.exists(this.importFile)) return;
      try {
         Files.writeString(this.importFile, "# 每行一个玩家名；也可用空格或逗号分隔。\n# 导入：/sregame whitelist import [replace]\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to create whitelist import template", e);
      }
   }

   private static String normalize(String name) {
      if (name == null) return null;
      String result = name.trim();
      if (result.isEmpty() || result.length() > 16 || result.chars().anyMatch(Character::isWhitespace)) return null;
      return result;
   }

   private static String key(String name) {
      String normalized = normalize(name);
      return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
   }

   private static String unquote(String value) {
      String trimmed = value.trim();
      return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"") ? trimmed.substring(1, trimmed.length() - 1) : "";
   }

   public record ImportResult(int read, int added, int invalid, boolean success) { }
}
