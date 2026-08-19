package net.exmo.sreGame.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsIo {
   private SettingsIo() {
   }

   public static int asInt(Map<String, ?> data, String key, int def) {
      if (data == null) {
         return def;
      }
      Object value = data.get(key);
      if (value instanceof Number number) {
         return number.intValue();
      }
      if (value instanceof String text) {
         try {
            return (int) Double.parseDouble(text.trim());
         } catch (NumberFormatException ignored) {
            return def;
         }
      }
      return def;
   }

   public static boolean asBool(Map<String, ?> data, String key, boolean def) {
      if (data == null) {
         return def;
      }
      Object value = data.get(key);
      if (value instanceof Boolean bool) {
         return bool;
      }
      if (value instanceof String text) {
         return Boolean.parseBoolean(text.trim());
      }
      if (value instanceof Number number) {
         return number.intValue() != 0;
      }
      return def;
   }

   public static String asString(Map<String, ?> data, String key, String def) {
      if (data == null) {
         return def;
      }
      Object value = data.get(key);
      return value == null ? def : String.valueOf(value);
   }

   @SuppressWarnings("unchecked")
   public static Map<String, Object> asMap(Map<String, ?> data, String key) {
      if (data == null) {
         return Map.of();
      }
      Object value = data.get(key);
      if (value instanceof Map<?, ?> map) {
         Map<String, Object> out = new LinkedHashMap<>();
         for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
               out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
         }
         return out;
      }
      return Map.of();
   }

   public static int[] asIntArray(Map<String, ?> data, String key, int[] def) {
      if (data == null) {
         return def;
      }
      Object value = data.get(key);
      if (value instanceof List<?> list) {
         int[] out = new int[Math.max(def.length, list.size())];
         System.arraycopy(def, 0, out, 0, def.length);
         for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number number) {
               out[i] = number.intValue();
            }
         }
         return out;
      }
      return def;
   }

   public static List<Integer> intList(int[] values) {
      List<Integer> list = new ArrayList<>();
      if (values != null) {
         for (int value : values) {
            list.add(value);
         }
      }
      return list;
   }
}
