package net.exmo.sreGame.games.hypixelsays;

/** Fixed, server-verifiable catalogue used by every Hypixel Says match. */
public enum HypixelSaysTask {
   JUMP("跳一下", Kind.JUMP), SNEAK("潜行", Kind.SNEAK), SPRINT("疾跑", Kind.SPRINT), STAND_STILL("保持静止", Kind.STILL),
   ENTER_WATER("进入水中", Kind.WATER), STAND_SLIME("站在史莱姆方块上", Kind.SLIME), STAND_HONEY("站在蜂蜜块上", Kind.HONEY),
   ENTER_COBWEB("钻进蜘蛛网", Kind.COBWEB), CLIMB_LADDER("爬上梯子", Kind.LADDER),
   LOOK_SKY("看向天空", Kind.LOOK_SKY), LOOK_GROUND("看向地面", Kind.LOOK_GROUND), LOOK_PLAYER("看着一名玩家", Kind.LOOK_PLAYER),
   LOOK_HEAD("看着一名玩家的头", Kind.LOOK_HEAD), HOLD_DIAMOND("手持钻石", Kind.HOLD_DIAMOND), HOLD_BOW("手持弓", Kind.HOLD_BOW),
   WEAR_LEATHER_CAP("戴上皮革帽子", Kind.WEAR_CAP), WEAR_PUMPKIN("戴上南瓜", Kind.WEAR_PUMPKIN), USE_SHIELD("使用盾牌", Kind.USE_SHIELD),
   BREAK_LOG("打碎橡木原木", Kind.BREAK_LOG), BREAK_WOOL("打碎羊毛", Kind.BREAK_WOOL), PLACE_WOOL("放置羊毛", Kind.PLACE_WOOL),
   PLACE_TORCH("放置火把", Kind.PLACE_TORCH), PLANT_TREE("种一棵树", Kind.PLANT_TREE), TILL_DIRT("耕一块地", Kind.TILL_DIRT),
   PRESS_BUTTON("按下按钮", Kind.BUTTON), FLIP_LEVER("拉下拉杆", Kind.LEVER), RING_BELL("敲响钟", Kind.BELL),
   CRAFT_STICKS("合成木棍", Kind.CRAFT_STICKS), CRAFT_TABLE("合成工作台", Kind.CRAFT_TABLE), CRAFT_SWORD("合成木剑", Kind.CRAFT_SWORD),
   EAT_APPLE("吃掉苹果", Kind.EAT_APPLE), DRINK_WATER("喝一瓶水", Kind.DRINK_WATER), FILL_BUCKET("装一桶水", Kind.FILL_BUCKET),
   MILK_COW("挤牛奶", Kind.MILK_COW), SHEAR_SHEEP("剪羊毛", Kind.SHEAR_SHEEP), CAST_ROD("甩出鱼竿", Kind.CAST_ROD),
   THROW_EGG("丢出鸡蛋", Kind.THROW_EGG), THROW_SNOWBALL("丢出雪球", Kind.THROW_SNOWBALL), THROW_XP("丢出经验之瓶", Kind.THROW_XP),
   SHOOT_TARGET("射中目标", Kind.SHOOT_TARGET), SHOOT_SELF("用弓箭射自己", Kind.SHOOT_SELF), HIT_PIG("攻击一只猪", Kind.HIT_PIG),
   HIT_CHICKEN("攻击一只鸡", Kind.HIT_CHICKEN), HIT_COW("攻击一头牛", Kind.HIT_COW), RIDE_PIG("骑上一只猪", Kind.RIDE_PIG),
   FEED_PIG("喂一只猪", Kind.FEED_PIG), RIDE_HORSE("骑上一匹马", Kind.RIDE_HORSE), BOARD_BOAT("坐上一条船", Kind.BOARD_BOAT),
   STAND_NEAR_PLAYER("靠近一名玩家", Kind.NEAR_PLAYER), HIT_PLAYER("攻击一名玩家", Kind.HIT_PLAYER), TAKE_CACTUS_DAMAGE("碰一下仙人掌", Kind.CACTUS),
   TAKE_FIRE_DAMAGE("碰一下火焰", Kind.FIRE), OPEN_CHEST("打开箱子", Kind.CHEST), EXTINGUISH_FIRE("用水灭火", Kind.EXTINGUISH_FIRE);

   public enum Kind {
      JUMP, SNEAK, SPRINT, STILL, WATER, SLIME, HONEY, COBWEB, LADDER,
      LOOK_SKY, LOOK_GROUND, LOOK_PLAYER, LOOK_HEAD, HOLD_DIAMOND, HOLD_BOW, WEAR_CAP, WEAR_PUMPKIN, USE_SHIELD,
      BREAK_LOG, BREAK_WOOL, PLACE_WOOL, PLACE_TORCH, PLANT_TREE, TILL_DIRT, BUTTON, LEVER, BELL,
      CRAFT_STICKS, CRAFT_TABLE, CRAFT_SWORD, EAT_APPLE, DRINK_WATER, FILL_BUCKET, MILK_COW, SHEAR_SHEEP, CAST_ROD,
      THROW_EGG, THROW_SNOWBALL, THROW_XP, SHOOT_TARGET, SHOOT_SELF, HIT_PIG, HIT_CHICKEN, HIT_COW, RIDE_PIG,
      FEED_PIG, RIDE_HORSE, BOARD_BOAT, NEAR_PLAYER, HIT_PLAYER, CACTUS, FIRE, CHEST, EXTINGUISH_FIRE
   }

   private final String text;
   private final Kind kind;
   HypixelSaysTask(String text, Kind kind) { this.text = text; this.kind = kind; }
   public String text() { return this.text; }
   public Kind kind() { return this.kind; }
}
