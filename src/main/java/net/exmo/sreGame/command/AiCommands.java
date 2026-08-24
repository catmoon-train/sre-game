package net.exmo.sreGame.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.ai.AiConfig;
import net.exmo.sreGame.ai.AiProviderConfig;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class AiCommands {
   private AiCommands() {
   }

   public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(
         Commands.literal("sregame")
            .requires(src -> src.hasPermission(2))
            .then(
               Commands.literal("ai")
                  .requires(src -> src.hasPermission(2))
                  .executes(c -> status(ctx, c.getSource()))
                  .then(Commands.literal("list").executes(c -> list(ctx, c.getSource())))
                  .then(Commands.literal("status").executes(c -> status(ctx, c.getSource())))
                  .then(
                     Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("api-type", StringArgumentType.word())
                              .then(Commands.argument("api-url", StringArgumentType.word())
                                 .then(Commands.argument("model", StringArgumentType.word())
                                    .executes(c -> setProvider(ctx, c, false))
                                    .then(Commands.argument("api-key", StringArgumentType.string())
                                       .executes(c -> setProvider(ctx, c, true))))))))
                  .then(
                     Commands.literal("key")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("api-key", StringArgumentType.string())
                              .executes(c -> setKey(ctx, c)))))
                  .then(
                     Commands.literal("url")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("api-url", StringArgumentType.word())
                              .executes(c -> setUrl(ctx, c)))))
                  .then(
                     Commands.literal("model")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("model", StringArgumentType.word())
                              .executes(c -> setModel(ctx, c)))))
                  .then(
                     Commands.literal("type")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("api-type", StringArgumentType.word())
                              .executes(c -> setType(ctx, c)))))
                  .then(
                     Commands.literal("thinking")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .then(Commands.argument("thinking-type", StringArgumentType.word())
                              .executes(c -> setThinking(ctx, c, false))
                              .then(Commands.argument("reasoning-effort", StringArgumentType.word())
                                 .executes(c -> setThinking(ctx, c, true))))))
                  .then(
                     Commands.literal("generator")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .executes(c -> select(ctx, c, true))))
                  .then(
                     Commands.literal("answerer")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .executes(c -> select(ctx, c, false))))
                  .then(
                     Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                           .executes(c -> remove(ctx, c))))
                  .then(
                     Commands.literal("password")
                        .executes(c -> clearPassword(ctx, c.getSource()))
                        .then(Commands.argument("password", StringArgumentType.string())
                           .executes(c -> setPassword(ctx, c))))
                  .then(
                     Commands.literal("deepseek")
                        .then(Commands.argument("api-key", StringArgumentType.string())
                           .executes(c -> preset(ctx, c, "deepseek", "openai", "https://api.deepseek.com", "deepseek-chat"))
                           .then(Commands.argument("model", StringArgumentType.word())
                              .executes(c -> preset(ctx, c, "deepseek", "openai", "https://api.deepseek.com", StringArgumentType.getString(c, "model"))))))
                  .then(
                     Commands.literal("openai")
                        .then(Commands.argument("api-key", StringArgumentType.string())
                           .executes(c -> preset(ctx, c, "openai", "openai", "https://api.openai.com", "gpt-4o-mini"))
                           .then(Commands.argument("model", StringArgumentType.word())
                              .executes(c -> preset(ctx, c, "openai", "openai", "https://api.openai.com", StringArgumentType.getString(c, "model"))))))
                  .then(
                     Commands.literal("anthropic")
                        .then(Commands.argument("api-key", StringArgumentType.string())
                           .executes(c -> preset(ctx, c, "anthropic", "anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest"))
                           .then(Commands.argument("model", StringArgumentType.word())
                              .executes(c -> preset(ctx, c, "anthropic", "anthropic", "https://api.anthropic.com", StringArgumentType.getString(c, "model"))))))
            )
      );
   }

   private static void msg(CommandSourceStack src, String text) {
      src.sendSuccess(() -> TextUtil.color(text), false);
   }

   private static void fail(CommandSourceStack src, String text) {
      src.sendFailure(TextUtil.color(text));
   }

   private static int status(GameContext ctx, CommandSourceStack src) {
      AiConfig cfg = ctx.aiConfig();
      msg(src, "&6===== AI 配置状态 =====");
      msg(src, "&7出题： &f" + (cfg.generatorProviderName() == null ? "&c未选择" : cfg.generatorProviderName())
         + " &8(" + (cfg.isGeneratorEnabled() ? "&a已启用" : "&c未配置 key") + "&8)");
      msg(src, "&7回答： &f" + (cfg.answererProviderName() == null ? "&c未选择" : cfg.answererProviderName())
         + " &8(" + (cfg.isAnswererEnabled() ? "&a已启用" : "&c未配置 key") + "&8)");
      msg(src, "&7共 &f" + cfg.providerNames().size() + " &7个提供商，&f/sregame ai list &7查看。");
      msg(src, "&7AI 模式密码： &f" + (cfg.aiPassword().isEmpty() ? "&c未设置（AI 模式无需密码）" : "&a已设置"));
      return 1;
   }

   private static int list(GameContext ctx, CommandSourceStack src) {
      AiConfig cfg = ctx.aiConfig();
      var names = cfg.providerNames();
      if (names.isEmpty()) {
         msg(src, "&7没有任何提供商。用 &f/sregame ai deepseek <key> &7等预设创建。");
         return 1;
      }
      msg(src, "&6===== AI 提供商列表 =====");
      for (String name : names) {
         AiProviderConfig p = cfg.provider(name);
         String roles = "";
         if (name.equals(cfg.generatorProviderName())) roles += " &a[出题]";
         if (name.equals(cfg.answererProviderName())) roles += " &b[回答]";
         msg(src, "&8• &f" + name + roles + " &8| &7" + p.apiType() + " &8| &7" + p.apiUrl()
            + " &8| &7" + p.model() + " &8| " + (p.hasApiKey() ? "&a有 key" : "&c无 key"));
      }
      msg(src, "&7预设：&f/sregame ai deepseek|openai|anthropic <key> [model]");
      return 1;
   }

   private static int setProvider(GameContext ctx, CommandContext<CommandSourceStack> c, boolean hasKey) {
      String name = StringArgumentType.getString(c, "name");
      String apiType = StringArgumentType.getString(c, "api-type");
      String apiUrl = StringArgumentType.getString(c, "api-url");
      String model = StringArgumentType.getString(c, "model");
      String apiKey = hasKey ? StringArgumentType.getString(c, "api-key") : null;
      if (!"openai".equalsIgnoreCase(apiType) && !"anthropic".equalsIgnoreCase(apiType)) {
         fail(c.getSource(), "&capi-type 仅支持 openai 或 anthropic。");
         return 0;
      }
      ctx.aiConfig().setProvider(name, apiType.toLowerCase(java.util.Locale.ROOT), apiUrl, apiKey, model, null, null);
      msg(c.getSource(), "&a已保存提供商 &f" + name + "&a。");
      return 1;
   }

   private static int setKey(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      String key = StringArgumentType.getString(c, "api-key");
      AiProviderConfig p = ctx.aiConfig().provider(name);
      if (p == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      ctx.aiConfig().setProvider(name, p.apiType(), p.apiUrl(), key, p.model(), p.thinkingType(), p.reasoningEffort());
      msg(c.getSource(), "&a已更新 &f" + name + " &a的 API key。");
      return 1;
   }

   private static int setUrl(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      String url = StringArgumentType.getString(c, "api-url");
      AiProviderConfig p = ctx.aiConfig().provider(name);
      if (p == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      ctx.aiConfig().setProvider(name, p.apiType(), url, p.apiKey(), p.model(), p.thinkingType(), p.reasoningEffort());
      msg(c.getSource(), "&a已更新 &f" + name + " &a的 URL：&f" + url);
      return 1;
   }

   private static int setModel(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      String model = StringArgumentType.getString(c, "model");
      AiProviderConfig p = ctx.aiConfig().provider(name);
      if (p == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      ctx.aiConfig().setProvider(name, p.apiType(), p.apiUrl(), p.apiKey(), model, p.thinkingType(), p.reasoningEffort());
      msg(c.getSource(), "&a已更新 &f" + name + " &a的模型：&f" + model);
      return 1;
   }

   private static int setType(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      String type = StringArgumentType.getString(c, "api-type");
      if (!"openai".equalsIgnoreCase(type) && !"anthropic".equalsIgnoreCase(type)) {
         fail(c.getSource(), "&capi-type 仅支持 openai 或 anthropic。"); return 0;
      }
      AiProviderConfig p = ctx.aiConfig().provider(name);
      if (p == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      ctx.aiConfig().setProvider(name, type.toLowerCase(java.util.Locale.ROOT), p.apiUrl(), p.apiKey(), p.model(), p.thinkingType(), p.reasoningEffort());
      msg(c.getSource(), "&a已更新 &f" + name + " &a的协议：&f" + type);
      return 1;
   }

   private static int setThinking(GameContext ctx, CommandContext<CommandSourceStack> c, boolean hasEffort) {
      String name = StringArgumentType.getString(c, "name");
      String thinking = StringArgumentType.getString(c, "thinking-type");
      String effort = hasEffort ? StringArgumentType.getString(c, "reasoning-effort") : null;
      AiProviderConfig p = ctx.aiConfig().provider(name);
      if (p == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      ctx.aiConfig().setProvider(name, p.apiType(), p.apiUrl(), p.apiKey(), p.model(), thinking, effort);
      msg(c.getSource(), "&a已更新 &f" + name + " &a的 thinking：&f" + thinking + (effort != null ? " / " + effort : ""));
      return 1;
   }

   private static int select(GameContext ctx, CommandContext<CommandSourceStack> c, boolean generator) {
      String name = StringArgumentType.getString(c, "name");
      if (ctx.aiConfig().provider(name) == null) { fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0; }
      if (generator) { ctx.aiConfig().setGeneratorProvider(name); msg(c.getSource(), "&a已选 &f" + name + " &a为出题提供商。"); }
      else { ctx.aiConfig().setAnswererProvider(name); msg(c.getSource(), "&a已选 &f" + name + " &a为回答提供商。"); }
      return 1;
   }

   private static int remove(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      if (ctx.aiConfig().removeProvider(name)) { msg(c.getSource(), "&a已删除提供商 &f" + name + "&a。"); return 1; }
      fail(c.getSource(), "&c找不到提供商 &f" + name + "&c。"); return 0;
   }

   private static int setPassword(GameContext ctx, CommandContext<CommandSourceStack> c) {
      String pw = StringArgumentType.getString(c, "password");
      ctx.aiConfig().setAiPassword(pw);
      msg(c.getSource(), "&a已设置海龟汤 AI 模式密码。房主开局 AI 模式时需输入此密码。");
      return 1;
   }

   private static int clearPassword(GameContext ctx, CommandSourceStack src) {
      ctx.aiConfig().setAiPassword("");
      msg(src, "&7已清除海龟汤 AI 模式密码。");
      return 1;
   }

   private static int preset(GameContext ctx, CommandContext<CommandSourceStack> c,
                              String name, String apiType, String apiUrl, String model) {
      String key = StringArgumentType.getString(c, "api-key");
      ctx.aiConfig().setProvider(name, apiType, apiUrl, key, model, "disabled", "");
      if (ctx.aiConfig().generatorProviderName() == null) ctx.aiConfig().setGeneratorProvider(name);
      if (ctx.aiConfig().answererProviderName() == null) ctx.aiConfig().setAnswererProvider(name);
      msg(c.getSource(), "&a预设 &f" + name + "&a 已配置（" + apiType + " / " + apiUrl + " / " + model + "）。");
      msg(c.getSource(), "&7已自动选为出题与回答提供商。可用 &f/sregame ai generator|answerer <name> &7单独调整。");
      return 1;
   }
}
