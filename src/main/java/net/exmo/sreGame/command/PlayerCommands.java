package net.exmo.sreGame.command;

import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.player.NameManager;
import net.exmo.sreGame.player.PlayerVisibility;
import net.exmo.sreGame.player.SkinManager;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands for skin management and player hiding.
 *
 * <pre>
 *   /skin copy &lt;target&gt; &lt;source&gt;      复制皮肤
 *   /skin set  &lt;target&gt; &lt;texture&gt;     直接设置皮肤纹理（base64）
 *   /skin url  &lt;target&gt; [slim] &lt;url&gt;  从皮肤图片 URL 设置
 *   /skin reset &lt;target&gt;               恢复原皮肤 / 清除为默认
 *   /skin info &lt;target&gt;                查看皮肤与隐藏状态
 *   /hide &lt;player&gt;                      隐藏玩家
 *   /unhide &lt;player&gt;                    取消隐藏
 *   /hide list                           列出已隐藏的玩家
 *   /hidetab &lt;player&gt;                    仅在 Tab 列表中隐藏玩家
 *   /unhidetab &lt;player&gt;                  取消 Tab 列表隐藏
 *   /hidetab list                        列出 Tab 列表中已隐藏的玩家
 *   /nick &lt;player&gt; &lt;name&gt;            修改名字（支持中文，最长 16 字）
 *   /nick reset &lt;player&gt;                 恢复原名
 * </pre>
 */
public final class PlayerCommands {
   private PlayerCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
      dispatcher.register(Commands.literal("skin")
         .requires(src -> src.hasPermission(2))
         .then(Commands.literal("copy")
            .then(Commands.argument("target", EntityArgument.player())
               .then(Commands.argument("source", EntityArgument.player())
                  .executes(c -> copy(c.getSource(),
                     EntityArgument.getPlayer(c, "target"),
                     EntityArgument.getPlayer(c, "source"))))))
         .then(Commands.literal("set")
            .then(Commands.argument("target", EntityArgument.player())
               .then(Commands.argument("texture", StringArgumentType.greedyString())
                  .executes(c -> set(c.getSource(),
                     EntityArgument.getPlayer(c, "target"),
                     StringArgumentType.getString(c, "texture"))))))
         .then(Commands.literal("url")
            .then(Commands.argument("target", EntityArgument.player())
               .then(Commands.literal("slim")
                  .then(Commands.argument("url", StringArgumentType.greedyString())
                     .executes(c -> fromUrl(c.getSource(),
                        EntityArgument.getPlayer(c, "target"),
                        StringArgumentType.getString(c, "url"), "slim"))))
               .then(Commands.argument("url", StringArgumentType.greedyString())
                  .executes(c -> fromUrl(c.getSource(),
                     EntityArgument.getPlayer(c, "target"),
                     StringArgumentType.getString(c, "url"), null)))))
         .then(Commands.literal("reset")
            .then(Commands.argument("target", EntityArgument.player())
               .executes(c -> reset(c.getSource(), EntityArgument.getPlayer(c, "target")))))
         .then(Commands.literal("info")
            .then(Commands.argument("target", EntityArgument.player())
               .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "target"))))));

      dispatcher.register(Commands.literal("hide")
         .requires(src -> src.hasPermission(2))
         .then(Commands.literal("list").executes(c -> hideList(c.getSource())))
         .then(Commands.argument("target", EntityArgument.player())
            .executes(c -> hide(c.getSource(), EntityArgument.getPlayer(c, "target")))));
      dispatcher.register(Commands.literal("unhide")
         .requires(src -> src.hasPermission(2))
         .then(Commands.argument("target", EntityArgument.player())
            .executes(c -> unhide(c.getSource(), EntityArgument.getPlayer(c, "target")))));

      dispatcher.register(Commands.literal("hidetab")
         .requires(src -> src.hasPermission(2))
         .then(Commands.literal("list").executes(c -> hideTabList(c.getSource())))
         .then(Commands.argument("target", EntityArgument.player())
            .executes(c -> hideTab(c.getSource(), EntityArgument.getPlayer(c, "target")))));
      dispatcher.register(Commands.literal("unhidetab")
         .requires(src -> src.hasPermission(2))
         .then(Commands.argument("target", EntityArgument.player())
            .executes(c -> unhideTab(c.getSource(), EntityArgument.getPlayer(c, "target")))));

      dispatcher.register(Commands.literal("nick")
         .requires(src -> src.hasPermission(2))
         .then(Commands.literal("reset")
            .then(Commands.argument("target", EntityArgument.player())
               .executes(c -> nickReset(c.getSource(), EntityArgument.getPlayer(c, "target")))))
         .then(Commands.argument("target", EntityArgument.player())
            .then(Commands.argument("name", StringArgumentType.greedyString())
               .executes(c -> nickSet(c.getSource(),
                  EntityArgument.getPlayer(c, "target"),
                  StringArgumentType.getString(c, "name"))))));
   }

   private static int copy(CommandSourceStack src, ServerPlayer target, ServerPlayer source) {
      if (target == source) {
         fail(src, "&c目标与来源是同一名玩家。");
         return 0;
      }
      if (!SkinManager.copy(target, source)) {
         fail(src, "&c" + source.getGameProfile().getName() + " 没有可复制的皮肤。");
         return 0;
      }
      ok(src, "&a已将 &f" + source.getGameProfile().getName() + " &a的皮肤复制给 &f"
         + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static int set(CommandSourceStack src, ServerPlayer target, String texture) {
      String value = texture.trim();
      if (value.isEmpty()) {
         fail(src, "&c纹理值不能为空。");
         return 0;
      }
      SkinManager.apply(target, value, null);
      ok(src, "&a已设置 &f" + target.getGameProfile().getName() + " &a的皮肤。");
      return 1;
   }

   private static int fromUrl(CommandSourceStack src, ServerPlayer target, String url, String model) {
      String clean = url.trim();
      if (clean.isEmpty()) {
         fail(src, "&cURL 不能为空。");
         return 0;
      }
      SkinManager.apply(target, textureFromUrl(clean, model), null);
      ok(src, "&a已从 URL 为 &f" + target.getGameProfile().getName() + " &a设置皮肤"
         + (model != null ? "（slim）" : "") + "。");
      return 1;
   }

   private static int reset(CommandSourceStack src, ServerPlayer target) {
      boolean restored = SkinManager.reset(target);
      ok(src, restored
         ? "&a已恢复 &f" + target.getGameProfile().getName() + " &a的原始皮肤。"
         : "&7已清除 &f" + target.getGameProfile().getName() + " &7的皮肤（默认）。");
      return 1;
   }

   private static int info(CommandSourceStack src, ServerPlayer target) {
      String name = target.getGameProfile().getName();
      ok(src, "&6===== &f" + name + " &6=====");
      ok(src, "&7隐藏状态： " + (PlayerVisibility.isHidden(target) ? "&c已隐藏" : "&a可见"));
      ok(src, "&7Tab 列表： " + (PlayerVisibility.isTabHidden(target) ? "&c已隐藏" : "&a可见"));
      Property texture = SkinManager.currentTexture(target);
      if (texture == null) {
         ok(src, "&7皮肤： &f默认（无纹理）");
         return 1;
      }
      SkinInfo parsed = SkinInfo.parse(texture.value());
      ok(src, "&7皮肤： &f已设置" + (SkinManager.hasOriginal(target) ? " &8（可 &f/skin reset&8 恢复）" : ""));
      if (parsed.model != null) {
         ok(src, "&7模型： &f" + parsed.model);
      }
      if (parsed.url != null) {
         ok(src, "&7纹理 URL： &f" + parsed.url);
      }
      if (parsed.url == null && parsed.model == null) {
         ok(src, "&7纹理值长度： &f" + texture.value().length());
      }
      return 1;
   }

   private static int hide(CommandSourceStack src, ServerPlayer target) {
      if (PlayerVisibility.isHidden(target)) {
         fail(src, "&c" + target.getGameProfile().getName() + " 已经处于隐藏状态。");
         return 0;
      }
      PlayerVisibility.hide(target);
      ok(src, "&a已隐藏玩家 &f" + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static int unhide(CommandSourceStack src, ServerPlayer target) {
      if (!PlayerVisibility.isHidden(target)) {
         fail(src, "&c" + target.getGameProfile().getName() + " 当前没有被隐藏。");
         return 0;
      }
      PlayerVisibility.unhide(target);
      ok(src, "&a已取消隐藏 &f" + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static int hideList(CommandSourceStack src) {
      Set<UUID> hidden = PlayerVisibility.hiddenPlayers();
      if (hidden.isEmpty()) {
         ok(src, "&7当前没有隐藏的玩家。");
         return 1;
      }
      ok(src, "&6已隐藏的玩家：");
      var server = src.getServer();
      for (UUID id : hidden) {
         String name = id.toString();
         if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
               name = p.getGameProfile().getName();
            }
         }
         ok(src, "&8• &f" + name);
      }
      return 1;
   }

   private static int hideTab(CommandSourceStack src, ServerPlayer target) {
      if (PlayerVisibility.isTabHidden(target)) {
         fail(src, "&c" + target.getGameProfile().getName() + " 已在 Tab 列表中隐藏。");
         return 0;
      }
      PlayerVisibility.hideTab(target);
      ok(src, "&a已在 Tab 列表中隐藏 &f" + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static int unhideTab(CommandSourceStack src, ServerPlayer target) {
      if (!PlayerVisibility.isTabHidden(target)) {
         fail(src, "&c" + target.getGameProfile().getName() + " 当前未在 Tab 列表中隐藏。");
         return 0;
      }
      PlayerVisibility.unhideTab(target);
      ok(src, "&a已恢复 &f" + target.getGameProfile().getName() + " &a在 Tab 列表中的显示。");
      return 1;
   }

   private static int hideTabList(CommandSourceStack src) {
      Set<UUID> hidden = PlayerVisibility.tabHiddenPlayers();
      if (hidden.isEmpty()) {
         ok(src, "&7当前没有在 Tab 列表中隐藏的玩家。");
         return 1;
      }
      ok(src, "&6Tab 列表中已隐藏的玩家：");
      var server = src.getServer();
      for (UUID id : hidden) {
         String name = id.toString();
         if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
               name = p.getGameProfile().getName();
            }
         }
         ok(src, "&8• &f" + name);
      }
      return 1;
   }

   private static int nickSet(CommandSourceStack src, ServerPlayer target, String name) {
      String error = NameManager.apply(target, name);
      if (error != null) {
         fail(src, error);
         return 0;
      }
      ok(src, "&a已将 &f" + NameManager.originalName(target) + " &a的名字改为 &f"
         + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static int nickReset(CommandSourceStack src, ServerPlayer target) {
      String current = target.getGameProfile().getName();
      if (!NameManager.reset(target)) {
         fail(src, "&c" + current + " 没有自定义名字。");
         return 0;
      }
      ok(src, "&a已恢复 &f" + current + " &a的原名 &f" + target.getGameProfile().getName() + "&a。");
      return 1;
   }

   private static String textureFromUrl(String url, String model) {
      StringBuilder json = new StringBuilder("{\"textures\":{\"SKIN\":{\"url\":\"");
      json.append(url.replace("\\", "\\\\").replace("\"", "\\\""));
      json.append('"');
      if (model != null && !model.isEmpty()) {
         json.append(",\"metadata\":{\"model\":\"").append(model).append("\"}");
      }
      json.append("}}}");
      return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static void ok(CommandSourceStack src, String text) {
      src.sendSuccess(() -> TextUtil.color(text), false);
   }

   private static void fail(CommandSourceStack src, String text) {
      src.sendFailure(TextUtil.color(text));
   }

   /** Lightweight parse of a base64 texture JSON; only extracts {@code url} and {@code model}. */
   private record SkinInfo(String url, String model) {
      static SkinInfo parse(String textureValue) {
         String url = null;
         String model = null;
         try {
            String json = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);
            url = extract(json, "\"url\"");
            model = extract(json, "\"model\"");
         } catch (IllegalArgumentException ignored) {
            // not valid base64 — leave fields null
         }
         return new SkinInfo(url, model);
      }

      private static String extract(String json, String key) {
         int idx = json.indexOf(key);
         if (idx < 0) {
            return null;
         }
         int colon = json.indexOf(':', idx);
         if (colon < 0) {
            return null;
         }
         int start = json.indexOf('"', colon);
         if (start < 0) {
            return null;
         }
         int end = json.indexOf('"', start + 1);
         if (end < 0) {
            return null;
         }
         return json.substring(start + 1, end);
      }
   }
}
