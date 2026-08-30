package net.exmo.sreGame.games.fillinthewall;

import java.util.ArrayList;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FillInTheWallMatch {
   public enum Phase {
      INTRO,
      PLAYING,
      ENDED
   }

   private enum Judgement {
      PERFECT, COOL, MISS
   }

   private static final int INTRO_SECONDS = 5;
   private static final int FULL_LENGTH = FillWallArena.TRACK_LENGTH;
   private static final int LEVEL_EVERY = 10;
   private static final int MIN_WALL_TIME = 40;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final FillWallArena arena;
   private final FillInTheWallSettings settings;
   private final SidebarBoard board;
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<Display.BlockDisplay> hardenedDisplays = new ArrayList<>();

   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private boolean begun;
   private int score;
   private int perfectWalls;
   private int wallsJudged;
   private int hardenedCount;
   private int level = 1;
   private int timedTicksRemaining;
   private FillWall activeWall;
   private int boardTicks;

   public FillInTheWallMatch(GameContext ctx, GameRoom room, List<UUID> seats, FillWallArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.fillInTheWallSettings();
      this.board = new SidebarBoard(ctx.server());
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

   public FillWallArena.Layout layout() {
      return new FillWallArena.Layout(
         this.settings.length(), this.settings.height(), this.settings.standingDistance(), FULL_LENGTH);
   }

   public ServerLevel level() {
      return this.ctx.fillInTheWall().arenas().level();
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
         this.board.create(player, "&6填墙游戏");
         player.setGameMode(GameType.CREATIVE);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.giveKit(player);
         if (level != null) {
            Vec3 spawn = this.spawnVec(uuid);
            player.teleportTo(level, spawn.x, spawn.y, spawn.z, 90.0F, 0.0F);
         }
      }
      this.timedTicksRemaining = this.settings.durationSeconds() * 20;
      this.phase = Phase.INTRO;
      this.ticksLeft = INTRO_SECONDS * 20;
      this.ctx.broadcast(this.room, "&e填墙游戏开始！用 &f白色混凝土 &e填满墙上的洞，&f下界之星 &e可立即提交。");
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.phase == Phase.INTRO) {
         if (this.ticksLeft <= 0) {
            this.beginPlay();
         }
      } else if (this.phase == Phase.PLAYING) {
         this.tickPlay();
      }
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
   }

   private void beginPlay() {
      this.phase = Phase.PLAYING;
      this.ticksLeft = 0;
      this.spawnNextWall();
      this.ctx.broadcast(this.room, "&a开始！");
   }

   private void tickPlay() {
      if (this.settings.mode() == FillInTheWallSettings.Mode.TIMED) {
         this.timedTicksRemaining--;
         if (this.timedTicksRemaining <= 0) {
            this.finish("时间到");
            return;
         }
      }
      if (this.activeWall != null) {
         boolean arrived = this.activeWall.tick();
         if (arrived) {
            this.judgeWall();
         }
      } else {
         this.spawnNextWall();
      }
   }

   private int effectiveLength() {
      return Math.max(0, FULL_LENGTH - this.hardenedCount);
   }

   private int baseWallTime() {
      int base = this.settings.wallActiveTime() - (this.level - 1) * 8;
      return Math.max(MIN_WALL_TIME, base);
   }

   private int wallTimeFor(int effective) {
      int t = (int) Math.round(this.baseWallTime() * (double) effective / (double) FULL_LENGTH);
      return Math.max(MIN_WALL_TIME, t);
   }

   private void spawnNextWall() {
      if (this.phase != Phase.PLAYING) {
         return;
      }
      int effective = this.effectiveLength();
      if (this.settings.mode() == FillInTheWallSettings.Mode.ENDLESS && effective <= 0) {
         this.finish("墙体堆满，游戏结束");
         return;
      }
      effective = Math.max(2, effective);
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int len = this.settings.length();
      int h = this.settings.height();
      FillWall wall = new FillWall(len, h);
      int randomHoles = this.settings.randomHoles() + (this.level - 1);
      int connectedHoles = this.settings.connectedHoles();
      int cap = (int) Math.floor(len * h * 0.6);
      randomHoles = Math.min(randomHoles, cap);
      wall.generateHoles(randomHoles, connectedHoles, this.settings.randomizeFurther(), 1, ThreadLocalRandom.current());
      int time = this.wallTimeFor(effective);
      wall.setTimeRemaining(time);
      double startX = this.arena.fieldX() - effective;
      double endX = this.arena.fieldX();
      wall.spawn(level, this.arena.origin(), startX, endX,
         Blocks.BLUE_CONCRETE.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
      this.activeWall = wall;
   }

   private void judgeWall() {
      FillWall wall = this.activeWall;
      if (wall == null) {
         return;
      }
      this.activeWall = null;
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int len = wall.length();
      int h = wall.height();
      int fx = this.arena.fieldX();
      int fy = this.arena.fieldY();
      int fz = this.arena.fieldZ();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int totalHoles = wall.holes().size();
      int filled = 0;
      int extra = 0;
      for (int z = 0; z < len; z++) {
         for (int y = 0; y < h; y++) {
            pos.set(fx, fy + y, fz + z);
            boolean hasBlock = !level.getBlockState(pos).isAir();
            boolean isHole = wall.holes().contains(new FillWall.Coord(z, y));
            if (isHole && hasBlock) {
               filled++;
            } else if (!isHole && hasBlock) {
               extra++;
            }
         }
      }
      double percent = totalHoles == 0 ? 1.0 : (double) filled / (double) totalHoles;
      Judgement j;
      int gained = filled;
      if (extra == 0 && filled == totalHoles) {
         j = Judgement.PERFECT;
         this.perfectWalls++;
         gained += totalHoles;
         this.hardenedCount = Math.max(0, this.hardenedCount - 2);
      } else if (filled == totalHoles || percent >= 0.5) {
         j = Judgement.COOL;
         this.hardenedCount = Math.max(0, this.hardenedCount - 1);
      } else {
         j = Judgement.MISS;
         this.hardenedCount++;
      }
      this.score += gained;
      this.wallsJudged++;
      this.level = 1 + this.score / LEVEL_EVERY;
      this.clearField(level);
      wall.despawn();
      this.broadcastJudgement(j, percent, filled, totalHoles, gained);
      this.refreshHardenedDisplays(level);
      if (this.settings.mode() == FillInTheWallSettings.Mode.ENDLESS && this.effectiveLength() <= 0) {
         this.finish("墙体堆满，游戏结束");
         return;
      }
      this.spawnNextWall();
   }

   private void clearField(ServerLevel level) {
      int fx = this.arena.fieldX();
      int fy = this.arena.fieldY();
      int fz = this.arena.fieldZ();
      int len = this.settings.length();
      int h = this.settings.height();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      BlockState air = Blocks.AIR.defaultBlockState();
      for (int z = 0; z < len; z++) {
         for (int y = 0; y < h; y++) {
            pos.set(fx, fy + y, fz + z);
            level.setBlock(pos, air, 2);
         }
      }
   }

   private void refreshHardenedDisplays(ServerLevel level) {
      for (Display.BlockDisplay d : this.hardenedDisplays) {
         if (d != null && !d.isRemoved()) {
            d.discard();
         }
      }
      this.hardenedDisplays.clear();
      int count = this.hardenedCount;
      if (count <= 0) {
         return;
      }
      int fx = this.arena.fieldX();
      int fy = this.arena.fieldY();
      int fz = this.arena.fieldZ();
      int len = this.settings.length();
      int h = this.settings.height();
      BlockState gray = Blocks.GRAY_CONCRETE.defaultBlockState();
      for (int i = 0; i < count; i++) {
         int x = fx - FULL_LENGTH + i;
         for (int z = -1; z <= len; z++) {
            Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
            if (d == null) {
               continue;
            }
            d.setPos(x, fy + h, fz + z);
            d.setBlockState(gray);
            level.addFreshEntity(d);
            this.hardenedDisplays.add(d);
         }
      }
   }

   private void broadcastJudgement(Judgement j, double percent, int filled, int total, int gained) {
      String title;
      String sub;
      if (j == Judgement.PERFECT) {
         title = "&6&lPERFECT!";
         sub = "&f" + filled + "/" + total + " &a+" + gained;
      } else if (j == Judgement.COOL) {
         title = "&b&lCOOL";
         sub = "&f" + filled + "/" + total + " &a+" + gained;
      } else {
         title = "&c&lMISS";
         sub = "&f" + filled + "/" + total + " &c+1 垃圾墙";
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.title(player, title, sub);
            player.playNotifySound(j == Judgement.MISS ? SoundEvents.ANVIL_LAND
               : SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.7F, j == Judgement.PERFECT ? 1.5F : 1.0F);
         }
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      if (this.phase != Phase.PLAYING) {
         return InteractionResult.PASS;
      }
      if (stack.is(Items.NETHER_STAR)) {
         this.instantSend();
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      if (this.phase != Phase.PLAYING) {
         return InteractionResult.FAIL;
      }
      BlockPos place = hit.getBlockPos().relative(hit.getDirection());
      if (!this.isFieldCell(place)) {
         this.ctx.send(player, "&c只能在场地墙面内放置方块。");
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      if (this.phase != Phase.PLAYING) {
         return false;
      }
      return this.isFieldCell(pos);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      return this.phase != Phase.ENDED;
   }

   public boolean handleDeath(ServerPlayer player) {
      if (this.phase == Phase.ENDED) {
         return false;
      }
      this.heal(player);
      Vec3 spawn = this.spawnVec(player.getUUID());
      ServerLevel level = this.level();
      if (level != null) {
         player.teleportTo(level, spawn.x, spawn.y, spawn.z, 90.0F, 0.0F);
      }
      return true;
   }

   public void onLeave(UUID uuid) {
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase != Phase.ENDED) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了填墙游戏。");
         boolean any = false;
         for (UUID s : this.seats) {
            if (this.ctx.player(s) != null) {
               any = true;
               break;
            }
         }
         if (!any) {
            this.finish("全员离场");
         }
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish("对局中止");
      }
   }

   private void instantSend() {
      FillWall wall = this.activeWall;
      if (wall == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      double endX = this.arena.fieldX();
      for (Display.BlockDisplay d : wall.blocks()) {
         if (d != null && !d.isRemoved()) {
            d.setPos(endX, d.getY(), d.getZ());
         }
      }
      for (Display.BlockDisplay d : wall.border()) {
         if (d != null && !d.isRemoved()) {
            d.setPos(endX, d.getY(), d.getZ());
         }
      }
      this.judgeWall();
   }

   private boolean isFieldCell(BlockPos pos) {
      int fx = this.arena.fieldX();
      int fy = this.arena.fieldY();
      int fz = this.arena.fieldZ();
      int len = this.settings.length();
      int h = this.settings.height();
      return pos.getX() == fx
         && pos.getZ() >= fz && pos.getZ() < fz + len
         && pos.getY() >= fy && pos.getY() < fy + h;
   }

   private Vec3 spawnVec(UUID uuid) {
      int fx = this.arena.fieldX();
      int fy = this.arena.fieldY();
      int fz = this.arena.fieldZ();
      int len = this.settings.length();
      int stand = this.settings.standingDistance();
      int index = Math.max(0, this.seats.indexOf(uuid));
      int slots = Math.max(1, this.seats.size());
      double z = fz + (len - 1) / 2.0 + 0.5;
      if (slots > 1) {
         z = fz + 1.5 + (double) index * Math.max(1.0, (len - 3) / (double) Math.max(1, slots - 1));
      }
      return new Vec3(fx + stand + 0.5, fy, z);
   }

   private void giveKit(ServerPlayer player) {
      ItemStack blocks = new ItemStack(Items.WHITE_CONCRETE, 64);
      blocks.set(DataComponents.CUSTOM_NAME, TextUtil.color("&f填墙方块"));
      player.getInventory().setItem(0, blocks);
      ItemStack submit = new ItemStack(Items.NETHER_STAR);
      submit.set(DataComponents.CUSTOM_NAME, TextUtil.color("&e立即提交 &7(右键)"));
      submit.set(DataComponents.LORE, new ItemLore(List.of(TextUtil.color("&7将当前墙体立刻送至墙面判定"))));
      player.getInventory().setItem(8, submit);
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
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   private void finish(String reason) {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      if (this.activeWall != null) {
         this.activeWall.despawn();
         this.activeWall = null;
      }
      ServerLevel level = this.level();
      if (level != null) {
         for (Display.BlockDisplay d : this.hardenedDisplays) {
            if (d != null && !d.isRemoved()) {
               d.discard();
            }
         }
         this.hardenedDisplays.clear();
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l填墙游戏结算 &7" + reason);
      this.ctx.broadcast(this.room, "&a总分 &f" + this.score + " &7| &f完美 " + this.perfectWalls
         + " &7| &f判定 " + this.wallsJudged + " &7| &f等级 " + this.level);
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.title(player, "&6游戏结束", "&f得分 " + this.score);
            this.restore(player);
         }
      }
      this.ctx.fillInTheWall().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.fillInTheWall().remove(this);
   }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7模式 &f" + this.settings.mode().label());
      lines.add("&7得分 &f" + this.score);
      if (this.settings.mode() == FillInTheWallSettings.Mode.TIMED) {
         int left = Math.max(0, this.timedTicksRemaining);
         lines.add("&7剩余 &f" + (left / 20 / 60) + ":" + String.format("%02d", (left / 20) % 60));
      } else {
         lines.add("&7等级 &f" + this.level);
      }
      lines.add("&7完美 &f" + this.perfectWalls);
      lines.add("&7判定 &f" + this.wallsJudged);
      lines.add("&7垃圾墙 &f" + this.hardenedCount + "&7/" + FULL_LENGTH);
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

