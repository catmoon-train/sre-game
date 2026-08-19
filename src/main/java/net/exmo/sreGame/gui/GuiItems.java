package net.exmo.sreGame.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class GuiItems {
   private GuiItems() {
   }

   public static ItemStack filler() {
      return filler("gray_stained_glass_pane", " ");
   }

   public static ItemStack filler(String materialName, String displayName) {
      Item item = resolveItem(materialName, Items.GRAY_STAINED_GLASS_PANE);
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(displayName == null || displayName.isBlank() ? " " : displayName));
      return stack;
   }

   public static ItemStack named(String materialName, String name, List<String> loreLines) {
      Item item = resolveItem(materialName, Items.STONE);
      ItemStack stack = new ItemStack(item);
      if (name != null) {
         stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      }
      if (loreLines != null && !loreLines.isEmpty()) {
         List<Component> lore = new ArrayList<>();
         for (String line : loreLines) {
            lore.add(TextUtil.color(line));
         }
         stack.set(DataComponents.LORE, new ItemLore(lore));
      }
      return stack;
   }

   public static ItemStack action(String materialName, String name, List<String> loreLines, String action) {
      return action(materialName, name, loreLines, action, null, null);
   }

   public static ItemStack action(String materialName, String name, List<String> loreLines, String action, String extraKey, String extraValue) {
      ItemStack stack = named(materialName, name, loreLines);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> {
         tag.putString("sre_action", action);
         if (extraKey != null && extraValue != null) {
            tag.putString(extraKey, extraValue);
         }
      }));
      return stack;
   }

   public static Item resolveItem(String materialName, Item fallback) {
      if (materialName == null || materialName.isBlank()) {
         return fallback;
      }
      String key = materialName.toLowerCase(Locale.ROOT);
      ResourceLocation id = key.indexOf(':') >= 0
         ? ResourceLocation.tryParse(key)
         : ResourceLocation.withDefaultNamespace(key);
      if (id == null) {
         return fallback;
      }
      Item item = BuiltInRegistries.ITEM.get(id);
      return item != null && item != Items.AIR ? item : fallback;
   }

   public static String actionTag(ItemStack stack) {
      return extraTag(stack, "sre_action");
   }

   public static String extraTag(ItemStack stack, String key) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return null;
      }
      String value = data.copyTag().getString(key);
      return value == null || value.isEmpty() ? null : value;
   }
}
