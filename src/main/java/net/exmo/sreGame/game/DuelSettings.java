package net.exmo.sreGame.game;

import com.mcrpvp.duel.fabric.api.DuelApi;
import com.mcrpvp.duel.fabric.queue.QueueType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.profile.SettingsIo;

public final class DuelSettings {
   private String gamemode;
   private QueueType queueType = QueueType.UNRANKED;
   private int rounds = 2;
   private final List<UUID> team1 = new ArrayList<>();
   private final List<UUID> team2 = new ArrayList<>();

   public DuelSettings() {
      this.gamemode = DuelApi.firstEnabledGamemode();
      if (this.gamemode != null) {
         this.rounds = DuelApi.defaultRounds(this.gamemode);
      }
   }

   public String gamemode() {
      return this.gamemode;
   }

   public void setGamemode(String gamemode) {
      this.gamemode = gamemode;
      if (gamemode != null) {
         this.rounds = DuelApi.defaultRounds(gamemode);
      }
   }

   public QueueType queueType() {
      return this.queueType;
   }

   public void setQueueType(QueueType queueType) {
      this.queueType = queueType;
   }

   public int rounds() {
      return this.rounds;
   }

   public void setRounds(int rounds) {
      this.rounds = Math.max(1, Math.min(10, rounds));
   }

   public List<UUID> team1() {
      return this.team1;
   }

   public List<UUID> team2() {
      return this.team2;
   }

   public int teamOf(UUID uuid) {
      if (this.team1.contains(uuid)) {
         return 1;
      }
      if (this.team2.contains(uuid)) {
         return 2;
      }
      return 0;
   }

   public void remove(UUID uuid) {
      this.team1.remove(uuid);
      this.team2.remove(uuid);
   }

   public void assign(UUID uuid, int team) {
      this.remove(uuid);
      if (team == 1) {
         this.team1.add(uuid);
      } else if (team == 2) {
         this.team2.add(uuid);
      }
   }

   public void assignToSmaller(UUID uuid) {
      this.remove(uuid);
      if (this.team1.size() <= this.team2.size()) {
         this.team1.add(uuid);
      } else {
         this.team2.add(uuid);
      }
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("gamemode", this.gamemode);
      data.put("queueType", this.queueType.name());
      data.put("rounds", this.rounds);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      String mode = SettingsIo.asString(data, "gamemode", this.gamemode);
      if (mode != null && DuelApi.isModeEnabled(mode)) {
         this.gamemode = mode;
      }
      try {
         this.queueType = QueueType.valueOf(SettingsIo.asString(data, "queueType", this.queueType.name()));
      } catch (IllegalArgumentException ignored) {
      }
      this.setRounds(SettingsIo.asInt(data, "rounds", this.rounds));
   }

   public void swapTeam(UUID uuid) {
      int current = this.teamOf(uuid);
      if (current == 1) {
         this.assign(uuid, 2);
      } else if (current == 2) {
         this.assign(uuid, 1);
      } else {
         this.assignToSmaller(uuid);
      }
   }
}
