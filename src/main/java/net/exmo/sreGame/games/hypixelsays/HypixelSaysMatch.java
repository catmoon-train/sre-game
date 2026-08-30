package net.exmo.sreGame.games.hypixelsays;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Fifteen-round, server-authoritative Hypixel Says match. */
public final class HypixelSaysMatch {
   public enum Phase { INTRO, ROUND, BETWEEN, ENDED }
   private static final int INTRO_TICKS = 5 * 20;
   private static final int ROUND_TICKS = 5 * 20;
   private static final int BETWEEN_TICKS = 2 * 20;
   private static final int ROUNDS = 15;
   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final HypixelSaysArena arena;
   private final SidebarBoard board;
   private final Map<UUID, PlayerState> players = new HashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<HypixelSaysTask> deck = new ArrayList<>();
   private Phase phase = Phase.INTRO;
   private int ticks = INTRO_TICKS;
   private int round;
   private int boardTicks;
   private HypixelSaysTask task;

   HypixelSaysMatch(GameContext ctx, GameRoom room, List<UUID> seats, HypixelSaysArena arena) {
      this.ctx = ctx; this.room = room; this.seats = List.copyOf(seats); this.arena = arena; this.board = new SidebarBoard(ctx.server());
      this.deck.addAll(List.of(HypixelSaysTask.values()));
      java.util.Collections.shuffle(this.deck, ThreadLocalRandom.current());
      for (UUID uuid : seats) this.players.put(uuid, new PlayerState());
   }

   public UUID id() { return id; }
   public Phase phase() { return phase; }
   public HypixelSaysArena arena() { return arena; }

   public void start() {
      ServerLevel level = level();
      if (level == null) { finish("场地世界不可用"); return; }
      int i = 0;
      for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid); if (player == null) continue;
         saved.put(uuid, Saved.capture(player));
         board.create(player, "&d我说你做");
         resetPlayer(player, i++);
      }
      ctx.broadcast(room, "&8&m----------------");
      ctx.broadcast(room, "&d&l我说你做");
      ctx.broadcast(room, "&7共 &f15 &7轮，每轮 &f5 &7秒。第 1 名 &a+3&7，第 2 名 &e+2&7，其余完成者 &b+1&7。");
      ctx.broadcast(room, "&8&m----------------");
      pushBoard();
   }

   public void tick() {
      if (phase == Phase.ENDED) return;
      ticks--;
      if (++boardTicks >= 10) { boardTicks = 0; pushBoard(); }
      if (phase == Phase.INTRO) {
         if (ticks > 0 && ticks % 20 == 0) titleAll("&d准备", "&f" + (ticks / 20));
         if (ticks <= 0) beginRound();
         return;
      }
      if (phase == Phase.BETWEEN) { if (ticks <= 0) beginRound(); return; }
      tickRoundConditions();
      if (ticks <= 0) endRound();
   }

   private void beginRound() {
      if (round >= ROUNDS) { finish("全部回合结束"); return; }
      task = deck.get(round++);
      phase = Phase.ROUND; ticks = ROUND_TICKS;
      ServerLevel level = level(); if (level == null) { finish("场地世界不可用"); return; }
      arena.resetRound(level);
      int index = 0;
      for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid); PlayerState state = players.get(uuid);
         if (player == null || state == null || state.left) { index++; continue; }
         state.done = false; state.stillTicks = 0; state.lastPos = player.position();
         resetPlayer(player, index++); giveTaskKit(player); buildTaskArea(player, index - 1);
      }
      titleAll("&d&l我说你做", "&f" + task.text());
      ctx.broadcast(room, "&d第 " + round + "/" + ROUNDS + " 轮：&f" + task.text());
      forEachOnline(p -> p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.9F, 1.3F));
   }

   private void endRound() {
      phase = Phase.BETWEEN; ticks = BETWEEN_TICKS;
      int completed = 0;
      for (PlayerState state : players.values()) if (state.done) completed++;
      titleAll("&e本轮结束", completed == 0 ? "&7无人完成" : "&f" + completed + " 人完成");
      if (round >= ROUNDS) { ticks = BETWEEN_TICKS; }
   }

   private void tickRoundConditions() {
      if (task == null) return;
      for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid); PlayerState state = players.get(uuid);
         if (player == null || state == null || state.done || state.left) continue;
         boolean complete = switch (task.kind()) {
            case SNEAK -> player.isShiftKeyDown();
            case SPRINT -> player.isSprinting();
            case STILL -> tickStill(player, state);
            case WATER -> player.isInWater();
            case SLIME -> standingOn(player, Blocks.SLIME_BLOCK);
            case HONEY -> standingOn(player, Blocks.HONEY_BLOCK);
            case COBWEB -> atOrAbove(player, Blocks.COBWEB);
            case LADDER -> atOrAbove(player, Blocks.LADDER);
            case LOOK_SKY -> player.getLookAngle().y > 0.92;
            case LOOK_GROUND -> player.getLookAngle().y < -0.92;
            case LOOK_PLAYER -> lookingAtPlayer(player, false);
            case LOOK_HEAD -> lookingAtPlayer(player, true);
            case HOLD_DIAMOND -> player.getMainHandItem().is(Items.DIAMOND);
            case HOLD_BOW -> player.getMainHandItem().is(Items.BOW);
            case WEAR_CAP -> player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET);
            case WEAR_PUMPKIN -> player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN);
            case CRAFT_STICKS -> hasItem(player, Items.STICK);
            case CRAFT_TABLE -> hasItem(player, Items.CRAFTING_TABLE);
            case CRAFT_SWORD -> hasItem(player, Items.WOODEN_SWORD);
            case RIDE_PIG -> player.getVehicle() instanceof Pig;
            case RIDE_HORSE -> player.getVehicle() instanceof Horse;
            case BOARD_BOAT -> player.getVehicle() instanceof Boat;
            case NEAR_PLAYER -> nearPlayer(player);
            default -> false;
         };
         if (complete) complete(player);
      }
   }

   private boolean tickStill(ServerPlayer player, PlayerState state) {
      boolean still = state.lastPos != null && state.lastPos.distanceToSqr(player.position()) < 0.0025;
      state.lastPos = player.position(); state.stillTicks = still ? state.stillTicks + 1 : 0;
      return state.stillTicks >= 20;
   }

   public void handleJump(ServerPlayer player) { ifActive(player, HypixelSaysTask.Kind.JUMP); }
   public boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state) {
      if (phase != Phase.ROUND || task == null || !players.containsKey(player.getUUID())) return false;
      if (task.kind() == HypixelSaysTask.Kind.BREAK_LOG && state.is(Blocks.OAK_LOG)) { complete(player); return true; }
      if (task.kind() == HypixelSaysTask.Kind.BREAK_WOOL && state.is(Blocks.RED_WOOL)) { complete(player); return true; }
      return false;
   }
   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      if (phase != Phase.ROUND || task == null || !players.containsKey(player.getUUID())) return InteractionResult.PASS;
      HypixelSaysTask.Kind k = task.kind();
      if (k == HypixelSaysTask.Kind.USE_SHIELD && stack.is(Items.SHIELD)) return completeAndFail(player);
      if (k == HypixelSaysTask.Kind.EAT_APPLE && stack.is(Items.APPLE)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.DRINK_WATER && stack.is(Items.POTION)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.THROW_EGG && stack.is(Items.EGG)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.THROW_SNOWBALL && stack.is(Items.SNOWBALL)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.THROW_XP && stack.is(Items.EXPERIENCE_BOTTLE)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.CAST_ROD && stack.is(Items.FISHING_ROD)) return completeAndPass(player);
      return InteractionResult.PASS;
   }
   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      if (phase != Phase.ROUND || task == null || !players.containsKey(player.getUUID())) return InteractionResult.FAIL;
      BlockPos pos = hit.getBlockPos(); Block block = player.level().getBlockState(pos).getBlock(); HypixelSaysTask.Kind k = task.kind();
      if (k == HypixelSaysTask.Kind.BUTTON && block == Blocks.STONE_BUTTON) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.LEVER && block == Blocks.LEVER) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.BELL && block == Blocks.BELL) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.CHEST && block == Blocks.CHEST) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.CRAFT_SWORD && block == Blocks.CRAFTING_TABLE) return InteractionResult.PASS;
      if (k == HypixelSaysTask.Kind.FILL_BUCKET && block == Blocks.WATER && stack.is(Items.BUCKET)) return completeAndFail(player);
      if (k == HypixelSaysTask.Kind.EXTINGUISH_FIRE && block == Blocks.FIRE && stack.is(Items.WATER_BUCKET)) { player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2); return completeAndFail(player); }
      if (k == HypixelSaysTask.Kind.PLACE_WOOL && stack.is(Items.RED_WOOL)) return placeAndComplete(player, hit, Blocks.RED_WOOL.defaultBlockState());
      if (k == HypixelSaysTask.Kind.PLACE_TORCH && stack.is(Items.TORCH)) return placeAndComplete(player, hit, Blocks.TORCH.defaultBlockState());
      if (k == HypixelSaysTask.Kind.PLANT_TREE && stack.is(Items.OAK_SAPLING) && block == Blocks.DIRT) return placeAndComplete(player, hit, Blocks.OAK_SAPLING.defaultBlockState());
      if (k == HypixelSaysTask.Kind.TILL_DIRT && stack.is(Items.WOODEN_HOE) && block == Blocks.DIRT) { player.level().setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 2); return completeAndFail(player); }
      return InteractionResult.FAIL;
   }
   public InteractionResult handleUseEntity(ServerPlayer player, Entity entity) {
      if (phase != Phase.ROUND || task == null || !players.containsKey(player.getUUID())) return InteractionResult.FAIL;
      HypixelSaysTask.Kind k = task.kind(); ItemStack held = player.getMainHandItem();
      if (k == HypixelSaysTask.Kind.MILK_COW && entity instanceof Cow && held.is(Items.BUCKET)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.SHEAR_SHEEP && entity instanceof Sheep && held.is(Items.SHEARS)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.FEED_PIG && entity instanceof Pig && held.is(Items.CARROT)) return completeAndPass(player);
      if (k == HypixelSaysTask.Kind.RIDE_PIG && entity instanceof Pig) { player.startRiding(entity, true); complete(player); return InteractionResult.FAIL; }
      if (k == HypixelSaysTask.Kind.RIDE_HORSE && entity instanceof Horse) { player.startRiding(entity, true); complete(player); return InteractionResult.FAIL; }
      if (k == HypixelSaysTask.Kind.BOARD_BOAT && entity instanceof Boat) { player.startRiding(entity, true); complete(player); return InteractionResult.FAIL; }
      return InteractionResult.FAIL;
   }
   public boolean handleAttack(ServerPlayer player, Entity target) {
      if (phase != Phase.ROUND || task == null || !players.containsKey(player.getUUID())) return false;
      boolean match = (task.kind() == HypixelSaysTask.Kind.HIT_PIG && target instanceof Pig)
         || (task.kind() == HypixelSaysTask.Kind.HIT_CHICKEN && target instanceof Chicken)
         || (task.kind() == HypixelSaysTask.Kind.HIT_COW && target instanceof Cow)
         || (task.kind() == HypixelSaysTask.Kind.HIT_PLAYER && target instanceof ServerPlayer && target != player);
      if (match) complete(player);
      return true;
   }
   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      if (phase == Phase.ENDED || !players.containsKey(player.getUUID())) return false;
      if (phase == Phase.ROUND && task != null) {
         Entity direct = source.getDirectEntity();
         if (task.kind() == HypixelSaysTask.Kind.SHOOT_SELF && direct instanceof Projectile projectile && projectile.getOwner() == player) complete(player);
         if (task.kind() == HypixelSaysTask.Kind.CACTUS && source.type().msgId().contains("cactus")) complete(player);
         if (task.kind() == HypixelSaysTask.Kind.FIRE && source.type().msgId().toLowerCase(java.util.Locale.ROOT).contains("fire")) complete(player);
      }
      return true;
   }
   public boolean handleMobDamage(Entity entity, DamageSource source) {
      if (phase != Phase.ROUND || task == null || task.kind() != HypixelSaysTask.Kind.SHOOT_TARGET) return false;
      Entity attacker = source.getEntity();
      if (attacker instanceof ServerPlayer player && players.containsKey(player.getUUID())) { complete(player); return true; }
      return false;
   }
   public boolean handleDeath(ServerPlayer player) { if (!players.containsKey(player.getUUID())) return false; resetPlayer(player, seats.indexOf(player.getUUID())); return true; }
   public void onLeave(UUID uuid) {
      PlayerState state = players.get(uuid); if (state == null) return; state.left = true;
      ServerPlayer player = ctx.player(uuid); if (player != null) restore(player); else board.remove(uuid);
      if (players.values().stream().noneMatch(s -> !s.left)) finish("全员离场");
   }
   public void endNow() { finish("对局中止"); }

   private void ifActive(ServerPlayer player, HypixelSaysTask.Kind kind) { if (phase == Phase.ROUND && task != null && task.kind() == kind && players.containsKey(player.getUUID())) complete(player); }
   private InteractionResult completeAndFail(ServerPlayer player) { complete(player); return InteractionResult.FAIL; }
   private InteractionResult completeAndPass(ServerPlayer player) { complete(player); return InteractionResult.PASS; }
   private InteractionResult placeAndComplete(ServerPlayer player, BlockHitResult hit, BlockState state) { player.level().setBlock(hit.getBlockPos().relative(hit.getDirection()), state, 2); complete(player); return InteractionResult.FAIL; }
   private void complete(ServerPlayer player) {
      PlayerState state = players.get(player.getUUID()); if (state == null || state.done || phase != Phase.ROUND) return;
      state.done = true; int place = (int) players.values().stream().filter(s -> s.done).count(); int gained = place == 1 ? 3 : place == 2 ? 2 : 1; state.score += gained;
      player.sendSystemMessage(TextUtil.color("&a完成！&7第 &f" + place + " &7名，&e+" + gained));
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8F, 1.4F);
   }
   private void resetPlayer(ServerPlayer player, int index) {
      ServerLevel level = level(); if (level == null) return;
      player.closeContainer(); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
      player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5.0F);
      Vec3 spawn = arena.spawn(index, seats.size()); player.teleportTo(level, spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
   }
   private void giveTaskKit(ServerPlayer player) {
      Inventory inv = player.getInventory(); if (task == null) return;
      switch (task.kind()) {
         case HOLD_DIAMOND -> inv.setItem(0, new ItemStack(Items.DIAMOND)); case HOLD_BOW -> inv.setItem(0, new ItemStack(Items.BOW));
         case WEAR_CAP -> inv.setItem(0, new ItemStack(Items.LEATHER_HELMET)); case WEAR_PUMPKIN -> inv.setItem(0, new ItemStack(Items.CARVED_PUMPKIN));
         case USE_SHIELD -> inv.setItem(0, new ItemStack(Items.SHIELD)); case BREAK_LOG -> inv.setItem(0, new ItemStack(Items.WOODEN_AXE));
         case BREAK_WOOL -> inv.setItem(0, new ItemStack(Items.SHEARS)); case PLACE_WOOL -> inv.setItem(0, new ItemStack(Items.RED_WOOL));
         case PLACE_TORCH -> inv.setItem(0, new ItemStack(Items.TORCH)); case PLANT_TREE -> inv.setItem(0, new ItemStack(Items.OAK_SAPLING));
         case TILL_DIRT -> inv.setItem(0, new ItemStack(Items.WOODEN_HOE)); case CRAFT_STICKS -> inv.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
         case CRAFT_TABLE -> inv.setItem(0, new ItemStack(Items.OAK_PLANKS, 4)); case CRAFT_SWORD -> { inv.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); inv.setItem(1, new ItemStack(Items.STICK)); }
         case EAT_APPLE -> inv.setItem(0, new ItemStack(Items.APPLE)); case DRINK_WATER -> inv.setItem(0, new ItemStack(Items.POTION));
         case FILL_BUCKET, MILK_COW -> inv.setItem(0, new ItemStack(Items.BUCKET)); case SHEAR_SHEEP -> inv.setItem(0, new ItemStack(Items.SHEARS));
         case CAST_ROD -> inv.setItem(0, new ItemStack(Items.FISHING_ROD)); case THROW_EGG -> inv.setItem(0, new ItemStack(Items.EGG));
         case THROW_SNOWBALL -> inv.setItem(0, new ItemStack(Items.SNOWBALL)); case THROW_XP -> inv.setItem(0, new ItemStack(Items.EXPERIENCE_BOTTLE));
         case SHOOT_TARGET, SHOOT_SELF -> { inv.setItem(0, new ItemStack(Items.BOW)); inv.setItem(1, new ItemStack(Items.ARROW, 16)); }
         case FEED_PIG -> inv.setItem(0, new ItemStack(Items.CARROT)); case EXTINGUISH_FIRE -> inv.setItem(0, new ItemStack(Items.WATER_BUCKET));
         default -> { }
      }
   }
   private void buildTaskArea(ServerPlayer player, int index) {
      ServerLevel level = level(); if (level == null || task == null) return;
      BlockPos p = arena.workPos(index, seats.size()); BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      level.setBlock(m.set(p.offset(2, 1, 0)), Blocks.WATER.defaultBlockState(), 2); level.setBlock(m.set(p.offset(-2, 1, 0)), Blocks.SLIME_BLOCK.defaultBlockState(), 2);
      level.setBlock(m.set(p.offset(0, 1, 2)), Blocks.HONEY_BLOCK.defaultBlockState(), 2); level.setBlock(m.set(p.offset(0, 1, -2)), Blocks.COBWEB.defaultBlockState(), 2);
      level.setBlock(m.set(p.offset(3, 1, 3)), Blocks.OAK_PLANKS.defaultBlockState(), 2); for (int y = 2; y <= 4; y++) level.setBlock(m.set(p.getX() + 3, p.getY() + y, p.getZ() + 3), Blocks.LADDER.defaultBlockState(), 2);
      BlockPos target = p.offset(0, 1, 4);
      switch (task.kind()) {
         case BREAK_LOG -> level.setBlock(target, Blocks.OAK_LOG.defaultBlockState(), 2); case BREAK_WOOL -> level.setBlock(target, Blocks.RED_WOOL.defaultBlockState(), 2);
         case PLANT_TREE, TILL_DIRT -> level.setBlock(p.offset(0, 1, 1), Blocks.DIRT.defaultBlockState(), 2);
         case BUTTON -> level.setBlock(target, Blocks.STONE_BUTTON.defaultBlockState(), 2); case LEVER -> level.setBlock(target, Blocks.LEVER.defaultBlockState(), 2);
         case BELL -> level.setBlock(target, Blocks.BELL.defaultBlockState(), 2); case FILL_BUCKET -> level.setBlock(target, Blocks.WATER.defaultBlockState(), 2);
         case CHEST -> level.setBlock(target, Blocks.CHEST.defaultBlockState(), 2); case EXTINGUISH_FIRE -> level.setBlock(target, Blocks.FIRE.defaultBlockState(), 2);
         case CRAFT_SWORD -> level.setBlock(target, Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
         case CACTUS -> level.setBlock(target, Blocks.CACTUS.defaultBlockState(), 2); case FIRE -> level.setBlock(target, Blocks.FIRE.defaultBlockState(), 2);
         default -> spawnTaskEntity(level, p.offset(0, 1, 4));
      }
   }
   private void spawnTaskEntity(ServerLevel level, BlockPos pos) {
      Entity entity = switch (task.kind()) {
         case MILK_COW, HIT_COW -> EntityType.COW.create(level); case SHEAR_SHEEP -> EntityType.SHEEP.create(level); case HIT_CHICKEN -> EntityType.CHICKEN.create(level);
         case HIT_PIG, RIDE_PIG, FEED_PIG -> EntityType.PIG.create(level); case RIDE_HORSE -> EntityType.HORSE.create(level); case BOARD_BOAT -> new Boat(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5);
         case SHOOT_TARGET -> EntityType.SHEEP.create(level); default -> null;
      };
      if (entity != null) { entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); level.addFreshEntity(entity); arena.track(entity); }
   }
   private boolean standingOn(ServerPlayer player, Block block) { return player.level().getBlockState(player.blockPosition().below()).is(block); }
   private boolean atOrAbove(ServerPlayer player, Block block) { return player.level().getBlockState(player.blockPosition()).is(block) || player.level().getBlockState(player.blockPosition().below()).is(block); }
   private boolean hasItem(ServerPlayer player, Item item) { for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(item)) return true; return false; }
   private boolean lookingAtPlayer(ServerPlayer player, boolean head) {
      Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
      for (UUID uuid : seats) { ServerPlayer target = ctx.player(uuid); if (target == null || target == player) continue; Vec3 point = target.position().add(0, head ? 1.55 : 0.9, 0); Vec3 delta = point.subtract(eye); if (delta.lengthSqr() < 100 && look.dot(delta.normalize()) > 0.985) return true; }
      return false;
   }
   private boolean nearPlayer(ServerPlayer player) { for (UUID uuid : seats) { ServerPlayer other = ctx.player(uuid); if (other != null && other != player && other.distanceToSqr(player) <= 9) return true; } return false; }
   private ServerLevel level() { return ctx.hypixelSays().arenas().level(); }
   private void titleAll(String title, String subtitle) { forEachOnline(p -> { p.connection.send(new ClientboundSetTitlesAnimationPacket(2, 20, 4)); p.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title))); p.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle))); }); }
   private void forEachOnline(java.util.function.Consumer<ServerPlayer> action) { for (UUID uuid : seats) { ServerPlayer p = ctx.player(uuid); if (p != null) action.accept(p); } }
   private void pushBoard() {
      List<Map.Entry<UUID, PlayerState>> ranked = new ArrayList<>(players.entrySet()); ranked.sort(Comparator.<Map.Entry<UUID, PlayerState>>comparingInt(e -> e.getValue().score).reversed());
      forEachOnline(player -> { List<String> lines = new ArrayList<>(); lines.add("&7回合 &e" + Math.min(round, ROUNDS) + "&7/&e" + ROUNDS); lines.add("&7" + (phase == Phase.ROUND ? "剩余" : "状态") + " &e" + Math.max(0, (ticks + 19) / 20) + (phase == Phase.ROUND ? " 秒" : "")); lines.add("&7&m---------------"); if (task != null) lines.add("&f" + task.text()); int place = 1; for (var entry : ranked) { if (place > 6) break; lines.add((entry.getKey().equals(player.getUUID()) ? "&a" : "&f") + place + ". " + ctx.name(entry.getKey()) + " &e" + entry.getValue().score); place++; } board.update(player, lines); });
   }
   private void finish(String reason) {
      if (phase == Phase.ENDED) return; phase = Phase.ENDED;
      List<Map.Entry<UUID, PlayerState>> ranked = new ArrayList<>(players.entrySet()); ranked.sort(Comparator.<Map.Entry<UUID, PlayerState>>comparingInt(e -> e.getValue().score).reversed());
      int best = ranked.isEmpty() ? 0 : ranked.getFirst().getValue().score; List<String> winners = new ArrayList<>(); for (var entry : ranked) if (!entry.getValue().left && entry.getValue().score == best) winners.add(ctx.name(entry.getKey()));
      ctx.broadcast(room, "&8&m----------------"); ctx.broadcast(room, "&d&l我说你做结束 &7(" + reason + ")"); ctx.broadcast(room, "&6获胜者：&f" + String.join("&7、&f", winners) + " &e" + best + " 分");
      int place = 1; for (var entry : ranked) { ctx.broadcast(room, "&7" + place++ + ". &f" + ctx.name(entry.getKey()) + " &e" + entry.getValue().score); }
      ctx.broadcast(room, "&8&m----------------");
      for (UUID uuid : seats) { ServerPlayer p = ctx.player(uuid); if (p != null) restore(p); else board.remove(uuid); }
      ctx.hypixelSays().remove(this); ctx.hypixelSays().arenas().release(arena); ctx.rooms().onMatchEnded(id);
   }
   private void restore(ServerPlayer player) { Saved data = saved.get(player.getUUID()); board.remove(player.getUUID()); if (data != null) data.apply(player, ctx); }
   private static final class PlayerState { int score; boolean done; boolean left; int stillTicks; Vec3 lastPos; }
   private record Saved(net.minecraft.resources.ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) { List<ItemStack> items = new ArrayList<>(); Inventory inv = player.getInventory(); for (int i = 0; i < inv.getContainerSize(); i++) items.add(inv.getItem(i).copy()); return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(), items); }
      void apply(ServerPlayer player, GameContext ctx) { ServerLevel level = ctx.server().getLevel(dimension); if (level == null) level = ctx.server().overworld(); player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch); player.setGameMode(gameType); Inventory inv = player.getInventory(); inv.clearContent(); for (int i = 0; i < Math.min(inv.getContainerSize(), items.size()); i++) inv.setItem(i, items.get(i).copy()); }
   }
}
