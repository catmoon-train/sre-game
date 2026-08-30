package net.exmo.sreGame.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.exmo.sreGame.games.buildrun.YouBuildRunMiniGame;
import net.exmo.sreGame.games.buildrun.YouBuildRunSettings;
import net.exmo.sreGame.games.buildwar.BuildWarMiniGame;
import net.exmo.sreGame.games.buildwar.BuildWarSettings;
import net.exmo.sreGame.games.caveguess.CaveGuessersMiniGame;
import net.exmo.sreGame.games.caveguess.CaveGuessersSettings;
import net.exmo.sreGame.games.chicken.ChickenHorseMiniGame;
import net.exmo.sreGame.games.chicken.ChickenHorseSettings;
import net.exmo.sreGame.games.dontdo.DontDoMiniGame;
import net.exmo.sreGame.games.dontdo.DontDoSettings;
import net.exmo.sreGame.games.dig.DigToDeathMiniGame;
import net.exmo.sreGame.games.dig.DigToDeathSettings;
import net.exmo.sreGame.games.dodgeball.DodgeballMiniGame;
import net.exmo.sreGame.games.dodgeball.DodgeballSettings;
import net.exmo.sreGame.games.football.FootballMiniGame;
import net.exmo.sreGame.games.draw.DrawGuessMiniGame;
import net.exmo.sreGame.games.draw.DrawWarMiniGame;
import net.exmo.sreGame.games.fakehuman.FakeHumanMiniGame;
import net.exmo.sreGame.games.fakehuman.FakeHumanSettings;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallMiniGame;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallSettings;
import net.exmo.sreGame.games.fraud.FraudMasterMiniGame;
import net.exmo.sreGame.games.fraud.FraudMasterSettings;
import net.exmo.sreGame.game.DuelSettings;
import net.exmo.sreGame.games.luckypillar.LuckyPillarMiniGame;
import net.exmo.sreGame.games.luckypillar.LuckyPillarSettings;
import net.exmo.sreGame.games.nametagwar.NameTagWarMiniGame;
import net.exmo.sreGame.games.nametagwar.NameTagWarSettings;
import net.exmo.sreGame.games.pillarpummel.PillarPummelMiniGame;
import net.exmo.sreGame.games.pillarpummel.PillarPummelSettings;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMiniGame;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonSettings;
import net.exmo.sreGame.games.rhythm.RhythmMiniGame;
import net.exmo.sreGame.games.rhythm.RhythmSettings;
import net.exmo.sreGame.games.skyworld.SkyWorldMiniGame;
import net.exmo.sreGame.games.skyworld.SkyWorldSettings;
import net.exmo.sreGame.games.blockedcombat.BlockedCombatMiniGame;
import net.exmo.sreGame.games.blockedcombat.BlockedCombatSettings;
import net.exmo.sreGame.games.tunnelrats.TunnelRatsMiniGame;
import net.exmo.sreGame.games.tunnelrats.TunnelRatsSettings;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleMiniGame;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleSettings;
import net.exmo.sreGame.games.youguess.YouGuessMiniGame;
import net.exmo.sreGame.games.youguess.YouGuessSettings;
import net.exmo.sreGame.games.partygames.PartyGameSettings;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.hypixelsays.HypixelSaysMiniGame;

public final class GameRoom {
   private final String id;
   private final long createdAt = System.currentTimeMillis();
   private String displayName;
   private UUID host;
   private final List<UUID> members = new CopyOnWriteArrayList<>();
   private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
   private String password;
   private boolean publicRoom = true;
   private boolean autoReady = true;
   /** Whether players return to the MCRPVPDuel lobby spawn after this room's match. */
   private boolean returnToDuelSpawn = true;
   private int maxPlayers = 2;
   private String miniGameId = "mcrpvp_duel";
   private final DuelSettings duelSettings = new DuelSettings();
   private final BuildWarSettings buildWarSettings = new BuildWarSettings();
   private final YouGuessSettings youGuessSettings = new YouGuessSettings();
   private final FraudMasterSettings fraudSettings = new FraudMasterSettings();
   private final FakeHumanSettings fakeHumanSettings = new FakeHumanSettings();
   private final CaveGuessersSettings caveSettings = new CaveGuessersSettings();
   private final ChickenHorseSettings chickenHorseSettings = new ChickenHorseSettings();
   private final DontDoSettings dontDoSettings = new DontDoSettings();
   private final LuckyPillarSettings luckyPillarSettings = new LuckyPillarSettings();
   private final PillarPummelSettings pillarPummelSettings = new PillarPummelSettings();
   private final DodgeballSettings dodgeballSettings = new DodgeballSettings();
   private final DigToDeathSettings digToDeathSettings = new DigToDeathSettings();
   private final YouBuildRunSettings youBuildRunSettings = new YouBuildRunSettings();
   private final PushTheButtonSettings pushTheButtonSettings = new PushTheButtonSettings();
   private final SkyWorldSettings skyWorldSettings = new SkyWorldSettings();
   private final BlockedCombatSettings blockedCombatSettings = new BlockedCombatSettings();
   private final TunnelRatsSettings tunnelRatsSettings = new TunnelRatsSettings();
   private final SituationPuzzleSettings situationPuzzleSettings = new SituationPuzzleSettings();
   private final NameTagWarSettings nameTagWarSettings = new NameTagWarSettings();
   private final FillInTheWallSettings fillInTheWallSettings = new FillInTheWallSettings();
   private final RhythmSettings rhythmSettings = new RhythmSettings();
   private final PartyGameSettings partyGameSettings = new PartyGameSettings();
   private final List<String> activeWords = new CopyOnWriteArrayList<>();
   private String wordPackLabel = "服务器默认";
   private RoomState state = RoomState.WAITING;
   private UUID activeMatchId;
   private RoomChatMode chatMode = RoomChatMode.ROOM_ONLY;

   public GameRoom(String id, String displayName, UUID host) {
      this.id = id;
      this.displayName = displayName;
      this.host = host;
      this.members.add(host);
      this.ready.add(host);
      this.duelSettings.assign(host, 1);
   }

   public String id() {
      return this.id;
   }

   public long createdAt() {
      return this.createdAt;
   }

   public String displayName() {
      return this.displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
   }

   public UUID host() {
      return this.host;
   }

   public void setHost(UUID host) {
      this.host = host;
      this.ready.add(host);
   }

   public boolean isHost(UUID uuid) {
      return this.host.equals(uuid);
   }

   public List<UUID> members() {
      return this.members;
   }

   public int size() {
      return this.members.size();
   }

   public boolean contains(UUID uuid) {
      return this.members.contains(uuid);
   }

   public Set<UUID> ready() {
      return this.ready;
   }

   public boolean isReady(UUID uuid) {
      return this.ready.contains(uuid);
   }

   public boolean allReady() {
      for (UUID member : this.members) {
         if (!this.ready.contains(member)) {
            return false;
         }
      }
      return !this.members.isEmpty();
   }

   public void toggleReady(UUID uuid) {
      if (this.isHost(uuid)) {
         this.ready.add(uuid);
         return;
      }
      if (!this.ready.add(uuid)) {
         this.ready.remove(uuid);
      }
   }

   public void clearReadyExceptHost() {
      this.ready.clear();
      if (this.autoReady) {
         this.ready.addAll(this.members);
      } else {
         this.ready.add(this.host);
      }
   }

   public boolean autoReady() {
      return this.autoReady;
   }

   public void setAutoReady(boolean autoReady) {
      this.autoReady = autoReady;
      if (autoReady) {
         this.ready.addAll(this.members);
      }
   }

   public String password() {
      return this.password;
   }

   public void setPassword(String password) {
      this.password = password == null || password.isBlank() ? null : password;
   }

   public boolean hasPassword() {
      return this.password != null;
   }

   public boolean publicRoom() {
      return this.publicRoom;
   }

   public void setPublicRoom(boolean publicRoom) {
      this.publicRoom = publicRoom;
   }

   public RoomChatMode chatMode() {
      return this.chatMode;
   }

   public void setChatMode(RoomChatMode chatMode) {
      this.chatMode = chatMode == null ? RoomChatMode.ROOM_ONLY : chatMode;
   }

   public int maxPlayers() {
      return this.maxPlayers;
   }

   public void setMaxPlayers(int maxPlayers) {
      this.maxPlayers = Math.max(2, Math.min(120, maxPlayers));
   }

   public String miniGameId() {
      return this.miniGameId;
   }

   public boolean isBuildWar() {
      return BuildWarMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isYouGuess() {
      return YouGuessMiniGame.ID.equals(this.miniGameId);
   }

   public boolean returnToDuelSpawn() {
      return this.returnToDuelSpawn;
   }

   public void setReturnToDuelSpawn(boolean returnToDuelSpawn) {
      this.returnToDuelSpawn = returnToDuelSpawn;
   }

   public boolean isHypixelSays() {
      return HypixelSaysMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isDrawGuess() {
      return DrawGuessMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isDrawWar() {
      return DrawWarMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isYouGuessFamily() {
      return this.isYouGuess() || this.isDrawGuess();
   }

   public boolean isBuildWarFamily() {
      return this.isBuildWar() || this.isDrawWar();
   }

   public boolean isFraudMaster() {
      return FraudMasterMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isFakeHuman() {
      return FakeHumanMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isCaveGuess() {
      return CaveGuessersMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isChickenHorse() {
      return ChickenHorseMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isDontDo() {
      return DontDoMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isLuckyPillar() {
      return LuckyPillarMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isPillarPummel() {
      return PillarPummelMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isDodgeball() {
      return DodgeballMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isFootball() {
      return FootballMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isDigToDeath() {
      return DigToDeathMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isYouBuildRun() {
      return YouBuildRunMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isPushTheButton() {
      return PushTheButtonMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isSkyWorld() {
      return SkyWorldMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isBlockedCombat() {
      return BlockedCombatMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isTunnelRats() {
      return TunnelRatsMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isSituationPuzzle() {
      return SituationPuzzleMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isNameTagWar() {
      return NameTagWarMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isFillInTheWall() {
      return FillInTheWallMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isRhythm() {
      return RhythmMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isQuake() {
      return net.exmo.sreGame.games.quakechasm.QuakeMiniGame.ID.equals(this.miniGameId)
         || net.exmo.sreGame.games.quakechasm.QuakeTDMMiniGame.ID.equals(this.miniGameId)
         || net.exmo.sreGame.games.quakechasm.QuakeCTFMiniGame.ID.equals(this.miniGameId);
   }

   public boolean isPartyGame() {
      return PartyGameType.byId(this.miniGameId) != null;
   }

   public boolean isBuildStyle() {
      return this.isBuildWarFamily() || this.isYouGuessFamily() || this.isFraudMaster() || this.isFakeHuman()
         || this.isCaveGuess() || this.isChickenHorse() || this.isDontDo() || this.isLuckyPillar()
         || this.isPillarPummel() || this.isDodgeball() || this.isDigToDeath() || this.isYouBuildRun()
         || this.isPushTheButton() || this.isSkyWorld() || this.isBlockedCombat() || this.isSituationPuzzle() || this.isNameTagWar()
         || this.isFillInTheWall() || this.isRhythm() || this.isPartyGame();
   }

   public void setMiniGameId(String miniGameId) {
      this.miniGameId = miniGameId;
      if (this.isFraudMaster() || this.isFakeHuman()) {
         if (this.maxPlayers < 4 || this.maxPlayers > 32) {
            this.maxPlayers = 24;
         }
      } else if (this.isChickenHorse()) {
         if (this.maxPlayers > 120) {
            this.maxPlayers = 64;
         }
      } else if (this.isDontDo()) {
         if (this.maxPlayers > 64) {
            this.maxPlayers = 64;
         }
      } else if (this.isLuckyPillar() || this.isDodgeball() || this.isDigToDeath()) {
         if (this.maxPlayers > 64) {
            this.maxPlayers = 64;
         } else if (this.maxPlayers < 2) {
            this.maxPlayers = 8;
         }
      } else if (this.isSkyWorld()) {
         if (this.maxPlayers > 32) {
            this.maxPlayers = 32;
         } else if (this.maxPlayers < 2) {
            this.maxPlayers = 8;
         }
      } else if (this.isBlockedCombat()) {
         if (this.maxPlayers > 24) {
            this.maxPlayers = 24;
         } else if (this.maxPlayers < 1) {
            this.maxPlayers = 4;
         }
      } else if (this.isYouBuildRun()) {
         if (this.maxPlayers > 32) {
            this.maxPlayers = 32;
         } else if (this.maxPlayers < 2) {
            this.maxPlayers = 8;
         }
      } else if (this.isSituationPuzzle()) {
         if (this.situationPuzzleSettings.soloMode()) {
            this.maxPlayers = 1;
         } else {
            if (this.maxPlayers > 64) {
               this.maxPlayers = 64;
            } else if (this.maxPlayers < 2) {
               this.maxPlayers = 8;
            }
         }
      } else if (this.isPushTheButton()) {
         if (this.maxPlayers < 4 || this.maxPlayers > 24) {
            this.maxPlayers = 8;
         }
      } else if (this.isNameTagWar()) {
         if (this.maxPlayers > 64) {
            this.maxPlayers = 64;
         } else if (this.maxPlayers < 2) {
            this.maxPlayers = 8;
         }
      } else if (this.isPillarPummel()) {
         if (this.maxPlayers > 64) {
            this.maxPlayers = 64;
         } else if (this.maxPlayers < 4) {
            this.maxPlayers = 8;
         }
      } else if (this.isFillInTheWall()) {
         if (this.maxPlayers > 32) {
            this.maxPlayers = 32;
         } else if (this.maxPlayers < 1) {
            this.maxPlayers = 4;
         }
      } else if (this.isCaveGuess()) {
         if (this.maxPlayers < 3) {
            this.maxPlayers = 8;
         } else if (this.maxPlayers > 64) {
            this.maxPlayers = 64;
         }
      } else if (this.isRhythm()) {
         if (this.maxPlayers > 4) {
            this.maxPlayers = 4;
         } else if (this.maxPlayers < 1) {
            this.maxPlayers = 1;
         }
      } else if (this.isFootball()) {
         if (this.maxPlayers > 24) {
            this.maxPlayers = 24;
         } else if (this.maxPlayers < 2) {
            this.maxPlayers = 2;
         }
      } else if (this.isPartyGame()) {
         PartyGameType type = PartyGameType.byId(this.miniGameId);
         if (type != null && (this.maxPlayers < 2 || this.maxPlayers > type.maxPlayers())) {
            this.maxPlayers = Math.min(8, type.maxPlayers());
         }
      } else if (this.isBuildStyle() && this.maxPlayers < 3) {
         this.maxPlayers = 8;
      }
   }

   public DuelSettings duelSettings() {
      return this.duelSettings;
   }

   public BuildWarSettings buildWarSettings() {
      return this.buildWarSettings;
   }

   public YouGuessSettings youGuessSettings() {
      return this.youGuessSettings;
   }

   public FraudMasterSettings fraudSettings() {
      return this.fraudSettings;
   }

   public FakeHumanSettings fakeHumanSettings() {
      return this.fakeHumanSettings;
   }

   public CaveGuessersSettings caveSettings() {
      return this.caveSettings;
   }

   public ChickenHorseSettings chickenHorseSettings() {
      return this.chickenHorseSettings;
   }

   public DontDoSettings dontDoSettings() {
      return this.dontDoSettings;
   }

   public LuckyPillarSettings luckyPillarSettings() {
      return this.luckyPillarSettings;
   }

   public PillarPummelSettings pillarPummelSettings() {
      return this.pillarPummelSettings;
   }

   public DodgeballSettings dodgeballSettings() {
      return this.dodgeballSettings;
   }

   public DigToDeathSettings digToDeathSettings() {
      return this.digToDeathSettings;
   }

   public YouBuildRunSettings youBuildRunSettings() {
      return this.youBuildRunSettings;
   }

   public PushTheButtonSettings pushTheButtonSettings() {
      return this.pushTheButtonSettings;
   }

   public SkyWorldSettings skyWorldSettings() {
      return this.skyWorldSettings;
   }

   public BlockedCombatSettings blockedCombatSettings() {
      return this.blockedCombatSettings;
   }

   public TunnelRatsSettings tunnelRatsSettings() {
      return this.tunnelRatsSettings;
   }

   public SituationPuzzleSettings situationPuzzleSettings() {
      return this.situationPuzzleSettings;
   }

   public NameTagWarSettings nameTagWarSettings() {
      return this.nameTagWarSettings;
   }

   public FillInTheWallSettings fillInTheWallSettings() {
      return this.fillInTheWallSettings;
   }

   public RhythmSettings rhythmSettings() {
      return this.rhythmSettings;
   }

   public PartyGameSettings partyGameSettings() {
      return this.partyGameSettings;
   }

   public boolean hasCustomWords() {
      return !this.activeWords.isEmpty();
   }

   public List<String> customWords() {
      return List.copyOf(this.activeWords);
   }

   public String wordPackLabel() {
      return this.wordPackLabel;
   }

   public List<String> resolvedWords(net.exmo.sreGame.GameContext ctx) {
      if (this.activeWords.isEmpty()) {
         return ctx.words().all();
      }
      return List.copyOf(this.activeWords);
   }

   public List<String> editableWords(net.exmo.sreGame.GameContext ctx) {
      if (this.activeWords.isEmpty()) {
         if (this.isCaveGuess()) {
            this.activeWords.addAll(ctx.caveWords().plainTexts());
         } else {
            this.activeWords.addAll(ctx.words().all());
         }
         this.wordPackLabel = "房间自定义";
      }
      return this.activeWords;
   }

   public void importWords(List<String> words, String label) {
      this.activeWords.clear();
      if (words != null) {
         for (String word : words) {
            if (word != null && !word.isBlank() && !this.activeWords.contains(word.trim())) {
               this.activeWords.add(word.trim());
            }
         }
      }
      this.wordPackLabel = label == null || label.isBlank() ? "自定义" : label;
   }

   public boolean addWord(String word) {
      String cleaned = word == null ? "" : word.trim();
      if (cleaned.isEmpty() || cleaned.length() > 32 || this.activeWords.contains(cleaned)) {
         return false;
      }
      this.activeWords.add(cleaned);
      this.wordPackLabel = "房间自定义";
      return true;
   }

   public boolean removeWord(String word) {
      boolean removed = this.activeWords.remove(word);
      if (removed) {
         this.wordPackLabel = "房间自定义";
      }
      return removed;
   }

   public RoomState state() {
      return this.state;
   }

   public void setState(RoomState state) {
      this.state = state;
   }

   public UUID activeMatchId() {
      return this.activeMatchId;
   }

   public void setActiveMatchId(UUID activeMatchId) {
      this.activeMatchId = activeMatchId;
   }

   public boolean isJoinable() {
      return this.state == RoomState.WAITING && this.members.size() < this.maxPlayers;
   }

   public List<UUID> unassigned() {
      List<UUID> out = new ArrayList<>();
      for (UUID member : this.members) {
         if (this.duelSettings.teamOf(member) == 0) {
            out.add(member);
         }
      }
      return out;
   }
}
