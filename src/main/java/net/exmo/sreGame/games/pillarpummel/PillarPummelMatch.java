package net.exmo.sreGame.games.pillarpummel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

public final class PillarPummelMatch {
   public enum Phase {
      INTRO,
      FIGHT,
      ENDED
   }

   private static final int INTRO_SECONDS = 5;
   private static final int GEN_TICKS = 40;
   private static final int ARROW_TICKS = 100;
   private static final int REGEN_TICKS = 200;
   private static final int TURRET_TICKS = 40;
   private static final int NUKE_TICKS = 100;
   private static final double NUKE_RADIUS = 15.0;
   private static final double TURRET_RANGE = 8.0;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final PummelArena arena;
   private final PillarPummelSettings settings;
   private final PummelTeam[] teams;
   private final PlotCell[][] cells;
   private final Bridge[][] xBridges;
   private final Bridge[][] zBridges;
   private final int[][] spawnCells;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<BlockPos> generators = new ArrayList<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final String teamPrefix;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int fightTicks;
   private int boardTicks;
   private int scoreTicks;
   private int genTicks;
   private int arrowTicks;
   private int nextCatchTicks;
   private int nukeTicks = -1;
   private boolean begun;

   public PillarPummelMatch(GameContext ctx, GameRoom room, List<UUID> seats, PummelArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.pillarPummelSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6柱联壁合"), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.teamPrefix = "pp" + this.id.toString().replace("-", "").substring(0, 8);
      this.teams = new PummelTeam[this.settings.teamCount()];
      for (int i = 0; i < this.teams.length; i++) {
         this.teams[i] = new PummelTeam(i);
      }
      int pillars = this.pillars();
      int n = pillars - 1;
      this.cells = new PlotCell[n][n];
      this.xBridges = new Bridge[n][pillars];
      this.zBridges = new Bridge[pillars][n];
      for (int cx = 0; cx < n; cx++) {
         for (int cz = 0; cz < n; cz++) {
            this.cells[cx][cz] = new PlotCell(cx, cz);
         }
      }
      for (int gx = 0; gx < n; gx++) {
         for (int gz = 0; gz < pillars; gz++) {
            this.xBridges[gx][gz] = new Bridge(Bridge.Axis.X, gx, gz);
         }
      }
      for (int gx = 0; gx < pillars; gx++) {
         for (int gz = 0; gz < n; gz++) {
            this.zBridges[gx][gz] = new Bridge(Bridge.Axis.Z, gx, gz);
         }
      }
      PummelArena.Layout layout = this.layout();
      for (int cx = 0; cx < n; cx++) {
         for (int cz = 0; cz < n; cz++) {
            this.cells[cx][cz].disabled = !this.arena.cellEnabled(cx, cz, layout);
         }
      }
      for (int gx = 0; gx < n; gx++) {
         for (int gz = 0; gz < pillars; gz++) {
            this.xBridges[gx][gz].disabled = !this.arena.xBridgeEnabled(gx, gz, layout);
         }
      }
      for (int gx = 0; gx < pillars; gx++) {
         for (int gz = 0; gz < n; gz++) {
            this.zBridges[gx][gz].disabled = !this.arena.zBridgeEnabled(gx, gz, layout);
         }
      }
      this.spawnCells = new int[this.teams.length][];
      boolean[] taken = new boolean[n * n];
      for (int t = 0; t < this.teams.length; t++) {
         int[] prefer = this.arena.spawnCell(t, layout);
         int[] pick = this.pickFreeCell(prefer, taken, n);
         this.spawnCells[t] = pick;
         if (pick != null) {
            taken[pick[0] * n + pick[1]] = true;
         }
      }
      this.assignTeams();
   }

   public UUID id() {
      return this.id;
   }

   public GameRoom room() {
      return this.room;
   }

   public Phase phase() {
      return this.phase;
   }

   public ServerLevel level() {
      return this.ctx.pillarPummel().arenas().level();
   }

   public PummelArena.Layout layout() {
      return new PummelArena.Layout(
         this.pillars(), this.settings.teamCount(), 0, 0,
         this.settings.arenaShape().name(), this.id.getMostSignificantBits());
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.level();
      this.arena.remember(this.layout());
      for (int t = 0; t < this.teams.length; t++) {
         this.setupSpawn(t);
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&6柱联壁合");
         this.boss.addPlayer(player);
         this.applyNametag(player, fighter);
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.giveKit(player, fighter);
         if (level != null) {
            this.arena.teleport(player, level, this.spawnVec(fighter.team));
         }
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l柱联壁合");
      this.ctx.broadcast(this.room, "&7在未占领的桥上放 1 个队色粉末即可铺桥");
      this.ctx.broadcast(this.room, "&7四面桥同色会自动生成 5×5 平台 · TNT 拆台 · 占地产分");
      this.ctx.broadcast(this.room, "&7" + this.settings.teamCount() + " 队 · "
         + this.settings.durationMinutes() + " 分钟 · " + this.settings.arenaShape().label()
         + " " + this.settings.grid() + "×" + this.settings.grid());
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.setTimer(INTRO_SECONDS * 20);
      this.phase = Phase.INTRO;
      this.nextCatchTicks = this.settings.catchUp() ? this.settings.firstTriggerMinutes() * 20 * 60 : -1;
      this.scoreTicks = this.settings.scoreInterval() * 20;
      this.genTicks = GEN_TICKS;
      this.arrowTicks = ARROW_TICKS;
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      if (this.phase == Phase.FIGHT) {
         this.tickFight();
      }
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      this.updateBoss();
      if (this.ticksLeft > 0) {
         return;
      }
      if (this.phase == Phase.INTRO) {
         this.beginFight();
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || fighter.respawnTicks > 0) {
         return InteractionResult.FAIL;
      }
      if (PummelShop.NUKE.equals(PummelShop.gadget(stack))) {
         this.armNuke(player, fighter, stack);
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || fighter.respawnTicks > 0) {
         return InteractionResult.FAIL;
      }
      BlockPos clicked = hit.getBlockPos();
      BlockPos place = clicked.relative(hit.getDirection());
      for (int t = 0; t < this.teams.length; t++) {
         if (this.shopOf(t).equals(clicked)) {
            if (fighter.team == t) {
               net.exmo.sreGame.games.pillarpummel.gui.PummelShopGui.open(this.ctx, player, this);
            } else {
               this.ctx.send(player, "&c这是敌方军火商。");
            }
            return InteractionResult.FAIL;
         }
         if (this.chestOf(t).equals(clicked)) {
            if (fighter.team == t) {
               this.openStorage(player, this.teams[t]);
            } else {
               this.ctx.send(player, "&c这是敌方箱子。");
            }
            return InteractionResult.FAIL;
         }
      }
      String gadget = PummelShop.gadget(stack);
      if (gadget != null) {
         this.useGadget(player, fighter, stack, gadget, clicked, place);
         return InteractionResult.FAIL;
      }
      if (PummelShop.isWool(stack) || PummelShop.isPowder(stack)) {
         if (this.tryClaimBridge(player, fighter, clicked) || this.tryClaimBridge(player, fighter, place)) {
            return InteractionResult.FAIL;
         }
         this.ctx.send(player, "&c只能在未占领的桥上放置队色粉末。");
         return InteractionResult.FAIL;
      }
      return InteractionResult.FAIL;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      return false;
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.INTRO || fighter.invulnTicks > 0 || fighter.respawnTicks > 0) {
         return true;
      }
      int safe = this.arena.safeZoneTeam(player.getX(), player.getZ(), this.teams.length,
         this.pillars(), this.settings.safeRadius());
      if (safe >= 0) {
         return true;
      }
      if (source.getEntity() instanceof ServerPlayer attacker) {
         Fighter other = this.fighters.get(attacker.getUUID());
         if (other != null && other.team == fighter.team) {
            return true;
         }
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      this.killPlayer(player, fighter);
      return true;
   }

   public void onLeave(UUID uuid) {
      this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase != Phase.ENDED) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了柱联壁合。");
         this.checkWin();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish(null, "对局中止");
      }
   }

   public boolean openIfPlaying(ServerPlayer player) {
      if (this.fighters.get(player.getUUID()) == null) {
         return false;
      }
      this.ctx.send(player, "&7柱联壁合进行中。");
      return true;
   }

   public PillarPummelSettings settings() {
      return this.settings;
   }

   public PummelTeam teamOf(UUID uuid) {
      Fighter fighter = this.fighters.get(uuid);
      if (fighter == null || fighter.team < 0 || fighter.team >= this.teams.length) {
         return null;
      }
      return this.teams[fighter.team];
   }

   public void buy(ServerPlayer player, String id) {
      Fighter fighter = this.fighters.get(player.getUUID());
      PummelTeam team = this.teamOf(player.getUUID());
      if (fighter == null || team == null || this.phase != Phase.FIGHT) {
         return;
      }
      int price = PummelShop.price(id, this.settings);
      if (!PummelShop.takeWool(player, team, price)) {
         this.ctx.send(player, "&c粉末不足，需要 &f" + price + " &c个。");
         return;
      }
      ItemStack reward = PummelShop.give(id, this.settings, fighter.color());
      if (reward.isEmpty()) {
         return;
      }
      if (!player.getInventory().add(reward)) {
         player.drop(reward, false);
      }
      if (PummelShop.BOW.equals(id)) {
         player.getInventory().add(new ItemStack(Items.ARROW, 3));
      }
      this.ctx.send(player, "&a兑换成功（-" + price + " 粉末）。");
   }

   private int pillars() {
      return Math.max(4, this.settings.grid());
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.fightTicks = 0;
      this.setTimer(this.settings.durationTicks());
      this.ctx.broadcast(this.room, "&a开始！铺桥连台、用 TNT 拆对面、占地产分。");
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.title(player, "&6开始", "&e铺桥成台");
         }
      }
   }

   private void tickFight() {
      this.fightTicks++;
      this.scoreTicks--;
      this.genTicks--;
      this.arrowTicks--;
      if (this.genTicks <= 0) {
         this.spawnPowder();
         this.genTicks = GEN_TICKS;
      }
      if (this.arrowTicks <= 0) {
         this.topUpArrows();
         this.arrowTicks = ARROW_TICKS;
      }
      if (this.scoreTicks <= 0) {
         this.tickScore();
         this.scoreTicks = this.settings.scoreInterval() * 20;
      }
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.invulnTicks > 0) {
            fighter.invulnTicks--;
         }
         if (fighter.respawnTicks > 0) {
            fighter.respawnTicks--;
            ServerPlayer player = this.ctx.player(fighter.uuid);
            if (player != null) {
               player.displayClientMessage(TextUtil.color("&a" + Math.max(1, (fighter.respawnTicks + 19) / 20) + " 秒内复活"),
                  true);
               if (fighter.respawnTicks <= 0) {
                  this.finishRespawn(player, fighter);
               }
            }
         }
      }
      this.convertInventories();
      this.tickRegen();
      this.tickTurrets();
      this.tickNuke();
      this.tickCatchUp();
      this.checkWin();
      if (this.phase == Phase.FIGHT && this.settings.winMode() == PillarPummelSettings.WinMode.TIME
         && this.ticksLeft <= 0) {
         this.finishByScore("时间到");
      }
   }

   private void tickScore() {
      for (PlotCell[] row : this.cells) {
         for (PlotCell cell : row) {
            if (cell.scores()) {
               this.teams[cell.owner].score += this.settings.scorePerPlot();
            }
         }
      }
      if (this.settings.winMode() == PillarPummelSettings.WinMode.SCORE) {
         for (PummelTeam team : this.teams) {
            if (team.score >= this.settings.targetScore()) {
               this.finish(team, "达到目标分");
               return;
            }
         }
      }
   }

   private void spawnPowder() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (BlockPos pos : this.generators) {
         PummelColor color = this.generatorColor(pos);
         if (color == null) {
            continue;
         }
         ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
            new ItemStack(color.powderItem()));
         item.setPickUpDelay(5);
         item.setDeltaMovement(0, 0.12, 0);
         level.addFreshEntity(item);
      }
   }

   private PummelColor generatorColor(BlockPos pos) {
      int pillars = this.pillars();
      for (int t = 0; t < this.teams.length; t++) {
         if (this.genOf(t).equals(pos)) {
            return PummelColor.of(t);
         }
      }
      PlotCell cell = this.cellAtBlock(pos);
      if (cell != null && cell.blockgen && cell.owned()) {
         return PummelColor.of(cell.owner);
      }
      return null;
   }

   private void topUpArrows() {
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null || fighter.respawnTicks > 0) {
            continue;
         }
         if (!player.getInventory().hasAnyMatching(stack -> stack.is(Items.BOW))) {
            continue;
         }
         int arrows = 0;
         Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.ARROW)) {
               arrows += inv.getItem(i).getCount();
            }
         }
         if (arrows < 3) {
            player.getInventory().add(new ItemStack(Items.ARROW, 3 - arrows));
         }
      }
   }

   private void convertInventories() {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null || fighter.respawnTicks > 0) {
            continue;
         }
         Item want = fighter.color().powderItem();
         Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!PummelShop.isWool(stack) || stack.is(want)) {
               continue;
            }
            inv.setItem(i, new ItemStack(want, stack.getCount()));
         }
      }
   }

   private void tickRegen() {
      for (PlotCell[] row : this.cells) {
         for (PlotCell cell : row) {
            if (!cell.owned() || cell.hp >= cell.maxHp || cell.hp <= 0) {
               cell.regenTicks = 0;
               continue;
            }
            if (cell.regenTicks <= 0) {
               cell.regenTicks = REGEN_TICKS;
            }
            cell.regenTicks--;
            if (cell.regenTicks <= 0) {
               cell.hp = Math.min(cell.maxHp, cell.hp + 1);
               this.paintPlatform(cell);
               if (cell.hp < cell.maxHp) {
                  cell.regenTicks = REGEN_TICKS;
               }
            }
         }
      }
   }

   private void tickTurrets() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int pillars = this.pillars();
      for (PlotCell[] row : this.cells) {
         for (PlotCell cell : row) {
            if (!cell.turret || !cell.owned()) {
               continue;
            }
            cell.turretTicks--;
            if (cell.turretTicks > 0) {
               continue;
            }
            cell.turretTicks = TURRET_TICKS;
            BlockPos center = cell.center(this.arena, pillars);
            Vec3 from = new Vec3(center.getX() + 0.5, center.getY() + 7.0, center.getZ() + 0.5);
            ServerPlayer target = null;
            double best = TURRET_RANGE * TURRET_RANGE;
            for (UUID uuid : this.seats) {
               Fighter fighter = this.fighters.get(uuid);
               ServerPlayer player = this.ctx.player(uuid);
               if (fighter == null || player == null || fighter.team == cell.owner
                  || fighter.respawnTicks > 0 || fighter.invulnTicks > 0) {
                  continue;
               }
               double dist = player.distanceToSqr(from.x, player.getY(), from.z);
               if (dist <= best) {
                  best = dist;
                  target = player;
               }
            }
            if (target != null) {
               target.hurt(target.damageSources().magic(), 6.0F);
               level.playSound(null, center, SoundEvents.SHULKER_SHOOT, SoundSource.BLOCKS, 0.7F, 1.4F);
            }
         }
      }
   }

   private void tickNuke() {
      if (this.nukeTicks < 0) {
         return;
      }
      this.nukeTicks--;
      if (this.nukeTicks == 40) {
         this.ctx.broadcast(this.room, "&4核弹即将落在场地中央！");
      }
      if (this.nukeTicks > 0) {
         return;
      }
      this.nukeTicks = -1;
      this.detonateNuke();
   }

   private void tickCatchUp() {
      if (!this.settings.catchUp() || this.nextCatchTicks < 0) {
         return;
      }
      this.nextCatchTicks--;
      if (this.nextCatchTicks > 0) {
         return;
      }
      PummelTeam first = this.ranked()[0];
      PummelTeam last = this.ranked()[this.teams.length - 1];
      if (first != last && first.score - last.score >= this.settings.catchGap()) {
         this.grantCatchUp(last);
      }
      this.nextCatchTicks = this.settings.assistIntervalMinutes() * 20 * 60;
   }

   private void grantCatchUp(PummelTeam team) {
      ServerLevel level = this.level();
      BlockPos chest = this.chestOf(team.id);
      if (level != null) {
         this.putStorage(team, new ItemStack(team.color.powderItem(), this.settings.assistWool()), level, chest);
      }
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null || fighter.team != team.id) {
            continue;
         }
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, this.settings.speedSeconds() * 20, 1));
         this.ctx.send(player, "&b落后补助：补给粉末、速度 " + this.settings.speedSeconds() + "s");
      }
      this.ctx.broadcast(this.room, team.display() + " &e获得追赶补助！");
   }

   private boolean tryClaimBridge(ServerPlayer player, Fighter fighter, BlockPos pos) {
      Bridge bridge = this.bridgeAt(pos);
      if (bridge == null || bridge.disabled || !bridge.empty()) {
         return false;
      }
      if (!PummelShop.takeWool(player, this.teams[fighter.team], 1)) {
         this.ctx.send(player, "&c需要 1 个队色粉末来铺桥。");
         return true;
      }
      bridge.owner = fighter.team;
      this.paintBridge(bridge);
      this.paintNearbyCaps(bridge);
      this.tryFormAround(bridge);
      this.ctx.send(player, "&a铺上了一座桥。");
      ServerLevel level = this.level();
      if (level != null) {
         level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
      }
      return true;
   }

   private void tryFormAround(Bridge bridge) {
      int pillars = this.pillars();
      int n = pillars - 1;
      if (bridge.axis == Bridge.Axis.X) {
         if (bridge.b > 0 && bridge.b - 1 < n) {
            this.tryForm(this.cells[bridge.a][bridge.b - 1]);
         }
         if (bridge.b < n) {
            this.tryForm(this.cells[bridge.a][bridge.b]);
         }
      } else {
         if (bridge.a > 0 && bridge.a - 1 < n) {
            this.tryForm(this.cells[bridge.a - 1][bridge.b]);
         }
         if (bridge.a < n) {
            this.tryForm(this.cells[bridge.a][bridge.b]);
         }
      }
   }

   private void tryForm(PlotCell cell) {
      if (cell == null || cell.disabled || cell.owned()) {
         return;
      }
      Bridge[] around = this.around(cell);
      int team = around[0].owner;
      if (team < 0) {
         return;
      }
      for (Bridge bridge : around) {
         if (bridge.disabled || bridge.owner != team) {
            return;
         }
      }
      cell.owner = team;
      cell.hp = PlotCell.BASE_HP;
      cell.maxHp = PlotCell.BASE_HP;
      this.paintPlatform(cell);
      this.ctx.broadcast(this.room, PummelColor.of(team).code() + PummelColor.of(team).label()
         + "队 &7连成了一座平台。");
      ServerLevel level = this.level();
      if (level != null) {
         BlockPos c = cell.center(this.arena, this.pillars());
         level.playSound(null, c, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.6F);
      }
   }

   private void setupSpawn(int team) {
      int[] at = this.spawnCells[team];
      if (at == null) {
         return;
      }
      PlotCell cell = this.cell(at[0], at[1]);
      if (cell == null || cell.disabled) {
         return;
      }
      for (Bridge bridge : this.around(cell)) {
         if (bridge.disabled) {
            continue;
         }
         bridge.owner = team;
         bridge.spawn = true;
         this.paintBridge(bridge);
      }
      cell.owner = team;
      cell.spawn = true;
      cell.hp = PlotCell.BASE_HP;
      cell.maxHp = PlotCell.BASE_HP;
      this.paintSpawn(cell, team);
      this.paintCap(cell.cx, cell.cz);
      this.paintCap(cell.cx + 1, cell.cz);
      this.paintCap(cell.cx, cell.cz + 1);
      this.paintCap(cell.cx + 1, cell.cz + 1);
      int pillars = this.pillars();
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos shop = this.shopOf(team);
      BlockPos chest = this.chestOf(team);
      BlockPos gen = this.genOf(team);
      level.setBlock(shop, Blocks.LECTERN.defaultBlockState(), 3);
      level.setBlock(chest, Blocks.ENDER_CHEST.defaultBlockState(), 3);
      level.setBlock(gen, PummelColor.of(team).generatorBlock().defaultBlockState(), 3);
      this.generators.add(gen);
   }

   private void useGadget(ServerPlayer player, Fighter fighter, ItemStack stack, String gadget,
      BlockPos clicked, BlockPos place) {
      PlotCell cell = this.cellAtBlock(clicked);
      if (cell == null) {
         cell = this.cellAtBlock(place);
      }
      switch (gadget) {
         case PummelShop.NUKE -> this.armNuke(player, fighter, stack);
         case PummelShop.TNT -> this.placeTnt(player, fighter, stack, cell, clicked, place);
         case PummelShop.REPAIR -> {
            if (cell == null || cell.owner != fighter.team) {
               this.ctx.send(player, "&c只能维修己方平台。");
               return;
            }
            if (cell.hp >= cell.maxHp) {
               this.ctx.send(player, "&c这座平台无需维修。");
               return;
            }
            stack.shrink(1);
            cell.hp = Math.min(cell.maxHp, cell.hp + 1);
            this.paintPlatform(cell);
            this.ctx.send(player, "&a平台已维修。");
         }
         case PummelShop.FORT -> this.placeStructure(player, fighter, stack, cell, "fort");
         case PummelShop.TURRET -> this.placeStructure(player, fighter, stack, cell, "turret");
         case PummelShop.BLOCKGEN -> this.placeStructure(player, fighter, stack, cell, "blockgen");
         default -> {
         }
      }
   }

   private void placeTnt(ServerPlayer player, Fighter fighter, ItemStack stack, PlotCell cell,
      BlockPos clicked, BlockPos place) {
      Bridge bridge = this.bridgeAt(clicked);
      if (bridge == null) {
         bridge = this.bridgeAt(place);
      }
      if (bridge != null && !bridge.empty()) {
         if (bridge.spawn) {
            this.ctx.send(player, "&c不能炸出生桥。");
            return;
         }
         stack.shrink(1);
         this.explodeAt(bridge.center(this.arena, this.pillars()));
         this.clearBridge(bridge);
         return;
      }
      if (cell == null || !cell.owned()) {
         this.ctx.send(player, "&cTNT 要放在已占领的桥或平台上。");
         return;
      }
      if (cell.spawn) {
         this.ctx.send(player, "&c不能炸出生台。");
         return;
      }
      stack.shrink(1);
      this.hurtCell(cell, 1);
      this.explodeAt(cell.center(this.arena, this.pillars()));
   }

   private void explodeAt(BlockPos pos) {
      ServerLevel level = this.level();
      if (level == null || pos == null) {
         return;
      }
      level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
      level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.2F,
         false, ServerLevel.ExplosionInteraction.NONE);
   }

   private void placeStructure(ServerPlayer player, Fighter fighter, ItemStack stack, PlotCell cell, String kind) {
      if (cell == null || cell.owner != fighter.team) {
         this.ctx.send(player, "&c只能建在己方平台上。");
         return;
      }
      if (cell.spawn) {
         this.ctx.send(player, "&c出生台不能改建。");
         return;
      }
      if (cell.fort || cell.turret || cell.blockgen) {
         this.ctx.send(player, "&c这座平台已经有建筑了。");
         return;
      }
      stack.shrink(1);
      switch (kind) {
         case "fort" -> {
            cell.fort = true;
            cell.maxHp = PlotCell.FORT_HP;
            cell.hp = PlotCell.FORT_HP;
            this.buildFort(cell);
            this.ctx.send(player, "&a堡垒已建成。");
         }
         case "turret" -> {
            cell.turret = true;
            cell.maxHp = PlotCell.TURRET_HP;
            cell.hp = PlotCell.TURRET_HP;
            cell.turretTicks = TURRET_TICKS;
            this.buildTurret(cell);
            this.ctx.send(player, "&a防御塔已建成。");
         }
         case "blockgen" -> {
            cell.blockgen = true;
            cell.maxHp = PlotCell.BLOCKGEN_HP;
            cell.hp = PlotCell.BLOCKGEN_HP;
            this.buildBlockgen(cell);
            this.ctx.send(player, "&a方块生成器已建成。");
         }
         default -> {
         }
      }
      this.paintPlatform(cell);
      this.restoreSpawnFurniture();
   }

   private void buildFort(PlotCell cell) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos c = cell.center(this.arena, this.pillars());
      int y = this.arena.platformY() + 1;
      BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (!this.fortWall(dx, dz)) {
               continue;
            }
            BlockPos base = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
            this.placeFortBlock(level, base, wall);
            this.placeFortBlock(level, base.above(), wall);
         }
      }
      this.restoreSpawnFurniture();
   }

   private void buildTurret(PlotCell cell) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos c = cell.center(this.arena, this.pillars());
      int y = this.arena.platformY() + 1;
      BlockState bars = Blocks.IRON_BARS.defaultBlockState();
      for (int h = 0; h < 5; h++) {
         level.setBlock(c.offset(0, h + 1, 0), h == 4 ? Blocks.SEA_LANTERN.defaultBlockState() : bars, 3);
      }
      level.setBlock(new BlockPos(c.getX(), y, c.getZ()), Blocks.IRON_BLOCK.defaultBlockState(), 3);
   }

   private void buildBlockgen(PlotCell cell) {
      ServerLevel level = this.level();
      if (level == null || !cell.owned()) {
         return;
      }
      BlockPos c = cell.center(this.arena, this.pillars()).above();
      level.setBlock(c, PummelColor.of(cell.owner).generatorBlock().defaultBlockState(), 3);
      cell.generator = c;
      this.generators.add(c);
   }

   private void clearStructure(PlotCell cell) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos c = cell.center(this.arena, this.pillars());
      if (cell.fort) {
         int y = this.arena.platformY() + 1;
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               if (!this.fortWall(dx, dz)) {
                  continue;
               }
               BlockPos base = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
               if (!this.blockedForFort(base)) {
                  level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
               }
               if (!this.blockedForFort(base.above())) {
                  level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
      }
      if (cell.turret) {
         for (int h = 0; h < 6; h++) {
            level.setBlock(c.offset(0, h + 1, 0), Blocks.AIR.defaultBlockState(), 3);
         }
      }
      if (cell.generator != null) {
         this.generators.remove(cell.generator);
         level.setBlock(cell.generator, Blocks.AIR.defaultBlockState(), 3);
         cell.generator = null;
      }
      this.restoreSpawnFurniture();
   }

   private void armNuke(ServerPlayer player, Fighter fighter, ItemStack stack) {
      if (this.nukeTicks >= 0) {
         this.ctx.send(player, "&c已经有一枚核弹在下落。");
         return;
      }
      stack.shrink(1);
      this.nukeTicks = NUKE_TICKS;
      this.ctx.broadcast(this.room, fighter.color().code() + this.ctx.name(player.getUUID())
         + " &4发射了核弹！中央场地即将被摧毁。");
   }

   private void detonateNuke() {
      Vec3 mid = this.arena.arenaCenter(this.pillars());
      int pillars = this.pillars();
      for (PlotCell[] row : this.cells) {
         for (PlotCell cell : row) {
            if (!cell.owned() || cell.spawn) {
               continue;
            }
            BlockPos c = cell.center(this.arena, pillars);
            if (c.distToCenterSqr(mid.x, mid.y, mid.z) <= NUKE_RADIUS * NUKE_RADIUS) {
               this.destroyCell(cell);
            }
         }
      }
      for (Bridge[] row : this.xBridges) {
         for (Bridge bridge : row) {
            if (bridge.spawn || bridge.empty()) {
               continue;
            }
            BlockPos c = bridge.center(this.arena, pillars);
            if (c.distToCenterSqr(mid.x, mid.y, mid.z) <= 12 * 12) {
               this.clearBridge(bridge);
            }
         }
      }
      for (Bridge[] row : this.zBridges) {
         for (Bridge bridge : row) {
            if (bridge.spawn || bridge.empty()) {
               continue;
            }
            BlockPos c = bridge.center(this.arena, pillars);
            if (c.distToCenterSqr(mid.x, mid.y, mid.z) <= 12 * 12) {
               this.clearBridge(bridge);
            }
         }
      }
      ServerLevel level = this.level();
      if (level != null) {
         level.playSound(null, BlockPos.containing(mid), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.6F);
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
         }
      }
      this.ctx.broadcast(this.room, "&4核弹在中央引爆！");
   }

   private void hurtCell(PlotCell cell, int amount) {
      if (cell == null || !cell.owned() || cell.spawn) {
         return;
      }
      cell.hp = Math.max(0, cell.hp - amount);
      if (cell.fort && cell.hp <= 3) {
         this.clearStructure(cell);
         cell.fort = false;
         cell.maxHp = PlotCell.BASE_HP;
      }
      if ((cell.turret || cell.blockgen) && cell.hp <= 3) {
         this.clearStructure(cell);
         cell.turret = false;
         cell.blockgen = false;
         cell.maxHp = PlotCell.BASE_HP;
      }
      this.paintPlatform(cell);
      if (cell.hp < cell.maxHp && cell.regenTicks <= 0) {
         cell.regenTicks = REGEN_TICKS;
      }
      if (cell.hp <= 0) {
         this.destroyCell(cell);
      }
   }

   private void destroyCell(PlotCell cell) {
      ServerLevel level = this.level();
      this.clearStructure(cell);
      if (level != null) {
         for (BlockPos pos : cell.floorBlocks(this.arena, this.pillars())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         }
      }
      cell.reset();
      for (Bridge bridge : this.around(cell)) {
         this.maybeIsolate(bridge);
      }
   }

   private void maybeIsolate(Bridge bridge) {
      if (bridge.spawn || bridge.empty()) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockPos c = bridge.center(this.arena, this.pillars());
      boolean a;
      boolean b;
      if (bridge.axis == Bridge.Axis.X) {
         a = level.getBlockState(c.offset(0, 0, 1)).isAir();
         b = level.getBlockState(c.offset(0, 0, -1)).isAir();
      } else {
         a = level.getBlockState(c.offset(1, 0, 0)).isAir();
         b = level.getBlockState(c.offset(-1, 0, 0)).isAir();
      }
      if (a && b) {
         this.clearBridge(bridge);
      }
   }

   private void clearBridge(Bridge bridge) {
      bridge.owner = -1;
      this.paintBridge(bridge);
      this.paintNearbyCaps(bridge);
   }

   private void paintBridge(Bridge bridge) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      BlockState want = bridge.empty()
         ? Blocks.STRUCTURE_VOID.defaultBlockState()
         : PummelColor.of(bridge.owner).woolState();
      for (BlockPos pos : bridge.blocks(this.arena, this.pillars())) {
         level.setBlock(pos, want, 3);
      }
   }

   private void paintPlatform(PlotCell cell) {
      ServerLevel level = this.level();
      if (level == null || !cell.owned()) {
         return;
      }
      List<BlockPos> blocks = cell.floorBlocks(this.arena, this.pillars());
      BlockState wool = PummelColor.of(cell.owner).woolState();
      for (int i = 0; i < blocks.size(); i++) {
         BlockState want = cell.floorPresent(i) ? wool : Blocks.AIR.defaultBlockState();
         if (level.getBlockState(blocks.get(i)).getBlock() != want.getBlock()) {
            level.setBlock(blocks.get(i), want, 3);
         }
      }
   }

   private void paintSpawn(PlotCell cell, int team) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      PummelColor color = PummelColor.of(team);
      BlockPos c = cell.center(this.arena, this.pillars());
      int y = this.arena.platformY();
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), color.concreteState(), 3);
         }
      }
      level.setBlock(c, color.glassState(), 3);
   }

   private void paintNearbyCaps(Bridge bridge) {
      int pillars = this.pillars();
      if (bridge.axis == Bridge.Axis.X) {
         this.paintCap(bridge.a, bridge.b);
         this.paintCap(bridge.a + 1, bridge.b);
      } else {
         this.paintCap(bridge.a, bridge.b);
         this.paintCap(bridge.a, bridge.b + 1);
      }
   }

   private void paintCap(int gx, int gz) {
      int pillars = this.pillars();
      if (gx < 0 || gz < 0 || gx >= pillars || gz >= pillars) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int team = -1;
      boolean mixed = false;
      int[] owners = {
         this.xOwner(gx - 1, gz), this.xOwner(gx, gz),
         this.zOwner(gx, gz - 1), this.zOwner(gx, gz)
      };
      for (int next : owners) {
         if (next < 0) {
            continue;
         }
         if (team < 0) {
            team = next;
         } else if (team != next) {
            mixed = true;
         }
      }
      BlockState want;
      if (mixed) {
         want = Blocks.GRAY_CONCRETE.defaultBlockState();
      } else if (team >= 0) {
         want = PummelColor.of(team).concreteState();
      } else {
         want = Blocks.QUARTZ_BLOCK.defaultBlockState();
      }
      level.setBlock(this.arena.pillarCap(gx, gz, pillars), want, 3);
   }

   private int xOwner(int gx, int gz) {
      if (gx < 0 || gz < 0 || gx >= this.xBridges.length || gz >= this.xBridges[0].length) {
         return -1;
      }
      return this.xBridges[gx][gz].owner;
   }

   private int zOwner(int gx, int gz) {
      if (gx < 0 || gz < 0 || gx >= this.zBridges.length || gz >= this.zBridges[0].length) {
         return -1;
      }
      return this.zBridges[gx][gz].owner;
   }

   private void killPlayer(ServerPlayer player, Fighter fighter) {
      if (fighter.respawnTicks > 0 || this.phase != Phase.FIGHT) {
         this.heal(player);
         return;
      }
      this.dropWoolOnDeath(player);
      this.teams[fighter.team].score = Math.max(0, this.teams[fighter.team].score + this.settings.deathScore());
      UUID killer = this.killerOf(player);
      if (killer != null && !killer.equals(player.getUUID()) && this.settings.killScore() != 0) {
         Fighter other = this.fighters.get(killer);
         if (other != null && other.team != fighter.team) {
            this.teams[other.team].kills++;
            this.teams[other.team].score += this.settings.killScore();
         }
      }
      fighter.respawnTicks = Math.max(20, this.settings.respawnInvuln() * 20);
      this.heal(player);
      player.setGameMode(GameType.SPECTATOR);
      player.getInventory().clearContent();
      ServerLevel level = this.level();
      if (level != null) {
         Vec3 spawn = this.spawnVec(fighter.team);
         this.arena.teleport(player, level, spawn.add(0, 6, 0));
      }
      this.title(player, "", "&a" + (fighter.respawnTicks / 20) + " 秒内复活");
   }

   private void finishRespawn(ServerPlayer player, Fighter fighter) {
      player.setGameMode(GameType.SURVIVAL);
      this.heal(player);
      player.getInventory().clearContent();
      this.giveKit(player, fighter);
      fighter.invulnTicks = 20;
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.spawnVec(fighter.team));
      }
   }

   private UUID killerOf(ServerPlayer player) {
      if (player.getLastHurtByMob() instanceof ServerPlayer sp) {
         return sp.getUUID();
      }
      return null;
   }

   private void dropWoolOnDeath(ServerPlayer player) {
      if (this.settings.woolDrop() == PillarPummelSettings.WoolDrop.NONE) {
         this.stripWool(player);
         return;
      }
      int total = 0;
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (PummelShop.isWool(stack)) {
            total += stack.getCount();
         }
      }
      int drop = this.settings.woolDrop() == PillarPummelSettings.WoolDrop.HALF ? total / 2 : total;
      Item want = Items.WHITE_CONCRETE_POWDER;
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter != null) {
         want = fighter.color().powderItem();
      }
      this.stripWool(player);
      if (drop > 0) {
         ServerLevel level = this.level();
         if (level != null) {
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
               new ItemStack(want, drop)));
         }
      }
   }

   private void stripWool(ServerPlayer player) {
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (PummelShop.isWool(inv.getItem(i))) {
            inv.setItem(i, ItemStack.EMPTY);
         }
      }
   }

   private void enforce() {
      ServerLevel level = this.level();
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null) {
            continue;
         }
         if (fighter.respawnTicks > 0) {
            if (player.getY() <= this.arena.basinY() + 1.5 && level != null) {
               Vec3 spawn = this.spawnVec(fighter.team);
               this.arena.teleport(player, level, spawn.add(0, 6, 0));
            }
            continue;
         }
         player.removeEffect(MobEffects.SATURATION);
         player.removeEffect(MobEffects.REGENERATION);
         player.getFoodData().setFoodLevel(20);
         player.getFoodData().setSaturation(20.0F);
         if (this.phase == Phase.FIGHT && player.getY() <= this.arena.basinY() + 1.5) {
            this.killPlayer(player, fighter);
            continue;
         }
         if (!this.arena.contains(player.getX(), player.getY(), player.getZ()) && level != null) {
            this.arena.teleport(player, level, this.spawnVec(fighter.team));
         }
         if (!player.getInventory().hasAnyMatching(stack -> stack.is(Items.IRON_SWORD)
            || stack.is(Items.WOODEN_SWORD) || stack.is(Items.STONE_SWORD))) {
            this.giveSword(player);
         }
      }
   }

   private void checkWin() {
      if (this.phase != Phase.FIGHT) {
         return;
      }
      int online = 0;
      int lastTeam = -1;
      for (UUID uuid : this.seats) {
         if (this.ctx.player(uuid) != null) {
            Fighter fighter = this.fighters.get(uuid);
            if (fighter != null) {
               online++;
               lastTeam = fighter.team;
            }
         }
      }
      if (online <= 0) {
         this.finish(null, "无人在场");
      } else if (online <= this.settings.teamSize() && lastTeam >= 0) {
         boolean only = true;
         for (UUID uuid : this.seats) {
            Fighter fighter = this.fighters.get(uuid);
            if (fighter != null && this.ctx.player(uuid) != null && fighter.team != lastTeam) {
               only = false;
               break;
            }
         }
         if (only && this.teams.length > 1 && online > 0 && this.fightTicks > 40) {
            this.finish(this.teams[lastTeam], "对方离场");
         }
      }
   }

   private void finishByScore(String reason) {
      PummelTeam[] ranked = this.ranked();
      PummelTeam winner = ranked[0];
      if (ranked.length > 1 && ranked[0].score == ranked[1].score) {
         winner = this.breakTie(ranked);
      }
      this.finish(winner, reason);
   }

   private PummelTeam breakTie(PummelTeam[] ranked) {
      List<PummelTeam> tied = new ArrayList<>();
      int top = ranked[0].score;
      for (PummelTeam team : ranked) {
         if (team.score == top) {
            tied.add(team);
         }
      }
      Comparator<PummelTeam> cmp = switch (this.settings.tieBreak()) {
         case KILLS -> Comparator.comparingInt((PummelTeam t) -> t.kills).reversed();
         case PLOTS -> Comparator.comparingInt((PummelTeam t) -> this.ownedCount(t.id)).reversed();
         case WOOL -> Comparator.comparingInt(this::teamWool).reversed();
         case RANDOM -> (a, b) -> ThreadLocalRandom.current().nextBoolean() ? -1 : 1;
      };
      tied.sort(cmp);
      return tied.get(0);
   }

   private int teamWool(PummelTeam team) {
      int total = team.woolStored();
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter != null && player != null && fighter.team == team.id) {
            total += PummelShop.woolCount(player, null);
         }
      }
      return total;
   }

   private int ownedCount(int team) {
      int n = 0;
      for (PlotCell[] row : this.cells) {
         for (PlotCell cell : row) {
            if (cell.owner == team && !cell.spawn) {
               n++;
            }
         }
      }
      return n;
   }

   private PummelTeam[] ranked() {
      PummelTeam[] copy = this.teams.clone();
      java.util.Arrays.sort(copy, (a, b) -> Integer.compare(b.score, a.score));
      return copy;
   }

   private void finish(PummelTeam winner, String reason) {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l柱联壁合结算 &7" + reason);
      if (winner != null) {
         this.ctx.broadcast(this.room, "&a获胜：" + winner.display() + " &f" + winner.score + " 分");
      }
      for (PummelTeam team : this.ranked()) {
         this.ctx.broadcast(this.room, team.display() + " &f" + team.score + " 分 &8| &7占地 "
            + this.ownedCount(team.id));
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            if (winner != null && this.fighters.get(uuid) != null && this.fighters.get(uuid).team == winner.id) {
               this.title(player, "&6胜利", winner.display());
            }
            this.restore(player);
         }
      }
      this.ctx.pillarPummel().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.pillarPummel().remove(this);
   }

   private void giveKit(ServerPlayer player, Fighter fighter) {
      this.giveSword(player);
      this.dyeArmor(player, fighter.color());
   }

   private void giveSword(ServerPlayer player) {
      ItemStack sword = switch (this.settings.startWeapon()) {
         case WOOD -> new ItemStack(Items.WOODEN_SWORD);
         case STONE -> new ItemStack(Items.STONE_SWORD);
         case IRON -> new ItemStack(Items.IRON_SWORD);
         case NONE -> ItemStack.EMPTY;
      };
      if (!sword.isEmpty()) {
         sword.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
         player.getInventory().add(sword);
      }
   }

   private void dyeArmor(ServerPlayer player, PummelColor color) {
      ItemStack[] pieces = {
         new ItemStack(Items.LEATHER_HELMET),
         new ItemStack(Items.LEATHER_CHESTPLATE),
         new ItemStack(Items.LEATHER_LEGGINGS),
         new ItemStack(Items.LEATHER_BOOTS)
      };
      DyedItemColor dye = new DyedItemColor(color.rgb(), true);
      for (ItemStack piece : pieces) {
         piece.set(DataComponents.DYED_COLOR, dye);
         piece.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      }
      player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, pieces[0]);
      player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, pieces[1]);
      player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, pieces[2]);
      player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, pieces[3]);
   }

   private void putStorage(PummelTeam team, ItemStack stack, ServerLevel level, BlockPos chest) {
      ItemStack leftover = stack.copy();
      for (int i = 0; i < team.storage.getContainerSize() && !leftover.isEmpty(); i++) {
         if (team.storage.getItem(i).isEmpty()) {
            team.storage.setItem(i, leftover.copy());
            leftover.setCount(0);
         }
      }
      if (!leftover.isEmpty()) {
         level.addFreshEntity(new ItemEntity(level, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, leftover));
      }
   }

   private void openStorage(ServerPlayer player, PummelTeam team) {
      player.openMenu(new net.minecraft.world.SimpleMenuProvider(
         (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, team.storage),
         TextUtil.color("&f团队箱子")
      ));
   }

   private void assignTeams() {
      List<UUID> shuffled = new ArrayList<>(this.seats);
      java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
      int i = 0;
      for (UUID uuid : shuffled) {
         this.fighters.put(uuid, new Fighter(uuid, i % this.teams.length));
         i++;
      }
   }

   private void applyNametag(ServerPlayer player, Fighter fighter) {
      ServerScoreboard board = this.ctx.server().getScoreboard();
      String name = this.teamPrefix + fighter.team;
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
         team.setColor(fighter.color().formatting());
         team.setCollisionRule(Team.CollisionRule.PUSH_OWN_TEAM);
      }
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void clearNametag(ServerPlayer player) {
      ServerScoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam current = board.getPlayersTeam(player.getScoreboardName());
      if (current != null && current.getName().startsWith("pp")) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.fallDistance = 0.0F;
      player.removeAllEffects();
      player.invulnerableTime = 20;
   }

   private void restore(ServerPlayer player) {
      player.closeContainer();
      player.removeAllEffects();
      this.board.remove(player);
      this.boss.removePlayer(player);
      this.clearNametag(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   private void setTimer(int ticks) {
      this.ticksLeft = ticks;
      this.phaseMaxTicks = Math.max(1, ticks);
   }

   private void updateBoss() {
      if (this.phase == Phase.INTRO) {
         this.boss.setName(TextUtil.color("&e准备 &f" + Math.max(0, this.ticksLeft / 20) + "s"));
         this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) this.phaseMaxTicks));
         return;
      }
      int left = Math.max(0, this.ticksLeft);
      this.boss.setName(TextUtil.color("&6剩余 &f" + (left / 20 / 60) + ":" + String.format("%02d", (left / 20) % 60)));
      this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) this.phaseMaxTicks));
   }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      int left = Math.max(0, this.ticksLeft);
      lines.add("&7时间 &f" + (this.phase == Phase.INTRO ? "准备" : (left / 20 / 60) + ":" + String.format("%02d", (left / 20) % 60)));
      for (PummelTeam team : this.ranked()) {
         lines.add(team.display() + " &f" + team.score + " &8(" + this.ownedCount(team.id) + ")");
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.board.update(player, lines);
         }
      }
   }

   private void title(ServerPlayer player, String title, String sub) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub)));
   }

   private int[] pickFreeCell(int[] prefer, boolean[] taken, int n) {
      int px = prefer == null ? 0 : prefer[0];
      int pz = prefer == null ? 0 : prefer[1];
      if (prefer != null) {
         PlotCell cell = this.cell(px, pz);
         if (cell != null && !cell.disabled && !taken[px * n + pz]) {
            return prefer;
         }
      }
      int best = Integer.MAX_VALUE;
      int[] found = null;
      for (int cx = 0; cx < n; cx++) {
         for (int cz = 0; cz < n; cz++) {
            PlotCell cell = this.cells[cx][cz];
            if (cell.disabled || taken[cx * n + cz]) {
               continue;
            }
            int dist = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
            if (dist < best) {
               best = dist;
               found = new int[] {cx, cz};
            }
         }
      }
      return found;
   }

   private int[] spawnCellOf(int team) {
      if (team < 0 || team >= this.spawnCells.length) {
         return null;
      }
      return this.spawnCells[team];
   }

   private Vec3 spawnVec(int team) {
      int[] at = this.spawnCellOf(team);
      if (at == null) {
         return this.arena.spawn(team, this.pillars());
      }
      return this.arena.spawnAt(at[0], at[1], this.pillars());
   }

   private BlockPos shopOf(int team) {
      int[] at = this.spawnCellOf(team);
      if (at == null) {
         return this.arena.shopPos(team, this.pillars());
      }
      return this.arena.shopPos(at[0], at[1], this.pillars());
   }

   private BlockPos chestOf(int team) {
      int[] at = this.spawnCellOf(team);
      if (at == null) {
         return this.arena.chestPos(team, this.pillars());
      }
      return this.arena.chestPos(at[0], at[1], this.pillars());
   }

   private BlockPos genOf(int team) {
      int[] at = this.spawnCellOf(team);
      if (at == null) {
         return this.arena.spawnGenerator(team, this.pillars());
      }
      return this.arena.spawnGenerator(at[0], at[1], this.pillars());
   }

   private boolean fortWall(int dx, int dz) {
      if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
         return false;
      }
      if (dx == 0 || dz == 0) {
         return false;
      }
      return true;
   }

   private boolean blockedForFort(BlockPos pos) {
      for (int t = 0; t < this.teams.length; t++) {
         if (pos.equals(this.shopOf(t)) || pos.equals(this.chestOf(t)) || pos.equals(this.genOf(t))) {
            return true;
         }
      }
      PlotCell cell = this.cellAtBlock(pos);
      return cell != null && cell.spawn;
   }

   private void placeFortBlock(ServerLevel level, BlockPos pos, BlockState wall) {
      if (this.blockedForFort(pos)) {
         return;
      }
      level.setBlock(pos, wall, 3);
   }

   private void restoreSpawnFurniture() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (int t = 0; t < this.teams.length; t++) {
         if (this.spawnCellOf(t) == null) {
            continue;
         }
         BlockPos shop = this.shopOf(t);
         BlockPos chest = this.chestOf(t);
         BlockPos gen = this.genOf(t);
         level.setBlock(shop, Blocks.LECTERN.defaultBlockState(), 3);
         level.setBlock(chest, Blocks.ENDER_CHEST.defaultBlockState(), 3);
         level.setBlock(gen, PummelColor.of(t).generatorBlock().defaultBlockState(), 3);
         if (!this.generators.contains(gen)) {
            this.generators.add(gen);
         }
      }
   }

   private Bridge[] around(PlotCell cell) {
      return new Bridge[] {
         this.xBridges[cell.cx][cell.cz],
         this.xBridges[cell.cx][cell.cz + 1],
         this.zBridges[cell.cx][cell.cz],
         this.zBridges[cell.cx + 1][cell.cz]
      };
   }

   private Bridge bridgeAt(BlockPos pos) {
      int pillars = this.pillars();
      int[] x = this.arena.xBridgeAt(pos.getX(), pos.getZ(), pillars);
      if (x != null && Math.abs(pos.getY() - this.arena.platformY()) <= 1) {
         Bridge bridge = this.xBridges[x[0]][x[1]];
         return bridge.disabled ? null : bridge;
      }
      int[] z = this.arena.zBridgeAt(pos.getX(), pos.getZ(), pillars);
      if (z != null && Math.abs(pos.getY() - this.arena.platformY()) <= 1) {
         Bridge bridge = this.zBridges[z[0]][z[1]];
         return bridge.disabled ? null : bridge;
      }
      return null;
   }

   private PlotCell cell(int cx, int cz) {
      if (cx < 0 || cz < 0 || cx >= this.cells.length || cz >= this.cells[0].length) {
         return null;
      }
      return this.cells[cx][cz];
   }

   private PlotCell cellAtBlock(BlockPos pos) {
      int[] at = this.arena.cellAt(pos, this.pillars());
      return at == null ? null : this.cells[at[0]][at[1]];
   }

   static final class Fighter {
      final UUID uuid;
      final int team;
      int invulnTicks;
      int respawnTicks;

      Fighter(UUID uuid, int team) {
         this.uuid = uuid;
         this.team = team;
      }

      PummelColor color() {
         return PummelColor.of(this.team);
      }
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos,
      float yaw, float pitch, GameType gameType, List<ItemStack> items) {
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
