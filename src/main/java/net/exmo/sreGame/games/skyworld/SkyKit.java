package net.exmo.sreGame.games.skyworld;

import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public enum SkyKit {
   NONE("空手", "barrier"),
   WARRIOR("战士", "stone_sword"),
   ARCHER("弓箭手", "bow"),
   BUILDER("建筑师", "oak_planks"),
   SCOUT("斥候", "feather"),
   TANK("重甲", "iron_chestplate"),
   MINER("矿工", "iron_pickaxe"),
   MEDIC("医师", "golden_apple");

   private final String label;
   private final String icon;

   SkyKit(String label, String icon) {
      this.label = label;
      this.icon = icon;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public SkyKit next() {
      SkyKit[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public ItemStack selector(boolean selected) {
      ItemStack stack = switch (this) {
         case NONE -> new ItemStack(Items.BARRIER);
         case WARRIOR -> new ItemStack(Items.STONE_SWORD);
         case ARCHER -> new ItemStack(Items.BOW);
         case BUILDER -> new ItemStack(Items.OAK_PLANKS);
         case SCOUT -> new ItemStack(Items.FEATHER);
         case TANK -> new ItemStack(Items.IRON_CHESTPLATE);
         case MINER -> new ItemStack(Items.IRON_PICKAXE);
         case MEDIC -> new ItemStack(Items.GOLDEN_APPLE);
      };
      String name = (selected ? "&a" : "&f") + this.label + (selected ? " &7（已选）" : "");
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString("sre_sky_kit", this.name())));
      return stack;
   }

   public void give(ServerPlayer player) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      switch (this) {
         case NONE -> {
         }
         case WARRIOR -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.STONE_SWORD), random, 70));
            player.setItemSlot(EquipmentSlot.CHEST, this.enchanted(player, new ItemStack(Items.LEATHER_CHESTPLATE), random, 55));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 16));
         }
         case ARCHER -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.BOW), random, 70));
            player.getInventory().add(new ItemStack(Items.ARROW, 16));
            player.setItemSlot(EquipmentSlot.FEET, this.enchanted(player, new ItemStack(Items.LEATHER_BOOTS), random, 55));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
         }
         case BUILDER -> {
            player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 64));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.STONE_PICKAXE), random, 70));
            player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
         }
         case SCOUT -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.STONE_SWORD), random, 45));
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.BOW), random, 35));
            player.getInventory().add(new ItemStack(Items.ARROW, 8));
            player.setItemSlot(EquipmentSlot.FEET, this.enchanted(player, new ItemStack(Items.LEATHER_BOOTS), random, 70));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 90 * 20, 0, false, false, true));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
         }
         case TANK -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.STONE_AXE), random, 65));
            player.setItemSlot(EquipmentSlot.CHEST, this.enchanted(player, new ItemStack(Items.IRON_CHESTPLATE), random, 45));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 10));
         }
         case MINER -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.IRON_PICKAXE), random, 70));
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.STONE_AXE), random, 45));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 12));
            player.getInventory().add(new ItemStack(Items.TORCH, 24));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 90 * 20, 0, false, false, true));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
         }
         case MEDIC -> {
            player.getInventory().add(this.enchanted(player, new ItemStack(Items.WOODEN_SWORD), random, 35));
            player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 2));
            player.getInventory().add(new ItemStack(Items.MILK_BUCKET));
            player.setItemSlot(EquipmentSlot.LEGS, this.enchanted(player, new ItemStack(Items.LEATHER_LEGGINGS), random, 65));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 12));
         }
      }
   }

   private ItemStack enchanted(ServerPlayer player, ItemStack stack, ThreadLocalRandom random, int chance) {
      SkyLoot.randomEnchant(stack, player.registryAccess(), random, chance);
      return stack;
   }

   public static SkyKit fromSelector(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return null;
      }
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return null;
      }
      String raw = data.copyTag().getString("sre_sky_kit");
      if (raw == null || raw.isBlank()) {
         return null;
      }
      try {
         return valueOf(raw);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public static SkyKit fromName(String name) {
      if (name == null || name.isBlank()) {
         return NONE;
      }
      try {
         return valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return NONE;
      }
   }
}
