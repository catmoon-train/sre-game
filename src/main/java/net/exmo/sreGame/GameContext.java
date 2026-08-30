package net.exmo.sreGame;

import java.nio.file.Path;
import java.util.UUID;
import net.exmo.sreGame.games.buildwar.BuildWarManager;
import net.exmo.sreGame.games.buildwar.PlotManager;
import net.exmo.sreGame.games.buildwar.WordBank;
import net.exmo.sreGame.games.caveguess.CaveGuessersManager;
import net.exmo.sreGame.games.caveguess.CaveWordBank;
import net.exmo.sreGame.games.chicken.ChickenHorseManager;
import net.exmo.sreGame.config.GameConfig;
import net.exmo.sreGame.games.dontdo.DontDoManager;
import net.exmo.sreGame.games.buildrun.YouBuildRunManager;
import net.exmo.sreGame.games.dig.DigToDeathManager;
import net.exmo.sreGame.games.dodgeball.DodgeballManager;
import net.exmo.sreGame.games.football.FootballManager;
import net.exmo.sreGame.games.fakehuman.FakeHumanManager;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallManager;
import net.exmo.sreGame.games.fraud.FraudMasterManager;
import net.exmo.sreGame.game.MiniGameRegistry;
import net.exmo.sreGame.games.luckypillar.LuckyPillarManager;
import net.exmo.sreGame.games.nametagwar.NameTagWarManager;
import net.exmo.sreGame.games.parkour.ParkourManager;
import net.exmo.sreGame.games.pillarpummel.PillarPummelManager;
import net.exmo.sreGame.profile.SettingsProfiles;
import net.exmo.sreGame.player.PlayerWhitelist;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonManager;
import net.exmo.sreGame.games.rhythm.RhythmManager;
import net.exmo.sreGame.games.skyworld.SkyWorldManager;
import net.exmo.sreGame.games.blockedcombat.BlockedCombatManager;
import net.exmo.sreGame.games.tunnelrats.TunnelRatsManager;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleManager;
import net.exmo.sreGame.ai.AiConfig;
import net.exmo.sreGame.ai.AiService;
import net.exmo.sreGame.command.WhitelistCommands;
import net.exmo.sreGame.room.RoomManager;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.words.WordLibrary;
import net.exmo.sreGame.games.youguess.YouGuessManager;
import net.exmo.sreGame.games.partygames.PartyGameManager;
import net.exmo.sreGame.games.hypixelsays.HypixelSaysManager;
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
   private final DodgeballManager dodgeball = new DodgeballManager(this);
   private final FootballManager football = new FootballManager(this);
   private final DigToDeathManager digToDeath = new DigToDeathManager(this);
   private final YouBuildRunManager youBuildRun = new YouBuildRunManager(this);
   private final PushTheButtonManager pushTheButton = new PushTheButtonManager(this);
   private final SkyWorldManager skyWorld = new SkyWorldManager(this);
   private final BlockedCombatManager blockedCombat = new BlockedCombatManager(this);
   private final TunnelRatsManager tunnelRats = new TunnelRatsManager(this);
   private final ParkourManager parkour = new ParkourManager(this);
   private final SituationPuzzleManager situationPuzzle = new SituationPuzzleManager(this);
   private final NameTagWarManager nameTagWar = new NameTagWarManager(this);
   private final FillInTheWallManager fillInTheWall = new FillInTheWallManager(this);
   private final RhythmManager rhythm = new RhythmManager(this);
   private final PartyGameManager partyGames = new PartyGameManager(this);
   private final HypixelSaysManager hypixelSays = new HypixelSaysManager(this);
   private final AiConfig aiConfig = new AiConfig(this.configDir);
   private final AiService aiService = new AiService(this.aiConfig);
   private final SettingsProfiles profiles = new SettingsProfiles(this.configDir);
   private final PlayerWhitelist whitelist = new PlayerWhitelist(this.configDir);
   private MinecraftServer server;
   private int whitelistCheckTicks;

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

   public DodgeballManager dodgeball() {
      return this.dodgeball;
   }

   public FootballManager football() {
      return this.football;
   }

   public DigToDeathManager digToDeath() {
      return this.digToDeath;
   }

   public YouBuildRunManager youBuildRun() {
      return this.youBuildRun;
   }

   public PushTheButtonManager pushTheButton() {
      return this.pushTheButton;
   }

   public SkyWorldManager skyWorld() {
      return this.skyWorld;
   }

   public BlockedCombatManager blockedCombat() {
      return this.blockedCombat;
   }

   public TunnelRatsManager tunnelRats() {
      return this.tunnelRats;
   }

   public ParkourManager parkour() {
      return this.parkour;
   }

   public SituationPuzzleManager situationPuzzle() {
      return this.situationPuzzle;
   }

   public NameTagWarManager nameTagWar() {
      return this.nameTagWar;
   }

   public FillInTheWallManager fillInTheWall() {
      return this.fillInTheWall;
   }

   public RhythmManager rhythm() {
      return this.rhythm;
   }

   public PartyGameManager partyGames() {
      return this.partyGames;
   }

   public HypixelSaysManager hypixelSays() {
      return this.hypixelSays;
   }

   public Path configDir() {
      return this.configDir;
   }

   public AiConfig aiConfig() {
      return this.aiConfig;
   }

   public AiService ai() {
      return this.aiService;
   }

   public SettingsProfiles profiles() {
      return this.profiles;
   }

   public PlayerWhitelist whitelist() {
      return this.whitelist;
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
      this.whitelist.load();
      this.luckyPillar.items().load();
      this.plots.pregen();
      this.fakeHuman.houses().pregen();
      this.chickenHorse.tracks().pregen();
      this.luckyPillar.arenas().pregen();
      this.pillarPummel.arenas().pregen();
      this.dodgeball.arenas().pregen();
      this.football.arenas().pregen();
      this.digToDeath.arenas().pregen();
      this.youBuildRun.tracks().pregen();
      this.pushTheButton.ships().pregen();
      this.skyWorld.arenas().pregen();
      this.blockedCombat.arenas().pregen();
      this.tunnelRats.arenas().pregen();
      this.nameTagWar.arenas().pregen();
      this.fillInTheWall.arenas().pregen();
      this.parkour.load();
      this.rhythm.load();
      this.partyGames.load();
      this.hypixelSays.pregen();
      this.aiConfig.load();
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
      this.dodgeball.endAll();
      this.football.endAll();
      this.digToDeath.endAll();
      this.youBuildRun.endAll();
      this.pushTheButton.endAll();
      this.skyWorld.endAll();
      this.blockedCombat.endAll();
      this.tunnelRats.endAll();
      this.parkour.endAll();
      this.situationPuzzle.endAll();
      this.nameTagWar.endAll();
      this.fillInTheWall.endAll();
      this.rhythm.endAll();
      this.partyGames.endAll();
      this.hypixelSays.endAll();
      this.rooms.disbandAll();
      this.server = null;
   }

   public void tick() {
      if (++this.whitelistCheckTicks >= 20) {
         this.whitelistCheckTicks = 0;
         WhitelistCommands.enforceOnline(this);
      }
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
      this.dodgeball.tick();
      this.football.tick();
      this.digToDeath.tick();
      this.youBuildRun.tick();
      this.pushTheButton.tick();
      this.skyWorld.tick();
      this.blockedCombat.tick();
      this.tunnelRats.tick();
      this.parkour.tick();
      this.situationPuzzle.tick();
      this.nameTagWar.tick();
      this.fillInTheWall.tick();
      this.rhythm.tick();
      this.partyGames.tick();
      this.hypixelSays.tick();
      net.exmo.sreGame.games.draw.DrawKit.tick(this);
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
