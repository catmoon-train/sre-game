package net.exmo.sreGame.pillarpummel;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class PillarPummelSettings {
   public enum StartWeapon {
      NONE("无"), WOOD("木剑"), STONE("石剑"), IRON("铁剑");

      private final String label;

      StartWeapon(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      StartWeapon next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static StartWeapon fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return IRON;
         }
      }
   }

   public enum WoolDrop {
      ALL("全部"), HALF("一半"), NONE("不掉落");

      private final String label;

      WoolDrop(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      WoolDrop next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static WoolDrop fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return ALL;
         }
      }
   }

   public enum PlaceMode {
      WOOL("仅羊毛"), WOOL_PLANKS("羊毛+木板"), ALL("全方块");

      private final String label;

      PlaceMode(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      PlaceMode next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static PlaceMode fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return WOOL;
         }
      }
   }

   public enum BuildRegion {
      ANY("无"), NEAR_BASE("基地附近"), OWN_PLOT("己方地皮上");

      private final String label;

      BuildRegion(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      BuildRegion next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static BuildRegion fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return ANY;
         }
      }
   }

   public enum WinMode {
      TIME("时限最高分"), SCORE("达到目标分"), ELIMINATE("消灭对方");

      private final String label;

      WinMode(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      WinMode next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static WinMode fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return TIME;
         }
      }
   }

   public enum TieBreak {
      KILLS("击杀数"), PLOTS("占地数"), WOOL("羊毛量"), RANDOM("随机");

      private final String label;

      TieBreak(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      TieBreak next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static TieBreak fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return KILLS;
         }
      }
   }

   public enum ArenaShape {
      SQUARE("方形"),
      CIRCLE("圆形"),
      RING("环形"),
      PLUS("十字"),
      CROSS("斜十字"),
      SCATTER("随机缺柱");

      private final String label;

      ArenaShape(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      ArenaShape next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static ArenaShape fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return SQUARE;
         }
      }
   }

   private static final int[] DURATIONS = {1, 2, 3, 5, 8, 10, 12, 15};
   private static final int[] TEAM_COUNTS = {2, 3, 4};
   private static final int[] TEAM_SIZES = {1, 2, 3, 4};
   private static final int[] GRIDS = {4, 5, 6, 7, 8, 9, 10, 11};
   private static final int[] OCCUPY = {1, 2, 3, 4, 5, 6, 8, 10};
   private static final int[] STEAL = {2, 3, 5, 8, 10, 12, 15};
   private static final int[] SCORE_INTERVALS = {5, 10, 15, 20, 30};
   private static final int[] SCORE_VALUES = {1, 2, 3, 4, 5};
   private static final int[] CENTER_MULT = {1, 2, 3, 4, 5};
   private static final int[] INITIAL_PLOTS = {0, 1, 2, 3, 4, 5};
   private static final int[] MINE_COUNTS = {2, 4, 6, 8, 10, 12};
   private static final int[] MINE_REFRESH = {10, 20, 30, 45, 60};
   private static final int[] MINE_AMOUNTS = {1, 3, 5, 8, 12, 16};
   private static final int[] INITIAL_WOOL = {0, 1, 3, 5, 8, 16};
   private static final int[] HOLD_BOOST = {1, 2, 3, 4, 5};
   private static final int[] KILL_SCORES = {0, 1, 2, 3, 5, 8, 10};
   private static final int[] DEATH_SCORES = {0, -1, -2, -3, -5};
   private static final int[] INVULN = {0, 3, 5, 8, 10, 15};
   private static final int[] SAFE = {0, 3, 5, 8};
   private static final int[] BREAK_TENTHS = {5, 10, 20, 30, 50};
   private static final int[] BUILD_HEIGHTS = {3, 5, 10, 16, 24, 32};
   private static final int[] GAPS = {5, 10, 20, 30, 50};
   private static final int[] FIRST_TRIGGER = {2, 3, 5, 8, 10};
   private static final int[] ASSIST_INTERVAL = {1, 2, 3, 5};
   private static final int[] ASSIST_WOOL = {5, 8, 16, 32, 64};
   private static final int[] SPEED_SECS = {5, 10, 15, 20, 30};
   private static final int[] OCCUPY_BOOST_SECS = {10, 30, 60, 90, 120};
   private static final int[] TARGET_SCORES = {30, 50, 100, 150, 200, 300};
   private static final int[] PRICE_LOW = {1, 2, 3, 4, 5, 8, 10, 12, 15, 20};
   private static final int[] PRICE_MID = {2, 5, 6, 8, 10, 12, 15, 16, 20, 25, 30, 40};
   private static final int[] PRICE_HIGH = {10, 16, 20, 25, 40, 60};
   private static final int[] PRICE_NUKE = {64, 80, 96, 128, 160};

   private int durationMinutes = 10;
   private int teamCount = 2;
   private int teamSize = 2;
   private int grid = 7;
   private ArenaShape arenaShape = ArenaShape.SQUARE;
   private int occupySeconds = 3;
   private int stealSeconds = 5;
   private int scoreInterval = 10;
   private int scorePerPlot = 1;
   private int centerMultiplier = 2;
   private int initialPlots = 3;
   private int mineCount = 6;
   private int mineRefreshSeconds = 30;
   private int mineAmount = 5;
   private int initialWool = 0;
   private int holdWoolBoost = 2;
   private StartWeapon startWeapon = StartWeapon.IRON;
   private int killScore = 0;
   private int deathScore = -1;
   private int respawnInvuln = 5;
   private WoolDrop woolDrop = WoolDrop.ALL;
   private int safeRadius = 0;
   private PlaceMode placeMode = PlaceMode.WOOL;
   private int breakTenths = 20;
   private boolean breakDropWool = true;
   private int maxBuildHeight = 10;
   private BuildRegion buildRegion = BuildRegion.ANY;
   private int priceStone = 5;
   private int priceIron = 12;
   private int priceBow = 16;
   private int priceShield = 6;
   private int priceSpeed = 4;
   private int priceJump = 3;
   private int priceHeal = 5;
   private int priceDiamond = 25;
   private int priceDefense = 15;
   private int priceTnt = 2;
   private int priceRepair = 3;
   private int priceNuke = 128;
   private int priceLaser = 10;
   private int priceResource = 20;
   private boolean catchUp = false;
   private int catchGap = 20;
   private int firstTriggerMinutes = 5;
   private int assistIntervalMinutes = 2;
   private int assistWool = 16;
   private int speedSeconds = 10;
   private int occupyBoostSeconds = 60;
   private WinMode winMode = WinMode.TIME;
   private int targetScore = 100;
   private TieBreak tieBreak = TieBreak.KILLS;

   public int durationMinutes() {
      return this.durationMinutes;
   }

   public int durationTicks() {
      return this.durationMinutes * 20 * 60;
   }

   public void cycleDuration() {
      this.durationMinutes = next(DURATIONS, this.durationMinutes, 10);
   }

   public int teamCount() {
      return this.teamCount;
   }

   public void cycleTeamCount() {
      this.teamCount = next(TEAM_COUNTS, this.teamCount, 2);
   }

   public int teamSize() {
      return this.teamSize;
   }

   public void cycleTeamSize() {
      this.teamSize = next(TEAM_SIZES, this.teamSize, 2);
   }

   public int maxPlayers() {
      return this.teamCount * this.teamSize;
   }

   public int grid() {
      return this.grid;
   }

   public int plotCount() {
      return new PummelArena(0, net.minecraft.core.BlockPos.ZERO).enabledCellCount(
         new PummelArena.Layout(this.grid, this.teamCount, 0, 0, this.arenaShape.name(), 0L));
   }

   public void cycleGrid() {
      this.grid = next(GRIDS, this.grid, 7);
   }

   public ArenaShape arenaShape() {
      return this.arenaShape;
   }

   public void cycleArenaShape() {
      this.arenaShape = this.arenaShape.next();
   }

   public int occupySeconds() {
      return this.occupySeconds;
   }

   public void cycleOccupy() {
      this.occupySeconds = next(OCCUPY, this.occupySeconds, 3);
   }

   public int stealSeconds() {
      return this.stealSeconds;
   }

   public void cycleSteal() {
      this.stealSeconds = next(STEAL, this.stealSeconds, 5);
   }

   public int scoreInterval() {
      return this.scoreInterval;
   }

   public void cycleScoreInterval() {
      this.scoreInterval = next(SCORE_INTERVALS, this.scoreInterval, 10);
   }

   public int scorePerPlot() {
      return this.scorePerPlot;
   }

   public void cycleScorePerPlot() {
      this.scorePerPlot = next(SCORE_VALUES, this.scorePerPlot, 1);
   }

   public int centerMultiplier() {
      return this.centerMultiplier;
   }

   public void cycleCenterMultiplier() {
      this.centerMultiplier = next(CENTER_MULT, this.centerMultiplier, 2);
   }

   public int initialPlots() {
      return this.initialPlots;
   }

   public void cycleInitialPlots() {
      this.initialPlots = next(INITIAL_PLOTS, this.initialPlots, 3);
   }

   public int mineCount() {
      return this.mineCount;
   }

   public void cycleMineCount() {
      this.mineCount = next(MINE_COUNTS, this.mineCount, 6);
   }

   public int mineRefreshSeconds() {
      return this.mineRefreshSeconds;
   }

   public void cycleMineRefresh() {
      this.mineRefreshSeconds = next(MINE_REFRESH, this.mineRefreshSeconds, 30);
   }

   public int mineAmount() {
      return this.mineAmount;
   }

   public void cycleMineAmount() {
      this.mineAmount = next(MINE_AMOUNTS, this.mineAmount, 5);
   }

   public int initialWool() {
      return this.initialWool;
   }

   public void cycleInitialWool() {
      this.initialWool = next(INITIAL_WOOL, this.initialWool, 3);
   }

   public int holdWoolBoost() {
      return this.holdWoolBoost;
   }

   public void cycleHoldWoolBoost() {
      this.holdWoolBoost = next(HOLD_BOOST, this.holdWoolBoost, 2);
   }

   public StartWeapon startWeapon() {
      return this.startWeapon;
   }

   public void cycleStartWeapon() {
      this.startWeapon = this.startWeapon.next();
   }

   public int killScore() {
      return this.killScore;
   }

   public void cycleKillScore() {
      this.killScore = next(KILL_SCORES, this.killScore, 0);
   }

   public int deathScore() {
      return this.deathScore;
   }

   public void cycleDeathScore() {
      this.deathScore = next(DEATH_SCORES, this.deathScore, -1);
   }

   public int respawnInvuln() {
      return this.respawnInvuln;
   }

   public void cycleRespawnInvuln() {
      this.respawnInvuln = next(INVULN, this.respawnInvuln, 5);
   }

   public WoolDrop woolDrop() {
      return this.woolDrop;
   }

   public void cycleWoolDrop() {
      this.woolDrop = this.woolDrop.next();
   }

   public int safeRadius() {
      return this.safeRadius;
   }

   public void cycleSafeRadius() {
      this.safeRadius = next(SAFE, this.safeRadius, 3);
   }

   public PlaceMode placeMode() {
      return this.placeMode;
   }

   public void cyclePlaceMode() {
      this.placeMode = this.placeMode.next();
   }

   public int breakTenths() {
      return this.breakTenths;
   }

   public int breakTicks() {
      return Math.max(1, this.breakTenths * 2);
   }

   public String breakLabel() {
      int whole = this.breakTenths / 10;
      int frac = this.breakTenths % 10;
      return frac == 0 ? whole + "s" : whole + "." + frac + "s";
   }

   public void cycleBreakTime() {
      this.breakTenths = next(BREAK_TENTHS, this.breakTenths, 20);
   }

   public boolean breakDropWool() {
      return this.breakDropWool;
   }

   public void toggleBreakDrop() {
      this.breakDropWool = !this.breakDropWool;
   }

   public int maxBuildHeight() {
      return this.maxBuildHeight;
   }

   public void cycleMaxBuildHeight() {
      this.maxBuildHeight = next(BUILD_HEIGHTS, this.maxBuildHeight, 10);
   }

   public BuildRegion buildRegion() {
      return this.buildRegion;
   }

   public void cycleBuildRegion() {
      this.buildRegion = this.buildRegion.next();
   }

   public int priceStone() {
      return this.priceStone;
   }

   public void cyclePriceStone() {
      this.priceStone = next(PRICE_LOW, this.priceStone, 5);
   }

   public int priceIron() {
      return this.priceIron;
   }

   public void cyclePriceIron() {
      this.priceIron = next(PRICE_MID, this.priceIron, 12);
   }

   public int priceBow() {
      return this.priceBow;
   }

   public void cyclePriceBow() {
      this.priceBow = next(PRICE_MID, this.priceBow, 16);
   }

   public int priceShield() {
      return this.priceShield;
   }

   public void cyclePriceShield() {
      this.priceShield = next(PRICE_MID, this.priceShield, 6);
   }

   public int priceSpeed() {
      return this.priceSpeed;
   }

   public void cyclePriceSpeed() {
      this.priceSpeed = next(PRICE_LOW, this.priceSpeed, 4);
   }

   public int priceJump() {
      return this.priceJump;
   }

   public void cyclePriceJump() {
      this.priceJump = next(PRICE_LOW, this.priceJump, 3);
   }

   public int priceHeal() {
      return this.priceHeal;
   }

   public void cyclePriceHeal() {
      this.priceHeal = next(PRICE_LOW, this.priceHeal, 5);
   }

   public int priceDiamond() {
      return this.priceDiamond;
   }

   public void cyclePriceDiamond() {
      this.priceDiamond = next(PRICE_HIGH, this.priceDiamond, 25);
   }

   public int priceDefense() {
      return this.priceDefense;
   }

   public void cyclePriceDefense() {
      this.priceDefense = next(PRICE_MID, this.priceDefense, 15);
   }

   public int priceTnt() {
      return this.priceTnt;
   }

   public void cyclePriceTnt() {
      this.priceTnt = next(PRICE_LOW, this.priceTnt, 2);
   }

   public int priceRepair() {
      return this.priceRepair;
   }

   public void cyclePriceRepair() {
      this.priceRepair = next(PRICE_LOW, this.priceRepair, 3);
   }

   public int priceNuke() {
      return this.priceNuke;
   }

   public void cyclePriceNuke() {
      this.priceNuke = next(PRICE_NUKE, this.priceNuke, 128);
   }

   public int priceLaser() {
      return this.priceLaser;
   }

   public void cyclePriceLaser() {
      this.priceLaser = next(PRICE_MID, this.priceLaser, 10);
   }

   public int priceResource() {
      return this.priceResource;
   }

   public void cyclePriceResource() {
      this.priceResource = next(PRICE_MID, this.priceResource, 20);
   }

   public boolean catchUp() {
      return this.catchUp;
   }

   public void toggleCatchUp() {
      this.catchUp = !this.catchUp;
   }

   public int catchGap() {
      return this.catchGap;
   }

   public void cycleCatchGap() {
      this.catchGap = next(GAPS, this.catchGap, 20);
   }

   public int firstTriggerMinutes() {
      return this.firstTriggerMinutes;
   }

   public void cycleFirstTrigger() {
      this.firstTriggerMinutes = next(FIRST_TRIGGER, this.firstTriggerMinutes, 5);
   }

   public int assistIntervalMinutes() {
      return this.assistIntervalMinutes;
   }

   public void cycleAssistInterval() {
      this.assistIntervalMinutes = next(ASSIST_INTERVAL, this.assistIntervalMinutes, 2);
   }

   public int assistWool() {
      return this.assistWool;
   }

   public void cycleAssistWool() {
      this.assistWool = next(ASSIST_WOOL, this.assistWool, 16);
   }

   public int speedSeconds() {
      return this.speedSeconds;
   }

   public void cycleSpeedSeconds() {
      this.speedSeconds = next(SPEED_SECS, this.speedSeconds, 10);
   }

   public int occupyBoostSeconds() {
      return this.occupyBoostSeconds;
   }

   public void cycleOccupyBoost() {
      this.occupyBoostSeconds = next(OCCUPY_BOOST_SECS, this.occupyBoostSeconds, 60);
   }

   public WinMode winMode() {
      return this.winMode;
   }

   public void cycleWinMode() {
      this.winMode = this.winMode.next();
   }

   public int targetScore() {
      return this.targetScore;
   }

   public void cycleTargetScore() {
      this.targetScore = next(TARGET_SCORES, this.targetScore, 100);
   }

   public TieBreak tieBreak() {
      return this.tieBreak;
   }

   public void cycleTieBreak() {
      this.tieBreak = this.tieBreak.next();
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("durationMinutes", this.durationMinutes);
      data.put("teamCount", this.teamCount);
      data.put("teamSize", this.teamSize);
      data.put("grid", this.grid);
      data.put("arenaShape", this.arenaShape.name());
      data.put("occupySeconds", this.occupySeconds);
      data.put("stealSeconds", this.stealSeconds);
      data.put("scoreInterval", this.scoreInterval);
      data.put("scorePerPlot", this.scorePerPlot);
      data.put("centerMultiplier", this.centerMultiplier);
      data.put("initialPlots", this.initialPlots);
      data.put("mineCount", this.mineCount);
      data.put("mineRefreshSeconds", this.mineRefreshSeconds);
      data.put("mineAmount", this.mineAmount);
      data.put("initialWool", this.initialWool);
      data.put("holdWoolBoost", this.holdWoolBoost);
      data.put("startWeapon", this.startWeapon.name());
      data.put("killScore", this.killScore);
      data.put("deathScore", this.deathScore);
      data.put("respawnInvuln", this.respawnInvuln);
      data.put("woolDrop", this.woolDrop.name());
      data.put("safeRadius", this.safeRadius);
      data.put("placeMode", this.placeMode.name());
      data.put("breakTenths", this.breakTenths);
      data.put("breakDropWool", this.breakDropWool);
      data.put("maxBuildHeight", this.maxBuildHeight);
      data.put("buildRegion", this.buildRegion.name());
      data.put("priceStone", this.priceStone);
      data.put("priceIron", this.priceIron);
      data.put("priceBow", this.priceBow);
      data.put("priceShield", this.priceShield);
      data.put("priceSpeed", this.priceSpeed);
      data.put("priceJump", this.priceJump);
      data.put("priceHeal", this.priceHeal);
      data.put("priceDiamond", this.priceDiamond);
      data.put("priceDefense", this.priceDefense);
      data.put("priceTnt", this.priceTnt);
      data.put("priceRepair", this.priceRepair);
      data.put("priceNuke", this.priceNuke);
      data.put("priceLaser", this.priceLaser);
      data.put("priceResource", this.priceResource);
      data.put("catchUp", this.catchUp);
      data.put("catchGap", this.catchGap);
      data.put("firstTriggerMinutes", this.firstTriggerMinutes);
      data.put("assistIntervalMinutes", this.assistIntervalMinutes);
      data.put("assistWool", this.assistWool);
      data.put("speedSeconds", this.speedSeconds);
      data.put("occupyBoostSeconds", this.occupyBoostSeconds);
      data.put("winMode", this.winMode.name());
      data.put("targetScore", this.targetScore);
      data.put("tieBreak", this.tieBreak.name());
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.durationMinutes = clampCycle(DURATIONS, SettingsIo.asInt(data, "durationMinutes", this.durationMinutes), 10);
      this.teamCount = clampCycle(TEAM_COUNTS, SettingsIo.asInt(data, "teamCount", this.teamCount), 2);
      this.teamSize = clampCycle(TEAM_SIZES, SettingsIo.asInt(data, "teamSize", this.teamSize), 2);
      this.grid = clampCycle(GRIDS, SettingsIo.asInt(data, "grid", this.grid), 7);
      this.arenaShape = ArenaShape.fromName(SettingsIo.asString(data, "arenaShape", this.arenaShape.name()));
      this.occupySeconds = clampCycle(OCCUPY, SettingsIo.asInt(data, "occupySeconds", this.occupySeconds), 3);
      this.stealSeconds = clampCycle(STEAL, SettingsIo.asInt(data, "stealSeconds", this.stealSeconds), 5);
      this.scoreInterval = clampCycle(SCORE_INTERVALS, SettingsIo.asInt(data, "scoreInterval", this.scoreInterval), 10);
      this.scorePerPlot = clampCycle(SCORE_VALUES, SettingsIo.asInt(data, "scorePerPlot", this.scorePerPlot), 1);
      this.centerMultiplier = clampCycle(CENTER_MULT, SettingsIo.asInt(data, "centerMultiplier", this.centerMultiplier), 2);
      this.initialPlots = clampCycle(INITIAL_PLOTS, SettingsIo.asInt(data, "initialPlots", this.initialPlots), 3);
      this.mineCount = clampCycle(MINE_COUNTS, SettingsIo.asInt(data, "mineCount", this.mineCount), 6);
      this.mineRefreshSeconds = clampCycle(MINE_REFRESH, SettingsIo.asInt(data, "mineRefreshSeconds", this.mineRefreshSeconds), 30);
      this.mineAmount = clampCycle(MINE_AMOUNTS, SettingsIo.asInt(data, "mineAmount", this.mineAmount), 5);
      this.initialWool = clampCycle(INITIAL_WOOL, SettingsIo.asInt(data, "initialWool", this.initialWool), 3);
      this.holdWoolBoost = clampCycle(HOLD_BOOST, SettingsIo.asInt(data, "holdWoolBoost", this.holdWoolBoost), 2);
      this.startWeapon = StartWeapon.fromName(SettingsIo.asString(data, "startWeapon", this.startWeapon.name()));
      this.killScore = clampCycle(KILL_SCORES, SettingsIo.asInt(data, "killScore", this.killScore), 2);
      this.deathScore = clampCycle(DEATH_SCORES, SettingsIo.asInt(data, "deathScore", this.deathScore), -1);
      this.respawnInvuln = clampCycle(INVULN, SettingsIo.asInt(data, "respawnInvuln", this.respawnInvuln), 5);
      this.woolDrop = WoolDrop.fromName(SettingsIo.asString(data, "woolDrop", this.woolDrop.name()));
      this.safeRadius = clampCycle(SAFE, SettingsIo.asInt(data, "safeRadius", this.safeRadius), 3);
      this.placeMode = PlaceMode.fromName(SettingsIo.asString(data, "placeMode", this.placeMode.name()));
      this.breakTenths = clampCycle(BREAK_TENTHS, SettingsIo.asInt(data, "breakTenths", this.breakTenths), 20);
      this.breakDropWool = SettingsIo.asBool(data, "breakDropWool", this.breakDropWool);
      this.maxBuildHeight = clampCycle(BUILD_HEIGHTS, SettingsIo.asInt(data, "maxBuildHeight", this.maxBuildHeight), 10);
      this.buildRegion = BuildRegion.fromName(SettingsIo.asString(data, "buildRegion", this.buildRegion.name()));
      this.priceStone = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceStone", this.priceStone), 5);
      this.priceIron = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceIron", this.priceIron), 12);
      this.priceBow = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceBow", this.priceBow), 16);
      this.priceShield = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceShield", this.priceShield), 6);
      this.priceSpeed = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceSpeed", this.priceSpeed), 4);
      this.priceJump = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceJump", this.priceJump), 3);
      this.priceHeal = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceHeal", this.priceHeal), 5);
      this.priceDiamond = clampCycle(PRICE_HIGH, SettingsIo.asInt(data, "priceDiamond", this.priceDiamond), 25);
      this.priceDefense = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceDefense", this.priceDefense), 15);
      this.priceTnt = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceTnt", this.priceTnt), 2);
      this.priceRepair = clampCycle(PRICE_LOW, SettingsIo.asInt(data, "priceRepair", this.priceRepair), 3);
      this.priceNuke = clampCycle(PRICE_NUKE, SettingsIo.asInt(data, "priceNuke", this.priceNuke), 128);
      this.priceLaser = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceLaser", this.priceLaser), 10);
      this.priceResource = clampCycle(PRICE_MID, SettingsIo.asInt(data, "priceResource", this.priceResource), 20);
      this.catchUp = SettingsIo.asBool(data, "catchUp", this.catchUp);
      this.catchGap = clampCycle(GAPS, SettingsIo.asInt(data, "catchGap", this.catchGap), 20);
      this.firstTriggerMinutes = clampCycle(FIRST_TRIGGER, SettingsIo.asInt(data, "firstTriggerMinutes", this.firstTriggerMinutes), 5);
      this.assistIntervalMinutes = clampCycle(ASSIST_INTERVAL, SettingsIo.asInt(data, "assistIntervalMinutes", this.assistIntervalMinutes), 2);
      this.assistWool = clampCycle(ASSIST_WOOL, SettingsIo.asInt(data, "assistWool", this.assistWool), 16);
      this.speedSeconds = clampCycle(SPEED_SECS, SettingsIo.asInt(data, "speedSeconds", this.speedSeconds), 10);
      this.occupyBoostSeconds = clampCycle(OCCUPY_BOOST_SECS, SettingsIo.asInt(data, "occupyBoostSeconds", this.occupyBoostSeconds), 60);
      this.winMode = WinMode.fromName(SettingsIo.asString(data, "winMode", this.winMode.name()));
      this.targetScore = clampCycle(TARGET_SCORES, SettingsIo.asInt(data, "targetScore", this.targetScore), 100);
      this.tieBreak = TieBreak.fromName(SettingsIo.asString(data, "tieBreak", this.tieBreak.name()));
   }

   static int next(int[] cycle, int current, int fallback) {
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return fallback;
   }

   private static int clampCycle(int[] cycle, int current, int fallback) {
      for (int value : cycle) {
         if (value == current) {
            return current;
         }
      }
      return fallback;
   }
}
