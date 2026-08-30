package net.exmo.sreGame.games.rhythm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 节奏大师对局：音符沿双轨（红左蓝右）下落，左键打红、右键打蓝，判定 Perfect/Great/Miss。
 * 支持单人 / 合作（分轨）/ 对战（同谱竞分 + 干扰道具）/ 纯左键四种模式。
 */
public final class RhythmMatch {

   public static final int MAX_HP = 10;
   public static final int PERFECT_SCORE = 300;
   public static final int GREAT_SCORE = 200;
   public static final int GOLD_PERFECT_SCORE = 600;
   public static final int GOLD_GREAT_SCORE = 400;
   public static final int ACCENT_PERFECT_SCORE = 450;
   public static final int ACCENT_GREAT_SCORE = 300;
   /** 输入延迟补偿（毫秒）：抵消客户端发包/右键防抖，让按得准就能命中。 */
   public static final int INPUT_LATENCY_MS = 50;
   public static final double SPEED_BLOCKS_PER_SEC = 20.0;
   public static final double TRAVEL_BLOCKS = 24.0;
   public static final double HORIZONTAL_TRAVEL_BLOCKS = 16.0;
   public static final double SLOW_FACTOR = 0.55;
   public static final long EFFECT_MS = 5000L;
   private static final int[] INTERFERENCE_THRESHOLDS = {30, 60, 100};

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seatIds;
   private final RhythmSettings settings;
   private final RhythmChart chart;
   private final RhythmSettings.Mode mode;
   private final ServerLevel level;
   private final SidebarBoard board;
   private final Map<UUID, Seat> seatByUuid = new HashMap<>();
   private final List<Seat> seats = new ArrayList<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<BlockPos> platform = new ArrayList<>();
   private boolean begun;
   private boolean ended;
   private long elapsedTicks;
   private int boardTicks;

   public RhythmMatch(GameContext ctx, GameRoom room, List<UUID> seatIds, RhythmChart chart,
                      RhythmSettings settings, double[][] origins) {
      this.ctx = ctx;
      this.room = room;
      this.seatIds = List.copyOf(seatIds);
      this.settings = settings;
      this.mode = settings.mode();
      this.chart = this.mode == RhythmSettings.Mode.PURE_LEFT ? chart.asPureLeft() : chart;
      this.level = ctx.server().overworld();
      this.board = new SidebarBoard(ctx.server());

      Stats shared = this.mode == RhythmSettings.Mode.COOP ? new Stats() : null;
      for (int i = 0; i < this.seatIds.size(); i++) {
         UUID uuid = this.seatIds.get(i);
         double ox = origins[i][0];
         double oy = origins[i][1];
         double oz = origins[i][2];
         boolean red = this.mode != RhythmSettings.Mode.COOP || i == 0;
         boolean blue = this.mode != RhythmSettings.Mode.COOP || i == 1;
         Stats stats = shared != null ? shared : new Stats();
         Seat seat = new Seat(uuid, ox, oy, oz, red, blue, stats);
         this.seats.add(seat);
         this.seatByUuid.put(uuid, seat);
      }
   }

   public UUID id() {
      return this.id;
   }

   public List<UUID> members() {
      return this.seatIds;
   }

   public void start() {
      this.begun = true;
      for (Seat seat : this.seats) {
         ServerPlayer player = this.ctx.player(seat.uuid);
         if (player == null) {
            continue;
         }
         this.saved.put(seat.uuid, Saved.capture(player));
         player.closeContainer();
         player.getInventory().clearContent();
         this.prepareInputItems(player);
         player.setGameMode(GameType.ADVENTURE);
         player.setInvisible(false);
         player.teleportTo(this.level, seat.x, seat.y, seat.z, 0.0F, 0.0F);
         this.board.create(player, "&d节奏大师");
         this.placePlatform(seat);
      }
      this.pushHud();
      this.ctx.broadcast(this.room, "&d节奏大师开始！&7曲目： &e" + this.chart.name
         + " &7| 音符 &f" + this.chart.noteCount()
         + " &7| 模式 &f" + this.mode.label()
         + " &7| " + this.inputHint());
   }

   private void prepareInputItems(ServerPlayer player) {
      if (this.mode == RhythmSettings.Mode.PURE_LEFT) {
         player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
         player.getInventory().setItem(1, new ItemStack(Items.GOLDEN_SWORD));
         player.getInventory().selected = 0;
      } else {
         // 手持物品以让右键（空手对着空气）也产生 use-item 包，用于检测右键。
         player.getInventory().setItem(0, new ItemStack(Items.STICK));
         player.getInventory().selected = 0;
      }
   }

   public String inputHint() {
      return this.mode == RhythmSettings.Mode.PURE_LEFT
         ? "钻石剑左键红 / 金剑左键金"
         : this.mode == RhythmSettings.Mode.COOP ? "左=红 右=蓝，分轨" : "左键红 / 右键蓝";
   }

   private void placePlatform(Seat seat) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = new BlockPos((int) Math.floor(seat.x) + dx, (int) Math.floor(seat.y) - 1, (int) Math.floor(seat.z) + dz);
            this.platform.add(pos);
            this.level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
         }
      }
   }

   public void tick() {
      if (!this.begun || this.ended) {
         return;
      }
      this.elapsedTicks++;
      long nowMs = this.elapsedTicks * 50L;
      for (Seat seat : this.seats) {
         seat.wall.tickEffects();
         seat.tick(nowMs);
      }
      this.boardTicks++;
      if (this.boardTicks % 10 == 0) {
         this.pushHud();
      }
      if (this.boardTicks % 5 == 0) {
         for (Seat seat : this.seats) {
            seat.actionBar(nowMs);
         }
         this.enforce();
      }
      // 单人/合作：血空即失败
      if (this.mode != RhythmSettings.Mode.VERSUS) {
         for (Seat seat : this.seats) {
            if (seat.stats.hp <= 0) {
               this.finish();
               return;
            }
         }
      }
      if (nowMs >= this.chart.lengthMs) {
         this.finish();
      }
   }

   public void handleLeftClick(ServerPlayer player) {
      Seat seat = this.seatByUuid.get(player.getUUID());
      if (seat != null) {
         RhythmChart.Lane lane = RhythmChart.Lane.RED;
         if (this.mode == RhythmSettings.Mode.PURE_LEFT) {
            if (player.getMainHandItem().is(Items.DIAMOND_SWORD)) {
               lane = RhythmChart.Lane.RED;
            } else if (player.getMainHandItem().is(Items.GOLDEN_SWORD)) {
               lane = RhythmChart.Lane.BLUE;
            } else {
               return;
            }
         }
         seat.handleClick(lane, this.elapsedTicks * 50L);
      }
   }

   public void handleRightClick(ServerPlayer player) {
      if (this.mode == RhythmSettings.Mode.PURE_LEFT) {
         return;
      }
      Seat seat = this.seatByUuid.get(player.getUUID());
      if (seat != null) {
         seat.handleClick(RhythmChart.Lane.BLUE, this.elapsedTicks * 50L);
      }
   }

   /** 定时把玩家钉回座位、保持冒险模式并补发手持物（防止掉落/走开）。 */
   private void enforce() {
      for (Seat seat : this.seats) {
         ServerPlayer player = this.ctx.player(seat.uuid);
         if (player == null) {
            continue;
         }
         if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
         }
         double dx = player.getX() - seat.x;
         double dz = player.getZ() - seat.z;
         if (dx * dx + dz * dz > 2.25) {
            player.teleportTo(this.level, seat.x, seat.y, seat.z, 0.0F, 0.0F);
         }
         if (this.mode == RhythmSettings.Mode.PURE_LEFT) {
            if (!player.getInventory().getItem(0).is(Items.DIAMOND_SWORD)) {
               player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            }
            if (!player.getInventory().getItem(1).is(Items.GOLDEN_SWORD)) {
               player.getInventory().setItem(1, new ItemStack(Items.GOLDEN_SWORD));
            }
            if (player.getInventory().selected != 0 && player.getInventory().selected != 1) {
               player.getInventory().selected = 0;
            }
         } else if (player.getMainHandItem().isEmpty()) {
            player.getInventory().setItem(0, new ItemStack(Items.STICK));
         }
      }
   }

   public void onLeave(UUID uuid) {
      Seat seat = this.seatByUuid.remove(uuid);
      if (seat == null) {
         return;
      }
      this.seats.remove(seat);
      seat.wall.despawnAll();
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      }
      if (this.mode == RhythmSettings.Mode.COOP && this.seats.size() < 2) {
         this.ctx.broadcast(this.room, "&c队友离开，合作提前结算。");
         this.finish();
      } else if (this.seats.isEmpty()) {
         this.finish();
      } else if (this.mode == RhythmSettings.Mode.VERSUS && this.seats.size() < 2) {
         this.ctx.broadcast(this.room, "&c对手不足，对战提前结算。");
         this.finish();
      }
   }

   public void endNow() {
      if (!this.ended) {
         this.finish();
      }
   }

   private void finish() {
      if (this.ended) {
         return;
      }
      this.ended = true;
      if (this.mode == RhythmSettings.Mode.VERSUS) {
         List<Seat> ranked = new ArrayList<>(this.seats);
         ranked.sort(Comparator.comparingInt((Seat s) -> s.stats.score).reversed());
         this.ctx.broadcast(this.room, "&d&l节奏大师 · 对战结算");
         int place = 1;
         for (Seat s : ranked) {
            this.ctx.broadcast(this.room, "&8" + place + ". &f" + this.ctx.name(s.uuid)
               + " &e" + s.stats.score + " 分"
               + " &8| &7连击 " + s.stats.maxCombo
               + " &8| &7评级 " + gradeOf(s.stats));
            place++;
         }
         if (!ranked.isEmpty()) {
            this.ctx.broadcast(this.room, "&6胜者： &e" + this.ctx.name(ranked.get(0).uuid)
               + " &7（" + ranked.get(0).stats.score + " 分）");
         }
      } else {
         Stats stats = this.seats.isEmpty() ? new Stats() : this.seats.get(0).stats;
         String grade = gradeOf(stats);
         this.ctx.broadcast(this.room, "&d&l节奏大师结算 &8| &7曲目 &f" + this.chart.name);
         this.ctx.broadcast(this.room, "&f得分 &e" + stats.score
            + " &8| &f最大连击 &e" + stats.maxCombo
            + " &8| &aPerfect " + stats.perfect
            + " &8| &bGreat " + stats.great
            + " &8| &7Miss " + stats.miss
            + " &8| &f评级 &6" + grade);
      }
      this.cleanup();
   }

   private void cleanup() {
      this.board.removeAll();
      for (Seat seat : this.seats) {
         seat.wall.despawnAll();
      }
      for (BlockPos pos : this.platform) {
         this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      }
      this.platform.clear();
      for (UUID uuid : this.seatIds) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.rhythm().remove(this);
      this.ctx.rooms().onMatchEnded(this.id);
   }

   private void restore(ServerPlayer player) {
      player.setInvisible(false);
      player.closeContainer();
      this.board.remove(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         ServerLevel overworld = this.ctx.server().overworld();
         player.teleportTo(overworld, overworld.getSharedSpawnPos().getX() + 0.5,
            overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ() + 0.5, 0.0F, 0.0F);
      }
   }

   private void pushHud() {
      long nowMs = this.elapsedTicks * 50L;
      for (Seat seat : this.seats) {
         ServerPlayer player = this.ctx.player(seat.uuid);
         if (player == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7&m---------------");
         lines.add("&7曲目 &e" + this.chart.name);
         lines.add("&7分数 &e" + seat.stats.score);
         lines.add("&7连击 &e" + seat.stats.combo + " &7/&f" + seat.stats.maxCombo);
         lines.add("&7血量 &c" + hpBar(seat.stats.hp));
         lines.add("&7进度 &f" + progressBar(nowMs));
         if (this.mode == RhythmSettings.Mode.VERSUS) {
            lines.add("&7&m---------------");
            List<Seat> ranked = new ArrayList<>(this.seats);
            ranked.sort(Comparator.comparingInt((Seat s) -> s.stats.score).reversed());
            int shown = 0;
            for (Seat s : ranked) {
               if (shown >= 6) {
                  break;
               }
               lines.add((s.uuid.equals(seat.uuid) ? "&a" : "&f") + this.ctx.name(s.uuid) + " &e" + s.stats.score);
               shown++;
            }
         } else if (this.mode == RhythmSettings.Mode.COOP) {
            lines.add("&7团队连击 &f" + this.seats.get(0).stats.combo + " &8| &7血量 &c" + hpBar(this.seats.get(0).stats.hp));
         }
         lines.add("&7&m---------------");
         this.board.update(player, lines);
      }
   }

   private String hpBar(int hp) {
      int filled = Math.max(0, Math.min(MAX_HP, hp));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < MAX_HP; i++) {
         sb.append(i < filled ? "&c█" : "&8█");
      }
      return sb.toString();
   }

   private String progressBar(long nowMs) {
      double ratio = Math.min(1.0, nowMs / (double) this.chart.lengthMs);
      int width = 20;
      int filled = (int) Math.round(ratio * width);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < width; i++) {
         sb.append(i < filled ? "&a▓" : "&8░");
      }
      return sb.toString();
   }

   private static String gradeOf(Stats stats) {
      if (stats.hp <= 0) {
         return "&c失败";
      }
      int total = stats.perfect + stats.great + stats.miss;
      if (total == 0) {
         return "&7—";
      }
      double accuracy = (stats.perfect + stats.great) / (double) total;
      double perfectRatio = stats.perfect / (double) total;
      if (stats.miss == 0 && perfectRatio >= 0.6) {
         return "&6S";
      }
      if (accuracy >= 0.9) {
         return "&aA";
      }
      if (accuracy >= 0.75) {
         return "&bB";
      }
      return "&eC";
   }

   private void title(ServerPlayer player, String text, String sub) {
      if (player == null) {
         return;
      }
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(text)));
      if (sub != null) {
         player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub)));
      }
   }

   private void nausea(ServerPlayer player, int ticks) {
      if (player != null) {
         player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0, false, false, true));
      }
   }

   /** 定向给单个玩家播放音符盒音效（击打反馈音，不打扰其他人）。 */
   private void playSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
      if (player == null) {
         return;
      }
      player.connection.send(new ClientboundSoundPacket(sound, SoundSource.RECORDS,
         player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
   }

   private static final class Stats {
      int score;
      int combo;
      int maxCombo;
      int hp = MAX_HP;
      int perfect;
      int great;
      int miss;
   }

   private final class Seat {
      final UUID uuid;
      final double x;
      final double y;
      final double z;
      final boolean redAssigned;
      final boolean blueAssigned;
      final Stats stats;
      final RhythmWall wall;
      final boolean horizontal;
      final double travelBlocks;
      final double baseSpeedBps;
      final long travelMs;
      final int[] state;
      final int[] entityId;
      final boolean[] interferenceUsed = new boolean[INTERFERENCE_THRESHOLDS.length];
      long chaosUntil;
      long slowUntil;
      long fogUntil;
      boolean failed;

      Seat(UUID uuid, double x, double y, double z, boolean red, boolean blue, Stats stats) {
         this.uuid = uuid;
         this.x = x;
         this.y = y;
         this.z = z;
         this.redAssigned = red;
         this.blueAssigned = blue;
         this.stats = stats;
         this.horizontal = RhythmMatch.this.settings.orientation() == RhythmSettings.Orientation.HORIZONTAL;
         this.travelBlocks = this.horizontal ? HORIZONTAL_TRAVEL_BLOCKS : TRAVEL_BLOCKS;
         this.wall = new RhythmWall(RhythmMatch.this.level, x, y, z, this.travelBlocks,
            RhythmMatch.this.settings.orientation(), RhythmMatch.this.mode == RhythmSettings.Mode.PURE_LEFT);
         this.baseSpeedBps = SPEED_BLOCKS_PER_SEC * RhythmMatch.this.settings.speedMultiplier();
         this.travelMs = (long) (this.travelBlocks / this.baseSpeedBps * 1000.0);
         this.state = new int[RhythmMatch.this.chart.noteCount()];
         this.entityId = new int[RhythmMatch.this.chart.noteCount()];
      }

      boolean assigned(RhythmChart.Lane lane) {
         return lane == RhythmChart.Lane.RED ? this.redAssigned : this.blueAssigned;
      }

      double speedBps() {
         return this.baseSpeedBps * (this.slowUntil > 0 ? SLOW_FACTOR : 1.0);
      }

      /** 音符在主轴上的坐标：纵向自上而下、横向自左向右。 */
      double progressAt(long spawnMs, long nowMs, double bps) {
         double dist = (nowMs - spawnMs) / 1000.0 * bps;
         return this.horizontal
            ? this.wall.judge() - this.travelBlocks + dist
            : this.wall.judge() + this.travelBlocks - dist;
      }

      void tick(long nowMs) {
         if (this.failed) {
            return;
         }
         if (this.stats.hp <= 0) {
            this.failed = true;
            this.wall.despawnAll();
            ServerPlayer p = RhythmMatch.this.ctx.player(this.uuid);
            if (p != null) {
               RhythmMatch.this.title(p, "&c血量归零", "&7等待其他玩家");
            }
            return;
         }
         if (this.chaosUntil > 0 && nowMs >= this.chaosUntil) {
            this.chaosUntil = 0;
            this.wall.setLaneSwap(false);
         }
         if (this.fogUntil > 0) {
            if (nowMs < this.fogUntil) {
               this.wall.spawnFog();
            } else {
               this.fogUntil = 0;
            }
         }
         List<RhythmChart.Note> notes = RhythmMatch.this.chart.notes();
         for (int i = 0; i < notes.size(); i++) {
            RhythmChart.Note n = notes.get(i);
            if (!this.assigned(n.lane)) {
               continue;
            }
            long spawnMs = n.timeMs - this.travelMs;
            if (this.state[i] == 0 && nowMs >= spawnMs) {
               double bps = this.speedBps();
               double holdL = n.type == RhythmChart.NoteType.HOLD ? n.durationMs / 1000.0 * bps : 0.0;
               double progress = this.progressAt(spawnMs, nowMs, bps);
               this.entityId[i] = this.wall.spawnNote(n.lane, n.type, progress, holdL);
               this.state[i] = 1;
            } else if (this.state[i] == 1) {
               if (nowMs >= n.timeMs + RhythmMatch.this.settings.greatWindowMs()) {
                  this.miss(i, n);
               } else {
                  double bps = this.speedBps();
                  double progress = this.progressAt(spawnMs, nowMs, bps);
                  double holdL = n.type == RhythmChart.NoteType.HOLD ? n.durationMs / 1000.0 * bps : 0.0;
                  this.wall.moveNote(this.entityId[i], progress, holdL);
               }
            }
         }
      }

      void handleClick(RhythmChart.Lane lane, long nowMs) {
         if (this.failed || !this.assigned(lane)) {
            return;
         }
         long effective = nowMs - INPUT_LATENCY_MS;
         List<RhythmChart.Note> notes = RhythmMatch.this.chart.notes();
         int best = -1;
         long bestDt = Long.MAX_VALUE;
         for (int i = 0; i < notes.size(); i++) {
            RhythmChart.Note n = notes.get(i);
            if (n.lane != lane || this.state[i] != 1) {
               continue;
            }
            long dt = Math.abs(effective - n.timeMs);
            if (dt < bestDt) {
               bestDt = dt;
               best = i;
            }
         }
         if (best < 0) {
            return;
         }
         RhythmChart.Note n = notes.get(best);
         long dt = Math.abs(effective - n.timeMs);
         if (dt <= RhythmMatch.this.settings.perfectWindowMs()) {
            this.hit(best, n, true);
         } else if (dt <= RhythmMatch.this.settings.greatWindowMs()) {
            this.hit(best, n, false);
         }
      }

      void hit(int index, RhythmChart.Note note, boolean perfect) {
         this.state[index] = 2;
         this.wall.removeNote(this.entityId[index]);
         boolean gold = note.type == RhythmChart.NoteType.GOLD;
         boolean accent = note.type == RhythmChart.NoteType.ACCENT;
         int gained;
         if (perfect) {
            gained = gold ? GOLD_PERFECT_SCORE : accent ? ACCENT_PERFECT_SCORE : PERFECT_SCORE;
            this.stats.score += gained;
            this.stats.perfect++;
         } else {
            gained = gold ? GOLD_GREAT_SCORE : accent ? ACCENT_GREAT_SCORE : GREAT_SCORE;
            this.stats.score += gained;
            this.stats.great++;
         }
         this.stats.combo++;
         this.stats.maxCombo = Math.max(this.stats.maxCombo, this.stats.combo);
         double hx = this.wall.hitX(note.lane);
         double hy = this.wall.hitY(note.lane);
         double hz = this.wall.hitZ(note.lane);
         ServerPlayer p = RhythmMatch.this.ctx.player(this.uuid);
         float pitch = accent ? 1.6f : note.lane == RhythmChart.Lane.RED ? 1.3f : 0.9f;
         if (perfect) {
            this.wall.burstAt(hx, hy, hz, note.lane, note.type, gold ? 20 : accent ? 24 : 16);
            this.wall.perfectBurst(hx, hy, hz);
            RhythmMatch.this.playSound(p, gold || accent ? SoundEvents.NOTE_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_PLING, 1.0f, pitch);
            RhythmMatch.this.title(p, comboColor(this.stats.combo) + (gold ? "Perfect! ★" : accent ? "Perfect! ◆" : "Perfect!"),
               "&7+" + gained + " &8连击 x" + this.stats.combo);
         } else {
            this.wall.burstAt(hx, hy, hz, note.lane, note.type, accent ? 14 : 10);
            this.wall.greatBurst(hx, hy, hz);
            RhythmMatch.this.playSound(p, SoundEvents.NOTE_BLOCK_HARP, 0.8f, pitch);
            RhythmMatch.this.title(p, "&bGreat!", "&7+" + gained + " &8连击 x" + this.stats.combo);
         }
         if (this.stats.combo > 0 && this.stats.combo % 50 == 0) {
            RhythmMatch.this.playSound(p, SoundEvents.NOTE_BLOCK_BELL, 1.0f, 1.5f);
         }
         this.maybeTriggerInterference();
      }

      void miss(int index, RhythmChart.Note note) {
         this.state[index] = 3;
         this.wall.removeNote(this.entityId[index]);
         this.stats.miss++;
         this.stats.combo = 0;
         this.stats.hp = Math.max(0, this.stats.hp - 1);
         double hx = this.wall.hitX(note.lane);
         double hy = this.wall.hitY(note.lane);
         double hz = this.wall.hitZ(note.lane);
         this.wall.burstAt(hx, hy, hz, note.lane, note.type, 8);
         this.wall.missBurst(hx, hy, hz);
         ServerPlayer mp = RhythmMatch.this.ctx.player(this.uuid);
         RhythmMatch.this.playSound(mp, SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.5f);
         RhythmMatch.this.title(mp, "&7Miss", "&c-1 血量");
         if (RhythmMatch.this.mode == RhythmSettings.Mode.COOP && this.stats.miss % 5 == 0) {
            for (Seat s : RhythmMatch.this.seats) {
               ServerPlayer p = RhythmMatch.this.ctx.player(s.uuid);
               if (p != null) {
                  RhythmMatch.this.nausea(p, 40);
               }
            }
            RhythmMatch.this.ctx.broadcast(RhythmMatch.this.room, "&c团队惩罚！&7连续失误过多，屏幕短暂模糊。");
         }
      }

      void maybeTriggerInterference() {
         if (RhythmMatch.this.mode != RhythmSettings.Mode.VERSUS) {
            return;
         }
         for (int t = 0; t < INTERFERENCE_THRESHOLDS.length; t++) {
            if (!this.interferenceUsed[t] && this.stats.combo >= INTERFERENCE_THRESHOLDS[t]) {
               this.interferenceUsed[t] = true;
               this.applyInterference();
            }
         }
      }

      void applyInterference() {
         Seat target = null;
         for (Seat other : RhythmMatch.this.seats) {
            if (other == this || other.failed) {
               continue;
            }
            if (target == null || other.stats.score > target.stats.score) {
               target = other;
            }
         }
         if (target == null) {
            return;
         }
         int kind = ThreadLocalRandom.current().nextInt(3);
         String effect;
         if (kind == 0) {
            target.chaosUntil = RhythmMatch.this.elapsedTicks * 50L + EFFECT_MS;
            target.wall.setLaneSwap(true);
            effect = "&d混乱（红蓝对调）";
         } else if (kind == 1) {
            target.slowUntil = RhythmMatch.this.elapsedTicks * 50L + EFFECT_MS;
            effect = "&b减速（下落变慢）";
         } else {
            target.fogUntil = RhythmMatch.this.elapsedTicks * 50L + EFFECT_MS;
            effect = "&7遮挡（粒子雾）";
         }
         String sourceName = RhythmMatch.this.ctx.name(this.uuid);
         String targetName = RhythmMatch.this.ctx.name(target.uuid);
         ServerPlayer sp = RhythmMatch.this.ctx.player(this.uuid);
         ServerPlayer tp = RhythmMatch.this.ctx.player(target.uuid);
         if (sp != null) {
            RhythmMatch.this.title(sp, "&e连击 x" + this.stats.combo + "!", "&f你向 " + targetName + " 释放了 " + effect);
         }
         if (tp != null) {
            RhythmMatch.this.title(tp, "&c被干扰！", sourceName + " 对你释放了 " + effect);
         }
      }

      void actionBar(long nowMs) {
         ServerPlayer player = RhythmMatch.this.ctx.player(this.uuid);
         if (player == null) {
            return;
         }
         String lane = RhythmMatch.this.mode == RhythmSettings.Mode.COOP
            ? (this.redAssigned ? "&c左键红" : "&9右键蓝")
            : "&c左=红 &9右=蓝";
         player.displayClientMessage(TextUtil.color(
            lane + " &8| &7HP " + RhythmMatch.this.hpBar(this.stats.hp)
               + " &8| " + comboColor(this.stats.combo) + "Combo x" + this.stats.combo
               + " &8| &e" + this.stats.score + " &8| " + RhythmMatch.this.progressBar(nowMs)), true);
      }
   }

   private static String comboColor(int combo) {
      if (combo >= 150) {
         return "&d";
      }
      if (combo >= 100) {
         return "&6";
      }
      if (combo >= 50) {
         return "&e";
      }
      return "&f";
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                        Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
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
         ServerLevel target = ctx.server().getLevel(this.dimension);
         if (target == null) {
            target = ctx.server().overworld();
         }
         player.teleportTo(target, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
         player.setGameMode(this.gameType);
         Inventory inv = player.getInventory();
         inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), this.items.size()); i++) {
            inv.setItem(i, this.items.get(i).copy());
         }
      }
   }
}
