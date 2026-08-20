package net.exmo.sreGame.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.buildrun.YouBuildRunMiniGame;
import net.exmo.sreGame.games.buildwar.BuildWarMiniGame;
import net.exmo.sreGame.games.caveguess.CaveGuessersMiniGame;
import net.exmo.sreGame.games.chicken.ChickenHorseMiniGame;
import net.exmo.sreGame.games.dontdo.DontDoMiniGame;
import net.exmo.sreGame.games.dig.DigToDeathMiniGame;
import net.exmo.sreGame.games.dodgeball.DodgeballMiniGame;
import net.exmo.sreGame.games.draw.DrawGuessMiniGame;
import net.exmo.sreGame.games.draw.DrawWarMiniGame;
import net.exmo.sreGame.games.fakehuman.FakeHumanMiniGame;
import net.exmo.sreGame.games.fraud.FraudMasterMiniGame;
import net.exmo.sreGame.games.luckypillar.LuckyPillarMiniGame;
import net.exmo.sreGame.games.pillarpummel.PillarPummelMiniGame;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMiniGame;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.games.youguess.YouGuessMiniGame;
import net.minecraft.server.level.ServerPlayer;

public final class SettingsProfiles {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final TypeToken<Map<String, Map<String, Object>>> TYPE = new TypeToken<>() {
   };

   private final Path dir;

   public SettingsProfiles(Path configDir) {
      this.dir = configDir.resolve("player-prefs");
   }

   public void load() {
      try {
         Files.createDirectories(this.dir);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to create player-prefs dir", e);
      }
   }

   public void save(ServerPlayer player, GameRoom room) {
      if (player == null || room == null) {
         return;
      }
      Map<String, Object> snapshot = snapshotOf(room);
      if (snapshot == null) {
         return;
      }
      Map<String, Map<String, Object>> file = this.read(player.getUUID());
      file.put(room.miniGameId(), snapshot);
      this.write(player.getUUID(), file);
   }

   public boolean load(ServerPlayer player, GameRoom room) {
      if (player == null || room == null) {
         return false;
      }
      Map<String, Map<String, Object>> file = this.read(player.getUUID());
      Map<String, Object> data = file.get(room.miniGameId());
      if (data == null || data.isEmpty()) {
         return false;
      }
      applyTo(room, data);
      return true;
   }

   public boolean has(UUID player, String miniGameId) {
      return this.read(player).containsKey(miniGameId);
   }

   private Map<String, Map<String, Object>> read(UUID player) {
      Path file = this.file(player);
      if (!Files.exists(file)) {
         return new LinkedHashMap<>();
      }
      try {
         Map<String, Map<String, Object>> data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE.getType());
         return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
      } catch (Exception e) {
         SreGame.LOGGER.warn("Failed to read settings profile for {}", player, e);
         return new LinkedHashMap<>();
      }
   }

   private void write(UUID player, Map<String, Map<String, Object>> data) {
      try {
         Files.createDirectories(this.dir);
         Files.writeString(this.file(player), GSON.toJson(data), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save settings profile for {}", player, e);
      }
   }

   private Path file(UUID player) {
      return this.dir.resolve(player.toString() + ".json");
   }

   static Map<String, Object> snapshotOf(GameRoom room) {
      String id = room.miniGameId();
      if (BuildWarMiniGame.ID.equals(id) || DrawWarMiniGame.ID.equals(id)) {
         return room.buildWarSettings().snapshot();
      }
      if (YouGuessMiniGame.ID.equals(id) || DrawGuessMiniGame.ID.equals(id)) {
         return room.youGuessSettings().snapshot();
      }
      if (ChickenHorseMiniGame.ID.equals(id)) {
         return room.chickenHorseSettings().snapshot();
      }
      if (DontDoMiniGame.ID.equals(id)) {
         return room.dontDoSettings().snapshot();
      }
      if (FakeHumanMiniGame.ID.equals(id)) {
         return room.fakeHumanSettings().snapshot();
      }
      if (CaveGuessersMiniGame.ID.equals(id)) {
         return room.caveSettings().snapshot();
      }
      if (FraudMasterMiniGame.ID.equals(id)) {
         return room.fraudSettings().snapshot();
      }
      if (LuckyPillarMiniGame.ID.equals(id)) {
         return room.luckyPillarSettings().snapshot();
      }
      if (PillarPummelMiniGame.ID.equals(id)) {
         return room.pillarPummelSettings().snapshot();
      }
      if (DodgeballMiniGame.ID.equals(id)) {
         return room.dodgeballSettings().snapshot();
      }
      if (DigToDeathMiniGame.ID.equals(id)) {
         return room.digToDeathSettings().snapshot();
      }
      if (YouBuildRunMiniGame.ID.equals(id)) {
         return room.youBuildRunSettings().snapshot();
      }
      if (PushTheButtonMiniGame.ID.equals(id)) {
         return room.pushTheButtonSettings().snapshot();
      }
      return room.duelSettings().snapshot();
   }

   static void applyTo(GameRoom room, Map<String, Object> data) {
      String id = room.miniGameId();
      if (BuildWarMiniGame.ID.equals(id) || DrawWarMiniGame.ID.equals(id)) {
         room.buildWarSettings().apply(data);
      } else if (YouGuessMiniGame.ID.equals(id) || DrawGuessMiniGame.ID.equals(id)) {
         room.youGuessSettings().apply(data);
      } else if (ChickenHorseMiniGame.ID.equals(id)) {
         room.chickenHorseSettings().apply(data);
      } else if (DontDoMiniGame.ID.equals(id)) {
         room.dontDoSettings().apply(data);
      } else if (FakeHumanMiniGame.ID.equals(id)) {
         room.fakeHumanSettings().apply(data);
      } else if (CaveGuessersMiniGame.ID.equals(id)) {
         room.caveSettings().apply(data);
      } else if (FraudMasterMiniGame.ID.equals(id)) {
         room.fraudSettings().apply(data);
      } else if (LuckyPillarMiniGame.ID.equals(id)) {
         room.luckyPillarSettings().apply(data);
      } else if (PillarPummelMiniGame.ID.equals(id)) {
         room.pillarPummelSettings().apply(data);
      } else if (DodgeballMiniGame.ID.equals(id)) {
         room.dodgeballSettings().apply(data);
      } else if (DigToDeathMiniGame.ID.equals(id)) {
         room.digToDeathSettings().apply(data);
      } else if (YouBuildRunMiniGame.ID.equals(id)) {
         room.youBuildRunSettings().apply(data);
      } else if (PushTheButtonMiniGame.ID.equals(id)) {
         room.pushTheButtonSettings().apply(data);
      } else {
         room.duelSettings().apply(data);
      }
   }
}
