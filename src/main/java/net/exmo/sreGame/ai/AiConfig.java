package net.exmo.sreGame.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.exmo.sreGame.SreGame;

/**
 * 加载并管理 AI 提供商配置。使用 JSON（Gson 已可用）避免引入 YAML 依赖。
 * 配置文件位于 {@code config/sre-game/ai.json}。
 */
public final class AiConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private final Path file;
   private final Map<String, AiProviderConfig> providers = new LinkedHashMap<>();
   private String generatorProvider;
   private String answererProvider;
   /** 全局 AI 模式密码；非空时房主启动海龟汤 AI 模式需输入此密码。由 OP 通过 /sregame ai password 设置。 */
   private String aiPassword = "";

   public AiConfig(Path configDir) {
      this.file = configDir.resolve("ai.json");
   }

   public void load() {
      this.providers.clear();
      this.generatorProvider = null;
      this.answererProvider = null;
      this.aiPassword = "";
      try {
         Files.createDirectories(this.file.getParent());
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to create config dir for ai.json", e);
      }
      JsonObject root = null;
      if (Files.exists(this.file)) {
         try {
            JsonElement parsed = JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8));
            if (parsed != null && parsed.isJsonObject()) {
               root = parsed.getAsJsonObject();
            }
         } catch (Exception e) {
            SreGame.LOGGER.warn("Failed to read ai.json, using defaults", e);
         }
      }
      if (root == null) {
         root = defaultRoot();
         this.write(root);
      }
      this.parse(root);
      if (this.providers.isEmpty()) {
         this.parse(defaultRoot());
      }
   }

   private void parse(JsonObject root) {
      JsonObject providersObj = root.getAsJsonObject("providers");
      if (providersObj != null) {
         for (Map.Entry<String, JsonElement> entry : providersObj.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
               continue;
            }
            JsonObject p = entry.getValue().getAsJsonObject();
            AiProviderConfig config = new AiProviderConfig(
                  entry.getKey(),
                  str(p, "apiType", "openai"),
                  str(p, "apiUrl", "https://api.deepseek.com"),
                  str(p, "apiKey", ""),
                  str(p, "model", "deepseek-chat"),
                  str(p, "thinkingType", "disabled"),
                  str(p, "reasoningEffort", "high"));
            this.providers.put(config.name(), config);
         }
      }
      JsonObject generator = root.getAsJsonObject("generator");
      if (generator != null) {
         this.generatorProvider = str(generator, "provider", this.generatorProvider);
      }
      JsonObject answerer = root.getAsJsonObject("answerer");
      if (answerer != null) {
         this.answererProvider = str(answerer, "provider", this.answererProvider);
      }
      this.aiPassword = str(root, "aiPassword", this.aiPassword);
   }

   private void write(JsonObject root) {
      try {
         Files.writeString(this.file, GSON.toJson(root), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to write ai.json", e);
      }
   }

   private static JsonObject defaultRoot() {
      JsonObject root = new JsonObject();
      JsonObject providers = new JsonObject();
      providers.add("deepseek-generator", provider("openai", "https://api.deepseek.com",
            "", "deepseek-chat", "disabled", "high"));
      providers.add("deepseek-answerer", provider("openai", "https://api.deepseek.com",
            "", "deepseek-chat", "disabled", ""));
      root.add("providers", providers);
      root.addProperty("aiPassword", "");
      JsonObject generator = new JsonObject();
      generator.addProperty("provider", "deepseek-generator");
      root.add("generator", generator);
      JsonObject answerer = new JsonObject();
      answerer.addProperty("provider", "deepseek-answerer");
      root.add("answerer", answerer);
      return root;
   }

   private static JsonObject provider(String apiType, String apiUrl, String apiKey,
                                      String model, String thinkingType, String reasoningEffort) {
      JsonObject p = new JsonObject();
      p.addProperty("apiType", apiType);
      p.addProperty("apiUrl", apiUrl);
      p.addProperty("apiKey", apiKey);
      p.addProperty("model", model);
      p.addProperty("thinkingType", thinkingType);
      if (reasoningEffort != null && !reasoningEffort.isBlank()) {
         p.addProperty("reasoningEffort", reasoningEffort);
      }
      return p;
   }

   private static String str(JsonObject obj, String key, String def) {
      JsonElement el = obj.get(key);
      if (el == null || el.isJsonNull()) {
         return def;
      }
      String s = el.getAsString();
      return s == null ? def : s;
   }

   public List<String> providerNames() {
      return new ArrayList<>(this.providers.keySet());
   }

   public AiProviderConfig provider(String name) {
      if (name == null || name.isBlank()) {
         return null;
      }
      return this.providers.get(name);
   }

   public AiProviderConfig generatorProvider() {
      return this.provider(this.generatorProvider);
   }

   public AiProviderConfig answererProvider() {
      return this.provider(this.answererProvider);
   }

   public boolean isGeneratorEnabled() {
      AiProviderConfig p = this.generatorProvider();
      return p != null && p.hasApiKey();
   }

   public boolean isAnswererEnabled() {
      AiProviderConfig p = this.answererProvider();
      return p != null && p.hasApiKey();
   }

   public String generatorProviderName() {
      return this.generatorProvider;
   }

   public String answererProviderName() {
      return this.answererProvider;
   }

   public String aiPassword() {
      return this.aiPassword == null ? "" : this.aiPassword;
   }

   public void setAiPassword(String password) {
      this.aiPassword = password == null ? "" : password;
      this.save();
   }

   /** 创建或更新一个提供商，并持久化到 ai.json。 */
   public void setProvider(String name, String apiType, String apiUrl, String apiKey,
                           String model, String thinkingType, String reasoningEffort) {
      if (name == null || name.isBlank()) {
         return;
      }
      AiProviderConfig existing = this.providers.get(name);
      if (apiType == null || apiType.isBlank()) apiType = existing != null ? existing.apiType() : "openai";
      if (apiUrl == null || apiUrl.isBlank()) apiUrl = existing != null ? existing.apiUrl() : "https://api.deepseek.com";
      if (apiKey == null) apiKey = existing != null ? existing.apiKey() : "";
      if (model == null || model.isBlank()) model = existing != null ? existing.model() : "deepseek-chat";
      if (thinkingType == null || thinkingType.isBlank()) thinkingType = existing != null ? existing.thinkingType() : "disabled";
      if (reasoningEffort == null) reasoningEffort = existing != null ? existing.reasoningEffort() : "";
      this.providers.put(name, new AiProviderConfig(name, apiType, apiUrl, apiKey, model, thinkingType, reasoningEffort));
      this.save();
   }

   public boolean removeProvider(String name) {
      if (name == null || !this.providers.containsKey(name)) {
         return false;
      }
      this.providers.remove(name);
      if (name.equals(this.generatorProvider)) this.generatorProvider = null;
      if (name.equals(this.answererProvider)) this.answererProvider = null;
      this.save();
      return true;
   }

   public void setGeneratorProvider(String name) {
      if (name != null && !this.providers.containsKey(name)) {
         return;
      }
      this.generatorProvider = name == null || name.isBlank() ? null : name;
      this.save();
   }

   public void setAnswererProvider(String name) {
      if (name != null && !this.providers.containsKey(name)) {
         return;
      }
      this.answererProvider = name == null || name.isBlank() ? null : name;
      this.save();
   }

   /** 将当前内存中的配置序列化写回 ai.json。 */
   public void save() {
      JsonObject root = new JsonObject();
      JsonObject providersObj = new JsonObject();
      for (AiProviderConfig p : this.providers.values()) {
         providersObj.add(p.name(), provider(p.apiType(), p.apiUrl(), p.apiKey(), p.model(), p.thinkingType(), p.reasoningEffort()));
      }
      root.add("providers", providersObj);
      root.addProperty("aiPassword", this.aiPassword == null ? "" : this.aiPassword);
      JsonObject generator = new JsonObject();
      if (this.generatorProvider != null) generator.addProperty("provider", this.generatorProvider);
      root.add("generator", generator);
      JsonObject answerer = new JsonObject();
      if (this.answererProvider != null) answerer.addProperty("provider", this.answererProvider);
      root.add("answerer", answerer);
      this.write(root);
   }
}
