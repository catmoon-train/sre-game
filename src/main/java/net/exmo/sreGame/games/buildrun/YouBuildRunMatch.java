package net.exmo.sreGame.games.buildrun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildwar.Plot;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class YouBuildRunMatch {
   public enum Phase {
      BUILD,
      SELF,
      SWAP,
      ENDED
   }

   private static final Block[] PALETTE = {
      Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.GLASS, Blocks.WHITE_CONCRETE,
      Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.SLIME_BLOCK, Blocks.PACKED_ICE,
      Blocks.LADDER, Blocks.IRON_BARS
   };

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final YouBuildRunSettings settings;
   private final List<Plot> plots;
   private final List<BuildRunTrack> tracks;
   private final Map<UUID, Course> courses = new HashMap<>();
   private final Map<UUID, Runner> runners = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.BUILD;
   private int ticksLeft;
   private int boardTicks;
   private boolean begun;

   public YouBuildRunMatch(GameContext ctx, GameRoom room, List<UUID> seats, List<Plot> plots, List<BuildRunTrack> tracks) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.settings = room.youBuildRunSettings();
      this.plots = plots == null ? List.of() : List.copyOf(plots);
      this.tracks = tracks == null ? List.of() : List.copyOf(tracks);
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&a你建我跑"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      for (int i = 0; i < this.seats.size(); i++) {
         UUID uuid = this.seats.get(i);
         Course course = new Course(uuid);
         if (this.settings.scene() == BuildRunScene.TRACK && i < this.tracks.size()) {
            course.track = this.tracks.get(i);
         } else if (i < this.plots.size()) {
            course.plot = this.plots.get(i);
         }
         this.courses.put(uuid, course);
         this.runners.put(uuid, new Runner(uuid));
      }
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
      return this.settings.scene() == BuildRunScene.TRACK
         ? this.ctx.youBuildRun().tracks().level()
         : this.ctx.plots().level();
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.level();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&a你建我跑");
         this.boss.addPlayer(player);
         player.closeContainer();
         this.heal(player);
         this.giveBuildKit(player);
         player.setGameMode(GameType.SURVIVAL);
         Course course = this.courses.get(uuid);
         if (level != null && course != null) {
            course.teleportHome(player, level);
         }
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&a&l你建我跑");
      this.ctx.broadcast(this.room, "&7用金块作起点、钻石块作终点、绿宝石作记录点。");
      this.ctx.broadcast(this.room, "&7建造 &f" + this.settings.buildSeconds() + "s &8| &7自测 &f"
         + this.settings.selfSeconds() + "s &8| &7交换生命 &f" + this.settings.lives());
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.beginBuild();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.ticksLeft <= 0) {
         if (this.phase == Phase.BUILD) {
            this.beginSelf();
         } else if (this.phase == Phase.SELF) {
            this.timeoutSelf();
         }
      }
      this.tickPlayers();
      if (this.boardTicks % 10 == 0) {
         this.refreshBoard();
      }
      this.updateBoss();
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Runner runner = this.runners.get(player.getUUID());
      if (runner == null || !runner.alive || this.phase != Phase.BUILD) {
         return false;
      }
      Course course = this.courses.get(player.getUUID());
      if (course == null || !course.canBuild(pos)) {
         return false;
      }
      if (pos.equals(course.start)) {
         course.start = null;
      }
      if (pos.equals(course.end)) {
         course.end = null;
      }
      if (pos.equals(course.checkpoint)) {
         course.checkpoint = course.start;
      }
      course.emeralds.remove(pos);
      return true;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Runner runner = this.runners.get(player.getUUID());
      if (runner == null || !runner.alive) {
         return InteractionResult.FAIL;
      }
      if (this.phase != Phase.BUILD) {
         return InteractionResult.FAIL;
      }
      Course course = this.courses.get(player.getUUID());
      if (course == null) {
         return InteractionResult.FAIL;
      }
      BlockPos place = hit.getBlockPos().relative(hit.getDirection());
      if (!course.canBuild(place)) {
         this.ctx.send(player, "&c只能在自己的场地内建造。");
         return InteractionResult.FAIL;
      }
      if (stack.is(Items.GOLD_BLOCK)) {
         if (course.start != null) {
            this.clearBlock(course.start);
         }
         course.start = place.immutable();
         course.checkpoint = course.start;
      } else if (stack.is(Items.DIAMOND_BLOCK)) {
         if (course.end != null) {
            this.clearBlock(course.end);
         }
         course.end = place.immutable();
      } else if (stack.is(Items.EMERALD_BLOCK)) {
         course.emeralds.add(place.immutable());
      }
      return InteractionResult.PASS;
   }

   public boolean handleDamage(ServerPlayer player) {
      return this.runners.containsKey(player.getUUID());
   }

   public boolean handleDeath(ServerPlayer player) {
      Runner runner = this.runners.get(player.getUUID());
      if (runner == null || this.phase == Phase.ENDED) {
         return false;
      }
      this.heal(player);
      if (this.phase == Phase.BUILD) {
         this.warpHome(player);
      } else {
         this.onFall(player, runner);
      }
      return true;
   }

   public void onLeave(UUID uuid) {
      this.runners.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase != Phase.ENDED) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了你建我跑。");
         this.checkWin();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish(null);
      }
   }

   private void beginBuild() {
      this.phase = Phase.BUILD;
      this.ticksLeft = this.settings.buildSeconds() * 20;
      this.forEachOnline((player, runner) -> this.title(player, "&a建造阶段", "&e金块起点 · 钻石块终点"));
   }

   private void beginSelf() {
      this.phase = Phase.SELF;
      this.ticksLeft = this.settings.selfSeconds() * 20;
      for (UUID uuid : this.seats) {
         Runner runner = this.runners.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         Course course = this.courses.get(uuid);
         if (runner == null || player == null || !runner.alive) {
            continue;
         }
         player.getInventory().clearContent();
         player.setGameMode(GameType.ADVENTURE);
         if (course == null || !course.valid()) {
            this.eliminate(player, runner, "&c未放置起终点", "建造无效");
            continue;
         }
         course.checkpoint = course.start;
         this.warpTo(player, course, course.start);
         this.title(player, "&e自测", "&f" + this.settings.selfSeconds() + "s 内通关");
      }
      this.checkWin();
   }

   private void timeoutSelf() {
      for (UUID uuid : List.copyOf(this.seats)) {
         Runner runner = this.runners.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (runner != null && runner.alive && !runner.selfDone && player != null) {
            this.eliminate(player, runner, "&c自测超时", "未到达终点");
         }
      }
      this.beginSwap();
   }

   private void beginSwap() {
      List<UUID> alive = this.aliveIds();
      if (alive.size() <= 1) {
         this.checkWin();
         return;
      }
      this.phase = Phase.SWAP;
      this.ticksLeft = Integer.MAX_VALUE;
      for (UUID uuid : alive) {
         Runner runner = this.runners.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (runner == null || player == null) {
            continue;
         }
         runner.lives = this.settings.lives();
         runner.cleared.clear();
         UUID next = this.nextCourse(uuid, runner);
         if (next == null) {
            this.markSafe(player, runner);
            continue;
         }
         this.sendToCourse(player, runner, next);
         this.title(player, "&c交换跑酷", "&e生命 " + runner.lives);
      }
      this.checkWin();
   }

   private void tickPlayers() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.seats) {
         Runner runner = this.runners.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (runner == null || player == null) {
            continue;
         }
         if (!runner.alive || runner.safe) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            continue;
         }
         if (this.phase == Phase.BUILD && player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
         }
         if (this.phase != Phase.BUILD && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
         }
         Course venue = this.currentVenue(runner);
         if (venue == null) {
            continue;
         }
         if (this.phase == Phase.BUILD) {
            if (!venue.contains(player.getX(), player.getY(), player.getZ())) {
               venue.teleportHome(player, level);
            }
            continue;
         }
         BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
         BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
         this.noteCheckpoint(venue, below);
         this.noteCheckpoint(venue, feet);
         if (this.isEnd(venue, below) || this.isEnd(venue, feet)) {
            this.onFinish(player, runner);
            continue;
         }
         if (!venue.contains(player.getX(), player.getY(), player.getZ()) || venue.fallen(player.getY())) {
            this.onFall(player, runner);
         }
      }
   }

   private void onFinish(ServerPlayer player, Runner runner) {
      if (this.phase == Phase.SELF) {
         if (runner.selfDone) {
            return;
         }
         runner.selfDone = true;
         this.title(player, "&a自测通过", "&e等待其他人");
         player.setGameMode(GameType.SPECTATOR);
         this.ctx.broadcast(this.room, "&a" + player.getGameProfile().getName() + " 通过了自己的跑酷。");
         if (this.allSelfResolved()) {
            this.beginSwap();
         }
         return;
      }
      if (this.phase != Phase.SWAP || runner.currentOwner == null) {
         return;
      }
      runner.cleared.add(runner.currentOwner);
      UUID next = this.nextCourse(player.getUUID(), runner);
      if (next == null) {
         this.markSafe(player, runner);
      } else {
         this.sendToCourse(player, runner, next);
         this.title(player, "&a通关", "&e下一张图");
      }
      this.checkWin();
   }

   private void onFall(ServerPlayer player, Runner runner) {
      Course venue = this.currentVenue(runner);
      if (venue == null) {
         return;
      }
      this.heal(player);
      if (this.phase == Phase.SELF) {
         this.warpTo(player, venue, venue.respawn());
         return;
      }
      if (this.phase != Phase.SWAP) {
         return;
      }
      runner.lives--;
      if (runner.lives <= 0) {
         this.eliminate(player, runner, "&c生命耗尽", "交换跑酷失败");
         return;
      }
      this.warpTo(player, venue, venue.respawn());
      this.ctx.send(player, "&c掉出！剩余生命 &f" + runner.lives);
   }

   private boolean allSelfResolved() {
      for (UUID uuid : this.seats) {
         Runner runner = this.runners.get(uuid);
         if (runner != null && runner.alive && !runner.selfDone) {
            return false;
         }
      }
      return true;
   }

   private void markSafe(ServerPlayer player, Runner runner) {
      runner.safe = true;
      player.setGameMode(GameType.SPECTATOR);
      this.title(player, "&a待胜", "&7已通关所有他人跑酷");
      this.ctx.broadcast(this.room, "&a" + player.getGameProfile().getName() + " 通关了所有跑酷，等待其他人。");
      this.checkWin();
   }

   private void sendToCourse(ServerPlayer player, Runner runner, UUID owner) {
      runner.currentOwner = owner;
      Course course = this.courses.get(owner);
      if (course == null) {
         return;
      }
      course.checkpoint = course.start;
      player.setGameMode(GameType.ADVENTURE);
      this.warpTo(player, course, course.start);
      this.ctx.send(player, "&e正在挑战 &f" + this.ctx.name(owner) + " &e的跑酷");
   }

   private UUID nextCourse(UUID runnerId, Runner runner) {
      List<UUID> alive = this.aliveIds();
      if (alive.size() <= 1) {
         return null;
      }
      int start = Math.max(0, alive.indexOf(runnerId));
      for (int i = 1; i <= alive.size(); i++) {
         UUID owner = alive.get((start + i) % alive.size());
         if (owner.equals(runnerId) || runner.cleared.contains(owner)) {
            continue;
         }
         Course course = this.courses.get(owner);
         if (course != null && course.valid()) {
            return owner;
         }
      }
      return null;
   }

   private Course currentVenue(Runner runner) {
      if (this.phase == Phase.BUILD || this.phase == Phase.SELF) {
         return this.courses.get(runner.uuid);
      }
      return runner.currentOwner == null ? this.courses.get(runner.uuid) : this.courses.get(runner.currentOwner);
   }

   private void noteCheckpoint(Course course, BlockPos pos) {
      if (course.emeralds.contains(pos) || pos.equals(course.start)) {
         course.checkpoint = pos.immutable();
      }
      ServerLevel level = this.level();
      if (level != null && level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) {
         course.emeralds.add(pos.immutable());
         course.checkpoint = pos.immutable();
      }
   }

   private boolean isEnd(Course course, BlockPos pos) {
      if (course.end != null && pos.equals(course.end)) {
         return true;
      }
      ServerLevel level = this.level();
      return level != null && level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK) && course.containsBlock(pos);
   }

   private void warpHome(ServerPlayer player) {
      Course course = this.courses.get(player.getUUID());
      ServerLevel level = this.level();
      if (course != null && level != null) {
         course.teleportHome(player, level);
      }
   }

   private void warpTo(ServerPlayer player, Course course, BlockPos pos) {
      ServerLevel level = this.level();
      if (course == null || level == null) {
         return;
      }
      Vec3 dest = pos == null ? course.home() : new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
      player.fallDistance = 0.0F;
      player.teleportTo(level, dest.x, dest.y, dest.z, player.getYRot(), 0.0F);
   }

   private void eliminate(ServerPlayer player, Runner runner, String title, String reason) {
      if (!runner.alive) {
         return;
      }
      runner.alive = false;
      runner.safe = false;
      player.getInventory().clearContent();
      this.heal(player);
      player.setGameMode(GameType.SPECTATOR);
      this.title(player, title, "&7" + reason);
      this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " 被淘汰（" + reason + "）。");
      this.checkWin();
   }

   private void checkWin() {
      if (this.phase == Phase.ENDED || this.phase == Phase.BUILD) {
         return;
      }
      List<UUID> alive = this.aliveIds();
      if (this.phase == Phase.SELF) {
         if (alive.isEmpty()) {
            this.finish(null);
         }
         return;
      }
      if (alive.size() <= 1) {
         this.finish(alive.isEmpty() ? null : alive.get(0));
      }
   }

   private void finish(UUID winner) {
      this.phase = Phase.ENDED;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&a你建我跑结束");
      if (winner != null) {
         this.ctx.broadcast(this.room, "&a胜者： &f" + this.ctx.name(winner));
         ServerPlayer player = this.ctx.player(winner);
         if (player != null) {
            this.title(player, "&6胜利", "&e你建我跑");
         }
      } else {
         this.ctx.broadcast(this.room, "&7没有幸存者。");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      if (!this.plots.isEmpty()) {
         this.ctx.plots().release(this.plots);
      }
      if (!this.tracks.isEmpty()) {
         this.ctx.youBuildRun().tracks().release(this.tracks);
      }
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.youBuildRun().remove(this);
   }

   private void giveBuildKit(ServerPlayer player) {
      Inventory inv = player.getInventory();
      inv.clearContent();
      inv.setItem(0, named(new ItemStack(Items.GOLD_BLOCK), "&6起点"));
      inv.setItem(1, named(new ItemStack(Items.DIAMOND_BLOCK), "&b终点"));
      inv.setItem(2, named(new ItemStack(Items.EMERALD_BLOCK, 8), "&a记录点"));
      int remaining = this.settings.blockLimit();
      int slot = 3;
      for (Block block : PALETTE) {
         if (remaining <= 0 || slot >= 36) {
            break;
         }
         int give = Math.min(64, Math.max(1, remaining / Math.max(1, PALETTE.length - (slot - 3))));
         give = Math.min(give, remaining);
         inv.setItem(slot++, new ItemStack(block, give));
         remaining -= give;
      }
      if (remaining > 0) {
         inv.add(new ItemStack(Items.STONE, remaining));
      }
   }

   private static ItemStack named(ItemStack stack, String name) {
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      return stack;
   }

   private void clearBlock(BlockPos pos) {
      ServerLevel level = this.level();
      if (level != null && pos != null) {
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      }
   }

   private void updateBoss() {
      float max = switch (this.phase) {
         case BUILD -> this.settings.buildSeconds() * 20f;
         case SELF -> this.settings.selfSeconds() * 20f;
         default -> 1f;
      };
      this.boss.setProgress(this.phase == Phase.SWAP ? 1f : Math.max(0f, this.ticksLeft / Math.max(1f, max)));
      String name = switch (this.phase) {
         case BUILD -> "&a建造 " + Math.max(0, (this.ticksLeft + 19) / 20) + "s";
         case SELF -> "&e自测 " + Math.max(0, (this.ticksLeft + 19) / 20) + "s";
         case SWAP -> "&c交换跑酷 · 存活 " + this.aliveIds().size();
         case ENDED -> "&7结束";
      };
      this.boss.setName(TextUtil.color(name));
   }

   private void refreshBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7阶段 &f" + switch (this.phase) {
         case BUILD -> "建造";
         case SELF -> "自测";
         case SWAP -> "交换";
         case ENDED -> "结束";
      });
      lines.add("&7存活 &a" + this.aliveIds().size());
      this.forEachOnline((player, runner) -> {
         List<String> copy = new ArrayList<>(lines);
         if (this.phase == Phase.SWAP && runner.alive) {
            copy.add("&7生命 &c" + runner.lives);
            if (runner.currentOwner != null) {
               copy.add("&7当前 &f" + this.ctx.name(runner.currentOwner));
            }
         }
         this.board.update(player, copy);
      });
   }

   private List<UUID> aliveIds() {
      List<UUID> out = new ArrayList<>();
      for (UUID uuid : this.seats) {
         Runner runner = this.runners.get(uuid);
         if (runner != null && runner.alive) {
            out.add(uuid);
         }
      }
      return out;
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.clearFire();
      player.fallDistance = 0.0F;
   }

   private void restore(ServerPlayer player) {
      this.board.remove(player);
      this.boss.removePlayer(player);
      Saved snap = this.saved.remove(player.getUUID());
      if (snap != null) {
         snap.apply(player, this.ctx);
      }
   }

   private void title(ServerPlayer player, String title, String sub) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub)));
   }

   private void forEachOnline(PlayerRunner action) {
      for (UUID uuid : this.seats) {
         Runner runner = this.runners.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (runner != null && player != null) {
            action.accept(player, runner);
         }
      }
   }

   @FunctionalInterface
   private interface PlayerRunner {
      void accept(ServerPlayer player, Runner runner);
   }

   static final class Runner {
      final UUID uuid;
      boolean alive = true;
      boolean selfDone;
      boolean safe;
      int lives;
      UUID currentOwner;
      final Set<UUID> cleared = new HashSet<>();

      Runner(UUID uuid) {
         this.uuid = uuid;
      }
   }

   static final class Course {
      final UUID owner;
      Plot plot;
      BuildRunTrack track;
      BlockPos start;
      BlockPos end;
      BlockPos checkpoint;
      final Set<BlockPos> emeralds = new HashSet<>();

      Course(UUID owner) {
         this.owner = owner;
      }

      boolean valid() {
         return this.start != null && this.end != null;
      }

      boolean canBuild(BlockPos pos) {
         if (this.track != null) {
            return this.track.canBuild(pos);
         }
         return this.plot != null && this.plot.contains(pos)
            && pos.getY() > this.plot.origin().getY()
            && pos.getY() < this.plot.origin().getY() + this.plot.height();
      }

      boolean contains(double x, double y, double z) {
         if (this.track != null) {
            return this.track.contains(x, y, z);
         }
         return this.plot != null && this.plot.containsWatch(x, y, z);
      }

      boolean containsBlock(BlockPos pos) {
         if (this.track != null) {
            return this.track.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
         }
         return this.plot != null && this.plot.contains(pos);
      }

      boolean fallen(double y) {
         if (this.track != null) {
            return this.track.onDeathFloor(y);
         }
         return this.plot != null && y < this.plot.origin().getY() - 1.0;
      }

      BlockPos respawn() {
         return this.checkpoint != null ? this.checkpoint : this.start;
      }

      Vec3 home() {
         if (this.track != null) {
            return this.track.spawn();
         }
         return this.plot == null ? Vec3.ZERO : this.plot.spawn();
      }

      void teleportHome(ServerPlayer player, ServerLevel level) {
         if (this.track != null) {
            this.track.teleport(player, level, this.track.spawn());
         } else if (this.plot != null) {
            this.plot.teleport(player, level);
         }
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
