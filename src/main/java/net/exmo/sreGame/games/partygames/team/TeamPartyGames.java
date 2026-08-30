package net.exmo.sreGame.games.partygames.team;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.api.PartyGameAction.Type;
import net.exmo.sreGame.games.partygames.api.PartyGameController;
import net.exmo.sreGame.games.partygames.api.PartyGameDefinition;

/**
 * Metadata for the room-isolated 201-214 and 301-314 team catalogues.
 *
 * <p>The old implementation encoded these rules in the 3,000-line legacy
 * match. Keeping the catalogue here makes the fixed phase/time/input contract
 * visible to the room UI and gives every controller the same source of truth.
 */
public final class TeamPartyGames {
   private static final Map<PartyGameType, PartyGameDefinition> DEFINITIONS = new EnumMap<>(PartyGameType.class);

   static {
      add(PartyGameType.PRISON_PALS, 120, List.of(
         "与你的队友合作逃离监狱，錾制石砖会记录检查点",
         "蓝方和红方各自带领一支僵尸随从军团穿过五个挑战关卡",
         "率先完成五个关卡的团队获胜；随从会同步队伍颜色"), Type.LOOK_CHANGED);
      add(PartyGameType.RPSC, 120, List.of(
         "每队先在石头、剪刀、布按钮上作出选择",
         "输的一队进入逃跑阶段，胜队可以在短时间内攻击对手",
         "逃跑阶段出现击杀或全队淘汰后结算；平局会重新选择"), Type.USE_BLOCK, Type.DROP_ITEM, Type.ATTACK_ENTITY);
      add(PartyGameType.TANKS, 150, List.of(
         "一名队员控制下方骷髅移动，另一名队员负责骷髅射击",
         "按使用键或丢弃键发射无下坠箭矢，箭矢有 1 秒冷却",
         "凋灵骷髅生命值更高；率先击杀所有敌方玩家的团队获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.DROP_ITEM);
      add(PartyGameType.CAPTURE_THE_FLAG, 180, List.of(
         "进入对方基地并破坏对方旗帜即可携旗",
         "携旗回到自己的旗座会得分；被击中或掉线会掉旗",
         "PvP 已启用；率先将敌方旗帜带回基地的队伍获胜"), Type.USE_BLOCK, Type.ATTACK_ENTITY);
      add(PartyGameType.MINE_YOUR_BUSINESS, 150, List.of(
         "在 12×10×12 的实心区域内挖掘，找寻你的敌人",
         "木桶可能藏有物品、钻石矿、岩浆或发光激活器",
         "率先击杀所有敌方玩家的团队获胜"), Type.USE_BLOCK, Type.ATTACK_ENTITY);
      add(PartyGameType.TEAM_HOCKEY, 150, List.of(
         "游戏开始时球场中间会出现两只无敌海龟",
         "将海龟击打到对方颜色球门即可得分，进球后会补充一只",
         "先得到两球，或时间到得分较高的队伍获胜"), Type.ATTACK_ENTITY);
      add(PartyGameType.MAZE_NAVIGATOR, 180, List.of(
         "在随机生成的迷宫里与队友合作寻找物品",
         "每名队员必须找到一枚己方团队颜色的染料，并收集两枚目标",
         "所有队友找到染料后尽快汇合；率先完成的团队获胜"), Type.USE_BLOCK, Type.USE_ITEM);
      add(PartyGameType.BOMBS_AWAY, 120, List.of(
         "向对手投掷 TNT，将他们从浮空岛击落",
         "丢弃键投掷，TNT 朝视线方向飞行并在接触方块后引爆",
         "每 5 秒获得一个 TNT（最多 5 个）；最后存活的团队获胜"), Type.USE_ITEM, Type.DROP_ITEM);
      add(PartyGameType.LABYRINTH, 180, List.of(
         "在上下交错的迷宫中收集己方两枚金色目标",
         "每枚目标只计入首次收集的队员，收集状态同步全队",
         "全队目标完成后立即获胜，时间到按进度判定"), Type.USE_BLOCK);
      add(PartyGameType.SNOW_WARS, 150, List.of(
         "使用无限补给的雪球攻击敌方队员",
         "每名队员承受三次有效命中后淘汰，命中会显示队伍颜色",
         "率先淘汰对方全队的队伍获胜"), Type.USE_ITEM, Type.ATTACK_ENTITY);
      add(PartyGameType.SPACE_JUMPERS, 150, List.of(
         "射手使用弓箭击中按钮，逐段延伸己方末地砖平台",
         "跳跃者拥有跳跃提升 IV；掉落会回到最近的平台并保留个人检查点",
         "任意一名跳跃者先到达己方颜色终点即可为队伍赢得比赛"), Type.JUMP, Type.USE_ITEM, Type.DROP_ITEM);
      add(PartyGameType.BOOM_CARTS, 180, List.of(
         "在按钮上选择目标并锁定爆炸矿车",
         "双方锁定后同时爆炸，命中会消耗对方一条生命",
         "五条生命全部耗尽的队伍失败，时间到按剩余生命判定"), Type.USE_BLOCK, Type.DROP_ITEM);
      add(PartyGameType.WHAT_THE_CLUCK, 150, List.of(
         "用鸡蛋攻击对方颜色的鸡群，鸡群会在场地内躲避",
         "每个有效鸡蛋命中只计一次，队友鸡不会受伤",
         "率先清空敌方鸡群或时间到鸡数较少的队伍获胜"), Type.USE_ITEM, Type.ATTACK_ENTITY);
      add(PartyGameType.RECRUITMENT_ROYALE, 180, List.of(
         "把怪物击退到己方颜色的坑中，捕获的怪物会加入军队",
         "招募结束后进入出兵阶段，可使用刷怪蛋安排已捕获单位",
         "两支军队在中线自动交战；击败敌方军队或时间到剩余人数较多的队伍获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.USE_BLOCK);

      // 301–314 are the asymmetric/role-oriented catalogue from the source
      // datapack.  They still run through the same room-isolated team shell:
      // role state is controller-local while the blue/red team is supplied by
      // the room and is reflected by every HUD/entity marker.
      add(PartyGameType.HIDE_AND_SEEK, 135, List.of(
         "随机选出一名抓捕者，其余玩家有 45 秒时间躲藏",
         "抓捕者释放后有 90 秒寻找并击杀所有躲藏者；躲藏者撑到时间结束获胜",
         "抓捕者丢出铁剑可切换速度；躲藏者隐身但保留队伍颜色护甲"), Type.ATTACK_ENTITY, Type.DROP_ITEM, Type.LOOK_CHANGED);
      add(PartyGameType.GAME_THEORY, 120, List.of(
         "每轮从 A、B、C 三种选择中提交一项，双方选择的交点决定分数",
         "共进行六轮；把选择物品丢出或点击棋盘提交，未提交按 A 处理",
         "六轮后总分更高的一方获胜，平局为平局"), Type.USE_BLOCK, Type.DROP_ITEM);
      add(PartyGameType.BOSS_BRAWL, 120, List.of(
         "一名玩家扮演雷电骷髅首领，其余玩家使用剑和弓进行围攻",
         "首领拥有火焰弹、刷怪蛋和真实伤害斧；挑战者每 8 秒补充箭矢",
         "击杀对方角色组即可获胜；死亡者进入旁观者模式"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.DROP_ITEM);
      add(PartyGameType.GOLD_RUSH, 120, List.of(
         "传送带会不断掉落金质物品，潜行站到物品上方即可拾取",
         "金粒 1 分、金锭 3 分、金块 6 分、镶金黑石 2 分",
         "钻石靴提供速度，下界合金靴提供跳跃；队伍总分最高获胜"), Type.SNEAK, Type.USE_ITEM, Type.USE_BLOCK);
      add(PartyGameType.BLOCK_BUSTER, 120, List.of(
         "躲藏者从快捷栏选择草、花、蛛网、树叶、泥土、圆石、沙砾或橡木并丢出伪装",
         "搜寻者攻击伪装方块寻找躲藏者；下界合金剑冷却完成后可追踪最近目标",
         "限时内躲藏者存活则躲藏者方获胜，全部躲藏者被找到则搜寻者方获胜"), Type.USE_BLOCK, Type.ATTACK_ENTITY, Type.DROP_ITEM, Type.HOTBAR_DELTA);
      add(PartyGameType.PAC_CUBE, 120, List.of(
         "一名玩家扮演幽灵，其余玩家扮演吃豆人并收集迷宫能量球",
         "吃豆人有两条命；幽灵碰撞会扣除生命，能量球全部收集后吃豆人获胜",
         "幽灵被能量豆强化时不可被吃豆人反击"), Type.ATTACK_ENTITY, Type.USE_BLOCK);
      add(PartyGameType.GHOST_HUNT, 120, List.of(
         "幽灵隐身并发出微弱粒子，目标是杀死村庄内的所有村民",
         "猎人持有快速攻击剑，误伤村民会失去保护时间",
         "猎杀所有幽灵或守住村民到时间结束即可获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM);
      add(PartyGameType.TREETOP_HOP, 120, List.of(
         "树顶队伍使用弓箭，地面队伍使用自动补箭的弩进行猎杀",
         "树上的玩家掉落会受伤并传送回最近树冠检查点",
         "率先淘汰敌方队伍的一方获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.JUMP);
      add(PartyGameType.SLIME_TIME, 55, List.of(
         "史莱姆方通过移动控制巨型史莱姆，逃生者必须远离它",
         "逃生者可拾取羽毛加速、玻璃短暂隐身，末影之眼可传送到队友",
         "史莱姆碰到全部逃生者时史莱姆方获胜；时间耗尽则逃生者获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.DROP_ITEM);
      add(PartyGameType.IN_THE_ZONE, 120, List.of(
         "站在中央据点持续为己方团队积累占领进度",
         "双方同时在场时进度暂停；使用击退木棒把敌人赶出区域",
         "率先填满占领进度条的团队获胜，时间到按进度判定"), Type.ATTACK_ENTITY, Type.USE_BLOCK);
      add(PartyGameType.GHAST_BLAST, 120, List.of(
         "恶魂方丢出火焰弹向幸存者发射火球，幸存者可左键打回",
         "恶魂可潜行下降、松开潜行上升；火球在墙体碰撞时消失",
         "恶魂击杀幸存者获胜，幸存者存活到时间结束获胜"), Type.ATTACK_ENTITY, Type.USE_ITEM, Type.DROP_ITEM, Type.SNEAK);
      add(PartyGameType.EGGCELLENCE, 180, List.of(
         "重新排列两面墙上的刷怪蛋，使每个对应位置完全一致",
         "只能修改自己队伍一侧的墙；每次交换会即时显示队伍颜色",
         "率先完成目标排列的团队获胜，时间到按正确格数判定"), Type.USE_ENTITY, Type.USE_BLOCK, Type.HOTBAR_DELTA);
      add(PartyGameType.RAVAGER_RODEO, 90, List.of(
         "骑乘劫掠兽把敌方玩家撞出竞技场，冲刺技能有 10 秒冷却",
         "劫掠兽朝骑手视线方向移动，冲撞会造成击退并显示红蓝粒子",
         "目标队伍全部出界或时间结束后按存活人数判定"), Type.ATTACK_ENTITY, Type.USE_ENTITY, Type.USE_ITEM, Type.JUMP);
      add(PartyGameType.MOUSE_TRAP, 70, List.of(
         "捕鼠方投掷鸡蛋，在落点生成方块构筑 1×1 陷阱",
         "老鼠方无法跳跃，可用石斧破坏最多 3 次方块",
         "捕鼠方把所有老鼠困住获胜；老鼠坚持到时间结束获胜"), Type.USE_ITEM, Type.DROP_ITEM, Type.JUMP);
   }

   private TeamPartyGames() { }

   private static void add(PartyGameType type, int seconds, List<String> rules, Type... inputs) {
      DEFINITIONS.put(type, new PartyGameDefinition(type, "mp2_" + type.name().toLowerCase(), 2, type.maxPlayers(), seconds * 20, rules, Set.of(inputs)));
   }

   public static boolean contains(PartyGameType type) { return DEFINITIONS.containsKey(type); }
   public static PartyGameDefinition definition(PartyGameType type) { return DEFINITIONS.get(type); }
   public static PartyGameController create(PartyGameType type) {
      PartyGameDefinition definition = definition(type);
      if (type != null && type.id().startsWith("mp2_3")) return AdvancedPartyControllers.create(definition);
      return TeamPartyControllers.create(definition);
   }
}
