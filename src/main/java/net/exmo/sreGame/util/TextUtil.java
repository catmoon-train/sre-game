package net.exmo.sreGame.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class TextUtil {
   private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
   private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-or])");

   private TextUtil() {
   }

   public static Component color(String input) {
      if (input == null || input.isEmpty()) {
         return Component.empty();
      }
      MutableComponent result = Component.empty();
      Matcher hexMatcher = HEX.matcher(input);
      int last = 0;
      Style style = Style.EMPTY;
      while (hexMatcher.find()) {
         if (hexMatcher.start() > last) {
            result.append(legacySegment(input.substring(last, hexMatcher.start()), style));
         }
         style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hexMatcher.group(1), 16)));
         last = hexMatcher.end();
      }
      if (last < input.length()) {
         result.append(legacySegment(input.substring(last), style));
      }
      return result;
   }

   private static Component legacySegment(String text, Style base) {
      MutableComponent out = Component.empty();
      Matcher m = LEGACY.matcher(text);
      int last = 0;
      Style style = base;
      while (m.find()) {
         if (m.start() > last) {
            out.append(Component.literal(text.substring(last, m.start())).withStyle(style));
         }
         ChatFormatting fmt = ChatFormatting.getByCode(m.group(1).charAt(0));
         if (fmt == ChatFormatting.RESET) {
            style = Style.EMPTY;
         } else if (fmt != null && fmt.isColor()) {
            style = Style.EMPTY.withColor(fmt);
         } else if (fmt != null) {
            style = style.applyFormat(fmt);
         }
         last = m.end();
      }
      if (last < text.length()) {
         out.append(Component.literal(text.substring(last)).withStyle(style));
      }
      return out;
   }
}
