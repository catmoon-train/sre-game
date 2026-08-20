package net.exmo.sreGame.games.pushthebutton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.games.pushthebutton.gui.PtbAnswerGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbCaptainGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbHackGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbNominateGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbProbeGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbViewGui;
import net.exmo.sreGame.games.pushthebutton.gui.PtbVoteGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PushTheButtonMatch {
   public enum Phase {
      INTRO, CAPTAIN, TEST, VIEW, LOUNGE, NOMINATE, VOTE, EJECT, ENDED
   }

   public static final String[] OPINIONS = {"非常不同意", "略不同意", "略同意", "非常同意"};
   private static final Block[] BIO_PALETTE = {
      Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
      Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.RED_WOOL,
      Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL,
      Blocks.BROWN_WOOL, Blocks.BLACK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL
   };
   private static final String[] WOOL_COLORS = {
      "white_wool", "orange_wool", "magenta_wool", "light_blue_wool",
      "yellow_wool", "lime_wool", "pink_wool", "red_wool"
   };

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Ship ship;
   private final PushTheButtonSettings settings;
   private final Map<UUID, Seat> players = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final List<History> history = new ArrayList<>();
   private final Set<PtbTestType> locked = new HashSet<>();
   private Phase phase = Phase.INTRO;
   private boolean begun;
   private int ticksLeft;
   private int gameTicks;
   private int gameLimitTicks;
   private int boardTicks;
   private int captainIndex;
   private UUID captain;
   private PtbTestType testType;
   private final Set<UUID> selected = new LinkedHashSet<>();
   private final Set<UUID> testees = new LinkedHashSet<>();
   private final Set<UUID> nominees = new LinkedHashSet<>();
   private UUID buttoneer;
   private int airlockAttempt;
   private int hacksLeft;
   private int alienCount;
   private boolean hasJester;
   private PromptBank.Pair pair;
   private PromptBank.Delib delib;
   private BlockState[][][] bioPatterns = new BlockState[Ship.BIO_STATIONS][3][3];

   public PushTheButtonMatch(GameContext ctx, GameRoom room, List<UUID> seats, Ship ship) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.ship = ship;
      this.settings = room.pushTheButtonSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&c拍下按钮"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      for (UUID uuid : this.seats) {
         this.players.put(uuid, new Seat(uuid));
      }
   }

   public UUID id() {
      return this.id;
   }

   public GameRoom room() {
      return this.room;
   }

   public GameContext ctx() {
      return this.ctx;
   }

   public Phase phase() {
      return this.phase;
   }

   public Ship ship() {
      return this.ship;
   }

   public PtbTestType testType() {
      return this.testType;
   }

   public UUID captain() {
      return this.captain;
   }

   public Set<UUID> selected() {
      return this.selected;
   }

   public Set<UUID> testees() {
      return this.testees;
   }

   public Set<UUID> nominees() {
      return this.nominees;
   }

   public List<History> history() {
      return this.history;
   }

   public int neededTestees() {
      if (this.testType == PtbTestType.BIO) {
         return this.playersPerBio();
      }
      return this.playersPerTest();
   }

   public int neededNominees() {
      return this.alienCount;
   }

   public int hacksLeft() {
      return this.hacksLeft;
   }

   public boolean isCaptain(UUID uuid) {
      return uuid != null && uuid.equals(this.captain);
   }

   public Seat seat(UUID uuid) {
      return this.players.get(uuid);
   }

   public List<UUID> aliveSeats() {
      List<UUID> list = new ArrayList<>();
      for (UUID uuid : this.seats) {
         Seat seat = this.players.get(uuid);
         if (seat != null && seat.alive) {
            list.add(uuid);
         }
      }
      return list;
   }

   public List<PtbTestType> availableTypes() {
      List<PtbTestType> list = new ArrayList<>();
      for (PtbTestType type : PtbTestType.values()) {
         if (this.locked.contains(type)) {
            continue;
         }
         if (type == PtbTestType.DRAWING && !this.settings.drawing()) {
            continue;
         }
         if (type == PtbTestType.BIO && !this.settings.bio()) {
            continue;
         }
         list.add(type);
      }
      if (list.isEmpty()) {
         for (PtbTestType type : PtbTestType.values()) {
            if (type == PtbTestType.DRAWING && !this.settings.drawing()) {
               continue;
            }
            if (type == PtbTestType.BIO && !this.settings.bio()) {
               continue;
            }
            list.add(type);
         }
      }
      return list;
   }

   public boolean locked(PtbTestType type) {
      return this.locked.contains(type) && this.availableTypes().size() > 1;
   }

   public PromptBank.Delib currentDelib() {
      return this.delib;
   }

   public String lastHumanPrompt() {
      if (this.history.isEmpty()) {
         return this.promptHuman();
      }
      return this.history.get(this.history.size() - 1).humanPrompt;
   }

   public List<Answer> lastAnswers() {
      if (this.history.isEmpty()) {
         return List.of();
      }
      return this.history.get(this.history.size() - 1).answers;
   }

   public String promptFor(UUID uuid) {
      Seat seat = this.seat(uuid);
      if (seat == null) {
         return "";
      }
      if (this.testType == PtbTestType.DELIB) {
         return seat.seesHuman ? (this.delib == null ? PromptBank.ALIEN_DELIB : this.delib.human())
            : PromptBank.ALIEN_DELIB;
      }
      if (this.pair == null) {
         return "";
      }
      return seat.seesHuman ? this.pair.human() : this.pair.alien();
   }

   public ServerLevel level() {
      return this.ctx.pushTheButton().ships().level();
   }

   public void start() {
      this.begun = true;
      this.assignRoles();
      this.gameLimitTicks = this.gameMinutes() * 60 * 20;
      this.gameTicks = this.gameLimitTicks;
      ServerLevel level = this.level();
      int i = 0;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&c拍下按钮");
         this.boss.addPlayer(player);
         player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         if (level != null) {
            this.ship.teleport(player, level, this.ship.loungeSpawn(), 0f + i * 15);
         }
         i++;
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&c&l拍下按钮");
      this.ctx.broadcast(this.room, "&7找出混入飞船的外星人，拍下按钮把他们送进气闸。");
      this.ctx.broadcast(this.room, "&7时间耗尽或误放人类，外星人获胜。");
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.revealRoles();
      this.phase = Phase.INTRO;
      this.ticksLeft = 6 * 20;
      this.refreshKits();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.phase != Phase.INTRO && this.phase != Phase.EJECT && this.phase != Phase.ENDED) {
         this.gameTicks--;
         if (this.gameTicks <= 0) {
            this.finish(PtbRole.ALIEN, "&c时间耗尽，外星人控制了飞船。", false);
            return;
         }
      }
      if (this.phase == Phase.INTRO && this.ticksLeft <= 0) {
         this.beginCaptain();
      } else if (this.phase == Phase.CAPTAIN && this.ticksLeft <= 0) {
         this.autoPickAndStart();
      } else if (this.phase == Phase.TEST && this.ticksLeft <= 0) {
         this.beginView();
      } else if (this.phase == Phase.VIEW && this.ticksLeft <= 0) {
         this.beginLounge();
      } else if (this.phase == Phase.LOUNGE && this.ticksLeft <= 0) {
         this.beginCaptain();
      } else if (this.phase == Phase.NOMINATE && this.ticksLeft <= 0) {
         this.beginVote();
      } else if (this.phase == Phase.VOTE && this.ticksLeft <= 0) {
         this.resolveVote();
      } else if (this.phase == Phase.EJECT && this.ticksLeft <= 0) {
         this.resolveEject();
      }
      if (this.phase == Phase.TEST && this.testType == PtbTestType.BIO && this.boardTicks % 10 == 0) {
         this.checkBio();
      }
      if (this.phase == Phase.CAPTAIN && this.boardTicks % 40 == 0) {
         this.openCaptainGuis();
      }
      if (this.phase == Phase.TEST && this.needsAnswerGui() && this.boardTicks % 40 == 0) {
         this.openAnswerGuis();
      }
      this.containPlayers();
      if (this.boardTicks % 10 == 0) {
         this.refreshBoard();
      }
      this.updateBoss();
   }

   public boolean handleChat(ServerPlayer player, String message) {
      if (this.phase != Phase.TEST || this.testType != PtbTestType.WRITING) {
         return false;
      }
      Seat seat = this.seat(player.getUUID());
      if (seat == null || !this.testees.contains(player.getUUID()) || seat.submitted) {
         return false;
      }
      String input = message == null ? "" : message.trim();
      if (input.isEmpty() || input.length() > 40) {
         this.ctx.send(player, "&c请输入 1–40 字的答案。");
         return true;
      }
      seat.answer = input;
      seat.submitted = true;
      this.ctx.send(player, "&a已提交：&f" + input);
      this.giveKit(player);
      this.maybeFinishTest();
      return true;
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      String action = GuiItems.actionTag(stack);
      if (action == null) {
         return false;
      }
      switch (action) {
         case "ptb_button" -> this.confirmButton(player);
         case "ptb_probe" -> PtbProbeGui.open(this, player);
         case "ptb_hack" -> {
            if (this.canHack(player.getUUID())) {
               PtbHackGui.open(this, player);
            }
         }
         case "ptb_rules" -> this.sendRules(player);
         case "ptb_captain" -> {
            if (this.phase == Phase.CAPTAIN && this.isCaptain(player.getUUID())) {
               PtbCaptainGui.open(this, player);
            }
         }
         case "ptb_answer" -> {
            if (this.phase == Phase.TEST && this.testees.contains(player.getUUID()) && this.needsAnswerGui()) {
               PtbAnswerGui.open(this, player);
            }
         }
         case "ptb_hurry" -> this.hurry(player);
         case "ptb_vote_yes" -> this.castVote(player, true);
         case "ptb_vote_no" -> this.castVote(player, false);
         case "ptb_nominate" -> {
            if (this.phase == Phase.NOMINATE && player.getUUID().equals(this.buttoneer)) {
               PtbNominateGui.open(this, player);
            }
         }
         case "ptb_view" -> {
            if (this.phase == Phase.VIEW) {
               PtbViewGui.open(this, player);
            }
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      if (hit == null) {
         return InteractionResult.FAIL;
      }
      BlockPos pos = hit.getBlockPos();
      if (this.ship.isButton(pos) && this.canPush(player.getUUID())) {
         this.confirmButton(player);
         return InteractionResult.FAIL;
      }
      BlockPos place = pos.relative(hit.getDirection());
      if (this.tryPlace(player, this.ship.isAnyCanvas(pos) || this.ship.isAnyBioCopy(pos) ? pos : place, stack)) {
         return InteractionResult.SUCCESS;
      }
      String action = GuiItems.actionTag(stack);
      if (action != null) {
         this.handleUseItem(player, stack);
      }
      return InteractionResult.FAIL;
   }

   public boolean tryPlace(ServerPlayer player, BlockPos pos, ItemStack stack) {
      if (this.phase != Phase.TEST || !(stack.getItem() instanceof BlockItem blockItem)) {
         return false;
      }
      Block block = blockItem.getBlock();
      if (!isPaintBlock(block)) {
         return false;
      }
      Seat seat = this.seat(player.getUUID());
      if (seat == null || !this.testees.contains(player.getUUID()) || seat.submitted) {
         return false;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return false;
      }
      if (this.testType == PtbTestType.DRAWING) {
         int index = this.testeeIndex(player.getUUID());
         if (index < 0 || !this.ship.isCanvas(pos, index)) {
            return false;
         }
         level.setBlock(pos, block.defaultBlockState(), 3);
         return true;
      }
      if (this.testType == PtbTestType.BIO) {
         int index = this.testeeIndex(player.getUUID());
         if (index < 0 || !this.ship.isBioCopy(pos, index)) {
            return false;
         }
         level.setBlock(pos, block.defaultBlockState(), 3);
         this.checkBio();
         return true;
      }
      return false;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      if (this.phase != Phase.TEST) {
         return false;
      }
      Seat seat = this.seat(player.getUUID());
      if (seat == null || !this.testees.contains(player.getUUID()) || seat.submitted) {
         return false;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return false;
      }
      int index = this.testeeIndex(player.getUUID());
      if (this.testType == PtbTestType.DRAWING && index >= 0 && this.ship.isCanvas(pos, index)) {
         level.setBlock(pos, Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
      } else if (this.testType == PtbTestType.BIO && index >= 0 && this.ship.isBioCopy(pos, index)) {
         level.setBlock(pos, Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player) {
      if (this.seat(player.getUUID()) == null) {
         return false;
      }
      this.heal(player);
      ServerLevel level = this.level();
      if (level != null) {
         this.ship.teleport(player, level, this.ship.loungeSpawn(), 90f);
      }
      return true;
   }

   public void handleGuiAction(ServerPlayer player, String action, String extra) {
      if (player == null || action == null || this.phase == Phase.ENDED) {
         return;
      }
      UUID uuid = player.getUUID();
      switch (action) {
         case "pick_test" -> {
            if (this.phase != Phase.CAPTAIN || !this.isCaptain(uuid) || extra == null) {
               return;
            }
            try {
               PtbTestType type = PtbTestType.valueOf(extra);
               if (this.availableTypes().contains(type)) {
                  this.testType = type;
                  this.selected.clear();
                  PtbCaptainGui.open(this, player);
               }
            } catch (IllegalArgumentException ignored) {
            }
         }
         case "toggle_testee" -> {
            if (this.phase != Phase.CAPTAIN || !this.isCaptain(uuid) || extra == null) {
               return;
            }
            UUID target = parseUuid(extra);
            if (target == null || !this.aliveSeats().contains(target) || target.equals(this.captain)
               && this.aliveSeats().size() > this.neededTestees()) {
               PtbCaptainGui.open(this, player);
               return;
            }
            if (this.selected.contains(target)) {
               this.selected.remove(target);
            } else if (this.selected.size() < this.neededTestees()) {
               this.selected.add(target);
            }
            PtbCaptainGui.open(this, player);
         }
         case "confirm_testees" -> {
            if (this.phase == Phase.CAPTAIN && this.isCaptain(uuid)) {
               this.startSelectedTest();
            }
         }
         case "answer" -> {
            if (this.phase == Phase.TEST && this.testees.contains(uuid) && extra != null) {
               this.submitChoice(player, extra);
            }
         }
         case "sus" -> {
            if (this.phase == Phase.VIEW && extra != null) {
               this.markSus(uuid, parseUuid(extra));
               PtbViewGui.open(this, player);
            }
         }
         case "nominate" -> {
            if (this.phase != Phase.NOMINATE || !uuid.equals(this.buttoneer) || extra == null) {
               return;
            }
            UUID target = parseUuid(extra);
            if (target == null || !this.aliveSeats().contains(target) || target.equals(this.buttoneer)) {
               PtbNominateGui.open(this, player);
               return;
            }
            if (this.nominees.contains(target)) {
               this.nominees.remove(target);
            } else if (this.nominees.size() < this.neededNominees()) {
               this.nominees.add(target);
            }
            PtbNominateGui.open(this, player);
         }
         case "confirm_noms" -> {
            if (this.phase == Phase.NOMINATE && uuid.equals(this.buttoneer)
               && this.nominees.size() == this.neededNominees()) {
               this.beginVote();
            }
         }
         case "vote_yes" -> this.castVote(player, true);
         case "vote_no" -> this.castVote(player, false);
         case "hack" -> {
            if (this.canHack(uuid) && extra != null) {
               this.hack(player, parseUuid(extra));
            }
         }
         default -> {
         }
      }
   }

   public void onLeave(UUID uuid) {
      Seat seat = this.seat(uuid);
      if (seat == null || this.phase == Phase.ENDED) {
         return;
      }
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      seat.alive = false;
      this.selected.remove(uuid);
      this.testees.remove(uuid);
      this.nominees.remove(uuid);
      if (uuid.equals(this.buttoneer) && (this.phase == Phase.NOMINATE || this.phase == Phase.VOTE)) {
         this.ctx.broadcast(this.room, "&e拍钮者离开，投票取消。");
         this.beginLounge();
      }
      this.checkForceWin();
      if (this.phase != Phase.ENDED && this.aliveSeats().size() < 2) {
         this.finish(null, "&7人数不足，对局结束。", false);
      }
   }

   public void endNow() {
      this.finish(null, "&7对局已终止。", true);
   }

   private void assignRoles() {
      List<UUID> pool = new ArrayList<>(this.seats);
      Collections.shuffle(pool);
      this.alienCount = this.settings.resolvedAliens(pool.size());
      for (int i = 0; i < this.alienCount && i < pool.size(); i++) {
         this.players.get(pool.get(i)).role = PtbRole.ALIEN;
      }
      this.hasJester = this.settings.jesterChance() >= 100
         || (this.settings.jesterChance() > 0
         && ThreadLocalRandom.current().nextInt(100) < this.settings.jesterChance());
      if (this.hasJester) {
         for (UUID uuid : pool) {
            Seat seat = this.players.get(uuid);
            if (seat.role == PtbRole.HUMAN) {
               seat.role = PtbRole.JESTER;
               break;
            }
         }
      }
      this.hacksLeft = this.alienCount == 1 ? (pool.size() >= 9 ? 4 : pool.size() >= 6 ? 3 : 2)
         : this.alienCount == 2 ? (pool.size() >= 9 ? 5 : 4) : 6;
   }

   private void revealRoles() {
      List<String> alienNames = new ArrayList<>();
      for (UUID uuid : this.seats) {
         Seat seat = this.seat(uuid);
         if (seat != null && seat.role == PtbRole.ALIEN) {
            alienNames.add(this.ctx.name(uuid));
         }
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Seat seat = this.seat(uuid);
         if (player == null || seat == null) {
            continue;
         }
         this.title(player, "&f你是 " + seat.role.colored(), seat.role == PtbRole.ALIEN
            ? "&7队友：&c" + String.join("&7, &c", alienNames)
            : seat.role == PtbRole.JESTER ? "&7被气闸送走且外星人获胜时，你也赢。" : "&7完成测试，识破外星人。");
         this.ctx.send(player, "&6<JerrBot> &f你是 " + seat.role.colored());
         if (seat.role == PtbRole.ALIEN) {
            this.ctx.send(player, "&c外星人同伴：&f" + String.join("&7, &f", alienNames));
            this.ctx.send(player, "&7入侵次数：&e" + this.hacksLeft);
         }
      }
   }

   private void beginCaptain() {
      if (this.noButtonsLeft()) {
         this.finish(PtbRole.ALIEN, "&c已经没有人能拍按钮了。", false);
         return;
      }
      List<UUID> alive = this.aliveSeats();
      if (alive.isEmpty()) {
         this.finish(null, "&7无人可继续。", false);
         return;
      }
      this.captainIndex = this.captainIndex % alive.size();
      this.captain = alive.get(this.captainIndex);
      this.captainIndex++;
      this.selected.clear();
      this.testees.clear();
      this.rotateLocks();
      List<PtbTestType> avail = this.availableTypes();
      this.testType = avail.isEmpty() ? PtbTestType.WRITING : avail.get(0);
      this.phase = Phase.CAPTAIN;
      this.ticksLeft = 45 * 20;
      this.ctx.broadcast(this.room, "&6<JerrBot> &e下一位船长是 &f" + this.ctx.name(this.captain));
      ServerPlayer capt = this.ctx.player(this.captain);
      if (capt != null) {
         this.title(capt, "&e你是船长", "&7选择测试与受试者");
         ServerLevel level = this.level();
         if (level != null) {
            this.ship.teleport(capt, level, this.ship.captainStand(), 90f);
         }
      }
      this.refreshKits();
      this.openCaptainGuis();
   }

   private void rotateLocks() {
      this.locked.clear();
      if (this.testType != null) {
         this.locked.add(this.testType);
      }
      List<PtbTestType> others = new ArrayList<>();
      for (PtbTestType type : PtbTestType.values()) {
         if (type != this.testType) {
            if (type == PtbTestType.DRAWING && !this.settings.drawing()) {
               continue;
            }
            if (type == PtbTestType.BIO && !this.settings.bio()) {
               continue;
            }
            others.add(type);
         }
      }
      if (!others.isEmpty()) {
         this.locked.add(others.get(ThreadLocalRandom.current().nextInt(others.size())));
      }
   }

   private void autoPickAndStart() {
      if (this.testType == null) {
         List<PtbTestType> avail = this.availableTypes();
         this.testType = avail.get(ThreadLocalRandom.current().nextInt(avail.size()));
      }
      this.selected.clear();
      List<UUID> pool = new ArrayList<>(this.aliveSeats());
      pool.remove(this.captain);
      Collections.shuffle(pool);
      int need = Math.min(this.neededTestees(), pool.isEmpty() ? this.aliveSeats().size() : pool.size());
      if (pool.size() < need) {
         pool = new ArrayList<>(this.aliveSeats());
         Collections.shuffle(pool);
      }
      for (int i = 0; i < need && i < pool.size(); i++) {
         this.selected.add(pool.get(i));
      }
      this.startSelectedTest();
   }

   private void startSelectedTest() {
      if (this.testType == null) {
         this.testType = this.availableTypes().get(0);
      }
      if (this.selected.size() != this.neededTestees()) {
         this.autoPickAndStart();
         return;
      }
      this.testees.clear();
      this.testees.addAll(this.selected);
      this.pair = null;
      this.delib = null;
      switch (this.testType) {
         case WRITING -> this.pair = PromptBank.writing();
         case OPINION -> this.pair = PromptBank.opinion();
         case DRAWING -> this.pair = PromptBank.drawing();
         case DELIB -> this.delib = PromptBank.delib();
         case BIO -> this.prepBio();
      }
      int i = 0;
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         if (seat == null) {
            continue;
         }
         seat.submitted = false;
         seat.answer = "";
         seat.hacked = false;
         seat.seesHuman = seat.role.humanLike();
         seat.hurried = false;
         this.sendToStation(uuid, i++);
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.ctx.send(player, "&6<JerrBot> &f提示：&e" + this.promptFor(uuid));
         }
      }
      this.phase = Phase.TEST;
      this.ticksLeft = this.testType.seconds() * 20;
      this.ctx.broadcast(this.room, "&6<JerrBot> &f本轮测试：&e" + this.testType.label()
         + " &7受试者 &f" + this.names(this.testees));
      this.refreshKits();
      if (this.needsAnswerGui()) {
         this.openAnswerGuis();
      }
   }

   private void sendToStation(UUID uuid, int index) {
      ServerPlayer player = this.ctx.player(uuid);
      ServerLevel level = this.level();
      if (player == null || level == null) {
         return;
      }
      if (this.testType == PtbTestType.DRAWING) {
         this.ship.resetCanvas(level, index);
         this.ship.teleport(player, level, this.ship.canvasStand(index), 0f);
      } else if (this.testType == PtbTestType.BIO) {
         this.ship.teleport(player, level, this.ship.bioStand(index), 180f);
      } else {
         this.ship.teleport(player, level, this.ship.loungeSpawn(), 90f);
      }
   }

   private void prepBio() {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      for (int s = 0; s < Ship.BIO_STATIONS; s++) {
         for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
               this.bioPatterns[s][x][y] = BIO_PALETTE[rng.nextInt(BIO_PALETTE.length)].defaultBlockState();
            }
         }
         this.ship.paintBioTemplate(this.level(), s, this.bioPatterns[s]);
      }
   }

   private void checkBio() {
      if (this.testType != PtbTestType.BIO || this.level() == null) {
         return;
      }
      int i = 0;
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         if (seat == null || seat.submitted) {
            i++;
            continue;
         }
         if (this.ship.bioMatches(this.level(), i, this.bioPatterns[i])) {
            seat.submitted = true;
            seat.answer = "扫描完成";
            ServerPlayer capt = this.ctx.player(this.captain);
            if (capt != null) {
               this.ctx.send(capt, "&6扫描结果：&f" + this.ctx.name(uuid) + " &7是 " + seat.role.colored());
               this.title(capt, seat.role.colored(), "&f" + this.ctx.name(uuid));
            }
            ServerPlayer testee = this.ctx.player(uuid);
            if (testee != null) {
               this.ctx.send(testee, "&e船长已经知道你的真实身份。");
            }
            this.giveKit(this.ctx.player(uuid));
         }
         i++;
      }
      this.maybeFinishTest();
   }

   private void submitChoice(ServerPlayer player, String extra) {
      Seat seat = this.seat(player.getUUID());
      if (seat == null || seat.submitted) {
         return;
      }
      if (this.testType == PtbTestType.OPINION) {
         try {
            int idx = Integer.parseInt(extra);
            if (idx < 0 || idx >= OPINIONS.length) {
               return;
            }
            seat.answer = OPINIONS[idx];
         } catch (NumberFormatException e) {
            return;
         }
      } else if (this.testType == PtbTestType.DELIB && this.delib != null) {
         seat.answer = switch (extra) {
            case "A" -> this.delib.a();
            case "B" -> this.delib.b();
            case "C" -> this.delib.c();
            default -> extra;
         };
      } else {
         return;
      }
      seat.submitted = true;
      this.ctx.send(player, "&a已选择：&f" + seat.answer);
      player.closeContainer();
      this.giveKit(player);
      this.maybeFinishTest();
   }

   private void maybeFinishTest() {
      if (this.phase != Phase.TEST) {
         return;
      }
      if (this.testType == PtbTestType.DRAWING) {
         return;
      }
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         if (seat != null && seat.alive && !seat.submitted) {
            return;
         }
      }
      this.beginView();
   }

   private void beginView() {
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         if (seat != null && !seat.submitted) {
            seat.answer = this.testType == PtbTestType.DRAWING ? "已作画" : "（未作答）";
            seat.submitted = true;
         }
      }
      History rec = new History(this.testType, this.captain, this.promptHuman(), new ArrayList<>());
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         rec.answers.add(new Answer(uuid, this.promptFor(uuid), seat == null ? "" : seat.answer, 0));
      }
      this.history.add(rec);
      this.phase = Phase.VIEW;
      this.ticksLeft = 25 * 20;
      this.ctx.broadcast(this.room, "&6<JerrBot> &f查看答案并标记可疑的人。");
      ServerLevel level = this.level();
      for (UUID uuid : this.aliveSeats()) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         player.closeContainer();
         if (level != null) {
            this.ship.teleport(player, level, this.ship.loungeSpawn(), 90f);
         }
         PtbViewGui.open(this, player);
      }
      this.refreshKits();
   }

   private String promptHuman() {
      if (this.testType == PtbTestType.DELIB && this.delib != null) {
         return this.delib.human();
      }
      return this.pair == null ? this.testType.label() : this.pair.human();
   }

   private void beginLounge() {
      this.phase = Phase.LOUNGE;
      this.ticksLeft = 12 * 20;
      this.buttoneer = null;
      this.nominees.clear();
      ServerLevel level = this.level();
      for (UUID uuid : this.aliveSeats()) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null && level != null) {
            player.closeContainer();
            this.ship.teleport(player, level, this.ship.loungeSpawn(), 90f);
         }
      }
      this.ctx.broadcast(this.room, "&6<JerrBot> &7可以讨论，也可以拍下按钮。下一位船长即将上场。");
      this.refreshKits();
   }

   private boolean canPush(UUID uuid) {
      Seat seat = this.seat(uuid);
      return seat != null && seat.alive && !seat.hasPushed
         && (this.phase == Phase.CAPTAIN || this.phase == Phase.VIEW || this.phase == Phase.LOUNGE || this.phase == Phase.TEST);
   }

   private void confirmButton(ServerPlayer player) {
      Seat seat = this.seat(player.getUUID());
      if (seat == null || !this.canPush(player.getUUID())) {
         this.ctx.send(player, "&c现在不能拍按钮。");
         return;
      }
      seat.buttonStep++;
      if (seat.buttonStep == 1) {
         this.ctx.send(player, "&c确定要拍下按钮吗？再点一次。");
      } else if (seat.buttonStep == 2) {
         this.ctx.send(player, "&c真的确定？再点一次就会启动气闸。");
      } else {
         this.startAirlock(player);
      }
      this.giveKit(player);
   }

   private void startAirlock(ServerPlayer player) {
      Seat seat = this.seat(player.getUUID());
      if (seat == null) {
         return;
      }
      seat.hasPushed = true;
      seat.buttonStep = 0;
      this.buttoneer = player.getUUID();
      this.nominees.clear();
      this.airlockAttempt++;
      this.phase = Phase.NOMINATE;
      int seconds = this.airlockAttempt <= 1 ? 121 : this.airlockAttempt == 2 ? 91 : this.airlockAttempt == 3 ? 61 : 31;
      this.ticksLeft = seconds * 20;
      this.ctx.broadcast(this.room, "&c&l" + this.ctx.name(player.getUUID()) + " 拍下了按钮！");
      this.title(player, "&c气闸启动", "&7选出 " + this.alienCount + " 名你认为的外星人");
      ServerLevel level = this.level();
      if (level != null) {
         this.ship.teleport(player, level, this.ship.airlockCell(0), 270f);
      }
      this.refreshKits();
      PtbNominateGui.open(this, player);
   }

   private void beginVote() {
      if (this.nominees.size() != this.neededNominees()) {
         List<UUID> pool = new ArrayList<>(this.aliveSeats());
         pool.remove(this.buttoneer);
         Collections.shuffle(pool);
         this.nominees.clear();
         for (int i = 0; i < this.neededNominees() && i < pool.size(); i++) {
            this.nominees.add(pool.get(i));
         }
      }
      for (Seat seat : this.players.values()) {
         seat.vote = 0;
      }
      this.phase = Phase.VOTE;
      this.ticksLeft = 31 * 20;
      ServerLevel level = this.level();
      int cell = 0;
      for (UUID uuid : this.nominees) {
         ServerPlayer nom = this.ctx.player(uuid);
         if (nom != null && level != null) {
            this.ship.teleport(nom, level, this.ship.airlockCell(Math.min(2, cell++)), 270f);
            this.title(nom, "&c你被送进气闸", "&7等待投票");
         }
      }
      this.ctx.broadcast(this.room, "&6<JerrBot> &f请投票：是否释放 "
         + this.names(this.nominees) + " ？");
      this.refreshKits();
      for (UUID uuid : this.aliveSeats()) {
         if (uuid.equals(this.buttoneer) || this.nominees.contains(uuid)) {
            continue;
         }
         ServerPlayer voter = this.ctx.player(uuid);
         if (voter != null) {
            PtbVoteGui.open(this, voter);
         }
      }
   }

   private void castVote(ServerPlayer player, boolean yes) {
      if (this.phase != Phase.VOTE) {
         return;
      }
      UUID uuid = player.getUUID();
      if (uuid.equals(this.buttoneer) || this.nominees.contains(uuid)) {
         this.ctx.send(player, "&c你不能投票。");
         return;
      }
      Seat seat = this.seat(uuid);
      if (seat == null || !seat.alive) {
         return;
      }
      seat.vote = yes ? 1 : 2;
      this.ctx.send(player, yes ? "&a已投赞成。" : "&c已投反对。");
      player.closeContainer();
      this.giveKit(player);
      if (this.allVoted()) {
         this.resolveVote();
      }
   }

   private boolean allVoted() {
      for (UUID uuid : this.aliveSeats()) {
         if (uuid.equals(this.buttoneer) || this.nominees.contains(uuid)) {
            continue;
         }
         Seat seat = this.seat(uuid);
         if (seat != null && seat.vote == 0) {
            return false;
         }
      }
      return true;
   }

   private void resolveVote() {
      int no = 0;
      List<String> noVoters = new ArrayList<>();
      int voters = 0;
      for (UUID uuid : this.aliveSeats()) {
         if (uuid.equals(this.buttoneer) || this.nominees.contains(uuid)) {
            continue;
         }
         voters++;
         Seat seat = this.seat(uuid);
         if (seat != null && seat.vote == 2) {
            no++;
            noVoters.add(this.ctx.name(uuid));
         }
      }
      boolean buffer = (this.hasJester && this.aliveSeats().size() >= 5) || voters >= 6;
      boolean pass = no == 0 || (buffer && no == 1);
      if (pass) {
         this.ctx.broadcast(this.room, "&a投票通过。气闸即将释放。");
         this.phase = Phase.EJECT;
         this.ticksLeft = 4 * 20;
         for (UUID uuid : this.nominees) {
            ServerPlayer nom = this.ctx.player(uuid);
            if (nom != null) {
               this.title(nom, "&c气闸打开", "&7下落…");
            }
         }
      } else {
         this.ctx.broadcast(this.room, "&c投票失败。" + (noVoters.isEmpty() ? "" : " 反对：&f" + String.join("&7, &f", noVoters)));
         this.beginLounge();
      }
   }

   private void resolveEject() {
      boolean humanIn = false;
      boolean jesterIn = false;
      for (UUID uuid : this.nominees) {
         Seat seat = this.seat(uuid);
         if (seat == null) {
            continue;
         }
         seat.alive = false;
         seat.ejected = true;
         if (seat.role != PtbRole.ALIEN) {
            humanIn = true;
         }
         if (seat.role == PtbRole.JESTER) {
            jesterIn = true;
         }
         this.ctx.broadcast(this.room, "&f" + this.ctx.name(uuid) + " &7是 " + seat.role.colored());
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            player.setGameMode(GameType.SPECTATOR);
         }
      }
      if (humanIn) {
         this.finish(PtbRole.ALIEN, "&c气闸里有非外星人。外星人获胜！", jesterIn);
      } else {
         this.finish(PtbRole.HUMAN, "&a气闸里全是外星人。人类获胜！", false);
      }
   }

   private boolean canHack(UUID uuid) {
      Seat seat = this.seat(uuid);
      return this.phase == Phase.TEST && seat != null && seat.role == PtbRole.ALIEN && this.hacksLeft > 0;
   }

   private void hack(ServerPlayer actor, UUID target) {
      if (target == null || !this.testees.contains(target)) {
         return;
      }
      Seat victim = this.seat(target);
      if (victim == null || victim.hacked || victim.submitted) {
         this.ctx.send(actor, "&c无法入侵该受试者。");
         return;
      }
      victim.hacked = true;
      victim.seesHuman = !victim.seesHuman;
      this.hacksLeft--;
      this.ctx.send(actor, "&c已入侵 &f" + this.ctx.name(target) + " &7剩余 &e" + this.hacksLeft);
      ServerPlayer tp = this.ctx.player(target);
      if (tp != null) {
         this.ctx.send(tp, "&e你的提示已更新：&f" + this.promptFor(target));
         if (this.needsAnswerGui()) {
            PtbAnswerGui.open(this, tp);
         }
      }
      actor.closeContainer();
      this.giveKit(actor);
   }

   private void markSus(UUID actor, UUID target) {
      if (target == null || !this.testees.contains(target) || actor.equals(target)) {
         return;
      }
      Seat seat = this.seat(actor);
      if (seat == null || seat.susMarked.contains(target)) {
         return;
      }
      seat.susMarked.add(target);
      if (!this.history.isEmpty()) {
         History last = this.history.get(this.history.size() - 1);
         for (Answer answer : last.answers) {
            if (answer.player.equals(target)) {
               answer.sus++;
            }
         }
      }
      this.ctx.send(this.ctx.player(actor), "&e已标记 &f" + this.ctx.name(target) + " &e可疑。");
   }

   private void hurry(ServerPlayer player) {
      if (this.phase != Phase.TEST || this.testees.contains(player.getUUID())) {
         return;
      }
      Seat seat = this.seat(player.getUUID());
      if (seat == null || seat.hurried) {
         return;
      }
      seat.hurried = true;
      this.ticksLeft = Math.max(20, this.ticksLeft - 5 * 20);
      this.ctx.broadcast(this.room, "&b" + this.ctx.name(player.getUUID()) + " 催促了测试。");
      this.giveKit(player);
   }

   private void sendRules(ServerPlayer player) {
      this.ctx.send(player, "&8&m----------------");
      this.ctx.send(player, "&c拍下按钮");
      this.ctx.send(player, "&7人类完成测试、讨论，把外星人送进气闸。");
      this.ctx.send(player, "&7外星人会看到错误提示。入侵可翻转他人提示。");
      this.ctx.send(player, "&7每人可拍一次按钮，提名恰好 &e" + this.alienCount + " &7人。");
      this.ctx.send(player, "&7全票通过且全是外星人才算人类赢；误放人类则外星人赢。");
      this.ctx.send(player, "&8&m----------------");
   }

   private boolean noButtonsLeft() {
      for (UUID uuid : this.aliveSeats()) {
         Seat seat = this.seat(uuid);
         if (seat != null && !seat.hasPushed) {
            return false;
         }
      }
      return true;
   }

   private void checkForceWin() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      boolean alienAlive = false;
      boolean humanAlive = false;
      for (UUID uuid : this.aliveSeats()) {
         Seat seat = this.seat(uuid);
         if (seat == null) {
            continue;
         }
         if (seat.role == PtbRole.ALIEN) {
            alienAlive = true;
         } else if (seat.role == PtbRole.HUMAN) {
            humanAlive = true;
         }
      }
      if (!alienAlive) {
         this.finish(PtbRole.HUMAN, "&a外星人已全部离场，人类获胜。", false);
      } else if (!humanAlive) {
         this.finish(PtbRole.ALIEN, "&c人类已全部离场，外星人获胜。", false);
      }
   }

   private void finish(PtbRole winner, String reason, boolean jesterWins) {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, reason);
      if (winner == PtbRole.HUMAN) {
         this.ctx.broadcast(this.room, "&a&l人类获胜");
      } else if (winner == PtbRole.ALIEN) {
         this.ctx.broadcast(this.room, "&c&l外星人获胜" + (jesterWins ? " &d(+小丑)" : ""));
      }
      for (UUID uuid : this.seats) {
         Seat seat = this.seat(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (seat != null) {
            this.ctx.broadcast(this.room, "&f" + this.ctx.name(uuid) + " &7· " + seat.role.colored()
               + (seat.ejected ? " &8(气闸)" : ""));
         }
         if (player != null) {
            boolean win = winner == PtbRole.HUMAN ? seat != null && seat.role == PtbRole.HUMAN
               : winner == PtbRole.ALIEN && seat != null && (seat.role == PtbRole.ALIEN || (jesterWins && seat.role == PtbRole.JESTER));
            this.title(player, win ? "&a胜利" : "&c失败", winner == null ? "&7结束" : winner.colored());
            this.restore(player);
         } else {
            this.board.remove(uuid);
         }
      }
      this.boss.removeAllPlayers();
      this.ctx.pushTheButton().ships().release(this.ship);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.pushTheButton().remove(this);
   }

   private void refreshKits() {
      for (UUID uuid : this.seats) {
         this.giveKit(this.ctx.player(uuid));
      }
   }

   private void giveKit(ServerPlayer player) {
      if (player == null) {
         return;
      }
      Seat seat = this.seat(player.getUUID());
      if (seat == null) {
         return;
      }
      Inventory inv = player.getInventory();
      inv.clearContent();
      if (!seat.alive) {
         inv.setItem(8, item("book", "&7规则", "ptb_rules"));
         return;
      }
      if (this.phase == Phase.CAPTAIN && this.isCaptain(player.getUUID())) {
         inv.setItem(0, item("writable_book", "&e船长面板", "ptb_captain"));
      }
      if (this.phase == Phase.TEST && this.testees.contains(player.getUUID())) {
         if (this.needsAnswerGui() && !seat.submitted) {
            inv.setItem(0, item("paper", "&a打开答题", "ptb_answer"));
         } else if (this.testType == PtbTestType.WRITING && !seat.submitted) {
            inv.setItem(0, GuiItems.named("paper", "&e在聊天栏输入答案", List.of("&71–40 字，仅你可见")));
         } else if ((this.testType == PtbTestType.DRAWING || this.testType == PtbTestType.BIO) && !seat.submitted) {
            int slot = 0;
            for (String wool : WOOL_COLORS) {
               inv.setItem(slot++, GuiItems.named(wool, "&f颜料", List.of("&7点在画板/临摹墙上")));
            }
         }
      }
      if (this.phase == Phase.TEST && !this.testees.contains(player.getUUID())) {
         inv.setItem(2, item("clock", "&b催促", "ptb_hurry"));
      }
      if (this.canHack(player.getUUID())) {
         inv.setItem(3, item("ender_eye", "&c入侵 &e×" + this.hacksLeft, "ptb_hack"));
      }
      if (this.canPush(player.getUUID())) {
         String dye = seat.buttonStep == 0 ? "red_dye" : seat.buttonStep == 1 ? "magenta_dye" : "yellow_dye";
         String name = seat.buttonStep == 0 ? "&c拍下按钮" : seat.buttonStep == 1 ? "&d确定拍下？" : "&e最后确认";
         inv.setItem(4, item(dye, name, "ptb_button"));
      }
      if (this.phase == Phase.VIEW) {
         inv.setItem(1, item("spyglass", "&e查看答案", "ptb_view"));
      }
      if (this.phase == Phase.NOMINATE && player.getUUID().equals(this.buttoneer)) {
         inv.setItem(0, item("hopper", "&c提名外星人", "ptb_nominate"));
      }
      if (this.phase == Phase.VOTE && !player.getUUID().equals(this.buttoneer) && !this.nominees.contains(player.getUUID())) {
         inv.setItem(0, item("lime_dye", "&a赞成释放", "ptb_vote_yes"));
         inv.setItem(1, item("red_dye", "&c反对释放", "ptb_vote_no"));
      }
      inv.setItem(7, item("compass", "&b探测仪", "ptb_probe"));
      inv.setItem(8, item("book", "&7规则", "ptb_rules"));
   }

   private static ItemStack item(String mat, String name, String action) {
      return GuiItems.action(mat, name, List.of("&e右键使用"), action);
   }

   private void openCaptainGuis() {
      ServerPlayer capt = this.ctx.player(this.captain);
      if (capt != null && !(capt.containerMenu instanceof PtbCaptainGui.Menu)) {
         PtbCaptainGui.open(this, capt);
      }
   }

   private void openAnswerGuis() {
      for (UUID uuid : this.testees) {
         Seat seat = this.seat(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (seat == null || player == null || seat.submitted) {
            continue;
         }
         if (!(player.containerMenu instanceof PtbAnswerGui.Menu)) {
            PtbAnswerGui.open(this, player);
         }
      }
   }

   private boolean needsAnswerGui() {
      return this.testType == PtbTestType.OPINION || this.testType == PtbTestType.DELIB;
   }

   private void containPlayers() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.aliveSeats()) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         if (!this.ship.contains(player.getX(), player.getY(), player.getZ())) {
            this.ship.teleport(player, level, this.ship.loungeSpawn(), 90f);
         }
      }
   }

   private void refreshBoard() {
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Seat seat = this.seat(uuid);
         if (player == null || seat == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7角色 " + (seat.alive ? seat.role.colored() : "&8已出局"));
         lines.add("&7阶段 " + this.phaseLabel());
         lines.add("&7剩余 " + formatTime(this.gameTicks));
         lines.add("&7外星人 &c" + this.alienCount + " &8| &7入侵 &e" + this.hacksLeft);
         if (this.captain != null) {
            lines.add("&7船长 &f" + this.ctx.name(this.captain));
         }
         if (this.testType != null && (this.phase == Phase.TEST || this.phase == Phase.VIEW || this.phase == Phase.CAPTAIN)) {
            lines.add("&7测试 &e" + this.testType.label());
         }
         this.board.update(player, lines);
      }
   }

   private String phaseLabel() {
      return switch (this.phase) {
         case INTRO -> "&f揭示";
         case CAPTAIN -> "&e船长选择";
         case TEST -> "&b测试中";
         case VIEW -> "&d查看";
         case LOUNGE -> "&a大厅";
         case NOMINATE -> "&c提名";
         case VOTE -> "&6投票";
         case EJECT -> "&4气闸";
         case ENDED -> "&8结束";
      };
   }

   private void updateBoss() {
      int cap = Math.max(1, this.phase == Phase.INTRO ? 6 * 20
         : this.phase == Phase.TEST && this.testType != null ? this.testType.seconds() * 20
         : this.phase == Phase.VOTE ? 31 * 20
         : Math.max(this.ticksLeft, 20));
      this.boss.setProgress(Math.max(0f, Math.min(1f, this.ticksLeft / (float) cap)));
      this.boss.setName(TextUtil.color("&c拍下按钮 &8· &f" + this.phaseLabel()
         + " &8· &e" + Math.max(0, (this.ticksLeft + 19) / 20) + "s"));
   }

   private int playersPerTest() {
      int n = this.aliveSeats().size();
      if (n <= 3) {
         return 1;
      }
      if (n <= 5) {
         return 2;
      }
      if (n <= 7) {
         return 3;
      }
      if (n <= 9) {
         return n >= 9 ? 4 : 3;
      }
      return 4;
   }

   private int playersPerBio() {
      return this.aliveSeats().size() <= 4 ? 1 : 2;
   }

   private int gameMinutes() {
      int n = this.seats.size();
      if (n <= 3) {
         return 9;
      }
      if (n == 4) {
         return 12;
      }
      if (n == 5) {
         return 14;
      }
      if (n <= 7) {
         return n == 6 ? 16 : 18;
      }
      if (n <= 9) {
         return 18;
      }
      return 22;
   }

   private int testeeIndex(UUID uuid) {
      int i = 0;
      for (UUID id : this.testees) {
         if (id.equals(uuid)) {
            return i;
         }
         i++;
      }
      return -1;
   }

   private String names(Set<UUID> ids) {
      List<String> names = new ArrayList<>();
      for (UUID uuid : ids) {
         names.add(this.ctx.name(uuid));
      }
      return String.join("&7, &f", names);
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20f);
      player.setRemainingFireTicks(0);
      player.fallDistance = 0f;
      player.setAirSupply(player.getMaxAirSupply());
   }

   private void restore(ServerPlayer player) {
      this.board.remove(player);
      this.boss.removePlayer(player);
      player.closeContainer();
      Saved snap = this.saved.remove(player.getUUID());
      if (snap != null) {
         snap.apply(player, this.ctx);
      }
   }

   private void title(ServerPlayer player, String title, String sub) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub)));
   }

   private static UUID parseUuid(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      try {
         return UUID.fromString(raw);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static boolean isPaintBlock(Block block) {
      if (block == null) {
         return false;
      }
      String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
      return key.endsWith("_wool") || key.endsWith("_concrete") || key.endsWith("_terracotta");
   }

   private static String formatTime(int ticks) {
      int sec = Math.max(0, ticks / 20);
      return (sec / 60) + ":" + String.format("%02d", sec % 60);
   }

   public static final class Seat {
      public final UUID uuid;
      public PtbRole role = PtbRole.HUMAN;
      public boolean alive = true;
      public boolean ejected;
      public boolean hasPushed;
      public int buttonStep;
      public boolean seesHuman = true;
      public boolean hacked;
      public boolean submitted;
      public String answer = "";
      public boolean hurried;
      public int vote;
      public final Set<UUID> susMarked = new HashSet<>();

      Seat(UUID uuid) {
         this.uuid = uuid;
      }
   }

   public static final class History {
      public final PtbTestType type;
      public final UUID captain;
      public final String humanPrompt;
      public final List<Answer> answers;

      History(PtbTestType type, UUID captain, String humanPrompt, List<Answer> answers) {
         this.type = type;
         this.captain = captain;
         this.humanPrompt = humanPrompt;
         this.answers = answers;
      }
   }

   public static final class Answer {
      public final UUID player;
      public final String prompt;
      public final String text;
      public int sus;

      Answer(UUID player, String prompt, String text, int sus) {
         this.player = player;
         this.prompt = prompt;
         this.text = text;
      }
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>();
         Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) {
            items.add(inv.getItem(i).copy());
         }
         return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
            player.gameMode.getGameModeForPlayer(), items);
      }

      void apply(ServerPlayer player, GameContext ctx) {
         ServerLevel level = ctx.server().getLevel(this.dimension);
         if (level == null) {
            level = ctx.server().overworld();
         }
         player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
         player.setGameMode(this.gameType);
         Inventory inv = player.getInventory();
         inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), this.items.size()); i++) {
            inv.setItem(i, this.items.get(i).copy());
         }
      }
   }
}
