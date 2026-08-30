package net.exmo.sreGame.games.partygames.official;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.api.PartyGameAction.Type;
import net.exmo.sreGame.games.partygames.api.PartyGameController;
import net.exmo.sreGame.games.partygames.api.PartyGameDefinition;

/** Source-of-truth metadata for the MP2 101-114 duel catalogue. */
public final class OfficialPartyGames {
   private static final Map<PartyGameType, PartyGameDefinition> DEFINITIONS = new EnumMap<>(PartyGameType.class);

   static {
      add(PartyGameType.MINIONS, 0, List.of("移动来引导你的 30 名随从，他们会跟随你前进", "随从从正面接触敌军时会把对方转换为己方颜色", "被转换的随从会加入你的军团并改为跟随你", "率先夺取对方全部随从的玩家获胜"), Type.LOOK_CHANGED);
      add(PartyGameType.RING_IN_THE_RING, 90, List.of("拼尽全力快速敲响己方的钟", "每敲一次：自己加 1 分，同时对手减 1 分", "获胜分差从 20 开始，并随时间依次降为 15、10、5", "达到当前获胜分差即刻获胜；时间到按分数判定"), Type.USE_BLOCK);
      add(PartyGameType.GLADIATOR_FIGHT, 0, List.of("使用铁剑、弓和 4 支箭进行单命决斗", "护甲和武器均按原玩法发放且不会损坏", "关闭自然恢复，首个杀死对手的玩家获胜"), Type.ATTACK_ENTITY);
      add(PartyGameType.TURTLE_HOCKEY, 90, List.of("攻击无敌海龟，它会朝击打方向移动", "在时间耗尽前把海龟打进对方颜色的球门", "率先进球者获胜；无人进球则平局"), Type.ATTACK_ENTITY);
      add(PartyGameType.GO_FISH, 90, List.of("在绿洲水池中使用普通钓鱼竿", "鳕鱼、鲑鱼、河豚或热带鱼均算成功", "倒计时结束前第一个真正钓到鱼的玩家获胜"), Type.FISH_CAUGHT);
      add(PartyGameType.DONT_PUSH_MY_BUTTONS, 0, List.of("按下按钮，将后方对应方块变为你的队伍颜色", "获胜条件从占领整面墙的 9 格开始", "15、22.5、30 秒时依次降为 8、7、6 格", "第一个达到当前获胜条件的玩家获胜"), Type.USE_BLOCK);
      add(PartyGameType.BRIDGE_CROSSING, 0, List.of("利用队伍颜色方块和武器抵达对方岛屿", "进入对方岛屿上的目标坑即可赢得本轮", "阵亡后等待 3 秒复活；每轮会重置搭建区域", "率先赢得两轮的玩家获胜"), Type.ATTACK_ENTITY, Type.USE_BLOCK);
      add(PartyGameType.PIG_PUSHERS, 90, List.of("猪无法自行移动，只能由玩家击打推动", "每局会按原玩法随机生成一条围栏路线", "在时间耗尽前率先把猪推进粗泥谷仓者获胜"), Type.ATTACK_ENTITY);
      add(PartyGameType.BALANCE_BEAM, 120, List.of("沿玻璃路线前进，小心不要掉下去", "白色玻璃是检查点，掉落后会回到最近检查点", "引力方向和强度每 10 秒改变一次", "先到金块终点者获胜；超时按最远检查点判定"), Type.JUMP, Type.SNEAK);
      add(PartyGameType.BUTTON_SEARCH, 210, List.of("先用 30 秒在与对手相同的房间中藏匿按钮", "可放置按钮、丢在当前位置、放入容器或物品展示框", "可提前确认；未藏好时按钮会自动留在脚下", "随后交换房间，在 180 秒内率先找到对方按钮者获胜"), Type.USE_BLOCK, Type.USE_ITEM, Type.USE_ENTITY, Type.DROP_ITEM);
      add(PartyGameType.BETRIS, 0, List.of("跳跃硬降、按住潜行软降、滚轮左右移动", "左键逆时针旋转，右键顺时针旋转，向前移动 Hold", "完整消行会向对手发送垃圾行，垃圾行不反送", "90 秒后每 10 秒增加永久障碍行；方块触顶即失败"), Type.JUMP, Type.SNEAK, Type.HOTBAR_DELTA, Type.LEFT_CLICK, Type.RIGHT_CLICK, Type.FORWARD);
      add(PartyGameType.DEUCE, 0, List.of("发球者丢出专用球，再击打出现的小史莱姆", "史莱姆必须越过铁栏网并落到对方场地", "漏接、回击未过网或出界都会让对手得分", "发球权每两球轮换，率先领先两分者获胜"), Type.DROP_ITEM, Type.ATTACK_ENTITY);
      add(PartyGameType.DECRYPTION, 0, List.of("把门口六位字母密码按墙上对应表转换成数字", "用铁砧把寂静纹饰锻造模板重命名为六位数字", "把模板投入己方漏斗；错误答案会在 0.5 秒后退回", "正确答案会开门，第一个离开房间的玩家获胜"), Type.ITEM_ENTERED_CONTAINER, Type.USE_BLOCK);
      add(PartyGameType.CANNONEERS, 0, List.of("移动鼠标瞄准，滚轮把蓄力强度调整为 1–20", "左键确认或取消准心，丢弃键锁定发射", "双方锁定后同时开火，炮弹受重力、风力和墙体影响", "仅命中对方炮台者获胜；同中或同失则生成下一轮环境"), Type.LOOK_CHANGED, Type.HOTBAR_DELTA, Type.LEFT_CLICK, Type.DROP_ITEM);
   }

   private OfficialPartyGames() { }

   private static void add(PartyGameType type, int seconds, List<String> rules, Type... inputs) {
      DEFINITIONS.put(type, new PartyGameDefinition(type, "mp2_" + type.name().toLowerCase(), 2, 2, seconds * 20, rules, Set.of(inputs)));
   }

   public static boolean contains(PartyGameType type) { return DEFINITIONS.containsKey(type); }
   public static PartyGameDefinition definition(PartyGameType type) { return DEFINITIONS.get(type); }
   public static PartyGameController create(PartyGameType type) { return OfficialControllers.create(definition(type)); }
}
