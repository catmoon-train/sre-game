package net.exmo.sreGame.ai;

/**
 * 单个 AI 提供商配置（OpenAI / Anthropic 兼容端点）。
 * 用 record 代替源工程的 Lombok @Value。
 */
public record AiProviderConfig(
      String name,
      String apiType,
      String apiUrl,
      String apiKey,
      String model,
      String thinkingType,
      String reasoningEffort) {

   public boolean hasApiKey() {
      return apiKey != null && !apiKey.isBlank();
   }
}
