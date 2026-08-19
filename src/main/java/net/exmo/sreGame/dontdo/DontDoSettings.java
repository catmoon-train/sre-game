package net.exmo.sreGame.dontdo;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class DontDoSettings {
   private static final int[] LIVES = {10, 15, 20, 25, 30};
   private static final int[] RULE_SECONDS = {60, 90, 120};
   private static final int[] TEAM_SIZES = {2, 3, 4};
   private static final int[] EVENT_SECONDS = {60, 90, 120};

   private int lives = 15;
   private int ruleSeconds = 90;
   private boolean teams;
   private int teamSize = 2;
   private boolean randomEvents;
   private int eventSeconds = 90;

   public int lives() {
      return this.lives;
   }

   public int ruleSeconds() {
      return this.ruleSeconds;
   }

   public boolean teams() {
      return this.teams;
   }

   public int teamSize() {
      return this.teamSize;
   }

   public boolean randomEvents() {
      return this.randomEvents;
   }

   public int eventSeconds() {
      return this.eventSeconds;
   }

   public String teamsLabel() {
      return this.teams ? "开" : "关";
   }

   public String eventsLabel() {
      return this.randomEvents ? "开" : "关";
   }

   public void cycleLives() {
      this.lives = next(LIVES, this.lives, 15);
   }

   public void cycleRuleSeconds() {
      this.ruleSeconds = next(RULE_SECONDS, this.ruleSeconds, 90);
   }

   public void toggleTeams() {
      this.teams = !this.teams;
   }

   public void cycleTeamSize() {
      this.teamSize = next(TEAM_SIZES, this.teamSize, 2);
   }

   public void toggleEvents() {
      this.randomEvents = !this.randomEvents;
   }

   public void cycleEventSeconds() {
      this.eventSeconds = next(EVENT_SECONDS, this.eventSeconds, 90);
   }

   public int minPlayersForTeams() {
      return Math.max(4, this.teamSize * 2);
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("lives", this.lives);
      data.put("ruleSeconds", this.ruleSeconds);
      data.put("teams", this.teams);
      data.put("teamSize", this.teamSize);
      data.put("randomEvents", this.randomEvents);
      data.put("eventSeconds", this.eventSeconds);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.lives = SettingsIo.asInt(data, "lives", this.lives);
      this.ruleSeconds = SettingsIo.asInt(data, "ruleSeconds", this.ruleSeconds);
      this.teams = SettingsIo.asBool(data, "teams", this.teams);
      this.teamSize = SettingsIo.asInt(data, "teamSize", this.teamSize);
      this.randomEvents = SettingsIo.asBool(data, "randomEvents", this.randomEvents);
      this.eventSeconds = SettingsIo.asInt(data, "eventSeconds", this.eventSeconds);
   }

   private static int next(int[] cycle, int current, int fallback) {
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return fallback;
   }
}
