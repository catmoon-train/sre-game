package net.exmo.sreGame.games.skyworld;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SkyLoot {
   private static final String INSTAKILL_TAG = "sre_sky_instakill";

   private SkyLoot() {
   }

   public static boolean isInstakill(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data != null && data.copyTag().getBoolean(INSTAKILL_TAG);
   }

   public static void fill(Container chest, boolean center, ChestTier tier, RegistryAccess access) {
      fill(chest, center ? SkyArena.Band.CENTER : SkyArena.Band.ISLAND, tier, access);
   }

   public static void fill(Container chest, SkyArena.Band band, ChestTier tier) {
      fill(chest, band, tier, RegistryAccess.EMPTY);
   }

   public static void fill(Container chest, SkyArena.Band band, ChestTier tier, RegistryAccess access) {
      if (chest == null) {
         return;
      }
      chest.clearContent();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int max = Math.min(26, chest.getContainerSize());
      int added = 0;
      int lootCap = band == SkyArena.Band.ISLAND ? Math.min(24, max) : max;
      int enchantOdds = enchantOdds(band, tier);
      for (Bucket bucket : table(band, tier)) {
         for (ItemStack template : bucket.items) {
            if (added >= lootCap) {
               break;
            }
            if (random.nextInt(100) + 1 > bucket.chance) {
               continue;
            }
            ItemStack stack = template.copy();
            randomEnchant(stack, access, random, enchantOdds);
            if (putEmpty(chest, stack, random)) {
               added++;
            }
         }
      }
      addToys(chest, band, tier, access, random);
      if (band == SkyArena.Band.ISLAND) {
         guaranteeStarterBlocks(chest, random);
      }
   }

   public static void randomEnchant(ItemStack stack, RegistryAccess access, ThreadLocalRandom random, int chance) {
      if (stack == null || stack.isEmpty() || access == null || access == RegistryAccess.EMPTY) {
         return;
      }
      if (chance <= 0 || random.nextInt(100) >= chance || stack.isEnchanted()) {
         return;
      }
      List<EnchantRoll> pool = rollsFor(stack);
      if (pool.isEmpty()) {
         return;
      }
      int rolls = 1 + random.nextInt(3);
      for (int i = 0; i < rolls && !pool.isEmpty(); i++) {
         EnchantRoll pick = pool.remove(random.nextInt(pool.size()));
         if (random.nextInt(100) > pick.weight) {
            continue;
         }
         int level = pick.min + random.nextInt(pick.max - pick.min + 1);
         applyEnchant(stack, access, pick.key, level);
      }
   }

   private static void addToys(Container chest, SkyArena.Band band, ChestTier tier, RegistryAccess access, ThreadLocalRandom random) {
      int jackpot = switch (band) {
         case CENTER -> 8;
         case MID -> 4;
         case ISLAND -> 2;
      };
      if (tier == ChestTier.OP) {
         jackpot += 5;
      } else if (tier == ChestTier.BASIC) {
         jackpot = Math.max(1, jackpot - 1);
      }
      if (random.nextInt(1000) < jackpot) {
         putEmpty(chest, instakillAxe(access), random);
      }
      maybePut(chest, random, toyChance(band, 8, 5, 3), knockbackStick(access, random));
      maybePut(chest, random, toyChance(band, 10, 6, 3), punchBow(access, random));
      maybePut(chest, random, toyChance(band, 12, 8, 5), new ItemStack(Items.TNT, 2 + random.nextInt(5)));
      maybePut(chest, random, toyChance(band, 14, 9, 5), new ItemStack(Items.COBWEB, 4 + random.nextInt(9)));
      maybePut(chest, random, toyChance(band, 12, 7, 4), new ItemStack(Items.WIND_CHARGE, 3 + random.nextInt(6)));
      maybePut(chest, random, toyChance(band, 10, 6, 3), new ItemStack(Items.LADDER, 8 + random.nextInt(9)));
      maybePut(chest, random, toyChance(band, 8, 5, 3), new ItemStack(Items.FLINT_AND_STEEL));
      maybePut(chest, random, toyChance(band, 8, 5, 2), new ItemStack(Items.SHIELD));
      maybePut(chest, random, toyChance(band, 10, 6, 3), new ItemStack(Items.FIRE_CHARGE, 4 + random.nextInt(5)));
      maybePut(chest, random, toyChance(band, 8, 5, 3), new ItemStack(Items.CHORUS_FRUIT, 2 + random.nextInt(4)));
      maybePut(chest, random, toyChance(band, 10, 6, 4), splash(Potions.STRONG_HARMING));
      maybePut(chest, random, toyChance(band, 10, 6, 4), splash(Potions.STRONG_SLOWNESS));
      maybePut(chest, random, toyChance(band, 8, 5, 3), splash(Potions.STRONG_HEALING));
      maybePut(chest, random, toyChance(band, 8, 5, 2), splash(Potions.SWIFTNESS));
      maybePut(chest, random, toyChance(band, 5, 3, 1), splash(Potions.INVISIBILITY));
      maybePut(chest, random, toyChance(band, 6, 3, 2), splash(Potions.SLOW_FALLING));
      maybePut(chest, random, toyChance(band, 5, 3, 1), splash(Potions.POISON));
      maybePut(chest, random, toyChance(band, 4, 2, 1), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
      maybePut(chest, random, toyChance(band, 4, 2, 1), loyaltyTrident(access, random));
      maybePut(chest, random, toyChance(band, 3, 2, 1), smashMace(access, random));
      maybePut(chest, random, toyChance(band, 4, 2, 1), trollRod(access, random));
      if (tier != ChestTier.BASIC) {
         maybePut(chest, random, toyChance(band, 3, 1, 1), named(new ItemStack(Items.TOTEM_OF_UNDYING), "&e不死图腾", "&7再活一次"));
      }
   }

   private static int toyChance(SkyArena.Band band, int center, int mid, int island) {
      return switch (band) {
         case CENTER -> center;
         case MID -> mid;
         case ISLAND -> island;
      };
   }

   private static int enchantOdds(SkyArena.Band band, ChestTier tier) {
      int base = switch (band) {
         case ISLAND -> 22;
         case MID -> 32;
         case CENTER -> 42;
      };
      base += switch (tier) {
         case BASIC -> -10;
         case NORMAL -> 0;
         case OP -> 18;
      };
      return Math.max(8, Math.min(70, base));
   }

   private static ItemStack instakillAxe(RegistryAccess access) {
      ItemStack stack = new ItemStack(Items.GOLDEN_AXE);
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color("&c&l秒人斧"));
      stack.set(DataComponents.LORE, new ItemLore(List.of(
         TextUtil.color("&7只剩 1 点耐久"),
         TextUtil.color("&8挥下去，然后它就碎了")
      )));
      stack.set(DataComponents.RARITY, Rarity.EPIC);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putBoolean(INSTAKILL_TAG, true)));
      int max = Math.max(1, stack.getMaxDamage());
      stack.set(DataComponents.DAMAGE, max - 1);
      stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
         .add(
            Attributes.ATTACK_DAMAGE,
            new AttributeModifier(ResourceLocation.fromNamespaceAndPath("sre-game", "instakill"), 39.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
         )
         .add(
            Attributes.ATTACK_SPEED,
            new AttributeModifier(ResourceLocation.fromNamespaceAndPath("sre-game", "instakill_speed"), -3.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
         )
         .build());
      applyEnchant(stack, access, Enchantments.SHARPNESS, 10);
      applyEnchant(stack, access, Enchantments.KNOCKBACK, 1);
      return stack;
   }

   private static ItemStack knockbackStick(RegistryAccess access, ThreadLocalRandom random) {
      ItemStack stack = named(new ItemStack(Items.STICK), "&b击飞棍", "&7把人打出岛外");
      applyEnchant(stack, access, Enchantments.KNOCKBACK, 1);
      double knock = 1.0;
      stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
         .add(
            Attributes.ATTACK_KNOCKBACK,
            new AttributeModifier(ResourceLocation.fromNamespaceAndPath("sre-game", "kb_stick"), knock, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
         )
         .build());
      return stack;
   }

   private static ItemStack punchBow(RegistryAccess access, ThreadLocalRandom random) {
      ItemStack stack = named(new ItemStack(Items.BOW), "&b击退弓", "&7射一箭，送一程");
      applyEnchant(stack, access, Enchantments.PUNCH, 2 + random.nextInt(2));
      applyEnchant(stack, access, Enchantments.POWER, 1 + random.nextInt(3));
      if (random.nextBoolean()) {
         applyEnchant(stack, access, Enchantments.UNBREAKING, 1 + random.nextInt(2));
      }
      return stack;
   }

   private static ItemStack loyaltyTrident(RegistryAccess access, ThreadLocalRandom random) {
      ItemStack stack = named(new ItemStack(Items.TRIDENT), "&3召回三叉戟", "&7丢出去还会回来");
      applyEnchant(stack, access, Enchantments.LOYALTY, 2 + random.nextInt(2));
      if (random.nextInt(3) == 0) {
         applyEnchant(stack, access, Enchantments.CHANNELING, 1);
      }
      return stack;
   }

   private static ItemStack smashMace(RegistryAccess access, ThreadLocalRandom random) {
      ItemStack stack = named(new ItemStack(Items.MACE), "&6坠击锤", "&7从高处砸下去");
      applyEnchant(stack, access, Enchantments.DENSITY, 1 + random.nextInt(3));
      if (random.nextBoolean()) {
         applyEnchant(stack, access, Enchantments.BREACH, 1);
      }
      int max = Math.max(1, stack.getMaxDamage());
      stack.set(DataComponents.DAMAGE, Math.max(0, max - (8 + random.nextInt(16))));
      return stack;
   }

   private static ItemStack trollRod(RegistryAccess access, ThreadLocalRandom random) {
      ItemStack stack = named(new ItemStack(Items.FISHING_ROD), "&a整蛊钓竿", "&7钩人比钓鱼有意思");
      applyEnchant(stack, access, Enchantments.LUCK_OF_THE_SEA, 3);
      applyEnchant(stack, access, Enchantments.LURE, 2 + random.nextInt(2));
      applyEnchant(stack, access, Enchantments.UNBREAKING, 1 + random.nextInt(3));
      return stack;
   }

   private static ItemStack splash(Holder<net.minecraft.world.item.alchemy.Potion> potion) {
      ItemStack stack = new ItemStack(Items.SPLASH_POTION);
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
      return stack;
   }

   private static ItemStack named(ItemStack stack, String name, String lore) {
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      stack.set(DataComponents.LORE, new ItemLore(List.of(TextUtil.color(lore))));
      return stack;
   }

   private static void applyEnchant(ItemStack stack, RegistryAccess access, ResourceKey<Enchantment> key, int level) {
      if (stack == null || stack.isEmpty() || access == null || access == RegistryAccess.EMPTY) {
         return;
      }
      Holder<Enchantment> holder = access.registryOrThrow(Registries.ENCHANTMENT).getHolder(key).orElse(null);
      if (holder == null || level <= 0) {
         return;
      }
      ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
      for (Holder<Enchantment> existing : current.keySet()) {
         if (!Enchantment.areCompatible(existing, holder)) {
            return;
         }
      }
      stack.enchant(holder, level);
   }

   private static List<EnchantRoll> rollsFor(ItemStack stack) {
      Item item = stack.getItem();
      List<EnchantRoll> out = new ArrayList<>();
      if (item == Items.BOW) {
         out.add(new EnchantRoll(Enchantments.POWER, 1, 4, 80));
         out.add(new EnchantRoll(Enchantments.PUNCH, 1, 2, 35));
         out.add(new EnchantRoll(Enchantments.FLAME, 1, 1, 18));
         out.add(new EnchantRoll(Enchantments.INFINITY, 1, 1, 12));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 40));
         return out;
      }
      if (item == Items.CROSSBOW) {
         out.add(new EnchantRoll(Enchantments.QUICK_CHARGE, 1, 3, 70));
         out.add(new EnchantRoll(Enchantments.MULTISHOT, 1, 1, 35));
         out.add(new EnchantRoll(Enchantments.PIERCING, 1, 3, 30));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 40));
         return out;
      }
      if (isSword(item)) {
         out.add(new EnchantRoll(Enchantments.SHARPNESS, 1, 4, 80));
         out.add(new EnchantRoll(Enchantments.KNOCKBACK, 1, 2, 35));
         out.add(new EnchantRoll(Enchantments.FIRE_ASPECT, 1, 2, 20));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 40));
         return out;
      }
      if (isAxe(item)) {
         out.add(new EnchantRoll(Enchantments.SHARPNESS, 1, 3, 55));
         out.add(new EnchantRoll(Enchantments.EFFICIENCY, 1, 4, 50));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 40));
         return out;
      }
      if (isTool(item)) {
         out.add(new EnchantRoll(Enchantments.EFFICIENCY, 1, 4, 75));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 45));
         out.add(new EnchantRoll(Enchantments.FORTUNE, 1, 2, 18));
         out.add(new EnchantRoll(Enchantments.SILK_TOUCH, 1, 1, 8));
         return out;
      }
      if (isArmor(item)) {
         out.add(new EnchantRoll(Enchantments.PROTECTION, 1, 3, 75));
         out.add(new EnchantRoll(Enchantments.UNBREAKING, 1, 3, 40));
         out.add(new EnchantRoll(Enchantments.THORNS, 1, 2, 12));
         out.add(new EnchantRoll(Enchantments.PROJECTILE_PROTECTION, 1, 3, 18));
         if (isBoots(item)) {
            out.add(new EnchantRoll(Enchantments.FEATHER_FALLING, 1, 4, 55));
         }
         return out;
      }
      return out;
   }

   private static boolean isSword(Item item) {
      return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD
         || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
   }

   private static boolean isAxe(Item item) {
      return item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE
         || item == Items.GOLDEN_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
   }

   private static boolean isTool(Item item) {
      return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
         || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE
         || item == Items.WOODEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.IRON_SHOVEL
         || item == Items.GOLDEN_SHOVEL || item == Items.DIAMOND_SHOVEL;
   }

   private static boolean isArmor(Item item) {
      return isBoots(item)
         || item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS
         || item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS
         || item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS
         || item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS
         || item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS
         || item == Items.CHAINMAIL_BOOTS;
   }

   private static boolean isBoots(Item item) {
      return item == Items.LEATHER_BOOTS || item == Items.GOLDEN_BOOTS || item == Items.IRON_BOOTS
         || item == Items.DIAMOND_BOOTS || item == Items.CHAINMAIL_BOOTS;
   }

   private static void guaranteeStarterBlocks(Container chest, ThreadLocalRandom random) {
      int total = 32 + random.nextInt(33);
      Item[] blocks = {
         Items.OAK_PLANKS, Items.COBBLESTONE, Items.DIRT, Items.STONE, Items.OAK_LOG
      };
      Item block = blocks[random.nextInt(blocks.length)];
      while (total > 0) {
         int stack = Math.min(64, total);
         if (!putEmpty(chest, new ItemStack(block, stack), random)) {
            break;
         }
         total -= stack;
      }
   }

   private static void maybePut(Container chest, ThreadLocalRandom random, int chance, ItemStack stack) {
      if (stack == null || stack.isEmpty() || random.nextInt(100) >= chance) {
         return;
      }
      putEmpty(chest, stack, random);
   }

   private static boolean putEmpty(Container chest, ItemStack stack, ThreadLocalRandom random) {
      int slot = random.nextInt(chest.getContainerSize());
      for (int n = 0; n < chest.getContainerSize(); n++) {
         int index = (slot + n) % chest.getContainerSize();
         if (chest.getItem(index).isEmpty()) {
            chest.setItem(index, stack);
            return true;
         }
      }
      return false;
   }

   private static List<Bucket> table(SkyArena.Band band, ChestTier tier) {
      List<Bucket> out = new ArrayList<>();
      int boost = switch (tier) {
         case BASIC -> -15;
         case NORMAL -> 0;
         case OP -> 20;
      };
      if (band == SkyArena.Band.CENTER) {
         out.add(new Bucket(clamp(65 + boost), woodFood()));
         out.add(new Bucket(clamp(50 + boost), projectiles()));
         out.add(new Bucket(clamp(50 + boost), stoneKit()));
         out.add(new Bucket(clamp(40 + boost), arrows(8)));
         out.add(new Bucket(clamp(40 + boost), ironArmor()));
         out.add(new Bucket(clamp(25 + boost), goldAndPearl()));
         out.add(new Bucket(clamp(tier == ChestTier.BASIC ? 4 : 18 + boost), diamondGear()));
      } else if (band == SkyArena.Band.MID) {
         out.add(new Bucket(clamp(55 + boost), woodFood()));
         out.add(new Bucket(clamp(45 + boost), projectiles()));
         out.add(new Bucket(clamp(40 + boost), stoneKit()));
         out.add(new Bucket(clamp(35 + boost), arrows(8)));
         out.add(new Bucket(clamp(30 + boost), ironArmor()));
         out.add(new Bucket(clamp(22 + boost), goldAndPearl()));
         if (tier != ChestTier.BASIC) {
            out.add(new Bucket(clamp(12 + boost), List.of(
               new ItemStack(Items.DIAMOND_PICKAXE),
               new ItemStack(Items.IRON_SWORD),
               new ItemStack(Items.GOLDEN_APPLE)
            )));
            out.add(new Bucket(clamp(8 + boost), diamondGear()));
         }
      } else {
         out.add(new Bucket(clamp(50 + boost), woodFood()));
         out.add(new Bucket(clamp(40 + boost), projectiles()));
         out.add(new Bucket(clamp(35 + boost), leatherAndWood()));
         out.add(new Bucket(clamp(30 + boost), stoneKit()));
         out.add(new Bucket(clamp(25 + boost), goldAndPearl()));
         out.add(new Bucket(clamp(20 + boost), arrows(8)));
         out.add(new Bucket(clamp(15 + boost), ironArmor()));
         if (tier != ChestTier.BASIC) {
            out.add(new Bucket(clamp(10 + boost), List.of(
               new ItemStack(Items.DIAMOND_PICKAXE),
               new ItemStack(Items.DIAMOND_SHOVEL)
            )));
            out.add(new Bucket(clamp(5 + boost), diamondGear()));
         }
      }
      if (tier == ChestTier.OP) {
         out.add(new Bucket(35, List.of(
            new ItemStack(Items.GOLDEN_APPLE, 2),
            new ItemStack(Items.ENDER_PEARL, 2),
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.IRON_SWORD)
         )));
      }
      return out;
   }

   private static int clamp(int chance) {
      return Math.max(3, Math.min(95, chance));
   }

   private static List<ItemStack> woodFood() {
      return List.of(
         new ItemStack(Items.OAK_PLANKS, 8),
         new ItemStack(Items.COBBLESTONE, 8),
         new ItemStack(Items.LADDER, 6),
         new ItemStack(Items.BREAD, 3),
         new ItemStack(Items.APPLE, 3),
         new ItemStack(Items.BAKED_POTATO, 3),
         new ItemStack(Items.WATER_BUCKET),
         new ItemStack(Items.LAVA_BUCKET)
      );
   }

   private static List<ItemStack> projectiles() {
      return List.of(
         new ItemStack(Items.SNOWBALL, 2),
         new ItemStack(Items.EGG, 2),
         new ItemStack(Items.BOW),
         new ItemStack(Items.CROSSBOW),
         new ItemStack(Items.WIND_CHARGE, 2),
         new ItemStack(Items.COBWEB, 4)
      );
   }

   private static List<ItemStack> leatherAndWood() {
      return List.of(
         new ItemStack(Items.LEATHER_BOOTS),
         new ItemStack(Items.LEATHER_CHESTPLATE),
         new ItemStack(Items.LEATHER_HELMET),
         new ItemStack(Items.LEATHER_LEGGINGS),
         new ItemStack(Items.WOODEN_SHOVEL),
         new ItemStack(Items.WOODEN_PICKAXE),
         new ItemStack(Items.WOODEN_AXE),
         new ItemStack(Items.WOODEN_SWORD)
      );
   }

   private static List<ItemStack> stoneKit() {
      return List.of(
         new ItemStack(Items.EGG, 4),
         new ItemStack(Items.SNOWBALL, 4),
         new ItemStack(Items.ARROW, 4),
         new ItemStack(Items.OAK_PLANKS, 16),
         new ItemStack(Items.COBBLESTONE, 16),
         new ItemStack(Items.COOKED_BEEF, 2),
         new ItemStack(Items.COOKED_CHICKEN, 2),
         new ItemStack(Items.STONE_PICKAXE),
         new ItemStack(Items.STONE_SWORD),
         new ItemStack(Items.STONE_AXE),
         new ItemStack(Items.STONE_SHOVEL),
         new ItemStack(Items.FLINT_AND_STEEL)
      );
   }

   private static List<ItemStack> arrows(int count) {
      return List.of(
         new ItemStack(Items.ARROW, count),
         new ItemStack(Items.SPECTRAL_ARROW, Math.max(2, count / 2))
      );
   }

   private static List<ItemStack> ironArmor() {
      return List.of(
         new ItemStack(Items.IRON_BOOTS),
         new ItemStack(Items.IRON_HELMET),
         new ItemStack(Items.IRON_LEGGINGS),
         new ItemStack(Items.IRON_CHESTPLATE),
         new ItemStack(Items.GOLDEN_APPLE),
         new ItemStack(Items.IRON_AXE),
         new ItemStack(Items.IRON_SWORD),
         new ItemStack(Items.ENDER_PEARL, 2),
         new ItemStack(Items.SHIELD)
      );
   }

   private static List<ItemStack> goldAndPearl() {
      return List.of(
         new ItemStack(Items.GOLDEN_BOOTS),
         new ItemStack(Items.GOLDEN_LEGGINGS),
         new ItemStack(Items.GOLDEN_HELMET),
         new ItemStack(Items.GOLDEN_CHESTPLATE),
         new ItemStack(Items.ENDER_PEARL),
         new ItemStack(Items.EXPERIENCE_BOTTLE),
         new ItemStack(Items.IRON_PICKAXE),
         new ItemStack(Items.IRON_SHOVEL),
         new ItemStack(Items.CHORUS_FRUIT, 3)
      );
   }

   private static List<ItemStack> diamondGear() {
      return List.of(
         new ItemStack(Items.DIAMOND_CHESTPLATE),
         new ItemStack(Items.DIAMOND_BOOTS),
         new ItemStack(Items.DIAMOND_LEGGINGS),
         new ItemStack(Items.DIAMOND_HELMET),
         new ItemStack(Items.DIAMOND_SWORD),
         new ItemStack(Items.DIAMOND_AXE),
         new ItemStack(Items.ENDER_PEARL, 2)
      );
   }

   private record Bucket(int chance, List<ItemStack> items) {
   }

   private record EnchantRoll(ResourceKey<Enchantment> key, int min, int max, int weight) {
   }
}
