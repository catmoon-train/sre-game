package net.exmo.sreGame;

import java.nio.file.Path;
import java.util.UUID;
import net.exmo.sreGame.buildwar.BuildWarManager;
import net.exmo.sreGame.buildwar.PlotManager;
import net.exmo.sreGame.buildwar.WordBank;
import net.exmo.sreGame.caveguess.CaveGuessersManager;
import net.exmo.sreGame.caveguess.CaveWordBank;
import net.exmo.sreGame.chicken.ChickenHorseManager;
import net.exmo.sreGame.config.GameConfig;
import net.exmo.sreGame.dontdo.DontDoManager;
import net.exmo.sreGame.fakehuman.FakeHumanManager;
import net.exmo.sreGame.fraud.FraudMasterManager;
import net.exmo.sreGame.game.MiniGameRegistry;
import net.exmo.sreGame.luckypillar.LuckyPillarManager;
import net.exmo.sreGame.pillarpummel.PillarPummelManager;
import net.exmo.sreGame.profile.SettingsProfiles;
import net.exmo.sreGame.room.RoomManager;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.words.WordLibrary;
import net.exmo.sreGame.youguess.YouGuessManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class GameContext {
   private final Path configDir = Path.of("config", "sre-game");
   private final GameConfig config = new GameConfig(this.configDir);
   private final WordBank words = new WordBank(this.configDir);
   private final CaveWordBank caveWords = new CaveWordBank(this.configDir);
   private final WordLibrary library = new WordLibrary(this.configDir, this.words);
   private final RoomManager rooms = new RoomManager(this);
   private final MiniGameRegistry games = new MiniGameRegistry();
   private final PlotManager plots = new PlotManager(this);
   private final BuildWarManager buildWar = new BuildWarManager(this);
   private final YouGuessManager youGuess = new YouGuessManager(this);
   private final FakeHumanManager fakeHuman = new FakeHumanManager(this);
   private final FraudMasterManager fraudMaster = new FraudMasterManager(this);
   private final CaveGuessersManager caveGuess = new CaveGuessersManager(this);
   private final ChickenHorseManager chickenHorse = new ChickenHorseManager(this);
   private final DontDoManager dontDo = new DontDoManager(this);
   private final LuckyPillarManager luckyPillar = new LuckyPillarManager(this);
   private final PillarPummelManager pillarPummel = new PillarPummelManager(this);
   private final SettingsProfiles profiles = new SettingsProfiles(this.configDir);
   private MinecraftServer server;

   public GameConfig config() {
      return this.config;
   }

   public WordBank words() {
      return this.words;
   }

   public CaveWordBank caveWords() {
      return this.caveWords;
   }

   public RoomManager rooms() {
      return this.rooms;
   }

   public MiniGameRegistry games() {
      return this.games;
   }

   public PlotManager plots() {
      return this.plots;
   }

   public BuildWarManager buildWar() {
      return this.buildWar;
   }

   public WordLibrary library() {
      return this.library;
   }

   public YouGuessManager youGuess() {
      return this.youGuess;
   }

   public FakeHumanManager fakeHuman() {
      return this.fakeHuman;
   }

   public FraudMasterManager fraudMaster() {
      return this.fraudMaster;
   }

   public CaveGuessersManager caveGuess() {
      return this.caveGuess;
   }

   public ChickenHorseManager chickenHorse() {
      return this.chickenHorse;
   }

   public DontDoManager dontDo() {
      return this.dontDo;
   }

   public LuckyPillarManager luckyPillar() {
      return this.luckyPillar;
   }

   public PillarPummelManager pillarPummel() {
      return this.pillarPummel;
   }

   public SettingsProfiles profiles() {
      return this.profiles;
   }

   public MinecraftServer server() {
      return this.server;
   }

   public void onServerStarted(MinecraftServer server) {
      this.server = server;
      this.config.load();
      this.library.load();
      this.caveWords.load();
      this.profiles.load();
      this.luckyPillar.items().load();
      this.plots.pregen();
      this.fakeHuman.houses().pregen();
      this.chickenHorse.tracks().pregen();
      this.luckyPillar.arenas().pregen();
      this.pillarPummel.arenas().pregen();
   }

   public void onServerStopping() {
      this.buildWar.endAll();
      this.youGuess.endAll();
      this.fakeHuman.endAll();
      this.fraudMaster.endAll();
      this.caveGuess.endAll();
      this.chickenHorse.endAll();
      this.dontDo.endAll();
      this.luckyPillar.endAll();
      this.pillarPummel.endAll();
      this.rooms.disbandAll();
      this.server = null;
   }

   public void tick() {
      this.plots.tick();
      this.buildWar.tick();
      this.youGuess.tick();
      this.fakeHuman.tick();
      this.fraudMaster.tick();
      this.caveGuess.tick();
      this.chickenHorse.tick();
      this.dontDo.tick();
      this.luckyPillar.tick();
      this.pillarPummel.tick();
      net.exmo.sreGame.draw.DrawKit.tick(this);
   }

   public ServerPlayer player(UUID uuid) {
      return this.server == null || uuid == null ? null : this.server.getPlayerList().getPlayer(uuid);
   }

   public String name(UUID uuid) {
      ServerPlayer player = this.player(uuid);
      return player != null ? player.getGameProfile().getName() : "离线玩家";
   }

   public void send(ServerPlayer player, String message) {
      if (player != null) {
         player.sendSystemMessage(TextUtil.color(message));
      }
   }

   public void broadcast(net.exmo.sreGame.room.GameRoom room, String message) {
      for (UUID uuid : room.members()) {
         this.send(this.player(uuid), message);
      }
   }
}
