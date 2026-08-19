package net.exmo.sreGame.dontdo;

import java.util.concurrent.ThreadLocalRandom;

public enum DontDoRule {
   JUMP("禁止跳跃", "不能跳跃", 10),
   USE_WORK_BLOCKS("不！许！用！", "不能使用工作方块", 8),
   TAKE_DAMAGE("脆骨症", "不能受到任何伤害", 10),
   HURT_FRIENDLY("圣母之心", "不能攻击友善动物", 10),
   HURT_HOSTILE("战斗狂魔", "不能攻击非友善生物", 10),
   PUNCH_BLOCKS("无力症", "不能破坏方块", 8),
   USE_TOOLS("有气无力症", "不能使用工具", 8),
   EAT_MEAT("素食者", "不能食用肉类", 10),
   GET_BUFFS("高敏体", "不能获得药水效果", 12),
   ATTACK_PLAYERS("诚信", "不能攻击玩家", 10),
   HURT_MOBS("忘本", "不能攻击非玩家生物", 10),
   NEARBY_DEATH("存护", "周围不能有玩家死亡", 10),
   HP_BELOW_HALF("稳一点", "原版生命不能低于一半", 10),
   MELEE_WEAPON("七步之内枪又准又快", "不能用近战武器", 10),
   RANGED_WEAPON("七步之内刀快", "不能用远程武器", 10),
   PLAYER_CLOSE("洁癖", "不能靠近其他玩家", 10),
   TOUCH_WATER("干旱者", "不能接触水", 10),
   SWIM("我不会游", "不能游泳", 10),
   SPRINT("身娇体弱", "不能疾跑", 10),
   WEAR_ARMOR("自信", "不能穿戴护甲", 8),
   OFFHAND("手无寸铁", "副手不能拿东西", 10),
   USE_SHIELD("只攻不防", "不能盾牌格挡", 10),
   SNEAK("潜行-100级", "不能潜行", 10),
   GET_RAINED("避水", "不能淋雨", 10),
   DROP_ITEMS("不给不给！", "不能丢出物品", 10),
   USE_CONTAINERS("我的背包足够大！", "不能使用容器", 8),
   PLACE_LIGHT("惧光者", "不能放置光源", 10),
   BRIGHT_LIGHT("惧光者之子", "不能待在亮度>10处", 10),
   DIM_LIGHT("喜光者", "不能走进亮度<8处", 10),
   FULL_HUNGER("刚刚好", "不能回满饱食度", 10),
   TIGHT_SPACE("幽秘恐惧症", "不能进入小于3×3空间", 10),
   NIGHT_MOVE("禁止夜行", "夜晚露天不能行动", 10),
   NON_WEAPON("我只会用这个", "不能用非武器攻击", 10),
   ANY_WEAPON("偏科", "不能用武器攻击", 10),
   OVER6_DAMAGE("攻速快才是真的快", "不能用伤害>6的武器", 10),
   FULL_INVENTORY("裤兜满啦", "物品栏不能满", 10),
   CRAFTING_GRID("糟糕...忘带了", "不能用背包合成栏", 10),
   PLACE_BLOCK("不可以！", "不能放置方块", 10),
   TOOL_MINING("有力无气症", "不能用工具挖方块", 8),
   NEAR_ATTACKING("二极管", "周围不能有人在攻击", 10),
   NEAR_RAINED("带着我的伞", "周围不能有人淋雨", 10),
   NEAR_WORKING("自私", "周围不能有人用工作方块", 10),
   NEAR_EATING("看的我都饿了", "周围不能有人吃东西", 10),
   SELF_BURN("hero死啦！", "自身不能燃烧", 10),
   HIGH_Y("恐高症", "不能去过高处", 10),
   PLAYERS_NEAR("恐人症", "5格内不能有其他玩家", 10),
   STAND_GRASS("绿色恐惧", "不能站在草地上", 8),
   HOSTILES_NEAR("贪生怕死", "周围不能有敌对生物", 10),
   HEALTH_CHANGE("贪死贪生", "原版血量不能变动", 10);

   public final String title;
   public final String describe;
   public final float weight;

   DontDoRule(String title, String describe, float weight) {
      this.title = title;
      this.describe = describe;
      this.weight = weight;
   }

   public static DontDoRule pick(DontDoRule except) {
      DontDoRule[] all = values();
      double total = 0.0;
      for (DontDoRule rule : all) {
         if (rule != except) {
            total += rule.weight;
         }
      }
      double roll = ThreadLocalRandom.current().nextDouble() * total;
      double cursor = 0.0;
      for (DontDoRule rule : all) {
         if (rule == except) {
            continue;
         }
         cursor += rule.weight;
         if (roll <= cursor) {
            return rule;
         }
      }
      return JUMP;
   }
}
