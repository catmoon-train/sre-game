package net.exmo.sreGame.dontdo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.mixin.LivingJumpAccessor;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class DontDoMatch {
   public enum Phase {
      PLAYING,
      ENDING,
      ENDED
   }

   private static final ChatFormatting[] TEAM_COLORS = {
      ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
      ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE
   };
   private static final String[] TEAM_NAMES = {"红", "蓝", "绿", "黄", "青", "金", "紫", "白"};
   private static final int ANIMAL_CAP = 32;
   private static final int ANIMAL_WAVE = 12;
   private static final int ANIMAL_REFRESH = 25 * 20;
   private static final int WEATHER_CYCLE = 70 * 20;
   @SuppressWarnings("unchecked")
   private static final EntityType<? extends Animal>[] ANIMALS = new EntityType[] {
      EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
      EntityType.RABBIT, EntityType.HORSE, EntityType.WOLF, EntityType.FOX,
      EntityType.CAT, EntityType.GOAT, EntityType.LLAMA
   };

   private enum LocalWeather {
      CLEAR("晴朗", 0.0F, 0.0F),
      RAIN("下雨", 1.0F, 0.0F),
      THUNDER("雷暴", 1.0F, 1.0F);

      final String title;
      final float rain;
      final float thunder;

      LocalWeather(String title, float rain, float thunder) {
         this.title = title;
         this.rain = rain;
         this.thunder = thunder;
      }
   }

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Island island;
   private final DontDoSettings settings;
   private final Map<UUID, Contestant> players = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.PLAYING;
   private boolean begun;
   private int ticks;
   private int ruleTicks;
   private int eventTicks;
   private int boardTicks;
   private int endTicks;
   private DontDoWorldEvent worldEvent = DontDoWorldEvent.NONE;
   private String winnerLine;
   private boolean animalsReady;
   private boolean atmosphereOn;
   private int animalTicks;
   private int weatherTicks;
   private LocalWeather localWeather = LocalWeather.CLEAR;

   public DontDoMatch(GameContext ctx, GameRoom room, List<UUID> seats, Island island) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.island = island;
      this.settings = room.dontDoSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&c不要做挑战"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.assignTeams();
      for (Contestant contestant : this.players.values()) {
         contestant.rule = DontDoRule.pick(null);
      }
   }

   public UUID id() {
      return this.id;
   }

   public Island island() {
      return this.island;
   }

   public Phase phase() {
      return this.phase;
   }

   public boolean begun() {
      return this.begun;
   }

   public void start() {
      this.begun = true;
      this.ruleTicks = this.settings.ruleSeconds() * 20;
      this.eventTicks = this.settings.eventSeconds() * 20;
      if (this.settings.randomEvents()) {
         this.worldEvent = DontDoWorldEvent.pick(null);
      }
      ServerLevel level = this.ctx.dontDo().islands().level();
      int i = 0;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Contestant contestant = this.players.get(uuid);
         if (player == null || contestant == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&c不要做挑战");
         this.boss.addPlayer(player);
         this.ensureTeam(player, contestant);
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.closeContainer();
         player.removeAllEffects();
         player.setHealth(player.getMaxHealth());
         player.getFoodData().setFoodLevel(16);
         player.getFoodData().setSaturation(4.0F);
         contestant.lastHealth = player.getMaxHealth();
         contestant.graceTicks = 80;
         Vec3 spawn = this.island.spawn(i++, this.seats.size());
         this.padSpawn(level, spawn);
         player.teleportTo(level, spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
      }
      this.refreshAnimals(level);
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&c&l不要做挑战");
      this.ctx.broadcast(this.room, "&7每人 &f" + this.settings.lives() + " &7点生命。挖到钻石矿 &a+1&7，违反事项 &c-1 &7并立刻换新。");
      this.ctx.broadcast(this.room, "&7事项每 &f" + this.settings.ruleSeconds() + "s &7刷新。你看不到自己的事项，只能看别人的。");
      this.ctx.broadcast(this.room, "&7原版死亡会重生，不扣挑战生命。");
      this.ctx.broadcast(this.room, "&7地面会刷新动物；落稳后本岛局部变天，所有人发光。");
      if (this.settings.teams()) {
         this.ctx.broadcast(this.room, "&7组队模式，每队约 &f" + this.settings.teamSize() + " &7人，关闭友伤。");
      }
      if (this.settings.randomEvents()) {
         this.ctx.broadcast(this.room, "&d随机事件已开启：&f" + this.worldEvent.title);
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.announceRefresh(true);
      this.pushBoard();
      this.updateBoss();
      this.beginAtmosphere();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticks++;
      this.boardTicks++;
      if (this.phase == Phase.ENDING) {
         this.endTicks--;
         if (this.boardTicks % 10 == 0) {
            this.pushBoard();
            this.updateBoss();
         }
         if (this.endTicks <= 0) {
            this.finish();
         }
         return;
      }
      this.ruleTicks--;
      if (this.settings.randomEvents()) {
         this.eventTicks--;
      }
      ServerLevel level = this.ctx.dontDo().islands().level();
      this.tickAtmosphere(level);
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Contestant contestant = this.players.get(uuid);
         if (player == null || contestant == null || !contestant.alive) {
            continue;
         }
         this.tickPlayer(player, contestant, level);
         if (this.settings.randomEvents() && this.worldEvent != DontDoWorldEvent.NONE) {
            this.worldEvent.tick(this, level, player, this.ticks);
         }
      }
      if (this.ruleTicks <= 0) {
         this.rerollAll("时间到，全员刷新不要做");
         this.ruleTicks = this.settings.ruleSeconds() * 20;
      }
      if (this.settings.randomEvents() && this.eventTicks <= 0) {
         DontDoWorldEvent next = DontDoWorldEvent.pick(this.worldEvent);
         this.worldEvent = next;
         this.eventTicks = this.settings.eventSeconds() * 20;
         this.ctx.broadcast(this.room, "&d全局事件：&f" + next.title + " &8- &7" + next.describe);
      }
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
         this.updateBoss();
      }
      this.checkWin();
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      Contestant contestant = this.alive(player);
      if (contestant == null) {
         return false;
      }
      if (isTool(stack)) {
         this.violate(player, contestant, DontDoRule.USE_TOOLS, "使用了工具");
      }
      if (isRanged(stack) && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
         || stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG || stack.getItem() == Items.ENDER_PEARL)) {
         this.violate(player, contestant, DontDoRule.RANGED_WEAPON, "使用了远程武器");
      }
      if (stack.getItem() instanceof ShieldItem) {
         this.violate(player, contestant, DontDoRule.USE_SHIELD, "举起了盾");
      }
      if (isMeat(stack)) {
         this.violate(player, contestant, DontDoRule.EAT_MEAT, "吃了肉");
         this.markEating(player);
      } else if (stack.has(DataComponents.FOOD)) {
         this.markEating(player);
      }
      return false;
   }

   public boolean handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Contestant contestant = this.alive(player);
      if (contestant == null) {
         return true;
      }
      BlockPos clicked = hit.getBlockPos();
      BlockPos place = clicked.relative(hit.getDirection());
      if (this.island.isBorder(clicked) || this.island.isBorder(place) || !this.island.inPlayable(place)
         && stack.getItem() instanceof BlockItem) {
         this.ctx.send(player, "&c不能在边界外放置。");
         return true;
      }
      BlockState state = player.level().getBlockState(clicked);
      if (isWorkBlock(state.getBlock())) {
         this.violate(player, contestant, DontDoRule.USE_WORK_BLOCKS, "用了工作方块");
         this.markWorking(player);
      }
      if (isContainer(state.getBlock())) {
         this.violate(player, contestant, DontDoRule.USE_CONTAINERS, "打开了容器");
      }
      if (stack.getItem() instanceof BlockItem && !isWorkBlock(state.getBlock()) && !isContainer(state.getBlock())) {
         this.violate(player, contestant, DontDoRule.PLACE_BLOCK, "放置了方块");
         if (isLightItem(stack)) {
            this.violate(player, contestant, DontDoRule.PLACE_LIGHT, "放置了光源");
         }
      }
      return false;
   }

   public boolean handleBreak(ServerPlayer player, BlockPos pos, BlockState state) {
      Contestant contestant = this.alive(player);
      if (contestant == null) {
         return false;
      }
      if (this.island.isBorder(pos) || !this.island.inPlayable(pos)) {
         this.ctx.send(player, "&c边界方块不可破坏。");
         return false;
      }
      if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
         this.onDiamond(player, contestant, pos);
         return false;
      }
      this.violate(player, contestant, DontDoRule.PUNCH_BLOCKS, "破坏了方块");
      if (isTool(player.getMainHandItem())) {
         this.violate(player, contestant, DontDoRule.TOOL_MINING, "用工具挖方块");
         this.violate(player, contestant, DontDoRule.USE_TOOLS, "使用了工具");
      }
      return true;
   }

   public void handleAttack(ServerPlayer player, Entity target) {
      Contestant contestant = this.alive(player);
      if (contestant == null) {
         return;
      }
      contestant.attackingTicks = 40;
      ItemStack stack = player.getMainHandItem();
      if (target instanceof ServerPlayer) {
         this.violate(player, contestant, DontDoRule.ATTACK_PLAYERS, "攻击了玩家");
      } else {
         this.violate(player, contestant, DontDoRule.HURT_MOBS, "攻击了生物");
         if (target instanceof Animal) {
            this.violate(player, contestant, DontDoRule.HURT_FRIENDLY, "攻击了友善生物");
         }
         if (target instanceof Enemy) {
            this.violate(player, contestant, DontDoRule.HURT_HOSTILE, "攻击了敌对生物");
         }
      }
      if (isMeleeWeapon(stack)) {
         this.violate(player, contestant, DontDoRule.MELEE_WEAPON, "用了近战武器");
         this.violate(player, contestant, DontDoRule.ANY_WEAPON, "用了武器");
      } else if (isRanged(stack)) {
         this.violate(player, contestant, DontDoRule.RANGED_WEAPON, "用了远程武器");
         this.violate(player, contestant, DontDoRule.ANY_WEAPON, "用了武器");
      } else {
         this.violate(player, contestant, DontDoRule.NON_WEAPON, "用非武器攻击");
      }
      if (player.getAttributeValue(Attributes.ATTACK_DAMAGE) > 6.0) {
         this.violate(player, contestant, DontDoRule.OVER6_DAMAGE, "用了高伤武器");
      }
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source, float amount) {
      Contestant contestant = this.players.get(player.getUUID());
      if (contestant == null || this.phase != Phase.PLAYING) {
         return false;
      }
      if (source.getEntity() instanceof ServerPlayer attacker) {
         Contestant other = this.players.get(attacker.getUUID());
         if (this.settings.teams() && other != null && other.team == contestant.team) {
            return true;
         }
      }
      if (!contestant.alive) {
         return true;
      }
      if (contestant.invulnTicks > 0) {
         return true;
      }
      if (player.getHealth() - amount <= 0.0F) {
         return false;
      }
      if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         this.violate(player, contestant, DontDoRule.TAKE_DAMAGE, "受到了伤害");
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player) {
      Contestant contestant = this.players.get(player.getUUID());
      if (contestant == null || this.phase == Phase.ENDED) {
         return false;
      }
      player.setHealth(player.getMaxHealth());
      if (!contestant.alive) {
         return true;
      }
      this.notifyNearbyDeath(player);
      contestant.lastHealth = player.getMaxHealth();
      contestant.graceTicks = 40;
      contestant.invulnTicks = 100;
      player.invulnerableTime = 110;
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      ServerLevel level = this.ctx.dontDo().islands().level();
      Vec3 pos = this.island.respawn();
      this.padSpawn(level, pos);
      player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
      this.ctx.send(player, "&7你死了，已重生。&a无敌 5 秒&7，饱食已回满。");
      return true;
   }

   public void handleJump(ServerPlayer player) {
      Contestant contestant = this.alive(player);
      if (contestant != null) {
         contestant.wasJumping = true;
         this.violate(player, contestant, DontDoRule.JUMP, "跳跃", true);
      }
   }

   public void handleDrop(ServerPlayer player) {
      Contestant contestant = this.alive(player);
      if (contestant != null) {
         this.violate(player, contestant, DontDoRule.DROP_ITEMS, "丢出了物品");
      }
   }

   public void onLeave(UUID uuid) {
      Contestant contestant = this.players.get(uuid);
      if (contestant != null && contestant.alive) {
         contestant.alive = false;
         contestant.hp = 0;
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      if (this.phase == Phase.PLAYING) {
         this.checkWin();
      }
   }

   public void endNow() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.board.removeAll();
      this.boss.setVisible(false);
      this.boss.removeAllPlayers();
      this.clearAnimals(this.ctx.dontDo().islands().level());
      this.ctx.dontDo().islands().release(this.island);
      this.ctx.dontDo().remove(this);
      this.ctx.rooms().onMatchEnded(this.id);
   }

   private void assignTeams() {
      List<UUID> order = new ArrayList<>(this.seats);
      java.util.Collections.shuffle(order);
      int team = 0;
      int inTeam = 0;
      int size = Math.max(2, this.settings.teamSize());
      for (UUID uuid : order) {
         Contestant contestant = new Contestant(uuid);
         contestant.hp = this.settings.lives();
         if (this.settings.teams()) {
            contestant.team = team;
            inTeam++;
            if (inTeam >= size) {
               inTeam = 0;
               team++;
            }
         } else {
            contestant.team = -1;
         }
         this.players.put(uuid, contestant);
      }
   }

   private void tickPlayer(ServerPlayer player, Contestant contestant, ServerLevel level) {
      if (contestant.graceTicks > 0) {
         contestant.graceTicks--;
      }
      if (contestant.invulnTicks > 0) {
         contestant.invulnTicks--;
         player.invulnerableTime = Math.max(player.invulnerableTime, 20);
         if (contestant.invulnTicks == 0) {
            this.ctx.send(player, "&e无敌结束。");
         }
      }
      if (contestant.attackingTicks > 0) {
         contestant.attackingTicks--;
      }
      if (contestant.workingTicks > 0) {
         contestant.workingTicks--;
      }
      if (contestant.eatingTicks > 0) {
         contestant.eatingTicks--;
      }
      boolean jumping = ((LivingJumpAccessor) (Object) player).sre$isJumping();
      boolean jumpPress = jumping && !contestant.wasJumping;
      contestant.wasJumping = jumping;
      if (jumpPress || contestant.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.2) {
         this.violate(player, contestant, DontDoRule.JUMP, "跳跃", true);
      }
      contestant.wasOnGround = player.onGround();
      if (player.isSprinting()) {
         this.violate(player, contestant, DontDoRule.SPRINT, "疾跑");
      }
      if (player.isShiftKeyDown()) {
         this.violate(player, contestant, DontDoRule.SNEAK, "潜行");
      }
      if (player.isInWater()) {
         this.violate(player, contestant, DontDoRule.TOUCH_WATER, "接触水");
      }
      if (player.isSwimming()) {
         this.violate(player, contestant, DontDoRule.SWIM, "游泳");
      }
      if (player.isBlocking()) {
         this.violate(player, contestant, DontDoRule.USE_SHIELD, "格挡");
      }
      if (player.isOnFire()) {
         this.violate(player, contestant, DontDoRule.SELF_BURN, "燃烧");
      }
      if (this.rainingAt(level, player.blockPosition())) {
         this.violate(player, contestant, DontDoRule.GET_RAINED, "淋雨");
      }
      if (!player.getOffhandItem().isEmpty()) {
         this.violate(player, contestant, DontDoRule.OFFHAND, "副手拿了东西");
      }
      if (wearingArmor(player)) {
         this.violate(player, contestant, DontDoRule.WEAR_ARMOR, "穿了护甲");
      }
      if (player.getHealth() < player.getMaxHealth() / 2.0F) {
         this.violate(player, contestant, DontDoRule.HP_BELOW_HALF, "生命过半");
      }
      if (Math.abs(player.getHealth() - contestant.lastHealth) > 0.05F && player.getHealth() > 0.0F) {
         this.violate(player, contestant, DontDoRule.HEALTH_CHANGE, "血量变动");
      }
      contestant.lastHealth = player.getHealth();
      int light = Math.max(level.getBrightness(LightLayer.BLOCK, player.blockPosition()),
         level.getBrightness(LightLayer.SKY, player.blockPosition()) - level.getSkyDarken());
      if (light > 10) {
         this.violate(player, contestant, DontDoRule.BRIGHT_LIGHT, "亮度过高");
      }
      if (light < 8) {
         this.violate(player, contestant, DontDoRule.DIM_LIGHT, "亮度过低");
      }
      if (player.getFoodData().getFoodLevel() >= 20) {
         this.violate(player, contestant, DontDoRule.FULL_HUNGER, "饱食已满");
      }
      if (inventoryFull(player)) {
         this.violate(player, contestant, DontDoRule.FULL_INVENTORY, "背包已满");
      }
      if (craftingUsed(player)) {
         this.violate(player, contestant, DontDoRule.CRAFTING_GRID, "用了合成栏");
      }
      if (player.getY() > 24.0) {
         this.violate(player, contestant, DontDoRule.HIGH_Y, "去了高处");
      }
      BlockState below = level.getBlockState(player.blockPosition().below());
      if (below.is(Blocks.GRASS_BLOCK) && player.onGround()) {
         this.violate(player, contestant, DontDoRule.STAND_GRASS, "站在草地上");
      }
      if (tightSpace(player)) {
         this.violate(player, contestant, DontDoRule.TIGHT_SPACE, "空间过窄");
      }
      if (!level.isDay() && level.canSeeSky(player.blockPosition()) && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
         this.violate(player, contestant, DontDoRule.NIGHT_MOVE, "夜晚露天移动");
      }
      if (hasForeignEffect(player)) {
         this.violate(player, contestant, DontDoRule.GET_BUFFS, "获得了效果");
      }
      AABB close = player.getBoundingBox().inflate(1.6);
      AABB five = player.getBoundingBox().inflate(5.0);
      AABB around = player.getBoundingBox().inflate(10.0);
      boolean otherClose = false;
      boolean otherFive = false;
      boolean otherAttack = false;
      boolean otherRain = false;
      boolean otherWork = false;
      boolean otherEat = false;
      for (UUID otherId : this.seats) {
         if (otherId.equals(player.getUUID())) {
            continue;
         }
         ServerPlayer other = this.ctx.player(otherId);
         Contestant oc = this.players.get(otherId);
         if (other == null || oc == null || !oc.alive) {
            continue;
         }
         if (close.intersects(other.getBoundingBox())) {
            otherClose = true;
         }
         if (five.intersects(other.getBoundingBox())) {
            otherFive = true;
         }
         if (around.intersects(other.getBoundingBox())) {
            if (oc.attackingTicks > 0) {
               otherAttack = true;
            }
            if (this.rainingAt(level, other.blockPosition())) {
               otherRain = true;
            }
            if (oc.workingTicks > 0) {
               otherWork = true;
            }
            if (oc.eatingTicks > 0) {
               otherEat = true;
            }
         }
      }
      if (otherClose) {
         this.violate(player, contestant, DontDoRule.PLAYER_CLOSE, "太靠近别人");
      }
      if (otherFive) {
         this.violate(player, contestant, DontDoRule.PLAYERS_NEAR, "5格内有人");
      }
      if (otherAttack) {
         this.violate(player, contestant, DontDoRule.NEAR_ATTACKING, "周围有人攻击");
      }
      if (otherRain) {
         this.violate(player, contestant, DontDoRule.NEAR_RAINED, "周围有人淋雨");
      }
      if (otherWork) {
         this.violate(player, contestant, DontDoRule.NEAR_WORKING, "周围有人工作");
      }
      if (otherEat) {
         this.violate(player, contestant, DontDoRule.NEAR_EATING, "周围有人进食");
      }
      if (!level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0), e -> e instanceof Enemy).isEmpty()) {
         this.violate(player, contestant, DontDoRule.HOSTILES_NEAR, "周围有敌对生物");
      }
   }

   private void onDiamond(ServerPlayer player, Contestant contestant, BlockPos pos) {
      player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      if (contestant.hp >= this.settings.lives()) {
         this.ctx.send(player, "&e生命已满，钻石化为尘土。");
         return;
      }
      contestant.hp++;
      this.ctx.broadcast(this.room, "&b" + player.getGameProfile().getName() + " &7挖到钻石，生命 &a" + contestant.hp);
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
      this.pushBoard();
   }

   private void beginAtmosphere() {
      this.atmosphereOn = true;
      this.animalsReady = false;
      this.animalTicks = 0;
      this.weatherTicks = WEATHER_CYCLE;
      this.localWeather = LocalWeather.CLEAR;
      this.applyGlow();
      this.pushWeather();
   }

   private void tickAtmosphere(ServerLevel level) {
      if (level == null || this.phase != Phase.PLAYING) {
         return;
      }
      this.animalTicks++;
      if (this.animalTicks % ANIMAL_REFRESH == 0) {
         this.refreshAnimals(level);
      }
      if (!this.animalsReady && (this.animalsLanded(level) || this.animalTicks >= 100)) {
         this.animalsReady = true;
         this.rollWeather(true);
      } else if (this.animalsReady) {
         this.weatherTicks--;
         if (this.weatherTicks <= 0) {
            this.rollWeather(false);
            this.weatherTicks = WEATHER_CYCLE;
         }
      }
      if (this.ticks % 20 == 0) {
         this.applyGlow();
         this.pushWeather();
      }
   }

   private void refreshAnimals(ServerLevel level) {
      if (level == null) {
         return;
      }
      List<Animal> existing = level.getEntitiesOfClass(Animal.class, this.island.playBox(), Entity::isAlive);
      int room = ANIMAL_CAP - existing.size();
      if (room <= 0) {
         return;
      }
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int spawn = Math.min(ANIMAL_WAVE, room);
      int placed = 0;
      int attempts = 0;
      while (placed < spawn && attempts < spawn * 8) {
         attempts++;
         int px = rng.nextInt(8, IslandGenerator.PLAY - 8);
         int pz = rng.nextInt(8, IslandGenerator.PLAY - 8);
         int height = this.island.heightAt(px, pz);
         if (height <= -2) {
            continue;
         }
         EntityType<? extends Animal> type = ANIMALS[rng.nextInt(ANIMALS.length)];
         Animal animal = type.create(level);
         if (animal == null) {
            continue;
         }
         double x = this.island.playMinX() + px + 0.5;
         double z = this.island.playMinZ() + pz + 0.5;
         animal.moveTo(x, height + 1.2, z, rng.nextFloat() * 360.0F, 0.0F);
         animal.setPersistenceRequired();
         if (animal instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(animal.blockPosition()), MobSpawnType.EVENT, null);
         }
         animal.setAge(0);
         level.addFreshEntity(animal);
         placed++;
      }
   }

   private boolean animalsLanded(ServerLevel level) {
      List<Animal> animals = level.getEntitiesOfClass(Animal.class, this.island.playBox(), Entity::isAlive);
      if (animals.isEmpty()) {
         return false;
      }
      for (Animal animal : animals) {
         if (!animal.onGround() && !animal.isInWater()) {
            return false;
         }
      }
      return true;
   }

   private void rollWeather(boolean first) {
      LocalWeather[] all = LocalWeather.values();
      LocalWeather next = all[ThreadLocalRandom.current().nextInt(all.length)];
      if (all.length > 1) {
         while (next == this.localWeather) {
            next = all[ThreadLocalRandom.current().nextInt(all.length)];
         }
      }
      this.localWeather = next;
      this.pushWeather();
      this.ctx.broadcast(this.room, (first ? "&b动物落稳，本岛天气：" : "&b本岛天气变换：") + "&f" + next.title);
   }

   private void applyGlow() {
      if (!this.atmosphereOn) {
         return;
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Contestant contestant = this.players.get(uuid);
         if (player == null || contestant == null || !contestant.alive) {
            continue;
         }
         player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, true, false));
      }
   }

   private void pushWeather() {
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.sendWeather(player, this.localWeather);
         }
      }
   }

   private void sendWeather(ServerPlayer player, LocalWeather weather) {
      if (weather.rain > 0.0F) {
         player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
      } else {
         player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
      }
      player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, weather.rain));
      player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, weather.thunder));
   }

   private void clearWeather(ServerPlayer player) {
      this.sendWeather(player, LocalWeather.CLEAR);
   }

   private boolean rainingAt(ServerLevel level, BlockPos pos) {
      if (this.localWeather == LocalWeather.CLEAR || !this.island.inPlayable(pos)) {
         return false;
      }
      return level.canSeeSky(pos);
   }

   private void clearAnimals(ServerLevel level) {
      if (level == null) {
         return;
      }
      for (Animal animal : level.getEntitiesOfClass(Animal.class, this.island.playBox(), Entity::isAlive)) {
         animal.discard();
      }
   }

   private void padSpawn(ServerLevel level, Vec3 spawn) {
      if (level == null) {
         return;
      }
      BlockPos center = BlockPos.containing(spawn.x, spawn.y - 1, spawn.z);
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = center.offset(dx, 0, dz);
            if (this.island.inPlayable(pos)) {
               level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
         }
      }
   }

   private void violate(ServerPlayer player, Contestant contestant, DontDoRule rule, String reason) {
      this.violate(player, contestant, rule, reason, false);
   }

   private void violate(ServerPlayer player, Contestant contestant, DontDoRule rule, String reason, boolean ignoreGrace) {
      if (this.phase != Phase.PLAYING || !contestant.alive || contestant.rule != rule) {
         return;
      }
      if (!ignoreGrace && contestant.graceTicks > 0) {
         return;
      }
      player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.8F);
      player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 25, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&c违规 -1")));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color("&7事项已换新（自己的仍然保密）")));
      this.ctx.send(player, "&c你违规了，生命 -1。自己的新事项仍然保密。");
      for (UUID uuid : this.seats) {
         if (uuid.equals(player.getUUID())) {
            continue;
         }
         ServerPlayer other = this.ctx.player(uuid);
         if (other != null) {
            this.ctx.send(other, "&c" + player.getGameProfile().getName() + " &7违规：&f" + rule.title + " &8(" + reason + ")");
         }
      }
      this.loseLife(player, contestant, "违规");
      if (contestant.alive) {
         contestant.rule = DontDoRule.pick(rule);
         contestant.graceTicks = 40;
         this.announceOneRule(contestant);
      }
   }

   private void loseLife(ServerPlayer player, Contestant contestant, String why) {
      contestant.hp--;
      if (contestant.hp <= 0) {
         contestant.hp = 0;
         contestant.alive = false;
         player.setGameMode(GameType.SPECTATOR);
         player.removeAllEffects();
         Vec3 watch = this.island.watch();
         player.teleportTo(this.ctx.dontDo().islands().level(), watch.x, watch.y, watch.z, 0.0F, 20.0F);
         this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " &7因" + why + "淘汰。");
         this.checkWin();
      } else {
         this.ctx.send(player, "&c生命剩余 &f" + contestant.hp);
      }
      this.pushBoard();
   }

   private void rerollAll(String reason) {
      this.ctx.broadcast(this.room, "&e" + reason);
      for (UUID uuid : this.seats) {
         Contestant contestant = this.players.get(uuid);
         if (contestant == null || !contestant.alive) {
            continue;
         }
         contestant.rule = DontDoRule.pick(contestant.rule);
         contestant.graceTicks = 40;
      }
      this.announceRefresh(false);
      this.pushBoard();
   }

   private void announceRefresh(boolean first) {
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Contestant contestant = this.players.get(uuid);
         if (player == null || contestant == null || !contestant.alive) {
            continue;
         }
         player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(first ? "&c不要做" : "&e事项已刷新")));
         player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color("&7聊天栏是别人的事项描述")));
         this.sendOthersRules(player, uuid);
      }
   }

   private void announceOneRule(Contestant changed) {
      String name = this.ctx.name(changed.uuid);
      for (UUID uuid : this.seats) {
         if (uuid.equals(changed.uuid)) {
            continue;
         }
         ServerPlayer other = this.ctx.player(uuid);
         if (other == null) {
            continue;
         }
         this.ctx.send(other, "&e" + name + " &7的新事项：&f" + changed.rule.title + " &8- &7" + changed.rule.describe);
      }
   }

   private void sendOthersRules(ServerPlayer viewer, UUID viewerId) {
      this.ctx.send(viewer, "&8&m-------- &e他人不要做 &8&m--------");
      boolean any = false;
      for (UUID uuid : this.seats) {
         if (uuid.equals(viewerId)) {
            continue;
         }
         Contestant other = this.players.get(uuid);
         if (other == null || !other.alive) {
            continue;
         }
         any = true;
         this.ctx.send(viewer, "&f" + this.ctx.name(uuid) + " &c" + other.rule.title + " &8- &7" + other.rule.describe);
      }
      if (!any) {
         this.ctx.send(viewer, "&7目前没有其他存活玩家。");
      }
      this.ctx.send(viewer, "&8你的事项保密，只能靠别人提醒或自己踩雷。");
   }

   private void notifyNearbyDeath(ServerPlayer dead) {
      AABB box = dead.getBoundingBox().inflate(10.0);
      for (UUID uuid : this.seats) {
         if (uuid.equals(dead.getUUID())) {
            continue;
         }
         ServerPlayer other = this.ctx.player(uuid);
         Contestant oc = this.players.get(uuid);
         if (other == null || oc == null || !oc.alive) {
            continue;
         }
         if (box.intersects(other.getBoundingBox())) {
            this.violate(other, oc, DontDoRule.NEARBY_DEATH, "周围有人死亡");
         }
      }
   }

   private void checkWin() {
      if (this.phase != Phase.PLAYING) {
         return;
      }
      List<Contestant> alive = this.aliveList();
      if (alive.isEmpty()) {
         this.winnerLine = "&7无人存活，平局";
         this.beginEnd();
         return;
      }
      if (this.settings.teams()) {
         int team = alive.get(0).team;
         for (Contestant contestant : alive) {
            if (contestant.team != team) {
               return;
            }
         }
         this.winnerLine = "&a" + teamName(team) + "队获胜";
         this.beginEnd();
         return;
      }
      if (alive.size() == 1) {
         this.winnerLine = "&a" + this.ctx.name(alive.get(0).uuid) + " 获胜";
         this.beginEnd();
      }
   }

   private void beginEnd() {
      this.phase = Phase.ENDING;
      this.endTicks = 100;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, this.winnerLine);
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.updateBoss();
   }

   private void finish() {
      this.endNow();
   }

   private List<Contestant> aliveList() {
      List<Contestant> list = new ArrayList<>();
      for (Contestant contestant : this.players.values()) {
         if (contestant.alive) {
            list.add(contestant);
         }
      }
      return list;
   }

   private Contestant alive(ServerPlayer player) {
      if (player == null || this.phase != Phase.PLAYING) {
         return null;
      }
      Contestant contestant = this.players.get(player.getUUID());
      return contestant != null && contestant.alive ? contestant : null;
   }

   private void markEating(ServerPlayer player) {
      Contestant contestant = this.players.get(player.getUUID());
      if (contestant != null) {
         contestant.eatingTicks = 40;
      }
   }

   private void markWorking(ServerPlayer player) {
      Contestant contestant = this.players.get(player.getUUID());
      if (contestant != null) {
         contestant.workingTicks = 40;
      }
   }

   private void pushBoard() {
      List<Contestant> others = new ArrayList<>(this.players.values());
      others.sort(Comparator.comparingInt((Contestant c) -> -c.hp).thenComparing(c -> this.ctx.name(c.uuid)));
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Contestant self = this.players.get(uuid);
         if (player == null || self == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7刷新 &f" + format(this.ruleTicks));
         if (this.settings.randomEvents()) {
            lines.add("&d" + this.worldEvent.title + " &f" + format(this.eventTicks));
         }
         String selfTeam = this.settings.teams() ? teamColor(self.team) : "&e";
         lines.add(selfTeam + "你 &f" + self.hp + (self.alive ? " &8???" : " &7已淘汰"));
         int shown = 0;
         int cap = this.settings.randomEvents() ? 11 : 12;
         for (Contestant other : others) {
            if (other.uuid.equals(uuid)) {
               continue;
            }
            if (shown >= cap) {
               lines.add("&8…还有 " + (others.size() - 1 - shown) + " 人");
               break;
            }
            String color = this.settings.teams() ? teamColor(other.team) : "&f";
            String name = this.ctx.name(other.uuid);
            if (name.length() > 8) {
               name = name.substring(0, 8);
            }
            if (!other.alive) {
               lines.add(color + name + " &8淘汰");
            } else {
               String rule = clip(other.rule.title, 10);
               String desc = clip(other.rule.describe, 12);
               lines.add(color + name + " &f" + other.hp + " &c" + rule);
               if (shown + 1 < cap) {
                  lines.add("&7 " + desc);
                  shown++;
               }
            }
            shown++;
         }
         if (this.winnerLine != null) {
            lines.add(this.winnerLine);
         }
         this.board.update(player, lines);
      }
   }

   private void updateBoss() {
      if (this.phase == Phase.ENDING) {
         this.boss.setName(TextUtil.color(this.winnerLine == null ? "&a结算中" : this.winnerLine));
         this.boss.setProgress(Math.max(0.0F, this.endTicks / 100.0F));
         return;
      }
      int max = this.settings.ruleSeconds() * 20;
      this.boss.setName(TextUtil.color("&c不要做 &f" + format(this.ruleTicks)
         + (this.settings.randomEvents() ? " &8| &d" + this.worldEvent.title : "")));
      this.boss.setProgress(max <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, this.ruleTicks / (float) max)));
   }

   private void ensureTeam(ServerPlayer player, Contestant contestant) {
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = this.settings.teams() ? "srdd" + contestant.team : "srddffa";
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
      }
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setNameTagVisibility(Team.Visibility.ALWAYS);
      team.setAllowFriendlyFire(!this.settings.teams());
      if (this.settings.teams() && contestant.team >= 0 && contestant.team < TEAM_COLORS.length) {
         team.setColor(TEAM_COLORS[contestant.team]);
      }
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void restore(ServerPlayer player) {
      Scoreboard scoreboard = this.ctx.server().getScoreboard();
      PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
      if (current != null) {
         scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
      }
      player.setInvisible(false);
      player.closeContainer();
      player.removeAllEffects();
      this.clearWeather(player);
      this.board.remove(player);
      this.boss.removePlayer(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   private static String format(int ticks) {
      int sec = Math.max(0, ticks / 20);
      return String.format("%d:%02d", sec / 60, sec % 60);
   }

   private static String clip(String text, int max) {
      if (text == null) {
         return "";
      }
      return text.length() <= max ? text : text.substring(0, max);
   }

   private static String teamName(int team) {
      return team >= 0 && team < TEAM_NAMES.length ? TEAM_NAMES[team] : "?";
   }

   private static String teamColor(int team) {
      if (team < 0 || team >= TEAM_COLORS.length) {
         return "&f";
      }
      return "&" + TEAM_COLORS[team].getChar();
   }

   private static boolean isTool(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
         || stack.is(ItemTags.HOES) || item instanceof ShearsItem;
   }

   private static boolean isMeleeWeapon(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || item == Items.TRIDENT;
   }

   private static boolean isRanged(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      return item instanceof BowItem || item instanceof CrossbowItem || item == Items.TRIDENT
         || item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL;
   }

   private static boolean isMeat(ItemStack stack) {
      Item item = stack.getItem();
      return item == Items.BEEF || item == Items.COOKED_BEEF
         || item == Items.PORKCHOP || item == Items.COOKED_PORKCHOP
         || item == Items.CHICKEN || item == Items.COOKED_CHICKEN
         || item == Items.MUTTON || item == Items.COOKED_MUTTON
         || item == Items.RABBIT || item == Items.COOKED_RABBIT || item == Items.RABBIT_STEW
         || item == Items.COD || item == Items.COOKED_COD
         || item == Items.SALMON || item == Items.COOKED_SALMON
         || item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE || item == Items.TROPICAL_FISH;
   }

   private static boolean isLightItem(ItemStack stack) {
      if (!(stack.getItem() instanceof BlockItem blockItem)) {
         return stack.getItem() == Items.TORCH || stack.getItem() == Items.SOUL_TORCH
            || stack.getItem() == Items.LANTERN || stack.getItem() == Items.SOUL_LANTERN;
      }
      Block block = blockItem.getBlock();
      return block == Blocks.TORCH || block == Blocks.SOUL_TORCH || block == Blocks.LANTERN
         || block == Blocks.SOUL_LANTERN || block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT
         || block == Blocks.SEA_LANTERN || block == Blocks.JACK_O_LANTERN || block == Blocks.CAMPFIRE;
   }

   private static boolean isWorkBlock(Block block) {
      return block == Blocks.CRAFTING_TABLE || block == Blocks.SMITHING_TABLE || block == Blocks.STONECUTTER
         || block == Blocks.LOOM || block == Blocks.GRINDSTONE || block == Blocks.CARTOGRAPHY_TABLE
         || block == Blocks.FLETCHING_TABLE || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL
         || block == Blocks.DAMAGED_ANVIL || block == Blocks.ENCHANTING_TABLE;
   }

   private static boolean isContainer(Block block) {
      return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
         || block == Blocks.HOPPER || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
         || block == Blocks.SMOKER || block == Blocks.DISPENSER || block == Blocks.DROPPER
         || block == Blocks.SHULKER_BOX || block == Blocks.ENDER_CHEST;
   }

   private static boolean wearingArmor(ServerPlayer player) {
      for (ItemStack stack : player.getArmorSlots()) {
         if (stack != null && !stack.isEmpty()) {
            return true;
         }
      }
      return false;
   }

   private static boolean inventoryFull(ServerPlayer player) {
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.items.size(); i++) {
         if (inv.items.get(i).isEmpty()) {
            return false;
         }
      }
      return true;
   }

   private static boolean craftingUsed(ServerPlayer player) {
      for (int i = 1; i <= 4; i++) {
         if (!player.inventoryMenu.getSlot(i).getItem().isEmpty()) {
            return true;
         }
      }
      return false;
   }

   private static boolean tightSpace(ServerPlayer player) {
      int solids = 0;
      BlockPos base = player.blockPosition();
      for (int x = -1; x <= 1; x++) {
         for (int y = 0; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
               BlockPos pos = base.offset(x, y, z);
               if (player.level().getBlockState(pos).isCollisionShapeFullBlock(player.level(), pos)) {
                  solids++;
               }
            }
         }
      }
      return solids > 7;
   }

   private static boolean hasForeignEffect(ServerPlayer player) {
      for (MobEffectInstance effect : player.getActiveEffects()) {
         if (effect.getEffect() == MobEffects.GLOWING
            || effect.getEffect() == MobEffects.JUMP
            || effect.getEffect() == MobEffects.SLOW_FALLING
            || effect.getEffect() == MobEffects.MOVEMENT_SPEED
            || effect.getEffect() == MobEffects.WEAKNESS
            || effect.getEffect() == MobEffects.REGENERATION) {
            continue;
         }
         return true;
      }
      return false;
   }

   static final class Contestant {
      final UUID uuid;
      int hp;
      int team = -1;
      DontDoRule rule = DontDoRule.JUMP;
      boolean alive = true;
      float lastHealth = 20.0F;
      boolean wasJumping;
      boolean wasOnGround = true;
      int attackingTicks;
      int workingTicks;
      int eatingTicks;
      int graceTicks;
      int invulnTicks;

      Contestant(UUID uuid) {
         this.uuid = uuid;
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
