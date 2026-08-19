package net.exmo.sreGame.pillarpummel;

import java.util.List;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.ConcretePowderBlock;

public final class PummelShop {
   public static final String BOW = "bow";
   public static final String TNT = "tnt";
   public static final String REPAIR = "repair";
   public static final String FORT = "fort";
   public static final String TURRET = "turret";
   public static final String BLOCKGEN = "blockgen";
   public static final String NUKE = "nuke";
   public static final String STONE = "stone";
   public static final String IRON = "iron";
   public static final String DIAMOND = "diamond";
   public static final String SHIELD = "shield";
   public static final String SPEED = "speed";
   public static final String JUMP = "jump";
   public static final String HEAL = "heal";
   public static final String GAPPLE = "gapple";
   public static final String PEARL = "pearl";
   public static final String WEB = "web";
   public static final String POWDER = "powder";
   public static final String SNOWBALL = "snowball";
   public static final String ARROWS = "arrows";
   public static final String ROD = "rod";
   public static final String WATER = "water";
   public static final String MILK = "milk";
   public static final String WIND = "wind";
   public static final String TOTEM = "totem";
   public static final String TNTPACK = "tntpack";

   private PummelShop() {
   }

   public static boolean isWool(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      if (stack.is(ItemTags.WOOL)) {
         return true;
      }
      return stack.getItem() instanceof BlockItem block
         && block.getBlock() instanceof ConcretePowderBlock;
   }

   public static boolean isPowder(ItemStack stack) {
      return stack != null && !stack.isEmpty()
         && stack.getItem() instanceof BlockItem block
         && block.getBlock() instanceof ConcretePowderBlock;
   }

   public static boolean isPlanks(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.is(ItemTags.PLANKS);
   }

   public static String gadget(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return null;
      }
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return null;
      }
      String value = data.copyTag().getString("pp_gadget");
      return value == null || value.isEmpty() ? null : value;
   }

   public static int woolCount(Player player, PummelTeam team) {
      int total = countWool(player.getInventory());
      if (team != null) {
         total += team.woolStored();
      }
      return total;
   }

   public static boolean takeWool(Player player, PummelTeam team, int amount) {
      if (amount <= 0) {
         return true;
      }
      if (woolCount(player, team) < amount) {
         return false;
      }
      int left = takeWoolFrom(player.getInventory(), amount);
      if (left > 0 && team != null) {
         takeWoolFrom(team.storage, left);
      }
      return true;
   }

   public static ItemStack gadgetStack(String id, String name, ItemStack base, List<String> lore) {
      base.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      if (lore != null && !lore.isEmpty()) {
         List<Component> lines = lore.stream().map(TextUtil::color).toList();
         base.set(DataComponents.LORE, new ItemLore(lines));
      }
      base.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString("pp_gadget", id)));
      return base;
   }

   public static ItemStack give(String id, PillarPummelSettings settings, PummelColor color) {
      PummelColor c = color == null ? PummelColor.RED : color;
      return switch (id) {
         case BOW -> {
            ItemStack bow = named(new ItemStack(Items.BOW), "&f弓");
            bow.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            yield bow;
         }
         case TNT -> gadgetStack(TNT, "&cTNT", new ItemStack(Items.TNT),
            List.of("&7放到已占领的桥或平台上炸毁"));
         case REPAIR -> gadgetStack(REPAIR, "&6维修工具盒", new ItemStack(Items.SNOW_BLOCK),
            List.of("&7右键损坏的己方平台，恢复 1 点耐久"));
         case FORT -> gadgetStack(FORT, "&d堡垒", new ItemStack(Items.STONE_BRICKS),
            List.of("&7右键己方平台：耐久改为 6", "&7墙在 5×5 台上，不会盖住商店"));
         case STONE -> named(new ItemStack(Items.COBBLESTONE, 16), "&7圆石 ×16");
         case IRON -> named(new ItemStack(Items.IRON_BLOCK, 2), "&f铁块 ×2");
         case DIAMOND -> named(new ItemStack(Items.OBSIDIAN, 4), "&5黑曜石 ×4");
         case WATER -> named(new ItemStack(Items.WATER_BUCKET), "&9水桶");
         case MILK -> named(new ItemStack(Items.MILK_BUCKET), "&f牛奶");
         case WIND -> named(new ItemStack(Items.WIND_CHARGE, 4), "&b风弹 ×4");
         case TOTEM -> named(new ItemStack(Items.TOTEM_OF_UNDYING), "&6不死图腾");
         case TNTPACK -> gadgetStack(TNT, "&cTNT ×3", new ItemStack(Items.TNT, 3),
            List.of("&7放到已占领的桥或平台上炸毁"));
         case TURRET -> gadgetStack(TURRET, "&d防御塔", new ItemStack(Items.IRON_BLOCK),
            List.of("&7右键己方平台：耐久 4，自动射击附近敌人"));
         case BLOCKGEN -> gadgetStack(BLOCKGEN, "&a方块生成器", new ItemStack(c.generatorBlock().asItem()),
            List.of("&7右键己方平台：持续产出队色混凝土粉末"));
         case NUKE -> gadgetStack(NUKE, "&4☢ 核弹 ☢", new ItemStack(Items.CARROT_ON_A_STICK),
            List.of("&7右键把场地中央炸光（出生台除外）"));
         case SHIELD -> {
            ItemStack shield = named(new ItemStack(Items.SHIELD), "&f盾牌");
            shield.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            yield shield;
         }
         case SPEED -> potion(Potions.SWIFTNESS, "&b速度药水");
         case JUMP -> potion(Potions.LEAPING, "&a跳跃药水");
         case HEAL -> potion(Potions.HEALING, "&c治疗药水");
         case GAPPLE -> named(new ItemStack(Items.GOLDEN_APPLE), "&6金苹果");
         case PEARL -> named(new ItemStack(Items.ENDER_PEARL, 2), "&5末影珍珠 ×2");
         case WEB -> named(new ItemStack(Items.COBWEB, 4), "&f蜘蛛网 ×4");
         case POWDER -> named(new ItemStack(c.powderItem(), 8), "&f队色粉末 ×8");
         case SNOWBALL -> named(new ItemStack(Items.SNOWBALL, 8), "&f雪球 ×8");
         case ARROWS -> named(new ItemStack(Items.ARROW, 8), "&f箭 ×8");
         case ROD -> {
            ItemStack rod = named(new ItemStack(Items.FISHING_ROD), "&b钓鱼竿");
            rod.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            yield rod;
         }
         default -> ItemStack.EMPTY;
      };
   }

   public static ItemStack give(String id, PillarPummelSettings settings) {
      return give(id, settings, PummelColor.RED);
   }

   public static int price(String id, PillarPummelSettings settings) {
      return switch (id) {
         case TNT -> settings.priceTnt();
         case FORT -> settings.priceDefense();
         case TURRET -> settings.priceLaser();
         case BLOCKGEN -> settings.priceResource();
         case BOW -> settings.priceBow();
         case REPAIR -> settings.priceRepair();
         case NUKE -> settings.priceNuke();
         case SHIELD -> settings.priceShield();
         case SPEED -> settings.priceSpeed();
         case JUMP -> settings.priceJump();
         case HEAL -> settings.priceHeal();
         case GAPPLE -> 8;
         case PEARL -> 12;
         case WEB -> 4;
         case POWDER -> 6;
         case SNOWBALL -> 3;
         case ARROWS -> 4;
         case ROD -> 10;
         case STONE -> settings.priceStone();
         case IRON -> settings.priceIron();
         case DIAMOND -> settings.priceDiamond();
         case WATER -> 6;
         case MILK -> 4;
         case WIND -> 8;
         case TOTEM -> 24;
         case TNTPACK -> Math.max(4, settings.priceTnt() * 3 - 1);
         default -> 99;
      };
   }

   static ItemStack named(ItemStack stack, String name) {
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      return stack;
   }

   private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion, String name) {
      ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      return stack;
   }

   private static int countWool(Inventory inv) {
      int total = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (isWool(inv.getItem(i))) {
            total += inv.getItem(i).getCount();
         }
      }
      return total;
   }

   private static int countWool(net.minecraft.world.SimpleContainer inv) {
      int total = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (isWool(inv.getItem(i))) {
            total += inv.getItem(i).getCount();
         }
      }
      return total;
   }

   private static int takeWoolFrom(Inventory inv, int amount) {
      for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (!isWool(stack)) {
            continue;
         }
         int take = Math.min(amount, stack.getCount());
         stack.shrink(take);
         amount -= take;
      }
      return amount;
   }

   private static int takeWoolFrom(net.minecraft.world.SimpleContainer inv, int amount) {
      for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (!isWool(stack)) {
            continue;
         }
         int take = Math.min(amount, stack.getCount());
         stack.shrink(take);
         amount -= take;
      }
      return amount;
   }
}
