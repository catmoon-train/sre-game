package net.exmo.sreGame.games.partygames;

import java.util.Arrays;
import java.util.List;

/** The fixed catalogue exposed in the room selector and map editor. */
public enum PartyGameType {
   // Minecraft Party 2 catalogue: the 100-series is intentionally kept together so
   // that room selection, map templates, and persisted settings all share the same
   // stable ids as the source game catalogue.
   MINIONS("mp2_101_minions", "随从争夺战", "allay_spawn_egg", Mode.SCORE, 2),
   RING_IN_THE_RING("mp2_102_ring_in_the_ring", "快速敲钟", "bell", Mode.SCORE, 2),
   GLADIATOR_FIGHT("mp2_103_gladiator_fight", "角斗士之争", "iron_sword", Mode.ELIMINATION, 2),
   TURTLE_HOCKEY("mp2_104_turtle_hockey", "乌龟冰球", "turtle_spawn_egg", Mode.SCORE, 2),
   GO_FISH("mp2_105_go_fish", "钓鱼时间", "fishing_rod", Mode.SCORE, 2),
   DONT_PUSH_MY_BUTTONS("mp2_106_dont_push_my_buttons", "别摁按钮", "stone_button", Mode.SCORE, 2),
   BRIDGE_CROSSING("mp2_107_bridge_crossing", "战桥", "stone_bricks", Mode.SCORE, 2),
   PIG_PUSHERS("mp2_108_pig_pushers", "赶猪上架", "pig_spawn_egg", Mode.SCORE, 2),
   BALANCE_BEAM("mp2_109_balance_beam", "平衡木", "light_weighted_pressure_plate", Mode.RACE, 2),
   BUTTON_SEARCH("mp2_110_button_search", "寻找按钮", "oak_button", Mode.RACE, 2),
   BETRIS("mp2_111_betris", "俄罗斯方块", "purple_concrete", Mode.SCORE, 2),
   DEUCE("mp2_112_deuce", "排球", "slime_ball", Mode.SCORE, 2),
   DECRYPTION("mp2_113_decryption", "破译密文", "writable_book", Mode.RACE, 2),
   CANNONEERS("mp2_114_cannoneers", "加农炮手", "fire_charge", Mode.ELIMINATION, 2),

   PRISON_PALS("mp2_201_prison_pals", "狱中好友", "iron_bars", Mode.SCORE, 24),
   RPSC("mp2_202_rpsc", "石头剪刀布，谁输谁跑路", "shears", Mode.ELIMINATION, 24),
   TANKS("mp2_203_tanks", "坦克大战", "bow", Mode.SCORE, 24),
   CAPTURE_THE_FLAG("mp2_204_capture_the_flag", "夺旗比赛", "light_blue_banner", Mode.SCORE, 24),
   MINE_YOUR_BUSINESS("mp2_205_mine_your_business", "地底之战", "diamond_pickaxe", Mode.ELIMINATION, 24),
   TEAM_HOCKEY("mp2_206_team_hockey", "团队冰球", "turtle_spawn_egg", Mode.SCORE, 24),
   MAZE_NAVIGATOR("mp2_207_maze_navigator", "寻物汇合", "compass", Mode.SCORE, 24),
   BOMBS_AWAY("mp2_208_bombs_away", "炸弹投出！", "tnt", Mode.ELIMINATION, 24),
   LABYRINTH("mp2_209_labyrinth", "天地迷宫", "gold_ingot", Mode.SCORE, 24),
   SNOW_WARS("mp2_210_snow_wars", "雪球大战", "snowball", Mode.ELIMINATION, 24),
   SPACE_JUMPERS("mp2_211_space_jumpers", "太空跳跃", "end_stone", Mode.RACE, 24),
   BOOM_CARTS("mp2_212_boom_carts", "爆炸矿车", "tnt_minecart", Mode.SCORE, 24),
   WHAT_THE_CLUCK("mp2_213_what_the_cluck", "鸡飞蛋打", "egg", Mode.ELIMINATION, 24),
   RECRUITMENT_ROYALE("mp2_214_recruitment_royale", "招兵买马上战场", "zombie_spawn_egg", Mode.SCORE, 24),

   HIDE_AND_SEEK("mp2_301_hide_and_seek", "捉迷藏", "barrel", Mode.ELIMINATION, 24),
   GAME_THEORY("mp2_302_game_theory", "博弈游戏", "redstone_torch", Mode.SCORE, 24),
   BOSS_BRAWL("mp2_303_boss_brawl", "斩首行动", "wither_skeleton_skull", Mode.ELIMINATION, 24),
   GOLD_RUSH("mp2_304_gold_rush", "淘金热", "gold_ingot", Mode.SCORE, 24),
   BLOCK_BUSTER("mp2_305_block_buster", "方块躲猫猫", "tnt", Mode.SCORE, 24),
   PAC_CUBE("mp2_306_pac_cube", "吃豆人", "lime_concrete", Mode.ELIMINATION, 24),
   GHOST_HUNT("mp2_307_ghost_hunt", "幽灵猎人", "soul_lantern", Mode.ELIMINATION, 24),
   TREETOP_HOP("mp2_308_treetop_hop", "树顶之战", "oak_leaves", Mode.RACE, 24),
   SLIME_TIME("mp2_309_slime_time", "史莱姆时间", "slime_block", Mode.ELIMINATION, 24),
   IN_THE_ZONE("mp2_310_in_the_zone", "据点争夺", "purpur_slab", Mode.SCORE, 24),
   GHAST_BLAST("mp2_311_ghast_blast", "恶魂打击", "ghast_spawn_egg", Mode.ELIMINATION, 24),
   EGGCELLENCE("mp2_312_eggcellence", "一模一样", "egg", Mode.RACE, 24),
   RAVAGER_RODEO("mp2_313_ravager_rodeo", "劫掠兽冲击", "ravager_spawn_egg", Mode.ELIMINATION, 24),
   MOUSE_TRAP("mp2_314_mouse_trap", "投鼠忌器", "tripwire_hook", Mode.ELIMINATION, 24),

   ONE_IN_CHAMBER("one_in_chamber", "一箭超人", "bow", Mode.ELIMINATION, 24),
   SUMO("sumo", "相扑", "stick", Mode.ELIMINATION, 24),
   DROPPER("dropper", "水立方", "water_bucket", Mode.RACE, 24),
   VOLCANO("volcano", "火山", "magma_block", Mode.ELIMINATION, 24),
   HOT_POTATO("hot_potato", "烫手山芋", "baked_potato", Mode.ELIMINATION, 24),
   PUNCH_THE_BAT("punch_the_bat", "拍蝙蝠", "bat_spawn_egg", Mode.SCORE, 24),
   ORE_MINER("ore_miner", "矿工达人", "diamond_ore", Mode.SCORE, 24),
   ANIMAL_SLAUGHTER("animal_slaughter", "动物猎杀者", "iron_sword", Mode.SCORE, 24),
   CRAFTING_MASTER("crafting_master", "合成达人", "crafting_table", Mode.SCORE, 24),
   HORSE_RACE("horse_race", "跑马赛", "saddle", Mode.RACE, 24),
   MINE_FIELD("mine_field", "地雷", "tnt", Mode.RACE, 24),
   SURVIVAL_GAMES("survival_games", "饥饿游戏", "chest", Mode.ELIMINATION, 24),
   TNT_RUN("tnt_run", "TNT跑酷", "tnt", Mode.ELIMINATION, 24),
   MOB_SHOOTER("mob_shooter", "射击动物", "bow", Mode.SCORE, 24),
   HOE_HOE_HOE("hoe_hoe_hoe", "锄锄锄", "diamond_hoe", Mode.SCORE, 24),
   COLORFUL_RUN("colorful_run", "颜色跑酷", "red_wool", Mode.ELIMINATION, 24),
   DIG_DOWN("dig_down", "挖挖挖", "diamond_pickaxe", Mode.RACE, 24);

   public enum Mode { ELIMINATION, SCORE, RACE }

   private final String id;
   private final String displayName;
   private final String icon;
   private final Mode mode;
   private final int maxPlayers;

   PartyGameType(String id, String displayName, String icon, Mode mode, int maxPlayers) {
      this.id = id;
      this.displayName = displayName;
      this.icon = icon;
      this.mode = mode;
      this.maxPlayers = maxPlayers;
   }

   public String id() { return this.id; }
   public String displayName() { return this.displayName; }
   public String icon() { return this.icon; }
   public Mode mode() { return this.mode; }
   public int maxPlayers() { return this.maxPlayers; }

   public static PartyGameType byId(String id) {
      for (PartyGameType type : values()) if (type.id.equals(id)) return type;
      return null;
   }

   public static List<PartyGameType> all() { return Arrays.asList(values()); }
}
