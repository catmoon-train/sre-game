package net.exmo.sreGame.buildwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;

public final class ThemeNameGuard {
   private ThemeNameGuard() {
   }

   public static boolean leaksTheme(ItemStack stack, String theme) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      String word = normalize(theme);
      if (word.isEmpty()) {
         return false;
      }
      for (String raw : namesOf(stack)) {
         String name = normalize(raw);
         if (name.isEmpty()) {
            continue;
         }
         if (name.contains(word) || word.contains(name) && name.length() >= 2) {
            return true;
         }
      }
      return false;
   }

   private static List<String> namesOf(ItemStack stack) {
      List<String> names = new ArrayList<>();
      add(names, stack.getHoverName().getString());
      if (stack.has(DataComponents.CUSTOM_NAME)) {
         add(names, stack.get(DataComponents.CUSTOM_NAME).getString());
      }
      if (stack.has(DataComponents.ITEM_NAME)) {
         add(names, stack.get(DataComponents.ITEM_NAME).getString());
      }
      Item item = stack.getItem();
      add(names, Language.getInstance().getOrDefault(item.getDescriptionId()));
      add(names, item.getDescriptionId());
      ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
      add(names, key.getPath());
      add(names, key.getPath().replace('_', ' '));
      if (item instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         add(names, block.getName().getString());
         add(names, Language.getInstance().getOrDefault(block.getDescriptionId()));
         add(names, block.getDescriptionId());
         ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);
         add(names, blockKey.getPath());
         add(names, blockKey.getPath().replace('_', ' '));
      }
      if (item instanceof PlayerHeadItem && stack.has(DataComponents.PROFILE)) {
         ResolvableProfile profile = stack.get(DataComponents.PROFILE);
         Optional<String> playerName = profile.name();
         playerName.ifPresent(n -> add(names, n));
      }
      return names;
   }

   private static void add(List<String> names, String value) {
      if (value != null && !value.isBlank()) {
         names.add(value);
      }
   }

   private static String normalize(String text) {
      if (text == null) {
         return "";
      }
      return text.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "").trim();
   }
}
