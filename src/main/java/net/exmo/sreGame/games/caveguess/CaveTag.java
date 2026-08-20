package net.exmo.sreGame.games.caveguess;

public enum CaveTag {
   SQUARE("方形", "形状", "yellow_concrete"),
   ROUND("圆形", "形状", "snowball"),
   LONG("长条形", "形状", "stick"),
   IRREGULAR("不规则", "形状", "fire_charge"),

   TINY("极小", "大小", "iron_nugget"),
   SMALL("小", "大小", "gold_nugget"),
   MEDIUM("中", "大小", "iron_ingot"),
   LARGE("大", "大小", "iron_block"),
   HUGE("巨大", "大小", "netherite_block"),

   ORGANIC("有机", "材质", "kelp"),
   METAL("金属", "材质", "iron_ingot"),
   STONE("石头", "材质", "cobblestone"),
   WOOD("木头", "材质", "oak_log"),
   LIQUID("液体", "材质", "water_bucket"),
   GAS("气体", "材质", "glass_bottle"),

   RED("红", "颜色", "red_dye"),
   ORANGE("橙", "颜色", "orange_dye"),
   YELLOW("黄", "颜色", "yellow_dye"),
   GREEN("绿", "颜色", "green_dye"),
   BLUE("蓝", "颜色", "blue_dye"),
   PURPLE("紫", "颜色", "purple_dye"),
   BLACK("黑", "颜色", "black_dye"),
   WHITE("白", "颜色", "white_dye"),
   MULTI("多色", "颜色", "firework_star"),

   UNDERGROUND("地下", "地点", "deepslate"),
   GROUND("地上", "地点", "grass_block"),
   UNDERWATER("水下", "地点", "prismarine"),
   SKY("空中", "地点", "feather"),
   INDOORS("房屋内", "地点", "oak_door"),

   TOOL("工具", "用途", "iron_pickaxe"),
   WEAPON("武器", "用途", "iron_sword"),
   FOOD("食物", "用途", "cooked_beef"),
   DECOR("装饰", "用途", "painting"),
   MONSTER("怪物", "用途", "creeper_head"),
   PLANT("植物", "用途", "oak_sapling");

   private final String display;
   private final String group;
   private final String icon;

   CaveTag(String display, String group, String icon) {
      this.display = display;
      this.group = group;
      this.icon = icon;
   }

   public String display() {
      return this.display;
   }

   public String group() {
      return this.group;
   }

   public String icon() {
      return this.icon;
   }
}
