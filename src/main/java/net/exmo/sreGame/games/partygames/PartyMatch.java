package net.exmo.sreGame.games.partygames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared lifecycle and event implementation for the 16 fixed party games. */
public final class PartyMatch implements PartySession {
   private enum Phase { HORSE_SELECTION, DIG_SELECTION, INTRO, RUNNING, ENDED }
   private static final int INTRO_TICKS = 5 * 20;
   private static final int HORSE_SELECTION_TICKS = 20 * 20;
   private static final int DIG_SELECTION_TICKS = 30 * 20;
   private static final int DROPPER_STAGES = 5;
   private static final int HORSE_LAPS = 10;
   private static final int HORSE_FINISH_COOLDOWN_TICKS = 20;
   private static final int[] DIG_TOOL_COSTS = {0, 3, 6, 10, 15};
   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final PartyGameType type;
   private final MapTemplate template;
   private final PartyArena arena;
   private final List<UUID> seats;
   private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Map<UUID, UUID> mobCredits = new HashMap<>();
   private final Map<BlockPos, Integer> delayedAir = new HashMap<>();
   private final Map<BlockPos, Integer> oreRespawns = new HashMap<>();
   private final List<Mob> bats = new ArrayList<>();
   private final List<Horse> horses = new ArrayList<>();
   private final SidebarBoard board;
   private Phase phase = Phase.INTRO;
   private int ticks = INTRO_TICKS;
   private int boardTicks;
   private int colorTicks;
   private int colorWarmupTicks;
   private int colorRound;
   private int colorIntervalTicks;
   private int openingGraceTicks;
   private BlockState safeColor = Blocks.RED_WOOL.defaultBlockState();
   private UUID potatoHolder;
   private final List<Mob> shooterTargets = new ArrayList<>();
   /** Runtime-only state for the Minecraft Party 2 100-series duels. */
   private final List<Mob> classicMobs = new ArrayList<>();
   private final Map<UUID, Pig> classicPigs = new HashMap<>();
   private final Map<UUID, UUID> minionOwners = new HashMap<>();
   private final Map<BlockPos, ClassicButton> classicButtons = new HashMap<>();
   private final Map<BlockPos, Integer> series200Buttons = new HashMap<>();
   private final Map<BlockPos, Integer> series200Flags = new HashMap<>();
   private final Map<UUID, Integer> series200CarriedFlags = new HashMap<>();
   private final Map<UUID, Integer> series200Choices = new HashMap<>();
   private final Map<UUID, Integer> series200ChickenTeams = new HashMap<>();
   private final Map<UUID, Integer> series200RecruitTeams = new HashMap<>();
   private final Map<BlockPos, Integer> series300Buttons = new HashMap<>();
   private final Map<UUID, Integer> series300Roles = new HashMap<>();
   private final Map<UUID, Integer> series300Lives = new HashMap<>();
   private final Map<UUID, Integer> series300MobTeams = new HashMap<>();
   private final Map<Integer, Integer> series200TeamScore = new HashMap<>();
   private final Map<Integer, Integer> series200TeamLives = new HashMap<>();
   private final List<Turtle> teamHockeyPucks = new ArrayList<>();
   private final int[] classicCells = new int[9];
   private Turtle hockeyPuck;
   private Slime deuceBall;
   private UUID lastBallHit;
   private int classicWindTicks;
   private int classicWindDirection = 1;
   private int classicWinThreshold = 9;
   private int series200RoundTicks;
   private int series200WinningTeam;
   private int series200CartTarget;
   private int series300Round;
   private int series300Capture;

   PartyMatch(GameContext ctx, GameRoom room, PartyGameType type, MapTemplate template, PartyArena arena) {
      this.ctx = ctx; this.room = room; this.type = type; this.template = template; this.arena = arena;
      this.seats = List.copyOf(room.members());
      this.board = new SidebarBoard(ctx.server());
      for (UUID uuid : this.seats) {
         this.fighters.put(uuid, new Fighter(uuid));
         ServerPlayer player = ctx.player(uuid);
         if (player != null) this.saved.put(uuid, Saved.capture(player));
      }
   }

   public UUID id() { return this.id; }
   public PartyGameType type() { return this.type; }
   public PartyArena arena() { return this.arena; }

   public void start() {
      ServerLevel level = level();
      if (level == null) { finish(null); return; }
      if (isClassic300()) primeClassic300Roles();
      int index = 0;
      for (UUID uuid : this.seats) {
         ServerPlayer player = ctx.player(uuid);
         if (player == null) continue;
         saved.putIfAbsent(uuid, Saved.capture(player));
         player.closeContainer();
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.removeAllEffects();
         player.setHealth(player.getMaxHealth());
         player.getFoodData().setFoodLevel(20);
         arena.teleport(player, level, spawnFor(index++));
         giveKit(player);
         board.create(player, "&6" + type.displayName());
      }
      pushHud();
      ctx.broadcast(room, "&8&m----------------");
      ctx.broadcast(room, "&6&l" + type.displayName());
      ctx.broadcast(room, "&7地图： &f" + template.id() + " &8| &7倒计时后开始");
      ctx.broadcast(room, rules());
      ctx.broadcast(room, "&8&m----------------");
      if (type == PartyGameType.HORSE_RACE) {
         beginHorseSelection();
         return;
      }
      if (type == PartyGameType.DIG_DOWN) {
         beginDigSelection();
         return;
      }
      if (type == PartyGameType.TNT_RUN) showTntCountdown(INTRO_TICKS / 20);
      else showPrepareCountdown(INTRO_TICKS / 20);
   }

   public void tick() {
      if (phase == Phase.ENDED) return;
      ticks--;
      if (++boardTicks >= 10) { boardTicks = 0; pushHud(); }
      if (phase == Phase.HORSE_SELECTION) {
         tickHorseSelection();
         return;
      }
      if (phase == Phase.DIG_SELECTION) {
         tickDigSelection();
         return;
      }
      if (phase == Phase.INTRO) {
         if (type == PartyGameType.TNT_RUN) holdTntPlayers();
         else if (type != PartyGameType.HORSE_RACE) holdIntroPlayers();
         if (type == PartyGameType.TNT_RUN) {
            if (ticks > 0 && ticks % 20 == 0) showTntCountdown(ticks / 20);
         } else if (ticks > 0 && ticks % 20 == 0) {
            showPrepareCountdown(ticks / 20);
         }
         if (ticks <= 0) begin();
         return;
      }
      tickDelayedBlocks();
      tickOreRespawns();
      tickPlayers();
      if (openingGraceTicks > 0) openingGraceTicks--;
      if (isClassic100()) {
         tickClassic100();
         if (phase != Phase.ENDED && ticks <= 0) finishByMode();
         return;
      }
      if (isClassic200()) {
         tickClassic200();
         if (phase != Phase.ENDED && ticks <= 0) finishSeries200ByScore();
         return;
      }
      if (isClassic300()) {
         tickClassic300();
         if (phase != Phase.ENDED && ticks <= 0) finishSeries300ByRule();
         return;
      }
      if (type == PartyGameType.PUNCH_THE_BAT) tickBatTargets();
      else if (type == PartyGameType.MOB_SHOOTER) tickShooterTargets();
      else if (type.mode() == PartyGameType.Mode.SCORE && ticks % 30 == 0) spawnTarget();
      if (type == PartyGameType.HOT_POTATO && ticks % (20 * 20) == 0) explodePotato();
      if (type == PartyGameType.COLORFUL_RUN) tickColorful();
      if (ticks <= 0) finishByMode();
   }

   public boolean handleDamage(ServerPlayer victim, DamageSource source) {
      Fighter fighter = fighters.get(victim.getUUID());
      if (fighter == null || phase == Phase.ENDED) return false;
      if (phase == Phase.INTRO) return true;
      if (openingGraceTicks > 0) return true;
      if (isClassic200()) return handleSeries200Damage(victim, source);
      if (isClassic300()) return handleSeries300Damage(victim, source);
      if (type == PartyGameType.ONE_IN_CHAMBER) {
         if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer attacker && attacker != victim) {
            eliminate(victim, attacker.getUUID(), true);
            return true;
         }
         return false;
      }
      if (type == PartyGameType.SURVIVAL_GAMES || type == PartyGameType.GLADIATOR_FIGHT) return false;
      if (type == PartyGameType.CANNONEERS && source.getDirectEntity() instanceof Projectile projectile
         && projectile.getOwner() instanceof ServerPlayer attacker && attacker != victim) {
         finish(attacker.getUUID());
         return true;
      }
      return true;
   }

   public boolean handleDeath(ServerPlayer player) {
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter == null) return false;
      if (phase == Phase.RUNNING && fighter.alive) {
         eliminate(player, null);
         if (isClassic200()) tickTeamElimination();
         if (isClassic300()) tickSeries300Elimination();
      }
      return true;
   }

   public boolean handleAttack(ServerPlayer attacker, Entity target) {
      Fighter fighter = fighters.get(attacker.getUUID());
      if (fighter == null || !fighter.alive || phase != Phase.RUNNING) return fighter != null;
      if (isClassic200()) return handleSeries200Attack(attacker, target);
      if (isClassic300()) return handleSeries300Attack(attacker, target);
      if (target instanceof ServerPlayer victim) {
         if (type == PartyGameType.BRIDGE_CROSSING) { push(attacker, victim, 0.9); return true; }
         if (type == PartyGameType.HOT_POTATO && attacker.getUUID().equals(potatoHolder)) {
            transferPotato(victim); return true;
         }
         if (type == PartyGameType.SUMO) { push(attacker, victim, 1.35); return true; }
         if (type == PartyGameType.ONE_IN_CHAMBER && attacker.getMainHandItem().is(Items.WOODEN_SWORD)) return false;
         if (type == PartyGameType.GLADIATOR_FIGHT) return false;
         return type != PartyGameType.SURVIVAL_GAMES;
      }
      if (target instanceof Mob mob) {
         if (type == PartyGameType.MINIONS && classicMobs.contains(mob)) { convertMinion(attacker, mob); return true; }
         if (type == PartyGameType.TURTLE_HOCKEY && mob == hockeyPuck) { pushClassicBall(attacker, mob, 1.25); return true; }
         if (type == PartyGameType.PIG_PUSHERS && classicPigs.containsValue(mob)) { pushClassicBall(attacker, mob, 0.72); return true; }
         if (type == PartyGameType.DEUCE && mob == deuceBall) { lastBallHit = attacker.getUUID(); pushClassicBall(attacker, mob, 1.15); return true; }
         if (type == PartyGameType.PUNCH_THE_BAT && mob instanceof Bat bat) {
            playBatHitEffects(bat); mob.discard(); bats.remove(mob); award(attacker.getUUID(), 1); return true;
         }
         if (type == PartyGameType.ANIMAL_SLAUGHTER) {
            mobCredits.put(mob.getUUID(), attacker.getUUID()); return false;
         }
         return type == PartyGameType.MOB_SHOOTER || type == PartyGameType.PUNCH_THE_BAT;
      }
      return type != PartyGameType.SURVIVAL_GAMES;
   }

   public boolean handleMobDeath(Entity entity, DamageSource source) {
      if (phase != Phase.RUNNING || entity == null || !arena.contains(entity.getX(), entity.getY(), entity.getZ())) return false;
      UUID player = mobCredits.remove(entity.getUUID());
      if (player == null && source.getEntity() instanceof ServerPlayer direct) player = direct.getUUID();
      if (player == null && source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) player = owner.getUUID();
      if (player == null || !fighters.containsKey(player)) return false;
      if (type == PartyGameType.ANIMAL_SLAUGHTER) award(player, animalScore(entity));
      else if (type == PartyGameType.MOB_SHOOTER && shooterTargets.remove(entity)) {
         announceShooterScore(player, entity);
      }
      return type == PartyGameType.ANIMAL_SLAUGHTER || type == PartyGameType.MOB_SHOOTER;
   }

   /** Consumes valid arrows on the shooting gallery target, making every animal a one-hit kill. */
   public boolean handleMobDamage(Entity entity, DamageSource source) {
      if (isClassic200() && phase == Phase.RUNNING && entity instanceof Chicken chicken) {
         Integer chickenTeam = series200ChickenTeams.get(chicken.getUUID());
         ServerPlayer attacker = sourcePlayer(source);
         if (chickenTeam == null || attacker == null || teamOf(attacker.getUUID()) == chickenTeam) return chickenTeam != null;
         int lives = series200TeamLives.merge(chickenTeam, -1, Integer::sum);
         chicken.playSound(SoundEvents.CHICKEN_HURT, 0.8F, 1.2F);
         if (lives <= 0) finishTeam(chickenTeam == 1 ? 2 : 1);
         return true;
      }
      if (isClassic300() && phase == Phase.RUNNING && entity instanceof Villager villager && type == PartyGameType.GHOST_HUNT) {
         ServerPlayer attacker = sourcePlayer(source);
         if (attacker == null || series300Roles.getOrDefault(attacker.getUUID(), 0) != 2) return true;
         villager.discard();
         classicMobs.remove(villager);
         int haunted = series200TeamScore.merge(2, 1, Integer::sum);
         if (haunted >= 6) finishSeries300Role(2);
         return true;
      }
      if (type == PartyGameType.PUNCH_THE_BAT && phase == Phase.RUNNING && entity instanceof Bat bat
         && source.getEntity() instanceof ServerPlayer attacker && fighters.containsKey(attacker.getUUID())) {
         playBatHitEffects(bat);
         bat.discard();
         bats.remove(bat);
         award(attacker.getUUID(), 1);
         return true;
      }
      if (type != PartyGameType.MOB_SHOOTER || phase != Phase.RUNNING || !(entity instanceof Mob mob) || !shooterTargets.contains(mob)) return false;
      if (!(source.getDirectEntity() instanceof Projectile projectile) || !(projectile.getOwner() instanceof ServerPlayer shooter)) return false;
      Fighter fighter = fighters.get(shooter.getUUID());
      if (fighter == null || !fighter.alive) return false;
      playShooterKillEffects(mob);
      mob.discard();
      shooterTargets.remove(mob);
      announceShooterScore(shooter.getUUID(), entity);
      return true;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter == null || !fighter.alive || phase != Phase.RUNNING) return InteractionResult.FAIL;
      if (isClassic100()) return handleClassicBlockUse(player, fighter, hit.getBlockPos());
      if (isClassic200()) return handleSeries200BlockUse(player, fighter, hit.getBlockPos());
      if (isClassic300()) return handleSeries300BlockUse(player, fighter, hit.getBlockPos());
      if (type == PartyGameType.HOE_HOE_HOE && stack.is(Items.DIAMOND_HOE)) {
         BlockPos pos = hit.getBlockPos();
         if (arena.inPlay(pos) && player.level().getBlockState(pos).is(Blocks.DIRT)) {
            player.level().setBlock(pos, ownColor(player.getUUID()), 2);
            award(player.getUUID(), 1);
         }
         return InteractionResult.FAIL;
      }
      return type == PartyGameType.SURVIVAL_GAMES || type == PartyGameType.CRAFTING_MASTER ? InteractionResult.PASS : InteractionResult.FAIL;
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter != null && type == PartyGameType.MINIONS && phase == Phase.RUNNING && stack.is(Items.ZOMBIE_SPAWN_EGG)) {
         summonMinion(player, fighter);
         return InteractionResult.FAIL;
      }
      if (fighter == null || type != PartyGameType.DIG_DOWN || phase != Phase.DIG_SELECTION) return InteractionResult.PASS;
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) return InteractionResult.PASS;
      String kind = data.copyTag().getString("sre_dig_tool");
      if (kind.isBlank()) return InteractionResult.PASS;
      changeDigTool(player, fighter, kind, player.isShiftKeyDown() ? -1 : 1);
      return InteractionResult.FAIL;
   }

   /** Horse selection is deliberately left as a normal entity interaction so Minecraft mounts it too. */
   public InteractionResult handleUseEntity(ServerPlayer player, Entity entity) {
      if (type != PartyGameType.HORSE_RACE || phase != Phase.HORSE_SELECTION || !(entity instanceof Horse horse)) return InteractionResult.PASS;
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter == null || !horses.contains(horse)) return InteractionResult.FAIL;
      if (horse.getOwnerUUID() != null && !player.getUUID().equals(horse.getOwnerUUID())) {
         ctx.send(player, "&c这匹马已经被其他玩家选择了。");
         return InteractionResult.FAIL;
      }
      selectHorse(player, fighter, horse);
      return InteractionResult.PASS;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state) {
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter == null || !fighter.alive || phase != Phase.RUNNING || !arena.inPlay(pos)) return false;
      if (isClassic200()) return tryBreakSeries200(player, fighter, pos, state);
      if (isClassic300()) return tryBreakSeries300(player, fighter, pos, state);
      if (type == PartyGameType.ORE_MINER && isOre(state.getBlock())) {
         award(player.getUUID(), oreScore(state.getBlock()));
         player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         oreRespawns.put(pos.immutable(), 12);
         return false;
      }
      if (type == PartyGameType.DIG_DOWN) {
         int owner = arena.digOwner(pos);
         if (owner != seats.indexOf(player.getUUID()) || !digToolCanBreak(player.getMainHandItem().getItem(), state.getBlock())) return false;
         return true;
      }
      if (type == PartyGameType.BRIDGE_CROSSING) return arena.inPlay(pos);
      return type == PartyGameType.SURVIVAL_GAMES;
   }

   private boolean tryBreakSeries200(ServerPlayer player, Fighter fighter, BlockPos pos, BlockState state) {
      if (type == PartyGameType.CAPTURE_THE_FLAG) {
         Integer flagTeam = series200Flags.get(pos);
         if (flagTeam == null || flagTeam == teamOf(player.getUUID()) || series200CarriedFlags.containsKey(player.getUUID())) return false;
         series200CarriedFlags.put(player.getUUID(), flagTeam);
         player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         player.getInventory().add(new ItemStack(flagTeam == 1 ? Items.LIGHT_BLUE_BANNER : Items.ORANGE_BANNER));
         ctx.broadcast(room, "&e" + ctx.name(player.getUUID()) + " &7夺走了" + (flagTeam == 1 ? "金队" : "青队") + "的旗帜！");
         return false;
      }
      if (type == PartyGameType.LABYRINTH) {
         Integer marker = series200Flags.get(pos);
         if (marker == null || marker < 11 || marker > 12 || state.getBlock() != Blocks.GOLD_BLOCK) return false;
         player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         fighter.classicValue++;
         fighter.score++;
         ctx.send(player, "&6找到一块金锭（" + fighter.classicValue + "/2）！");
         return false;
      }
      return type == PartyGameType.MINE_YOUR_BUSINESS && arena.inPlay(pos);
   }

   private ServerPlayer sourcePlayer(DamageSource source) {
      if (source.getEntity() instanceof ServerPlayer player) return player;
      if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
      return null;
   }

   private boolean handleSeries200Damage(ServerPlayer victim, DamageSource source) {
      ServerPlayer attacker = sourcePlayer(source);
      if (attacker == null || attacker.getUUID().equals(victim.getUUID())) return true;
      int attackingTeam = teamOf(attacker.getUUID());
      if (attackingTeam == teamOf(victim.getUUID())) return true;
      return switch (type) {
         case MINE_YOUR_BUSINESS -> false;
         case RPSC -> series200WinningTeam != attackingTeam;
         case TANKS -> { damageTank(teamOf(victim.getUUID())); yield true; }
         case CAPTURE_THE_FLAG -> { dropFlag(victim); push(attacker, victim, 0.75); yield true; }
         case BOMBS_AWAY -> { push(attacker, victim, 1.35); yield true; }
         case SNOW_WARS -> { hitTeamPlayer(victim, attacker, 3, "雪球"); yield true; }
         case RECRUITMENT_ROYALE -> true;
         default -> true;
      };
   }

   private boolean handleSeries200Attack(ServerPlayer attacker, Entity target) {
      if (target instanceof ServerPlayer victim) {
         if (teamOf(attacker.getUUID()) == teamOf(victim.getUUID())) return true;
         return switch (type) {
            case MINE_YOUR_BUSINESS -> false;
            case RPSC -> series200WinningTeam != teamOf(attacker.getUUID());
            case CAPTURE_THE_FLAG -> { dropFlag(victim); push(attacker, victim, 0.75); yield true; }
            case RECRUITMENT_ROYALE -> true;
            default -> true;
         };
      }
      if (target instanceof Turtle puck && type == PartyGameType.TEAM_HOCKEY && teamHockeyPucks.contains(puck)) {
         pushClassicBall(attacker, puck, 1.18);
         return true;
      }
      return true;
   }

   private void hitTeamPlayer(ServerPlayer victim, ServerPlayer attacker, int hitsToEliminate, String weapon) {
      Fighter target = fighters.get(victim.getUUID());
      if (target == null || !target.alive) return;
      target.classicValue++;
      attacker.displayClientMessage(TextUtil.color("&b" + weapon + "命中：&f" + ctx.name(victim.getUUID())), true);
      victim.displayClientMessage(TextUtil.color("&c受到" + weapon + "命中：&f" + target.classicValue + "/" + hitsToEliminate), true);
      if (target.classicValue >= hitsToEliminate) eliminate(victim, attacker.getUUID());
   }

   private void damageTank(int team) {
      int lives = series200TeamLives.merge(team, -1, Integer::sum);
      ctx.broadcast(room, "&c" + (team == 1 ? "金队" : "青队") + "坦克受损，剩余 " + Math.max(0, lives) + " 装甲。");
      if (lives <= 0) finishTeam(team == 1 ? 2 : 1);
   }

   private void dropFlag(ServerPlayer player) {
      Integer flagTeam = series200CarriedFlags.remove(player.getUUID());
      if (flagTeam == null) return;
      for (Map.Entry<BlockPos, Integer> entry : series200Flags.entrySet()) {
         if (entry.getValue() != flagTeam) continue;
         player.level().setBlock(entry.getKey(), flagTeam == 1 ? Blocks.LIGHT_BLUE_WOOL.defaultBlockState() : Blocks.ORANGE_WOOL.defaultBlockState(), 2);
         ctx.broadcast(room, "&7旗帜已回到" + (flagTeam == 1 ? "金队" : "青队") + "基地。");
         break;
      }
      Item carried = flagTeam == 1 ? Items.LIGHT_BLUE_BANNER : Items.ORANGE_BANNER;
      player.getInventory().removeItem(new ItemStack(carried));
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = fighters.get(uuid);
      if (fighter != null && fighter.alive) {
         fighter.alive = false;
         if (isClassic200()) tickTeamElimination();
         else if (isClassic300()) tickSeries300Elimination();
         else checkEliminationWin();
      }
      board.remove(uuid);
      ServerPlayer player = ctx.player(uuid);
      if (player != null) restore(player);
   }

   public void endNow() { finish(null); }

   private void begin() {
      phase = Phase.RUNNING;
      ticks = room.partyGameSettings().durationSeconds(type) * 20;
      openingGraceTicks = 40;
      if (isClassic100()) setupClassic100();
      if (isClassic200()) setupClassic200();
      if (isClassic300()) setupClassic300();
      if (type == PartyGameType.HOT_POTATO) givePotato(randomAlive());
      if (type == PartyGameType.COLORFUL_RUN) startColorRound();
      if (type == PartyGameType.CRAFTING_MASTER) {
         for (Fighter fighter : fighters.values()) {
            ServerPlayer player = ctx.player(fighter.uuid);
            if (player != null) assignCraftingRecipe(player, fighter);
         }
      }
      if (type == PartyGameType.HORSE_RACE) beginHorseRace();
      if (type == PartyGameType.MOB_SHOOTER) tickShooterTargets();
      if (type == PartyGameType.PUNCH_THE_BAT) tickBatTargets();
      if (type == PartyGameType.TNT_RUN) showTntStart(); else showPartyStart();
      ctx.broadcast(room, "&a开始！");
   }

   /** The shared five-second ready period: every normal party game starts from a fixed position. */
   private void holdIntroPlayers() {
      ServerLevel level = level();
      if (level == null) return;
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         Vec3 spawn = spawnFor(seats.indexOf(fighter.uuid));
         if (player.position().distanceToSqr(spawn) > 0.05) {
            player.teleportTo(level, spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
         }
         player.setDeltaMovement(Vec3.ZERO);
      }
   }

   /** Locks the chosen horses into their own lanes only when the race actually begins. */
   private void beginHorseRace() {
      ServerLevel level = level();
      if (level == null) return;
      arena.lockHorseLanes(level);
      for (Fighter fighter : fighters.values()) {
         if (fighter.horse == null) continue;
         Vec3 start = arena.spawn(fighter.horseIndex, seats.size());
         fighter.horse.teleportTo(start.x, start.y, start.z);
         fighter.horse.setDeltaMovement(Vec3.ZERO);
         fighter.horse.setNoAi(false);
         fighter.horseLastX = start.x;
         fighter.horseFinishCooldown = HORSE_FINISH_COOLDOWN_TICKS;
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null && player.getVehicle() != fighter.horse) player.startRiding(fighter.horse, true);
      }
   }

   private void beginHorseSelection() {
      phase = Phase.HORSE_SELECTION;
      ticks = HORSE_SELECTION_TICKS;
      ServerLevel level = level();
      if (level == null) return;
      for (int index = 0; index < seats.size(); index++) {
         Horse horse = new Horse(net.minecraft.world.entity.EntityType.HORSE, level);
         Vec3 stable = horseStable(index);
         horse.setTamed(true);
         horse.equipSaddle(new ItemStack(Items.SADDLE), null);
         // The old range made the race primarily a lottery.  Keep choice meaningful while
         // ensuring a full 24-player field is decided by obstacle handling, not one outlier.
         horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ThreadLocalRandom.current().nextDouble(0.225, 0.255));
         horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(ThreadLocalRandom.current().nextDouble(0.66, 0.76));
         horse.setNoAi(true);
         horse.moveTo(stable.x, stable.y, stable.z, 0.0F, 0.0F);
         level.addFreshEntity(horse);
         horses.add(horse);
      }
      showHorseSelection();
      ctx.broadcast(room, "&e跑马赛：20 秒内右键选择并骑上一匹马；准备阶段可自由试跑，开赛后赛道会封闭。" );
   }

   private Vec3 horseStable(int index) {
      Vec3 start = arena.spawn(index, seats.size());
      return new Vec3(start.x + 2.0, start.y, start.z);
   }

   private void tickHorseSelection() {
      for (int index = 0; index < horses.size(); index++) {
         Horse horse = horses.get(index);
         if (horse.isRemoved()) continue;
         Vec3 stable = horseStable(index);
         horse.teleportTo(stable.x, stable.y, stable.z);
         horse.setDeltaMovement(Vec3.ZERO);
      }
      if (ticks > 0 && ticks % 20 == 0) showHorseSelection();
      if (ticks <= 0) finishHorseSelection();
   }

   private void showHorseSelection() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&6选择马匹 &f" + Math.max(0, (ticks + 19) / 20) + " 秒")));
         player.displayClientMessage(TextUtil.color("&7右键骑上想要的马；选定后可在开赛前自由试跑。"), true);
      }
   }

   private void selectHorse(ServerPlayer player, Fighter fighter, Horse horse) {
      if (fighter.horse != null && fighter.horse != horse) fighter.horse.setOwnerUUID(null);
      fighter.horse = horse;
      fighter.horseIndex = horses.indexOf(horse);
      horse.setOwnerUUID(player.getUUID());
      player.startRiding(horse, true);
      ctx.send(player, "&a已选择马匹。&7 时间结束后将进入 5 秒准备。" );
   }

   private void finishHorseSelection() {
      List<Horse> free = new ArrayList<>();
      for (Horse horse : horses) if (horse.getOwnerUUID() == null) free.add(horse);
      for (Fighter fighter : fighters.values()) {
         if (fighter.horse != null) continue;
         Horse horse = free.isEmpty() ? horses.get(Math.floorMod(fighter.uuid.hashCode(), horses.size())) : free.remove(0);
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null) selectHorse(player, fighter, horse);
      }
      phase = Phase.INTRO;
      ticks = INTRO_TICKS;
      showPrepareCountdown(INTRO_TICKS / 20);
   }

   private void holdHorsePlayers() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null || fighter.horse == null) continue;
         Vec3 stable = horseStable(fighter.horseIndex);
         fighter.horse.teleportTo(stable.x, stable.y, stable.z);
         fighter.horse.setDeltaMovement(Vec3.ZERO);
         if (player.getVehicle() != fighter.horse) player.startRiding(fighter.horse, true);
         player.setDeltaMovement(Vec3.ZERO);
      }
   }

   private void beginDigSelection() {
      phase = Phase.DIG_SELECTION;
      ticks = DIG_SELECTION_TICKS;
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         fighter.digPoints = 24;
         refreshDigTools(player, fighter);
      }
      showDigSelection();
      ctx.broadcast(room, "&e挖挖挖：30 秒内右键三种工具升级；潜行右键可降级返还点数。" );
   }

   private void tickDigSelection() {
      if (ticks > 0 && ticks % 20 == 0) showDigSelection();
      if (ticks > 0) return;
      for (Fighter fighter : fighters.values()) {
         autoFinishDigTools(fighter);
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null) refreshDigTools(player, fighter);
      }
      phase = Phase.INTRO;
      ticks = INTRO_TICKS;
      showPrepareCountdown(INTRO_TICKS / 20);
   }

   private void showDigSelection() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&b工具选择 &f" + Math.max(0, (ticks + 19) / 20) + " 秒")));
         player.displayClientMessage(TextUtil.color("&e剩余 " + fighter.digPoints + " 点 &7| 右键工具升级，潜行右键降级"), true);
      }
   }

   private void changeDigTool(ServerPlayer player, Fighter fighter, String kind, int direction) {
      int tier = digTier(fighter, kind);
      int next = Math.max(0, Math.min(DIG_TOOL_COSTS.length - 1, tier + direction));
      if (next == tier) return;
      int delta = DIG_TOOL_COSTS[next] - DIG_TOOL_COSTS[tier];
      if (delta > fighter.digPoints) {
         player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
         player.displayClientMessage(TextUtil.color("&c点数不足。&7 当前剩余 " + fighter.digPoints + " 点"), true);
         return;
      }
      setDigTier(fighter, kind, next);
      fighter.digPoints -= delta;
      refreshDigTools(player, fighter);
      player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.25F);
      player.displayClientMessage(TextUtil.color("&a" + digKindName(kind) + " " + digTierName(next) + " &7| 剩余 &e" + fighter.digPoints + " 点"), true);
   }

   private void autoFinishDigTools(Fighter fighter) {
      String[] kinds = {"pick", "axe", "shovel"};
      while (fighter.digPoints >= 3) {
         List<String> options = new ArrayList<>();
         for (String kind : kinds) if (digTier(fighter, kind) < DIG_TOOL_COSTS.length - 1
            && DIG_TOOL_COSTS[digTier(fighter, kind) + 1] - DIG_TOOL_COSTS[digTier(fighter, kind)] <= fighter.digPoints) options.add(kind);
         if (options.isEmpty()) return;
         String kind = options.get(ThreadLocalRandom.current().nextInt(options.size()));
         int tier = digTier(fighter, kind);
         fighter.digPoints -= DIG_TOOL_COSTS[tier + 1] - DIG_TOOL_COSTS[tier];
         setDigTier(fighter, kind, tier + 1);
      }
   }

   private int digTier(Fighter fighter, String kind) {
      return switch (kind) { case "pick" -> fighter.pickTier; case "axe" -> fighter.axeTier; default -> fighter.shovelTier; };
   }

   private void setDigTier(Fighter fighter, String kind, int tier) {
      switch (kind) { case "pick" -> fighter.pickTier = tier; case "axe" -> fighter.axeTier = tier; default -> fighter.shovelTier = tier; }
   }

   private void refreshDigTools(ServerPlayer player, Fighter fighter) {
      Inventory inventory = player.getInventory();
      inventory.clearContent();
      inventory.setItem(0, digTool("pick", fighter.pickTier));
      inventory.setItem(1, digTool("axe", fighter.axeTier));
      inventory.setItem(2, digTool("shovel", fighter.shovelTier));
      ItemStack guide = new ItemStack(Items.PAPER);
      guide.set(DataComponents.CUSTOM_NAME, TextUtil.color("&e剩余点数：" + fighter.digPoints + " &7(右键工具升级 / 潜行右键降级)"));
      inventory.setItem(8, guide);
   }

   private ItemStack digTool(String kind, int tier) {
      Item item = switch (kind) {
         case "pick" -> switch (tier) { case 0 -> Items.WOODEN_PICKAXE; case 1 -> Items.STONE_PICKAXE; case 2 -> Items.IRON_PICKAXE; case 3 -> Items.DIAMOND_PICKAXE; default -> Items.NETHERITE_PICKAXE; };
         case "axe" -> switch (tier) { case 0 -> Items.WOODEN_AXE; case 1 -> Items.STONE_AXE; case 2 -> Items.IRON_AXE; case 3 -> Items.DIAMOND_AXE; default -> Items.NETHERITE_AXE; };
         default -> switch (tier) { case 0 -> Items.WOODEN_SHOVEL; case 1 -> Items.STONE_SHOVEL; case 2 -> Items.IRON_SHOVEL; case 3 -> Items.DIAMOND_SHOVEL; default -> Items.NETHERITE_SHOVEL; };
      };
      ItemStack tool = new ItemStack(item);
      tool.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString("sre_dig_tool", kind)));
      tool.set(DataComponents.CUSTOM_NAME, TextUtil.color("&b" + digKindName(kind) + " &f" + digTierName(tier) + " &8(" + DIG_TOOL_COSTS[tier] + " 点)"));
      return tool;
   }

   private String digKindName(String kind) { return switch (kind) { case "pick" -> "镐子"; case "axe" -> "斧头"; default -> "铲子"; }; }
   private String digTierName(int tier) { return switch (tier) { case 0 -> "木制"; case 1 -> "石制"; case 2 -> "铁制"; case 3 -> "钻石"; default -> "下界合金"; }; }

   private boolean digToolCanBreak(Item item, Block block) {
      boolean pick = item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
      boolean axe = item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
      boolean shovel = item == Items.WOODEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.IRON_SHOVEL || item == Items.DIAMOND_SHOVEL || item == Items.NETHERITE_SHOVEL;
      if (block == Blocks.DIRT || block == Blocks.SAND) return shovel;
      if (block == Blocks.OAK_LOG) return axe;
      return pick;
   }

   private void holdTntPlayers() {
      ServerLevel level = level();
      if (level == null) return;
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         Vec3 initial = arena.spawn(seats.indexOf(fighter.uuid), seats.size());
         Vec3 spawn = new Vec3(initial.x, arena.floorY() + 1.0, initial.z);
         if (player.position().distanceToSqr(spawn) > 0.05) {
            player.teleportTo(level, spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
         }
         player.setDeltaMovement(Vec3.ZERO);
      }
   }

   private void showTntCountdown(int seconds) {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 14, 4));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&c&l" + seconds)));
         player.displayClientMessage(TextUtil.color("&eTNT 跑酷将在 &f" + seconds + " &e秒后开始"), true);
      }
   }

   private void showTntStart() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 6));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&a&l开始！")));
         player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.25F);
      }
   }

   private void showPrepareCountdown(int seconds) {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 14, 4));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&e&l准备：&f" + seconds)));
         player.displayClientMessage(TextUtil.color("&7" + type.displayName() + " &e将在 &f" + seconds + " &e秒后开始"), true);
      }
   }

   private void showPartyStart() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) continue;
         player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 6));
         player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&a&l开始！")));
         player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.25F);
      }
   }

   /**
    * The original Minecraft Party 2 100-series consists of short two-player duels.
    * Keeping their state here (rather than in global scoreboards) makes concurrent
    * rooms safe: every arena owns its buttons, mobs, score, and win conditions.
    */
   private boolean isClassic100() {
      return switch (type) {
         case MINIONS, RING_IN_THE_RING, GLADIATOR_FIGHT, TURTLE_HOCKEY, GO_FISH,
            DONT_PUSH_MY_BUTTONS, BRIDGE_CROSSING, PIG_PUSHERS, BALANCE_BEAM,
            BUTTON_SEARCH, BETRIS, DEUCE, DECRYPTION, CANNONEERS -> true;
         default -> false;
      };
   }

   private boolean isClassic200() {
      return switch (type) {
         case PRISON_PALS, RPSC, TANKS, CAPTURE_THE_FLAG, MINE_YOUR_BUSINESS, TEAM_HOCKEY,
            MAZE_NAVIGATOR, BOMBS_AWAY, LABYRINTH, SNOW_WARS, SPACE_JUMPERS, BOOM_CARTS,
            WHAT_THE_CLUCK, RECRUITMENT_ROYALE -> true;
         default -> false;
      };
   }

   private boolean isClassic300() {
      return switch (type) {
         case HIDE_AND_SEEK, GAME_THEORY, BOSS_BRAWL, GOLD_RUSH, BLOCK_BUSTER, PAC_CUBE,
            GHOST_HUNT, TREETOP_HOP, SLIME_TIME, IN_THE_ZONE, GHAST_BLAST, EGGCELLENCE,
            RAVAGER_RODEO, MOUSE_TRAP -> true;
         default -> false;
      };
   }

   private boolean isPartyCatalogue() { return isClassic100() || isClassic200() || isClassic300(); }

   private Vec3 spawnFor(int index) {
      if (isClassic300()) {
         if (type == PartyGameType.TREETOP_HOP) return new Vec3(arena.minX() + 7.5, arena.floorY() + 5.0, arena.minZ() + 8 + index * 4.0);
         if (type == PartyGameType.RAVAGER_RODEO) return new Vec3(arena.centerX() + 0.5, arena.floorY() + 2.0, arena.centerZ() + 0.5);
         return arena.spawn(index, seats.size());
      }
      if (isClassic200()) {
         UUID uuid = seats.get(index);
         int team = teamOf(uuid);
         int member = teamMemberIndex(uuid);
         if (type == PartyGameType.PRISON_PALS || type == PartyGameType.SPACE_JUMPERS) {
            return new Vec3(arena.minX() + 7.5, arena.floorY() + (type == PartyGameType.SPACE_JUMPERS ? 3.0 : 1.0),
               arena.centerZ() + (team == 1 ? -5.0 : 5.0) + member * 2.0 + 0.5);
         }
         if (type == PartyGameType.RPSC || type == PartyGameType.BOMBS_AWAY) {
            double x = arena.centerX() + (team == 1 ? -1 : 1) * (type == PartyGameType.RPSC ? 12.0 : 15.0);
            return new Vec3(x + 0.5, arena.floorY() + 3.0, arena.centerZ() + (member - 1) * 3.0 + 0.5);
         }
         if (type == PartyGameType.CAPTURE_THE_FLAG) {
            return new Vec3(arena.centerX() + (team == 1 ? -11.0 : 11.0), arena.floorY() + 1.0,
               arena.centerZ() + (member - 1) * 3.0 + 0.5);
         }
         double x = arena.centerX() + (team == 1 ? -1 : 1) * Math.max(11.0, arena.size() * 0.3);
         return new Vec3(x, arena.floorY() + 1.0, arena.centerZ() + (member - 1) * 3.0 + 0.5);
      }
      if (!isClassic100()) return arena.spawn(index, seats.size());
      int side = index == 0 ? -1 : 1;
      if (type == PartyGameType.BALANCE_BEAM) {
         return new Vec3(arena.minX() + 7.5, arena.floorY() + 2.0, arena.centerZ() + side * 4.0 + 0.5);
      }
      if (type == PartyGameType.BRIDGE_CROSSING) {
         return new Vec3(arena.centerX() + side * (arena.size() / 2.0 - 6.0), arena.floorY() + 2.0, arena.centerZ() + 0.5);
      }
      return new Vec3(arena.centerX() + side * Math.max(9.0, arena.size() * 0.28) + 0.5,
         arena.floorY() + 1.0, arena.centerZ() + 0.5);
   }

   private void setupClassic100() {
      ServerLevel level = level();
      if (level == null) return;
      classicButtons.clear();
      classicPigs.clear();
      minionOwners.clear();
      java.util.Arrays.fill(classicCells, 0);
      classicWinThreshold = 9;
      classicWindTicks = 0;
      classicWindDirection = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
      switch (type) {
         case MINIONS -> setupMinions(level);
         case RING_IN_THE_RING -> setupRings(level);
         case TURTLE_HOCKEY -> setupTurtleHockey(level);
         case GO_FISH -> setupFishing(level);
         case DONT_PUSH_MY_BUTTONS -> setupButtonWall(level);
         case BRIDGE_CROSSING -> setupBridge(level);
         case PIG_PUSHERS -> setupPigPushers(level);
         case BALANCE_BEAM -> setupBalanceBeams(level);
         case BUTTON_SEARCH -> setupButtonSearch(level);
         case BETRIS -> setupBetrisConsole(level);
         case DEUCE -> setupDeuce(level);
         case DECRYPTION -> setupDecryptionPanels(level);
         case CANNONEERS -> setupCannoneers(level);
         default -> { }
      }
   }

   /** Team-oriented Minecraft Party 2 games. Team membership is read from the room
    * settings, with a deterministic fallback for rooms restored from older data. */
   private int teamOf(UUID uuid) {
      int assigned = room.duelSettings().teamOf(uuid);
      if (assigned == 1 || assigned == 2) return assigned;
      return Math.floorMod(seats.indexOf(uuid), 2) + 1;
   }

   private int teamMemberIndex(UUID uuid) {
      int index = 0;
      for (UUID member : seats) {
         if (teamOf(member) != teamOf(uuid)) continue;
         if (member.equals(uuid)) return index;
         index++;
      }
      return 0;
   }

   private List<UUID> teamMembers(int team) {
      return seats.stream().filter(uuid -> teamOf(uuid) == team).toList();
   }

   private void setupClassic200() {
      series200Buttons.clear();
      series200Flags.clear();
      series200CarriedFlags.clear();
      series200Choices.clear();
      series200ChickenTeams.clear();
      series200RecruitTeams.clear();
      series300Buttons.clear();
      series300Roles.clear();
      series300Lives.clear();
      series300MobTeams.clear();
      teamHockeyPucks.clear();
      series200TeamScore.clear();
      series200TeamLives.clear();
      for (int team = 1; team <= 2; team++) {
         series200TeamScore.put(team, 0);
         series200TeamLives.put(team, 5);
      }
      series200RoundTicks = 0;
      series200WinningTeam = 0;
      series200CartTarget = 0;
      ServerLevel level = level();
      if (level == null) return;
      switch (type) {
         case PRISON_PALS -> buildPrisonCourse(level);
         case RPSC -> buildRpscArena(level);
         case TANKS -> buildTankField(level);
         case CAPTURE_THE_FLAG -> buildFlagFort(level);
         case MINE_YOUR_BUSINESS -> buildMineBattlefield(level);
         case TEAM_HOCKEY -> buildTeamHockeyRink(level);
         case MAZE_NAVIGATOR -> buildNavigatorMaze(level);
         case BOMBS_AWAY -> buildBombIslands(level);
         case LABYRINTH -> buildLabyrinth(level);
         case SNOW_WARS -> buildSnowFort(level);
         case SPACE_JUMPERS -> buildSpaceCourse(level);
         case BOOM_CARTS -> buildCartWorks(level);
         case WHAT_THE_CLUCK -> buildChickenArena(level);
         case RECRUITMENT_ROYALE -> buildRecruitCamp(level);
         default -> { }
      }
   }

   /** 300-series games use asymmetric roles more often than the team games above.
    * Roles are local to the arena: 1 is the featured player/team, 2 the opposing side. */
   private void setupClassic300() {
      series300Buttons.clear();
      series300Lives.clear();
      series300MobTeams.clear();
      series300Round = 0;
      series300Capture = 0;
      primeClassic300Roles();
      for (UUID uuid : seats) {
         series300Lives.put(uuid, type == PartyGameType.PAC_CUBE && series300Roles.getOrDefault(uuid, 0) == 1 ? 2 : 1);
      }
      ServerLevel level = level();
      if (level == null) return;
      switch (type) {
         case HIDE_AND_SEEK -> buildHideAndSeek(level);
         case GAME_THEORY -> buildGameTheory(level);
         case BOSS_BRAWL -> buildBossBrawl(level);
         case GOLD_RUSH -> buildGoldRush(level);
         case BLOCK_BUSTER -> buildBlockBuster(level);
         case PAC_CUBE -> buildPacCube(level);
         case GHOST_HUNT -> buildGhostHunt(level);
         case TREETOP_HOP -> buildTreetopHop(level);
         case SLIME_TIME -> buildSlimeTime(level);
         case IN_THE_ZONE -> buildInTheZone(level);
         case GHAST_BLAST -> buildGhastBlast(level);
         case EGGCELLENCE -> buildEggcellence(level);
         case RAVAGER_RODEO -> buildRavagerRodeo(level);
         case MOUSE_TRAP -> buildMouseTrap(level);
         default -> { }
      }
   }

   private void primeClassic300Roles() {
      series300Roles.clear();
      for (int index = 0; index < seats.size(); index++) {
         UUID uuid = seats.get(index);
         int role = switch (type) {
            case HIDE_AND_SEEK, BOSS_BRAWL, PAC_CUBE, GHOST_HUNT, SLIME_TIME, GHAST_BLAST, MOUSE_TRAP -> index == 0 ? 1 : 2;
            default -> 1;
         };
         series300Roles.put(uuid, role);
      }
   }

   private void buildHideAndSeek(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 22, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 22, Blocks.DARK_OAK_PLANKS.defaultBlockState());
      for (int x = arena.centerX() - 18; x <= arena.centerX() + 18; x += 9)
         for (int z = arena.centerZ() - 18; z <= arena.centerZ() + 18; z += 9)
            fillScene(level, x, arena.floorY() + 1, z, x + 4, arena.floorY() + 4, z + 4, Blocks.BARREL.defaultBlockState());
   }

   private void buildGameTheory(ServerLevel level) {
      fillScene(level, arena.centerX() - 17, arena.floorY(), arena.centerZ() - 17, arena.centerX() + 17, arena.floorY(), arena.centerZ() + 17, Blocks.QUARTZ_BLOCK.defaultBlockState());
      for (int index = 0; index < seats.size(); index++) {
         int x = arena.centerX() - 15 + (index % 4) * 10, z = arena.centerZ() - 10 + (index / 4) * 12;
         for (int choice = 1; choice <= 2; choice++) {
            BlockPos pos = new BlockPos(x + (choice == 1 ? -2 : 2), arena.floorY() + 2, z);
            scene(level, pos.getX(), pos.getY() - 1, pos.getZ(), choice == 1 ? Blocks.LIME_CONCRETE.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState());
            scene(level, pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE_BUTTON.defaultBlockState());
            series300Buttons.put(pos.immutable(), index * 10 + choice);
         }
      }
   }

   private void buildBossBrawl(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 18, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 18, Blocks.NETHER_BRICKS.defaultBlockState());
      fillScene(level, arena.centerX() - 3, arena.floorY() + 1, arena.centerZ() - 3, arena.centerX() + 3, arena.floorY() + 5, arena.centerZ() + 3, Blocks.CRYING_OBSIDIAN.defaultBlockState());
   }

   private void buildGoldRush(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 18, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 18, Blocks.BLACKSTONE.defaultBlockState());
      for (int x = arena.centerX() - 18; x <= arena.centerX() + 18; x += 6) for (int z = arena.centerZ() - 12; z <= arena.centerZ() + 12; z += 6)
         scene(level, x, arena.floorY() + 1, z, Math.floorMod(x * 17 + z, 3) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState());
   }

   private void buildBlockBuster(ServerLevel level) {
      fillScene(level, arena.centerX() - 20, arena.floorY(), arena.centerZ() - 20, arena.centerX() + 20, arena.floorY(), arena.centerZ() + 20, Blocks.OBSIDIAN.defaultBlockState());
      for (int x = arena.centerX() - 16; x <= arena.centerX() + 16; x += 4) for (int z = arena.centerZ() - 16; z <= arena.centerZ() + 16; z += 4)
         fillScene(level, x, arena.floorY() + 1, z, x + 2, arena.floorY() + 3, z + 2, (x + z) % 8 == 0 ? Blocks.TNT.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState());
   }

   private void buildPacCube(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 22, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 22, Blocks.DEEPSLATE_TILES.defaultBlockState());
      for (int x = arena.centerX() - 20; x <= arena.centerX() + 20; x += 8) fillScene(level, x, arena.floorY() + 1, arena.centerZ() - 20, x, arena.floorY() + 3, arena.centerZ() + 20, Blocks.BLUE_CONCRETE.defaultBlockState());
      for (int z = arena.centerZ() - 16; z <= arena.centerZ() + 16; z += 8) fillScene(level, arena.centerX() - 20, arena.floorY() + 1, z, arena.centerX() + 20, arena.floorY() + 3, z, Blocks.BLUE_CONCRETE.defaultBlockState());
      for (int x = arena.centerX() - 16; x <= arena.centerX() + 16; x += 8) for (int z = arena.centerZ() - 16; z <= arena.centerZ() + 16; z += 8) {
         BlockPos orb = new BlockPos(x + 2, arena.floorY() + 1, z + 2);
         scene(level, orb.getX(), orb.getY(), orb.getZ(), Blocks.LIME_CONCRETE.defaultBlockState());
         series300Buttons.put(orb.immutable(), 900 + series300Buttons.size());
      }
   }

   private void buildGhostHunt(ServerLevel level) {
      fillScene(level, arena.centerX() - 21, arena.floorY(), arena.centerZ() - 21, arena.centerX() + 21, arena.floorY(), arena.centerZ() + 21, Blocks.PODZOL.defaultBlockState());
      for (int index = 0; index < 6; index++) {
         Villager villager = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
         villager.moveTo(arena.centerX() - 12 + (index % 3) * 12, arena.floorY() + 1, arena.centerZ() - 8 + (index / 3) * 16, 0, 0);
         villager.setNoAi(true); villager.setCustomName(TextUtil.color("&f村民")); level.addFreshEntity(villager); classicMobs.add(villager);
      }
   }

   private void buildTreetopHop(ServerLevel level) {
      fillScene(level, arena.minX() + 3, arena.floorY(), arena.minZ() + 3, arena.maxX() - 3, arena.floorY(), arena.maxZ() - 3, Blocks.AIR.defaultBlockState());
      for (int index = 0; index < 10; index++) {
         int x = arena.minX() + 6 + index * 8;
         fillScene(level, x, arena.floorY() + 3 + index % 2, arena.minZ() + 5, x + 3, arena.floorY() + 3 + index % 2, arena.maxZ() - 5, Blocks.OAK_LEAVES.defaultBlockState());
         fillScene(level, x + 1, arena.floorY(), arena.centerZ(), x + 2, arena.floorY() + 3, arena.centerZ() + 1, Blocks.OAK_LOG.defaultBlockState());
      }
   }

   private void buildSlimeTime(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 22, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 22, Blocks.SLIME_BLOCK.defaultBlockState());
      for (UUID uuid : seats) if (series300Roles.getOrDefault(uuid, 0) == 2) {
         Slime slime = new Slime(net.minecraft.world.entity.EntityType.SLIME, level);
         slime.moveTo(arena.centerX() + ThreadLocalRandom.current().nextInt(-12, 13), arena.floorY() + 1, arena.centerZ() + ThreadLocalRandom.current().nextInt(-12, 13), 0, 0);
         slime.setNoAi(true); slime.setSize(5, true); level.addFreshEntity(slime); classicMobs.add(slime); series300MobTeams.put(slime.getUUID(), 2);
      }
   }

   private void buildInTheZone(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 22, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 22, Blocks.END_STONE.defaultBlockState());
      fillScene(level, arena.centerX() - 4, arena.floorY() + 1, arena.centerZ() - 4, arena.centerX() + 4, arena.floorY() + 1, arena.centerZ() + 4, Blocks.PURPUR_SLAB.defaultBlockState());
   }

   private void buildGhastBlast(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 18, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 18, Blocks.NETHERRACK.defaultBlockState());
      for (UUID uuid : seats) if (series300Roles.getOrDefault(uuid, 0) == 2) {
         Ghast ghast = new Ghast(net.minecraft.world.entity.EntityType.GHAST, level);
         ghast.moveTo(arena.centerX() + ThreadLocalRandom.current().nextInt(-12, 13), arena.floorY() + 8, arena.centerZ() + ThreadLocalRandom.current().nextInt(-9, 10), 0, 0);
         ghast.setNoAi(true); level.addFreshEntity(ghast); classicMobs.add(ghast); series300MobTeams.put(ghast.getUUID(), 2);
      }
   }

   private void buildEggcellence(ServerLevel level) {
      fillScene(level, arena.centerX() - 18, arena.floorY(), arena.centerZ() - 14, arena.centerX() + 18, arena.floorY(), arena.centerZ() + 14, Blocks.WHITE_CONCRETE.defaultBlockState());
      for (int row = 0; row < 3; row++) for (int col = 0; col < 4; col++) for (int side : new int[] {-1, 1}) {
         BlockPos egg = new BlockPos(arena.centerX() + side * 10, arena.floorY() + 2 + row * 2, arena.centerZ() - 6 + col * 4);
         scene(level, egg.getX(), egg.getY() - 1, egg.getZ(), Blocks.HAY_BLOCK.defaultBlockState());
         scene(level, egg.getX(), egg.getY(), egg.getZ(), (row + col + side) % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.BROWN_WOOL.defaultBlockState());
         series300Buttons.put(egg.immutable(), 700 + row * 10 + col);
      }
   }

   private void buildRavagerRodeo(ServerLevel level) {
      fillScene(level, arena.centerX() - 20, arena.floorY(), arena.centerZ() - 20, arena.centerX() + 20, arena.floorY(), arena.centerZ() + 20, Blocks.RED_SAND.defaultBlockState());
      Ravager ravager = new Ravager(net.minecraft.world.entity.EntityType.RAVAGER, level);
      ravager.moveTo(arena.centerX() + 0.5, arena.floorY() + 1, arena.centerZ() + 0.5, 0, 0); ravager.setNoAi(true); level.addFreshEntity(ravager); classicMobs.add(ravager);
   }

   private void buildMouseTrap(ServerLevel level) {
      fillScene(level, arena.centerX() - 22, arena.floorY(), arena.centerZ() - 22, arena.centerX() + 22, arena.floorY(), arena.centerZ() + 22, Blocks.BIRCH_PLANKS.defaultBlockState());
      for (int x = arena.centerX() - 16; x <= arena.centerX() + 16; x += 8) fillScene(level, x, arena.floorY() + 1, arena.centerZ() - 18, x, arena.floorY() + 3, arena.centerZ() + 18, Blocks.COBWEB.defaultBlockState());
   }

   private void buildPrisonCourse(ServerLevel level) {
      int y = arena.floorY();
      fillScene(level, arena.centerX() - 20, y, arena.centerZ() - 8, arena.centerX() + 20, y, arena.centerZ() + 8, Blocks.STONE_BRICKS.defaultBlockState());
      for (int stage = 1; stage <= 5; stage++) {
         int x = arena.centerX() - 20 + stage * 7;
         fillScene(level, x, y + 1, arena.centerZ() - 7, x, y + 4, arena.centerZ() + 7, Blocks.IRON_BARS.defaultBlockState());
         fillScene(level, x + 1, y, arena.centerZ() - 2, x + 2, y, arena.centerZ() + 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
      }
   }

   private void buildRpscArena(ServerLevel level) {
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         int x = arena.centerX() + side * 12;
         fillScene(level, x - 2, arena.floorY(), arena.centerZ() - 6, x + 2, arena.floorY() + 1, arena.centerZ() + 6,
            team == 1 ? Blocks.ORANGE_CONCRETE.defaultBlockState() : Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
         for (int choice = 1; choice <= 3; choice++) {
            BlockPos button = new BlockPos(x, arena.floorY() + 2, arena.centerZ() + (choice - 2) * 3);
            scene(level, button.getX(), button.getY() - 1, button.getZ(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            scene(level, button.getX(), button.getY(), button.getZ(), Blocks.STONE_BUTTON.defaultBlockState());
            series200Buttons.put(button.immutable(), team * 10 + choice);
         }
      }
   }

   private void buildTankField(ServerLevel level) {
      fillScene(level, arena.centerX() - 20, arena.floorY(), arena.centerZ() - 15, arena.centerX() + 20, arena.floorY(), arena.centerZ() + 15, Blocks.TERRACOTTA.defaultBlockState());
      for (int x = arena.centerX() - 16; x <= arena.centerX() + 16; x += 8) {
         fillScene(level, x, arena.floorY() + 1, arena.centerZ() - 10, x + 2, arena.floorY() + 3, arena.centerZ() - 8, Blocks.CUT_SANDSTONE.defaultBlockState());
         fillScene(level, x - 2, arena.floorY() + 1, arena.centerZ() + 8, x, arena.floorY() + 3, arena.centerZ() + 10, Blocks.CUT_SANDSTONE.defaultBlockState());
      }
   }

   private void buildFlagFort(ServerLevel level) {
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         int x = arena.centerX() + side * 17;
         BlockState colour = team == 1 ? Blocks.LIGHT_BLUE_WOOL.defaultBlockState() : Blocks.ORANGE_WOOL.defaultBlockState();
         fillScene(level, x - 3, arena.floorY(), arena.centerZ() - 6, x + 3, arena.floorY() + 3, arena.centerZ() + 6, colour);
         BlockPos flag = new BlockPos(x, arena.floorY() + 4, arena.centerZ());
         scene(level, flag.getX(), flag.getY() - 1, flag.getZ(), Blocks.OAK_FENCE.defaultBlockState());
         scene(level, flag.getX(), flag.getY(), flag.getZ(), team == 1 ? Blocks.LIGHT_BLUE_WOOL.defaultBlockState() : Blocks.ORANGE_WOOL.defaultBlockState());
         series200Flags.put(flag.immutable(), team);
      }
      fillScene(level, arena.centerX() - 3, arena.floorY(), arena.centerZ() - 10, arena.centerX() + 3, arena.floorY(), arena.centerZ() + 10, Blocks.COBBLESTONE.defaultBlockState());
   }

   private void buildMineBattlefield(ServerLevel level) {
      fillScene(level, arena.centerX() - 18, arena.floorY(), arena.centerZ() - 14, arena.centerX() + 18, arena.floorY() + 5, arena.centerZ() + 14, Blocks.STONE.defaultBlockState());
      // Both teams start in ventilated shafts; the surrounding solid mass is intentionally mineable.
      for (int team = 1; team <= 2; team++) {
         int x = arena.centerX() + (team == 1 ? -1 : 1) * Math.max(11, arena.size() * 3 / 10);
         fillScene(level, x - 2, arena.floorY() + 1, arena.centerZ() - 5, x + 2, arena.floorY() + 4, arena.centerZ() + 5, Blocks.AIR.defaultBlockState());
      }
      for (int x = arena.centerX() - 16; x <= arena.centerX() + 16; x += 4) {
         for (int z = arena.centerZ() - 12; z <= arena.centerZ() + 12; z += 4) {
            fillScene(level, x, arena.floorY() + 1, z, x + 1, arena.floorY() + 3, z + 1,
               Math.floorMod(x * 31 + z * 17, 5) == 0 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
         }
      }
      for (int team = 1; team <= 2; team++) {
         int x = arena.centerX() + (team == 1 ? -1 : 1) * Math.max(11, arena.size() * 3 / 10);
         fillScene(level, x - 2, arena.floorY() + 1, arena.centerZ() - 5, x + 2, arena.floorY() + 4, arena.centerZ() + 5, Blocks.AIR.defaultBlockState());
      }
   }

   private void buildTeamHockeyRink(ServerLevel level) {
      buildHockeyRink(level);
      for (int i = 0; i < 2; i++) {
         Turtle puck = new Turtle(net.minecraft.world.entity.EntityType.TURTLE, level);
         puck.moveTo(arena.centerX() + (i == 0 ? -2 : 2), arena.floorY() + 1.0, arena.centerZ() + 0.5, 0.0F, 0.0F);
         puck.setNoAi(true);
         puck.setCustomName(TextUtil.color("&f团队冰球"));
         level.addFreshEntity(puck);
         classicMobs.add(puck);
         teamHockeyPucks.add(puck);
      }
   }

   private void buildNavigatorMaze(ServerLevel level) {
      buildSearchGarden(level);
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         for (int orb = 0; orb < Math.max(1, teamMembers(team).size()); orb++) {
            BlockPos pos = new BlockPos(arena.centerX() + side * (8 + orb % 2 * 4), arena.floorY() + 2, arena.centerZ() + (orb - 1) * 7);
            scene(level, pos.getX(), pos.getY() - 1, pos.getZ(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            scene(level, pos.getX(), pos.getY(), pos.getZ(), team == 1 ? Blocks.ORANGE_GLAZED_TERRACOTTA.defaultBlockState() : Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState());
            series200Buttons.put(pos.immutable(), team * 10 + 4 + orb);
         }
      }
   }

   private void buildBombIslands(ServerLevel level) {
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         int x = arena.centerX() + side * 15;
         fillScene(level, x - 5, arena.floorY() + 2, arena.centerZ() - 6, x + 5, arena.floorY() + 2, arena.centerZ() + 6,
            team == 1 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.END_STONE.defaultBlockState());
         fillScene(level, x - 1, arena.floorY(), arena.centerZ() - 1, x + 1, arena.floorY() + 2, arena.centerZ() + 1, Blocks.OBSIDIAN.defaultBlockState());
      }
   }

   private void buildLabyrinth(ServerLevel level) {
      fillScene(level, arena.centerX() - 18, arena.floorY(), arena.centerZ() - 15, arena.centerX() + 18, arena.floorY(), arena.centerZ() + 15, Blocks.DEEPSLATE_TILES.defaultBlockState());
      for (int x = arena.centerX() - 15; x <= arena.centerX() + 15; x += 5) {
         fillScene(level, x, arena.floorY() + 1, arena.centerZ() - 14, x, arena.floorY() + 3, arena.centerZ() + 8, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
      }
      for (int team = 1; team <= 2; team++) for (int gold = 0; gold < Math.max(2, teamMembers(team).size() * 2); gold++) {
         int row = gold / 2;
         BlockPos pos = new BlockPos(arena.centerX() + (team == 1 ? -1 : 1) * (7 + (gold % 2) * 6), arena.floorY() + 1,
            arena.centerZ() + (row % 2 == 0 ? -10 : 10) + (row / 2) * 3);
         scene(level, pos.getX(), pos.getY(), pos.getZ(), Blocks.GOLD_BLOCK.defaultBlockState());
         series200Flags.put(pos.immutable(), 10 + team);
      }
   }

   private void buildSnowFort(ServerLevel level) {
      fillScene(level, arena.centerX() - 20, arena.floorY(), arena.centerZ() - 14, arena.centerX() + 20, arena.floorY(), arena.centerZ() + 14, Blocks.SNOW_BLOCK.defaultBlockState());
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * 14;
         fillScene(level, x - 3, arena.floorY() + 1, arena.centerZ() - 10, x + 3, arena.floorY() + 3, arena.centerZ() - 8, Blocks.PACKED_ICE.defaultBlockState());
         fillScene(level, x - 3, arena.floorY() + 1, arena.centerZ() + 8, x + 3, arena.floorY() + 3, arena.centerZ() + 10, Blocks.PACKED_ICE.defaultBlockState());
      }
   }

   private void buildSpaceCourse(ServerLevel level) {
      fillScene(level, arena.minX() + 3, arena.floorY(), arena.minZ() + 3, arena.maxX() - 3, arena.floorY() + 2, arena.maxZ() - 3, Blocks.AIR.defaultBlockState());
      for (int team = 1; team <= 2; team++) {
         int z = arena.centerZ() + (team == 1 ? -5 : 5);
         int steps = 7;
         int stride = Math.max(4, (arena.size() - 14) / (steps - 1));
         for (int step = 0; step < steps; step++) {
            int x = arena.minX() + 6 + step * stride;
            fillScene(level, x, arena.floorY() + 1 + (step % 2), z - 1, x + 3, arena.floorY() + 1 + (step % 2), z + 1,
               team == 1 ? Blocks.END_STONE.defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
         }
      }
   }

   private void buildCartWorks(ServerLevel level) {
      fillScene(level, arena.centerX() - 20, arena.floorY(), arena.centerZ() - 8, arena.centerX() + 20, arena.floorY(), arena.centerZ() + 8, Blocks.DEEPSLATE_TILES.defaultBlockState());
      for (int z = arena.centerZ() - 6; z <= arena.centerZ() + 6; z += 4) {
         fillScene(level, arena.centerX() - 19, arena.floorY() + 1, z, arena.centerX() + 19, arena.floorY() + 1, z, Blocks.RAIL.defaultBlockState());
      }
      for (int team = 1; team <= 2; team++) {
         BlockPos button = new BlockPos(arena.centerX() + (team == 1 ? -1 : 1) * 15, arena.floorY() + 2, arena.centerZ());
         scene(level, button.getX(), button.getY() - 1, button.getZ(), Blocks.IRON_BLOCK.defaultBlockState());
         scene(level, button.getX(), button.getY(), button.getZ(), Blocks.STONE_BUTTON.defaultBlockState());
         series200Buttons.put(button.immutable(), team * 10 + 8);
      }
   }

   private void buildChickenArena(ServerLevel level) {
      fillScene(level, arena.centerX() - 18, arena.floorY(), arena.centerZ() - 12, arena.centerX() + 18, arena.floorY(), arena.centerZ() + 12, Blocks.WHITE_TERRACOTTA.defaultBlockState());
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         Chicken chicken = new Chicken(net.minecraft.world.entity.EntityType.CHICKEN, level);
         chicken.moveTo(arena.centerX() + side * 10, arena.floorY() + 1, arena.centerZ(), 0.0F, 0.0F);
         chicken.setNoAi(true);
         chicken.setCustomName(TextUtil.color(team == 1 ? "&6金队战鸡" : "&b青队战鸡"));
         chicken.setCustomNameVisible(true);
         level.addFreshEntity(chicken);
         classicMobs.add(chicken);
         series200ChickenTeams.put(chicken.getUUID(), team);
         series200TeamLives.put(team, 12);
      }
   }

   private void buildRecruitCamp(ServerLevel level) {
      fillScene(level, arena.centerX() - 18, arena.floorY(), arena.centerZ() - 12, arena.centerX() + 18, arena.floorY(), arena.centerZ() + 12, Blocks.GRASS_BLOCK.defaultBlockState());
      for (int team = 1; team <= 2; team++) {
         int side = team == 1 ? -1 : 1;
         int x = arena.centerX() + side * 12;
         for (int recruit = 0; recruit < 3; recruit++) {
            BlockPos button = new BlockPos(x, arena.floorY() + 2, arena.centerZ() + (recruit - 1) * 4);
            scene(level, button.getX(), button.getY() - 1, button.getZ(), Blocks.OAK_LOG.defaultBlockState());
            scene(level, button.getX(), button.getY(), button.getZ(), Blocks.STONE_BUTTON.defaultBlockState());
            series200Buttons.put(button.immutable(), team * 10 + 20 + recruit);
         }
      }
   }

   private void setupMinions(ServerLevel level) {
      buildMinionCourtyard(level);
      for (int i = 0; i < 12; i++) {
         Zombie minion = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
         double angle = i * Math.PI * 2.0 / 12.0;
         minion.moveTo(arena.centerX() + Math.cos(angle) * 6.0, arena.floorY() + 1.0,
            arena.centerZ() + Math.sin(angle) * 6.0, 0.0F, 0.0F);
         minion.setNoAi(true);
         minion.setCustomName(TextUtil.color("&7中立随从"));
         minion.setCustomNameVisible(true);
         level.addFreshEntity(minion);
         classicMobs.add(minion);
      }
   }

   private void setupRings(ServerLevel level) {
      buildBellTowers(level);
      for (int index = 0; index < seats.size(); index++) {
         BlockPos pos = new BlockPos(arena.centerX() + (index == 0 ? -6 : 6), arena.floorY() + 2, arena.centerZ());
         level.setBlock(pos.below(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
         level.setBlock(pos, Blocks.BELL.defaultBlockState(), 2);
         classicButtons.put(pos.immutable(), new ClassicButton(seats.get(index), 0));
      }
   }

   private void setupTurtleHockey(ServerLevel level) {
      buildHockeyRink(level);
      int half = arena.size() / 2 - 4;
      for (int z = arena.centerZ() - 5; z <= arena.centerZ() + 5; z++) {
         level.setBlock(new BlockPos(arena.centerX() - half, arena.floorY() + 1, z), Blocks.BLUE_CONCRETE.defaultBlockState(), 2);
         level.setBlock(new BlockPos(arena.centerX() + half, arena.floorY() + 1, z), Blocks.RED_CONCRETE.defaultBlockState(), 2);
      }
      hockeyPuck = new Turtle(net.minecraft.world.entity.EntityType.TURTLE, level);
      hockeyPuck.moveTo(arena.centerX() + 0.5, arena.floorY() + 1.0, arena.centerZ() + 0.5, 0.0F, 0.0F);
      hockeyPuck.setNoAi(true);
      hockeyPuck.setCustomName(TextUtil.color("&f冰球"));
      hockeyPuck.setCustomNameVisible(true);
      level.addFreshEntity(hockeyPuck);
      classicMobs.add(hockeyPuck);
   }

   private void setupFishing(ServerLevel level) {
      buildFishingDock(level);
      for (int x = arena.centerX() - 7; x <= arena.centerX() + 7; x++) {
         for (int z = arena.centerZ() - 7; z <= arena.centerZ() + 7; z++) {
            level.setBlock(new BlockPos(x, arena.floorY(), z), Blocks.WATER.defaultBlockState(), 2);
         }
      }
   }

   private void setupButtonWall(ServerLevel level) {
      buildButtonGallery(level);
      int baseX = arena.centerX();
      for (int i = 0; i < 9; i++) {
         int x = baseX + i % 3 - 1;
         int y = arena.floorY() + 2 + i / 3;
            BlockPos cell = new BlockPos(x, y, arena.centerZ());
            level.setBlock(cell, Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
            for (int side = 0; side < 2; side++) {
               BlockPos button = cell.offset(0, 0, side == 0 ? -1 : 1);
               level.setBlock(cell.offset(0, 0, side == 0 ? -2 : 2), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
               level.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState(), 2);
            classicButtons.put(button.immutable(), new ClassicButton(seats.get(side), i));
         }
      }
   }

   private void setupBridge(ServerLevel level) {
      buildBridgeIslands(level);
      int left = arena.centerX() - arena.size() / 2 + 3;
      int right = arena.centerX() + arena.size() / 2 - 3;
      for (int x = left; x <= right; x++) {
         level.setBlock(new BlockPos(x, arena.floorY() + 1, arena.centerZ()), Blocks.STONE_BRICKS.defaultBlockState(), 2);
         if ((x - left) % 9 == 4) level.setBlock(new BlockPos(x, arena.floorY() + 2, arena.centerZ()), Blocks.OAK_PLANKS.defaultBlockState(), 2);
      }
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * (arena.size() / 2 - 5);
         level.setBlock(new BlockPos(x, arena.floorY() + 1, arena.centerZ()), Blocks.LODESTONE.defaultBlockState(), 2);
      }
   }

   private void setupPigPushers(ServerLevel level) {
      buildPigLanes(level);
      for (int index = 0; index < seats.size(); index++) {
         int side = index == 0 ? -1 : 1;
         Pig pig = new Pig(net.minecraft.world.entity.EntityType.PIG, level);
         pig.moveTo(arena.centerX() + side * 7.0, arena.floorY() + 1.0, arena.centerZ() + side * 5.0 + 0.5, 0.0F, 0.0F);
         pig.setNoAi(true);
         pig.setCustomName(TextUtil.color("&e" + ctx.name(seats.get(index)) + " 的猪"));
         pig.setCustomNameVisible(true);
         level.addFreshEntity(pig);
         classicMobs.add(pig);
         classicPigs.put(seats.get(index), pig);
         BlockPos barn = new BlockPos(arena.centerX() - side * (arena.size() / 2 - 5), arena.floorY() + 1, arena.centerZ() + side * 5);
         level.setBlock(barn, Blocks.HAY_BLOCK.defaultBlockState(), 2);
      }
   }

   private void setupBalanceBeams(ServerLevel level) {
      buildBalanceHall(level);
      for (int side : new int[] {-1, 1}) {
         int z = arena.centerZ() + side * 4;
         for (int x = arena.minX() + 6; x <= arena.maxX() - 6; x++) {
            level.setBlock(new BlockPos(x, arena.floorY() + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
            if ((x - arena.minX()) % 16 == 0) level.setBlock(new BlockPos(x, arena.floorY() + 2, z), Blocks.WHITE_STAINED_GLASS.defaultBlockState(), 2);
         }
      }
   }

   private void setupButtonSearch(ServerLevel level) {
      buildSearchGarden(level);
      int[][] offsets = {{-10, -6}, {-7, 8}, {8, -7}, {11, 6}};
      for (int index = 0; index < seats.size(); index++) {
         int[] offset = offsets[(index * 2 + Math.floorMod((int) template.seed(), 2)) % offsets.length];
         BlockPos pos = new BlockPos(arena.centerX() + offset[0], arena.floorY() + 2, arena.centerZ() + offset[1]);
         level.setBlock(pos.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
         level.setBlock(pos, Blocks.OAK_BUTTON.defaultBlockState(), 2);
         classicButtons.put(pos.immutable(), new ClassicButton(seats.get(index), 0));
      }
   }

   private void setupBetrisConsole(ServerLevel level) { buildTetrisBoards(level); setupThreeButtonConsole(level, PartyGameType.BETRIS); }
   private void setupDecryptionPanels(ServerLevel level) { buildDecryptionChambers(level); setupThreeButtonConsole(level, PartyGameType.DECRYPTION); }

   private void setupThreeButtonConsole(ServerLevel level, PartyGameType game) {
      for (int index = 0; index < seats.size(); index++) {
         int side = index == 0 ? -1 : 1;
         for (int value = 0; value < 3; value++) {
            BlockPos pos = new BlockPos(arena.centerX() + side * 7, arena.floorY() + 2 + value * 2, arena.centerZ());
            level.setBlock(pos.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            BlockState face = game == PartyGameType.BETRIS ? switch (value) {
               case 0 -> Blocks.BLUE_CONCRETE.defaultBlockState();
               case 1 -> Blocks.PURPLE_CONCRETE.defaultBlockState();
               default -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            } : switch (value) {
               case 0 -> Blocks.RED_CONCRETE.defaultBlockState();
               case 1 -> Blocks.YELLOW_CONCRETE.defaultBlockState();
               default -> Blocks.LIME_CONCRETE.defaultBlockState();
            };
            level.setBlock(pos, face, 2);
            classicButtons.put(pos.immutable(), new ClassicButton(seats.get(index), value));
         }
         ServerPlayer player = ctx.player(seats.get(index));
         if (player == null) continue;
         ItemStack guide = new ItemStack(Items.PAPER);
         if (game == PartyGameType.BETRIS) {
            guide.set(DataComponents.CUSTOM_NAME, TextUtil.color("&d方块序列：&9蓝 &7→ &5紫 &7→ &6橙（循环）"));
         } else {
            StringBuilder code = new StringBuilder();
            for (int digit = 0; digit < 3; digit++) code.append(Math.floorMod(player.getUUID().hashCode() + digit * 7, 3) + 1);
            guide.set(DataComponents.CUSTOM_NAME, TextUtil.color("&e个人密码：&f" + code + " &7(红=1 黄=2 绿=3)"));
         }
         player.getInventory().setItem(8, guide);
      }
   }

   private void setupDeuce(ServerLevel level) {
      buildVolleyballCourt(level);
      for (int z = arena.centerZ() - 7; z <= arena.centerZ() + 7; z++) {
         level.setBlock(new BlockPos(arena.centerX(), arena.floorY() + 1, z), Blocks.IRON_BARS.defaultBlockState(), 2);
      }
      deuceBall = new Slime(net.minecraft.world.entity.EntityType.SLIME, level);
      deuceBall.setSize(1, true);
      deuceBall.moveTo(arena.centerX() + 0.5, arena.floorY() + 2.0, arena.centerZ() + 0.5, 0.0F, 0.0F);
      deuceBall.setNoAi(true);
      deuceBall.setCustomName(TextUtil.color("&a排球"));
      deuceBall.setCustomNameVisible(true);
      level.addFreshEntity(deuceBall);
      classicMobs.add(deuceBall);
   }

   private void setupCannoneers(ServerLevel level) {
      buildCannonPlatforms(level);
      for (int index = 0; index < seats.size(); index++) {
         Vec3 spawn = spawnFor(index);
         BlockPos base = BlockPos.containing(spawn.x, arena.floorY(), spawn.z);
         for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(base.offset(x, 0, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
         }
      }
   }

   private void buildMinionCourtyard(ServerLevel level) {
      int cX = arena.centerX(), cZ = arena.centerZ();
      fillScene(level, cX - 14, arena.floorY(), cZ - 14, cX + 14, arena.floorY(), cZ + 14, Blocks.MOSS_BLOCK.defaultBlockState());
      fillScene(level, cX - 1, arena.floorY(), cZ - 14, cX + 1, arena.floorY(), cZ + 14, Blocks.POLISHED_ANDESITE.defaultBlockState());
      fillScene(level, cX - 14, arena.floorY(), cZ - 1, cX + 14, arena.floorY(), cZ + 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
      for (int dx : new int[] {-12, 12}) for (int dz : new int[] {-12, 12}) {
         fillScene(level, cX + dx, arena.floorY() + 1, cZ + dz, cX + dx, arena.floorY() + 5, cZ + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
         scene(level, cX + dx, arena.floorY() + 6, cZ + dz, Blocks.SOUL_LANTERN.defaultBlockState());
      }
      fillScene(level, cX - 2, arena.floorY() + 1, cZ - 2, cX + 2, arena.floorY() + 1, cZ + 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
   }

   private void buildBellTowers(ServerLevel level) {
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * 6;
         fillScene(level, x - 2, arena.floorY(), arena.centerZ() - 3, x + 2, arena.floorY() + 1, arena.centerZ() + 3, Blocks.SMOOTH_STONE.defaultBlockState());
         fillScene(level, x - 2, arena.floorY() + 2, arena.centerZ() - 3, x - 2, arena.floorY() + 5, arena.centerZ() + 3, Blocks.STONE_BRICK_WALL.defaultBlockState());
         fillScene(level, x + 2, arena.floorY() + 2, arena.centerZ() - 3, x + 2, arena.floorY() + 5, arena.centerZ() + 3, Blocks.STONE_BRICK_WALL.defaultBlockState());
         scene(level, x, arena.floorY() + 6, arena.centerZ(), Blocks.COPPER_BULB.defaultBlockState());
      }
   }

   private void buildHockeyRink(ServerLevel level) {
      int half = Math.min(18, arena.size() / 2 - 3);
      fillScene(level, arena.centerX() - half, arena.floorY(), arena.centerZ() - 9,
         arena.centerX() + half, arena.floorY(), arena.centerZ() + 9, Blocks.ICE.defaultBlockState());
      for (int y = arena.floorY() + 1; y <= arena.floorY() + 3; y++) {
         fillScene(level, arena.centerX() - half, y, arena.centerZ() - 9, arena.centerX() + half, y, arena.centerZ() - 9, Blocks.WHITE_CONCRETE.defaultBlockState());
         fillScene(level, arena.centerX() - half, y, arena.centerZ() + 9, arena.centerX() + half, y, arena.centerZ() + 9, Blocks.WHITE_CONCRETE.defaultBlockState());
      }
      fillScene(level, arena.centerX(), arena.floorY() + 1, arena.centerZ() - 9, arena.centerX(), arena.floorY() + 1, arena.centerZ() + 9, Blocks.RED_CONCRETE.defaultBlockState());
   }

   private void buildFishingDock(ServerLevel level) {
      int cX = arena.centerX(), cZ = arena.centerZ();
      fillScene(level, cX - 9, arena.floorY(), cZ - 9, cX + 9, arena.floorY(), cZ + 9, Blocks.DARK_OAK_PLANKS.defaultBlockState());
      fillScene(level, cX - 8, arena.floorY() + 1, cZ - 8, cX + 8, arena.floorY() + 1, cZ + 8, Blocks.WATER.defaultBlockState());
      for (int dx : new int[] {-10, 10}) for (int dz : new int[] {-10, 10}) {
         fillScene(level, cX + dx, arena.floorY() + 1, cZ + dz, cX + dx, arena.floorY() + 4, cZ + dz, Blocks.OAK_FENCE.defaultBlockState());
         scene(level, cX + dx, arena.floorY() + 5, cZ + dz, Blocks.LANTERN.defaultBlockState());
      }
   }

   private void buildButtonGallery(ServerLevel level) {
      fillScene(level, arena.centerX() - 4, arena.floorY() + 1, arena.centerZ() - 3,
         arena.centerX() + 4, arena.floorY() + 5, arena.centerZ() - 3, Blocks.DEEPSLATE_TILES.defaultBlockState());
      fillScene(level, arena.centerX() - 4, arena.floorY() + 1, arena.centerZ() + 3,
         arena.centerX() + 4, arena.floorY() + 5, arena.centerZ() + 3, Blocks.DEEPSLATE_TILES.defaultBlockState());
      fillScene(level, arena.centerX() - 7, arena.floorY(), arena.centerZ() - 8,
         arena.centerX() + 7, arena.floorY(), arena.centerZ() + 8, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
   }

   private void buildBridgeIslands(ServerLevel level) {
      int cX = arena.centerX(), cZ = arena.centerZ();
      int half = Math.min(18, arena.size() / 2 - 3);
      fillScene(level, cX - half, arena.floorY(), cZ - 7, cX - half + 6, arena.floorY() + 1, cZ + 7, Blocks.GRASS_BLOCK.defaultBlockState());
      fillScene(level, cX + half - 6, arena.floorY(), cZ - 7, cX + half, arena.floorY() + 1, cZ + 7, Blocks.GRASS_BLOCK.defaultBlockState());
      fillScene(level, cX - half + 7, arena.floorY(), cZ - 4, cX + half - 7, arena.floorY() + 2, cZ + 4, Blocks.AIR.defaultBlockState());
      for (int side : new int[] {-1, 1}) {
         int x = cX + side * (half - 3);
         fillScene(level, x - 1, arena.floorY() + 2, cZ - 3, x + 1, arena.floorY() + 4, cZ + 3, Blocks.OAK_PLANKS.defaultBlockState());
         scene(level, x, arena.floorY() + 5, cZ, Blocks.RED_BANNER.defaultBlockState());
      }
   }

   private void buildPigLanes(ServerLevel level) {
      for (int side : new int[] {-1, 1}) {
         int z = arena.centerZ() + side * 5;
         fillScene(level, arena.centerX() - 18, arena.floorY(), z - 2, arena.centerX() + 18, arena.floorY(), z + 2, Blocks.PODZOL.defaultBlockState());
         fillScene(level, arena.centerX() - 18, arena.floorY() + 1, z - 3, arena.centerX() + 18, arena.floorY() + 2, z - 3, Blocks.OAK_FENCE.defaultBlockState());
         fillScene(level, arena.centerX() - 18, arena.floorY() + 1, z + 3, arena.centerX() + 18, arena.floorY() + 2, z + 3, Blocks.OAK_FENCE.defaultBlockState());
      }
   }

   private void buildBalanceHall(ServerLevel level) {
      fillScene(level, arena.minX() + 4, arena.floorY(), arena.centerZ() - 8,
         arena.maxX() - 4, arena.floorY(), arena.centerZ() + 8, Blocks.AIR.defaultBlockState());
      for (int x = arena.minX() + 5; x <= arena.maxX() - 5; x += 8) {
         scene(level, x, arena.floorY() + 1, arena.centerZ() - 8, Blocks.SEA_LANTERN.defaultBlockState());
         scene(level, x, arena.floorY() + 1, arena.centerZ() + 8, Blocks.SEA_LANTERN.defaultBlockState());
      }
   }

   private void buildSearchGarden(ServerLevel level) {
      int cX = arena.centerX(), cZ = arena.centerZ();
      fillScene(level, cX - 14, arena.floorY(), cZ - 14, cX + 14, arena.floorY(), cZ + 14, Blocks.GRASS_BLOCK.defaultBlockState());
      for (int x = cX - 12; x <= cX + 12; x += 6) {
         fillScene(level, x, arena.floorY() + 1, cZ - 12, x, arena.floorY() + 3, cZ - 3, Blocks.OAK_LEAVES.defaultBlockState());
         fillScene(level, x, arena.floorY() + 1, cZ + 3, x, arena.floorY() + 3, cZ + 12, Blocks.OAK_LEAVES.defaultBlockState());
      }
      for (int z = cZ - 9; z <= cZ + 9; z += 6) {
         fillScene(level, cX - 12, arena.floorY() + 1, z, cX - 3, arena.floorY() + 3, z, Blocks.OAK_LEAVES.defaultBlockState());
         fillScene(level, cX + 3, arena.floorY() + 1, z, cX + 12, arena.floorY() + 3, z, Blocks.OAK_LEAVES.defaultBlockState());
      }
   }

   private void buildTetrisBoards(ServerLevel level) {
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * 11;
         fillScene(level, x, arena.floorY() + 1, arena.centerZ() - 6, x, arena.floorY() + 10, arena.centerZ() + 6, Blocks.GRAY_CONCRETE.defaultBlockState());
         for (int y = arena.floorY() + 2; y <= arena.floorY() + 9; y += 2) {
            fillScene(level, x + (side < 0 ? 1 : -1), y, arena.centerZ() - 5, x + (side < 0 ? 1 : -1), y, arena.centerZ() + 5, Blocks.BLACK_CONCRETE.defaultBlockState());
         }
      }
   }

   private void buildDecryptionChambers(ServerLevel level) {
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * 9;
         fillScene(level, x - 2, arena.floorY(), arena.centerZ() - 5, x + 2, arena.floorY() + 5, arena.centerZ() + 5, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
         fillScene(level, x - 1, arena.floorY() + 1, arena.centerZ() - 4, x + 1, arena.floorY() + 4, arena.centerZ() + 4, Blocks.AIR.defaultBlockState());
         scene(level, x, arena.floorY() + 5, arena.centerZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
      }
   }

   private void buildVolleyballCourt(ServerLevel level) {
      fillScene(level, arena.centerX() - 17, arena.floorY(), arena.centerZ() - 9,
         arena.centerX() + 17, arena.floorY(), arena.centerZ() + 9, Blocks.SAND.defaultBlockState());
      for (int z : new int[] {arena.centerZ() - 9, arena.centerZ() + 9}) {
         fillScene(level, arena.centerX() - 17, arena.floorY() + 1, z, arena.centerX() + 17, arena.floorY() + 2, z, Blocks.WHITE_CONCRETE.defaultBlockState());
      }
   }

   private void buildCannonPlatforms(ServerLevel level) {
      for (int side : new int[] {-1, 1}) {
         int x = arena.centerX() + side * 14;
         fillScene(level, x - 3, arena.floorY(), arena.centerZ() - 5, x + 3, arena.floorY() + 1, arena.centerZ() + 5, Blocks.DEEPSLATE_TILES.defaultBlockState());
         fillScene(level, x - 3, arena.floorY() + 2, arena.centerZ() - 5, x + 3, arena.floorY() + 2, arena.centerZ() - 5, Blocks.IRON_BARS.defaultBlockState());
         fillScene(level, x - 3, arena.floorY() + 2, arena.centerZ() + 5, x + 3, arena.floorY() + 2, arena.centerZ() + 5, Blocks.IRON_BARS.defaultBlockState());
         scene(level, x, arena.floorY() + 2, arena.centerZ(), Blocks.DISPENSER.defaultBlockState());
      }
   }

   private void scene(ServerLevel level, int x, int y, int z, BlockState state) {
      if (x < arena.minX() || x > arena.maxX() || y < arena.baseY() || y > arena.topY() || z < arena.minZ() || z > arena.maxZ()) return;
      level.setBlock(new BlockPos(x, y, z), state, 2);
   }

   private void fillScene(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
      for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
         for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) scene(level, x, y, z, state);
         }
      }
   }

   private void tickClassic100() {
      if (phase != Phase.RUNNING) return;
      for (int index = 0; index < seats.size(); index++) {
         Fighter fighter = fighters.get(seats.get(index));
         ServerPlayer player = ctx.player(seats.get(index));
         if (fighter == null || player == null || !fighter.alive) continue;
         if ((type == PartyGameType.BRIDGE_CROSSING || type == PartyGameType.BALANCE_BEAM)
            && player.getY() <= arena.floorY() + 1.05) {
            resetClassicPlayer(player, fighter, index);
            continue;
         }
         if (player.getY() < arena.floorY() - 3) {
            if (type == PartyGameType.GLADIATOR_FIGHT || type == PartyGameType.CANNONEERS) eliminate(player, null);
            else resetClassicPlayer(player, fighter, index);
         }
      }
      switch (type) {
         case RING_IN_THE_RING -> tickRings();
         case TURTLE_HOCKEY -> tickTurtleHockey();
         case GO_FISH -> tickFishing();
         case DONT_PUSH_MY_BUTTONS -> tickButtonWall();
         case BRIDGE_CROSSING -> tickBridge();
         case PIG_PUSHERS -> tickPigPushers();
         case BALANCE_BEAM -> tickBalanceBeams();
         case DEUCE -> tickDeuce();
         case CANNONEERS -> tickCannoneersWind();
         default -> { }
      }
   }

   private void resetClassicPlayer(ServerPlayer player, Fighter fighter, int index) {
      arena.teleport(player, level(), spawnFor(index));
      player.setHealth(player.getMaxHealth());
      player.setDeltaMovement(Vec3.ZERO);
      fighter.attempts++;
   }

   private void tickRings() {
      int threshold = ticks <= 400 ? 5 : ticks <= 800 ? 10 : ticks <= 1200 ? 15 : 20;
      for (Fighter fighter : fighters.values()) if (Math.abs(fighter.score) >= threshold) { finish(bestScore()); return; }
   }

   private void tickTurtleHockey() {
      if (hockeyPuck == null || hockeyPuck.isRemoved()) return;
      double line = arena.size() / 2.0 - 5.0;
      if (hockeyPuck.getX() <= arena.centerX() - line) finish(seats.get(1));
      else if (hockeyPuck.getX() >= arena.centerX() + line) finish(seats.get(0));
   }

   private void tickFishing() {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null && fishCount(player) > 0) { finish(player.getUUID()); return; }
      }
   }

   private int fishCount(ServerPlayer player) {
      Inventory inv = player.getInventory();
      int total = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH)) total += stack.getCount();
      }
      return total;
   }

   private void tickButtonWall() {
      classicWinThreshold = ticks <= 400 ? 3 : ticks <= 800 ? 5 : ticks <= 1200 ? 7 : 9;
      for (int index = 0; index < seats.size(); index++) if (ownedCells(index + 1) >= classicWinThreshold) { finish(seats.get(index)); return; }
   }

   private void tickBridge() {
      double finishLine = arena.size() / 2.0 - 5.0;
      for (int index = 0; index < seats.size(); index++) {
         ServerPlayer player = ctx.player(seats.get(index));
         Fighter fighter = fighters.get(seats.get(index));
         if (player == null || fighter == null) continue;
         boolean reached = index == 0 ? player.getX() >= arena.centerX() + finishLine : player.getX() <= arena.centerX() - finishLine;
         if (!reached) continue;
         award(player.getUUID(), 1);
         if (fighter.score >= 2) { finish(player.getUUID()); return; }
         resetClassicPlayer(player, fighter, index);
         ctx.broadcast(room, "&e" + ctx.name(player.getUUID()) + " &7拿下了一分！");
      }
   }

   private void tickPigPushers() {
      double finishLine = arena.size() / 2.0 - 5.0;
      for (int index = 0; index < seats.size(); index++) {
         Pig pig = classicPigs.get(seats.get(index));
         if (pig == null || pig.isRemoved()) continue;
         boolean reached = index == 0 ? pig.getX() >= arena.centerX() + finishLine : pig.getX() <= arena.centerX() - finishLine;
         if (reached) { finish(seats.get(index)); return; }
      }
   }

   private void tickBalanceBeams() {
      if (++classicWindTicks >= 200) {
         classicWindTicks = 0;
         classicWindDirection = -classicWindDirection;
         ctx.broadcast(room, "&b重力方向改变！");
      }
      for (int index = 0; index < seats.size(); index++) {
         ServerPlayer player = ctx.player(seats.get(index));
         Fighter fighter = fighters.get(seats.get(index));
         if (player == null || fighter == null) continue;
         player.push(0.0, 0.0, classicWindDirection * 0.018);
         player.hurtMarked = true;
         fighter.progress = Math.max(fighter.progress, (int) Math.max(0, player.getX() - (arena.minX() + 7)) / 16);
         if (player.getX() >= arena.maxX() - 6) { finish(player.getUUID()); return; }
      }
   }

   private void tickDeuce() {
      if (deuceBall == null || deuceBall.isRemoved()) return;
      double line = arena.size() / 2.0 - 5.0;
      UUID scorer = null;
      if (deuceBall.getX() <= arena.centerX() - line) scorer = seats.get(1);
      else if (deuceBall.getX() >= arena.centerX() + line) scorer = seats.get(0);
      if (scorer == null) return;
      Fighter fighter = fighters.get(scorer);
      if (fighter != null) award(scorer, 1);
      if (fighter != null && fighter.score >= 5 && fighter.score - opposingScore(scorer) >= 2) { finish(scorer); return; }
      deuceBall.teleportTo(arena.centerX() + 0.5, arena.floorY() + 2.0, arena.centerZ() + 0.5);
      deuceBall.setDeltaMovement(Vec3.ZERO);
      lastBallHit = null;
   }

   private int opposingScore(UUID player) {
      for (Fighter fighter : fighters.values()) if (!fighter.uuid.equals(player)) return fighter.score;
      return 0;
   }

   private void tickCannoneersWind() {
      ServerLevel level = level();
      if (level == null) return;
      AABB area = new AABB(arena.minX(), arena.baseY(), arena.minZ(), arena.maxX() + 1, arena.topY() + 6, arena.maxZ() + 1);
      for (Entity entity : level.getEntities((Entity) null, area, entity -> entity instanceof Projectile)) {
         entity.push(0.0, 0.0, classicWindDirection * 0.0035);
         entity.hurtMarked = true;
      }
      if (++classicWindTicks < 160) return;
      classicWindTicks = 0;
      classicWindDirection = -classicWindDirection;
      for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid);
         if (player != null) player.displayClientMessage(TextUtil.color("&e横风已转向：&f" + (classicWindDirection > 0 ? "→" : "←")), true);
      }
   }

   private InteractionResult handleClassicBlockUse(ServerPlayer player, Fighter fighter, BlockPos pos) {
      if (type == PartyGameType.GO_FISH || type == PartyGameType.BRIDGE_CROSSING) return InteractionResult.PASS;
      ClassicButton button = classicButtons.get(pos);
      if (button == null) return InteractionResult.FAIL;
      switch (type) {
         case RING_IN_THE_RING -> {
            if (!button.owner().equals(player.getUUID())) return InteractionResult.FAIL;
            award(player.getUUID(), 1);
            for (Fighter other : fighters.values()) if (!other.uuid.equals(player.getUUID())) other.score--;
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.25F);
         }
         case DONT_PUSH_MY_BUTTONS -> {
            if (!button.owner().equals(player.getUUID())) return InteractionResult.FAIL;
            classicCells[button.value()] = seats.indexOf(player.getUUID()) + 1;
            BlockPos cell = new BlockPos(arena.centerX() + button.value() % 3 - 1, arena.floorY() + 2 + button.value() / 3, arena.centerZ());
            level().setBlock(cell, ownColor(player.getUUID()), 2);
            for (int index = 0; index < seats.size(); index++) {
               Fighter owner = fighters.get(seats.get(index));
               if (owner != null) owner.score = ownedCells(index + 1);
            }
         }
         case BUTTON_SEARCH -> {
            if (button.owner().equals(player.getUUID())) return InteractionResult.FAIL;
            finish(player.getUUID());
         }
         case BETRIS -> {
            if (!button.owner().equals(player.getUUID())) return InteractionResult.FAIL;
            int expected = Math.floorMod(fighter.classicValue, 3);
            if (button.value() == expected) { award(player.getUUID(), 1); fighter.classicValue++; }
            else fighter.classicValue = Math.max(0, fighter.classicValue - 1);
            if (fighter.score >= 12) finish(player.getUUID());
         }
         case DECRYPTION -> {
            if (!button.owner().equals(player.getUUID())) return InteractionResult.FAIL;
            int expected = Math.floorMod(player.getUUID().hashCode() + fighter.classicValue * 7, 3);
            if (button.value() == expected) fighter.classicValue++;
            else fighter.classicValue = 0;
            if (fighter.classicValue >= 3) finish(player.getUUID());
         }
         default -> { return InteractionResult.FAIL; }
      }
      return InteractionResult.FAIL;
   }

   private InteractionResult handleSeries200BlockUse(ServerPlayer player, Fighter fighter, BlockPos pos) {
      Integer action = series200Buttons.get(pos);
      if (action == null) return type == PartyGameType.MINE_YOUR_BUSINESS ? InteractionResult.PASS : InteractionResult.FAIL;
      int team = teamOf(player.getUUID());
      int owner = action / 10;
      int value = action % 10;
      if (owner != team) return InteractionResult.FAIL;
      switch (type) {
         case RPSC -> {
            series200Choices.put(player.getUUID(), value);
            player.displayClientMessage(TextUtil.color("&e已为队伍选择：&f" + rpscName(value)), true);
         }
         case MAZE_NAVIGATOR -> {
            if (fighter.classicValue == 0) {
               fighter.classicValue = 1;
               player.getInventory().setItem(0, new ItemStack(team == 1 ? Items.ORANGE_DYE : Items.LIGHT_BLUE_DYE));
               ctx.send(player, "&a已找到队伍魔珠，前往队友身边汇合！");
            }
         }
         case BOOM_CARTS -> {
            if (series200CartTarget == 0) {
               series200CartTarget = team == 1 ? 2 : 1;
               series200RoundTicks = 60;
               ctx.broadcast(room, "&c" + (team == 1 ? "金队" : "青队") + " &7改变了矿车轨道方向！");
            }
         }
         case RECRUITMENT_ROYALE -> {
            if (series200RoundTicks < 400) {
               int points = value == 0 ? 1 : value == 1 ? 2 : 3;
               series200TeamScore.merge(team, points, Integer::sum);
               fighter.score += points;
               player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.25F);
            }
         }
         default -> { }
      }
      return InteractionResult.FAIL;
   }

   private void tickClassic200() {
      if (phase != Phase.RUNNING) return;
      for (int index = 0; index < seats.size(); index++) {
         UUID uuid = seats.get(index);
         Fighter fighter = fighters.get(uuid);
         ServerPlayer player = ctx.player(uuid);
         if (fighter == null || player == null || !fighter.alive) continue;
         if (player.getY() < arena.floorY() - 2) {
            if (type == PartyGameType.BOMBS_AWAY || type == PartyGameType.SPACE_JUMPERS) eliminate(player, null);
            else resetClassicPlayer(player, fighter, index);
         }
      }
      switch (type) {
         case PRISON_PALS -> tickPrisonPals();
         case RPSC -> tickRpsc();
         case CAPTURE_THE_FLAG -> tickCaptureTheFlag();
         case TEAM_HOCKEY -> tickTeamHockey();
         case MAZE_NAVIGATOR -> tickMazeNavigator();
         case MINE_YOUR_BUSINESS, BOMBS_AWAY, SNOW_WARS, WHAT_THE_CLUCK -> tickTeamElimination();
         case LABYRINTH -> tickLabyrinth();
         case SPACE_JUMPERS -> tickSpaceJumpers();
         case BOOM_CARTS -> tickBoomCarts();
         case RECRUITMENT_ROYALE -> tickRecruitmentRoyale();
         default -> { }
      }
      if (type == PartyGameType.SNOW_WARS && ticks % 60 == 0) refillTeamAmmo(Items.SNOWBALL, 6);
      if (type == PartyGameType.BOMBS_AWAY && ticks % 100 == 0) refillTeamAmmo(Items.SNOWBALL, 5);
      if (type == PartyGameType.WHAT_THE_CLUCK && ticks % 10 == 0) refillTeamAmmo(Items.EGG, 10);
   }

   private void tickPrisonPals() {
      for (int team = 1; team <= 2; team++) {
         int stage = 5;
         for (UUID uuid : teamMembers(team)) {
            ServerPlayer player = ctx.player(uuid);
            Fighter fighter = fighters.get(uuid);
            if (player == null || fighter == null) continue;
            int reached = Math.max(0, Math.min(5, (int) ((player.getX() - (arena.centerX() - 20)) / 7)));
            fighter.classicValue = Math.max(fighter.classicValue, reached);
            stage = Math.min(stage, fighter.classicValue);
         }
         series200TeamScore.put(team, stage);
         if (stage >= 5) { finishTeam(team); return; }
      }
   }

   private void tickRpsc() {
      if (series200WinningTeam != 0) {
         if (--series200RoundTicks <= 0) {
            series200WinningTeam = 0;
            series200Choices.clear();
            ctx.broadcast(room, "&e下一轮石头剪刀布开始！");
         }
         tickTeamElimination();
         return;
      }
      Integer left = firstTeamChoice(1), right = firstTeamChoice(2);
      if (left == null || right == null) return;
      if (left.equals(right)) {
         ctx.broadcast(room, "&7双方平局，重新选择。");
         series200Choices.clear();
         return;
      }
      series200WinningTeam = rpscWins(left, right) ? 1 : 2;
      series200RoundTicks = 160;
      ctx.broadcast(room, "&6" + (series200WinningTeam == 1 ? "金队" : "青队") + " &a获胜，可攻击对手！");
   }

   private Integer firstTeamChoice(int team) {
      for (UUID uuid : teamMembers(team)) {
         Integer choice = series200Choices.get(uuid);
         if (choice != null) return choice;
      }
      return null;
   }

   private boolean rpscWins(int first, int second) { return (first == 1 && second == 2) || (first == 2 && second == 3) || (first == 3 && second == 1); }
   private String rpscName(int choice) { return switch (choice) { case 1 -> "石头"; case 2 -> "剪刀"; default -> "布"; }; }

   private void tickCaptureTheFlag() {
      for (Map.Entry<UUID, Integer> entry : new ArrayList<>(series200CarriedFlags.entrySet())) {
         ServerPlayer player = ctx.player(entry.getKey());
         if (player == null) continue;
         int carrierTeam = teamOf(entry.getKey());
         double baseX = arena.centerX() + (carrierTeam == 1 ? -17 : 17);
         if (Math.abs(player.getX() - baseX) <= 4.0) {
            finishTeam(carrierTeam);
            return;
         }
      }
   }

   private void tickTeamHockey() {
      double line = arena.size() / 2.0 - 5.0;
      for (Turtle puck : List.copyOf(teamHockeyPucks)) {
         if (puck.isRemoved()) continue;
         int scorer = puck.getX() <= arena.centerX() - line ? 2 : puck.getX() >= arena.centerX() + line ? 1 : 0;
         if (scorer == 0) continue;
         int goals = series200TeamScore.merge(scorer, 1, Integer::sum);
         ctx.broadcast(room, "&e" + (scorer == 1 ? "金队" : "青队") + " &7进球！");
         if (goals >= 2) { finishTeam(scorer); return; }
         puck.teleportTo(arena.centerX() + 0.5, arena.floorY() + 1.0, arena.centerZ() + 0.5);
         puck.setDeltaMovement(Vec3.ZERO);
      }
   }

   private void tickMazeNavigator() {
      for (int team = 1; team <= 2; team++) {
         List<UUID> members = teamMembers(team);
         boolean allFound = !members.isEmpty() && members.stream().allMatch(uuid -> fighters.get(uuid) != null && fighters.get(uuid).classicValue > 0);
         if (!allFound) continue;
         ServerPlayer anchor = ctx.player(members.get(0));
         boolean grouped = anchor != null && members.stream().allMatch(uuid -> {
            ServerPlayer other = ctx.player(uuid);
            return other != null && other.position().distanceToSqr(anchor.position()) <= 25.0;
         });
         if (grouped) { finishTeam(team); return; }
      }
   }

   private void tickTeamElimination() {
      for (int team = 1; team <= 2; team++) {
         boolean alive = teamMembers(team).stream().anyMatch(uuid -> fighters.get(uuid) != null && fighters.get(uuid).alive);
         if (!alive) { finishTeam(team == 1 ? 2 : 1); return; }
      }
   }

   private void tickLabyrinth() {
      for (int team = 1; team <= 2; team++) {
         boolean ready = !teamMembers(team).isEmpty() && teamMembers(team).stream().allMatch(uuid -> fighters.get(uuid) != null && fighters.get(uuid).classicValue >= 2);
         if (ready) { finishTeam(team); return; }
      }
   }

   private void tickSpaceJumpers() {
      for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid);
         if (player != null && player.getX() >= arena.maxX() - 7) { finishTeam(teamOf(uuid)); return; }
      }
   }

   private void tickBoomCarts() {
      if (series200CartTarget == 0) return;
      if (--series200RoundTicks > 0) return;
      int lives = series200TeamLives.merge(series200CartTarget, -1, Integer::sum);
      ctx.broadcast(room, "&c矿车爆炸！&7 " + (series200CartTarget == 1 ? "金队" : "青队") + "失去一条命。");
      if (lives <= 0) { finishTeam(series200CartTarget == 1 ? 2 : 1); return; }
      series200CartTarget = 0;
   }

   private void tickRecruitmentRoyale() {
      series200RoundTicks++;
      if (series200RoundTicks == 400) {
         spawnRecruitArmies();
         ctx.broadcast(room, "&c招募结束，军团开始交战！");
      }
      if (series200RoundTicks < 400) return;
      if (series200RoundTicks % 20 == 0) resolveRecruitSkirmish();
      if (phase == Phase.ENDED || series200RoundTicks < 560) return;
      int first = recruitCount(1), second = recruitCount(2);
      if (first != second) finishTeam(first > second ? 1 : 2);
   }

   private void spawnRecruitArmies() {
      ServerLevel level = level();
      if (level == null) return;
      for (int team = 1; team <= 2; team++) {
         int recruits = Math.max(1, Math.min(18, series200TeamScore.getOrDefault(team, 0)));
         for (int index = 0; index < recruits; index++) {
            Zombie recruit = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
            int row = index / 3;
            double x = arena.centerX() + (team == 1 ? -1 : 1) * (5.0 + row * 1.4);
            double z = arena.centerZ() + (index % 3 - 1) * 2.0;
            recruit.moveTo(x, arena.floorY() + 1.0, z, team == 1 ? 0.0F : 180.0F, 0.0F);
            recruit.setNoAi(true);
            recruit.setCustomName(TextUtil.color(team == 1 ? "&6金队新兵" : "&b青队新兵"));
            level.addFreshEntity(recruit);
            classicMobs.add(recruit);
            series200RecruitTeams.put(recruit.getUUID(), team);
         }
      }
   }

   private void resolveRecruitSkirmish() {
      int first = recruitCount(1), second = recruitCount(2);
      if (first == 0 || second == 0) { finishTeam(first > 0 ? 1 : 2); return; }
      int losingTeam = first == second ? (ThreadLocalRandom.current().nextBoolean() ? 1 : 2) : (first > second ? 2 : 1);
      UUID casualty = series200RecruitTeams.entrySet().stream().filter(entry -> entry.getValue() == losingTeam).map(Map.Entry::getKey).findAny().orElse(null);
      if (casualty == null) return;
      Entity entity = level() == null ? null : level().getEntity(casualty);
      if (entity != null) entity.discard();
      series200RecruitTeams.remove(casualty);
      ctx.broadcast(room, "&7战场传来一声号角，" + (losingTeam == 1 ? "金队" : "青队") + "失去一名新兵。");
   }

   private int recruitCount(int team) {
      return (int) series200RecruitTeams.values().stream().filter(value -> value == team).count();
   }

   private void refillTeamAmmo(Item item, int max) {
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null || !fighter.alive || player.getInventory().countItem(item) >= max) continue;
         player.getInventory().add(new ItemStack(item));
      }
   }

   private void finishTeam(int team) {
      if (phase == Phase.ENDED) return;
      for (UUID uuid : teamMembers(team)) {
         Fighter fighter = fighters.get(uuid);
         if (fighter != null) fighter.score = Math.max(fighter.score, 1000);
      }
      UUID winner = teamMembers(team).stream().filter(uuid -> fighters.get(uuid) != null && fighters.get(uuid).alive).findFirst().orElse(teamMembers(team).get(0));
      ctx.broadcast(room, "&6" + (team == 1 ? "金队" : "青队") + " &a获胜！");
      finish(winner);
   }

   private void finishSeries200ByScore() {
      int first = series200TeamScore.getOrDefault(1, 0), second = series200TeamScore.getOrDefault(2, 0);
      if (first == second) { finish(null); return; }
      finishTeam(first > second ? 1 : 2);
   }

   private InteractionResult handleSeries300BlockUse(ServerPlayer player, Fighter fighter, BlockPos pos) {
      Integer action = series300Buttons.get(pos);
      if (action == null) return InteractionResult.FAIL;
      if (type == PartyGameType.GAME_THEORY) {
         int owner = action / 10, choice = action % 10;
         if (owner != seats.indexOf(player.getUUID())) return InteractionResult.FAIL;
         fighter.classicValue = choice;
         player.displayClientMessage(TextUtil.color("&e本轮选择：" + (choice == 1 ? "合作" : "背叛")), true);
      } else if (type == PartyGameType.PAC_CUBE && series300Roles.getOrDefault(player.getUUID(), 0) == 1) {
         player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         series300Buttons.remove(pos);
         award(player.getUUID(), 1);
         if (series300Buttons.isEmpty()) finishSeries300Role(1);
      } else if (type == PartyGameType.EGGCELLENCE) {
         fighter.classicValue++;
         BlockState now = player.level().getBlockState(pos);
         player.level().setBlock(pos, now.is(Blocks.WHITE_WOOL) ? Blocks.BROWN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState(), 2);
         if (fighter.classicValue >= 12) finish(player.getUUID());
      }
      return InteractionResult.FAIL;
   }

   private boolean tryBreakSeries300(ServerPlayer player, Fighter fighter, BlockPos pos, BlockState state) {
      if (type == PartyGameType.GOLD_RUSH) {
         if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.GILDED_BLACKSTONE)) {
            award(player.getUUID(), state.is(Blocks.GOLD_BLOCK) ? 6 : 2);
            player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         }
         return false;
      }
      if (type == PartyGameType.BLOCK_BUSTER && (state.is(Blocks.RED_CONCRETE) || state.is(Blocks.TNT))) {
         award(player.getUUID(), state.is(Blocks.TNT) ? 3 : 1);
         player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         return false;
      }
      return false;
   }

   private boolean handleSeries300Damage(ServerPlayer victim, DamageSource source) {
      ServerPlayer attacker = sourcePlayer(source);
      if (attacker == null || attacker.getUUID().equals(victim.getUUID())) return true;
      int victimRole = series300Roles.getOrDefault(victim.getUUID(), 0), attackerRole = series300Roles.getOrDefault(attacker.getUUID(), 0);
      if (victimRole == attackerRole) return true;
      return switch (type) {
         case HIDE_AND_SEEK -> series300Round < 300 || attackerRole != 1;
         case BOSS_BRAWL -> false;
         case PAC_CUBE -> { hitPacCube(victim, attacker); yield true; }
         case GHOST_HUNT -> attackerRole != 1;
         case SLIME_TIME, GHAST_BLAST, MOUSE_TRAP -> true;
         default -> true;
      };
   }

   private boolean handleSeries300Attack(ServerPlayer attacker, Entity target) {
      if (target instanceof ServerPlayer victim) {
         int victimRole = series300Roles.getOrDefault(victim.getUUID(), 0), attackerRole = series300Roles.getOrDefault(attacker.getUUID(), 0);
         if (victimRole == attackerRole) return true;
         return switch (type) {
            case HIDE_AND_SEEK -> series300Round < 300 || attackerRole != 1;
            case BOSS_BRAWL -> false;
            case PAC_CUBE -> { hitPacCube(victim, attacker); yield true; }
            case GHOST_HUNT -> attackerRole != 1;
            default -> true;
         };
      }
      if (type == PartyGameType.SLIME_TIME && target instanceof Slime slime && series300Roles.getOrDefault(attacker.getUUID(), 0) == 2) {
         pushClassicBall(attacker, slime, 1.1); return true;
      }
      if (type == PartyGameType.RAVAGER_RODEO && target instanceof Ravager ravager) {
         pushClassicBall(attacker, ravager, 0.5); return true;
      }
      return true;
   }

   private void hitPacCube(ServerPlayer victim, ServerPlayer attacker) {
      if (series300Roles.getOrDefault(victim.getUUID(), 0) != 1) return;
      int lives = series300Lives.merge(victim.getUUID(), -1, Integer::sum);
      if (lives <= 0) eliminate(victim, attacker.getUUID());
      else { Fighter fighter = fighters.get(victim.getUUID()); if (fighter != null) resetClassicPlayer(victim, fighter, seats.indexOf(victim.getUUID())); }
   }

   private void tickClassic300() {
      if (phase != Phase.RUNNING) return;
      series300Round++;
      for (int index = 0; index < seats.size(); index++) {
         ServerPlayer player = ctx.player(seats.get(index)); Fighter fighter = fighters.get(seats.get(index));
         if (player == null || fighter == null || !fighter.alive) continue;
         if (player.getY() < arena.floorY() - 3) {
            if (type == PartyGameType.TREETOP_HOP || type == PartyGameType.SLIME_TIME || type == PartyGameType.RAVAGER_RODEO) eliminate(player, null);
            else resetClassicPlayer(player, fighter, index);
         }
      }
      switch (type) {
         case HIDE_AND_SEEK -> tickHideAndSeek();
         case GAME_THEORY -> tickGameTheory();
         case BOSS_BRAWL, GHOST_HUNT -> tickSeries300Elimination();
         case PAC_CUBE -> tickPacCube();
         case TREETOP_HOP -> tickTreetopHop();
         case SLIME_TIME -> tickSlimeTime();
         case IN_THE_ZONE -> tickInTheZone();
         case GHAST_BLAST -> tickGhastBlast();
         case RAVAGER_RODEO -> tickRavagerRodeo();
         case MOUSE_TRAP -> tickMouseTrap();
         default -> { }
      }
   }

   private void tickHideAndSeek() {
      if (series300Round == 300) ctx.broadcast(room, "&c搜寻者已释放！");
      if (roleAlive(2) == 0) finishSeries300Role(1);
   }

   private void tickGameTheory() {
      if (series300Round % 100 != 0 || seats.stream().anyMatch(uuid -> fighters.get(uuid) != null && fighters.get(uuid).classicValue == 0)) return;
      for (Fighter fighter : fighters.values()) {
         int choice = fighter.classicValue;
         award(fighter.uuid, choice == 1 ? 2 : 3);
         fighter.classicValue = 0;
      }
      if (series300Round >= 600) finish(bestScore());
   }

   private void tickPacCube() {
      for (UUID ghost : seats) if (series300Roles.getOrDefault(ghost, 0) == 2) {
         ServerPlayer hunter = ctx.player(ghost);
         if (hunter == null) continue;
         for (UUID pac : seats) if (series300Roles.getOrDefault(pac, 0) == 1) {
            ServerPlayer runner = ctx.player(pac);
            if (runner != null && runner.position().distanceToSqr(hunter.position()) < 2.25) hitPacCube(runner, hunter);
         }
      }
   }

   private void tickTreetopHop() {
      for (UUID uuid : seats) { ServerPlayer player = ctx.player(uuid); if (player != null && player.getX() >= arena.maxX() - 8) { finish(uuid); return; } }
   }

   private void tickSlimeTime() {
      for (Mob mob : classicMobs) if (mob instanceof Slime slime && !slime.isRemoved()) for (UUID uuid : seats) if (series300Roles.getOrDefault(uuid, 0) == 1) {
         ServerPlayer runner = ctx.player(uuid); if (runner != null && runner.position().distanceToSqr(slime.position()) < 9.0) { finishSeries300Role(2); return; }
      }
   }

   private void tickInTheZone() {
      int gold = 0, aqua = 0;
      for (UUID uuid : seats) { ServerPlayer player = ctx.player(uuid); if (player == null || !fighters.get(uuid).alive || Math.abs(player.getX() - arena.centerX()) > 4 || Math.abs(player.getZ() - arena.centerZ()) > 4) continue; if (teamOf(uuid) == 1) gold++; else aqua++; }
      series300Capture += Integer.compare(gold, aqua);
      if (series300Capture >= 160) finishTeam(1); else if (series300Capture <= -160) finishTeam(2);
   }

   private void tickGhastBlast() {
      for (Mob mob : classicMobs) if (mob instanceof Ghast ghast && !ghast.isRemoved()) for (UUID uuid : seats) if (series300Roles.getOrDefault(uuid, 0) == 1) {
         ServerPlayer player = ctx.player(uuid); if (player != null && player.position().distanceToSqr(ghast.position()) < 9.0) { finishSeries300Role(2); return; }
      }
   }

   private void tickRavagerRodeo() {
      if (series300Round % 30 != 0) return;
      for (Mob mob : classicMobs) if (mob instanceof Ravager ravager && !ravager.isRemoved()) for (UUID uuid : seats) {
         ServerPlayer player = ctx.player(uuid); if (player != null && player.position().distanceToSqr(ravager.position()) < 16.0) { player.push(player.getX() - ravager.getX(), 0.55, player.getZ() - ravager.getZ()); player.hurtMarked = true; }
      }
   }

   private void tickMouseTrap() {
      if (roleAlive(1) == 0) finishSeries300Role(2);
   }

   private int roleAlive(int role) { return (int) seats.stream().filter(uuid -> series300Roles.getOrDefault(uuid, 0) == role && fighters.get(uuid) != null && fighters.get(uuid).alive).count(); }
   private void tickSeries300Elimination() { if (roleAlive(1) == 0) finishSeries300Role(2); else if (roleAlive(2) == 0) finishSeries300Role(1); }
   private void finishSeries300Role(int role) {
      UUID winner = seats.stream().filter(uuid -> series300Roles.getOrDefault(uuid, 0) == role && fighters.get(uuid) != null && fighters.get(uuid).alive).findFirst().orElse(null);
      finish(winner);
   }
   private void finishSeries300ByRule() {
      if (type == PartyGameType.HIDE_AND_SEEK) finishSeries300Role(2);
      else if (type == PartyGameType.SLIME_TIME || type == PartyGameType.RAVAGER_RODEO || type == PartyGameType.MOUSE_TRAP || type == PartyGameType.GHAST_BLAST) finishSeries300Role(1);
      else finishByMode();
   }

   private int ownedCells(int owner) {
      int count = 0;
      for (int cell : classicCells) if (cell == owner) count++;
      return count;
   }

   private void convertMinion(ServerPlayer player, Mob minion) {
      UUID old = minionOwners.put(minion.getUUID(), player.getUUID());
      if (old != null && !old.equals(player.getUUID())) award(old, -1);
      if (!player.getUUID().equals(old)) award(player.getUUID(), 1);
      minion.setCustomName(TextUtil.color("&a" + ctx.name(player.getUUID()) + " 的随从"));
      player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.45F);
   }

   private void summonMinion(ServerPlayer player, Fighter fighter) {
      ServerLevel level = level();
      if (level == null) return;
      Zombie minion = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
      Vec3 spawn = player.position();
      minion.moveTo(spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
      minion.setNoAi(true);
      level.addFreshEntity(minion);
      classicMobs.add(minion);
      convertMinion(player, minion);
   }

   private void pushClassicBall(ServerPlayer player, Entity ball, double strength) {
      Vec3 direction = player.getLookAngle();
      ball.push(direction.x * strength, Math.max(0.18, direction.y * strength + 0.24), direction.z * strength);
      ball.hurtMarked = true;
   }

   private void tickPlayers() {
      for (Fighter fighter : List.copyOf(fighters.values())) {
         if (!fighter.alive) continue;
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player == null) { fighter.alive = false; continue; }
         if (openingGraceTicks > 0 && player.getY() < arena.floorY() - 1) {
            arena.teleport(player, level(), spawnFor(seats.indexOf(fighter.uuid)));
            player.setDeltaMovement(Vec3.ZERO);
            continue;
         }
         if (type == PartyGameType.MOB_SHOOTER) {
            keepOnShootingStand(player, seats.indexOf(fighter.uuid));
            refillShooterArrows(player);
         }
         if (type == PartyGameType.DROPPER) {
            if (player.isInWater()) {
               fighter.dropperStage++;
               fighter.progress = fighter.dropperStage;
               if (fighter.dropperStage >= DROPPER_STAGES) { finish(player.getUUID()); return; }
               arena.teleport(player, level(), arena.spawn(seats.indexOf(fighter.uuid), seats.size()));
               player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.35F);
               player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&b第 " + (fighter.dropperStage + 1) + "/" + DROPPER_STAGES + " 关")));
               continue;
            }
            if (player.getY() <= arena.floorY() + 0.2) { fighter.attempts++; arena.teleport(player, level(), arena.spawn(seats.indexOf(fighter.uuid), seats.size())); }
            continue;
         }
         if (type == PartyGameType.HORSE_RACE || type == PartyGameType.MINE_FIELD) {
            if (type == PartyGameType.MINE_FIELD && arena.mineAt(player.blockPosition(), seats.indexOf(fighter.uuid))) {
               fighter.attempts++;
               // Keep the discovered mine visible after the runner respawns.
               // The arena is rebuilt when released, so these markers never leak into a later match.
               markMine(player.blockPosition(), seats.indexOf(fighter.uuid));
               arena.teleport(player, level(), arena.spawn(seats.indexOf(fighter.uuid), seats.size()));
               player.setHealth(player.getMaxHealth());
               player.invulnerableTime = 20;
               ctx.send(player, "&c踩到地雷！&7 已回到起点（可无限复活）。");
               continue;
            }
            Vec3 start = arena.spawn(seats.indexOf(fighter.uuid), seats.size());
            fighter.progress = Math.max(fighter.progress, Math.max(0, (int) Math.floor(player.getX() - start.x)));
            if (type == PartyGameType.MINE_FIELD && player.getX() >= arena.centerX() + arena.size() / 2.0 - 2) { finish(player.getUUID()); return; }
            if (type == PartyGameType.HORSE_RACE && tickHorseFinish(player, fighter)) return;
         }
         if (type == PartyGameType.DIG_DOWN) {
            fighter.progress = Math.max(fighter.progress, Math.max(0, arena.topY() - (int) Math.floor(player.getY())));
            if (!fighter.digFinished && player.getY() <= arena.floorY() + 0.1) finishDigDown(player, fighter);
         }
         if (type == PartyGameType.CRAFTING_MASTER) tickCrafting(player, fighter);
         if (type == PartyGameType.VOLCANO) heatBlock(player.blockPosition().below());
         if (type == PartyGameType.TNT_RUN) delayedAir.putIfAbsent(player.blockPosition().below(), 10);
         if (!isPartyCatalogue() && openingGraceTicks <= 0 && player.getY() < arena.floorY() - 1) eliminate(player, null);
      }
      if (!isPartyCatalogue() && type.mode() == PartyGameType.Mode.ELIMINATION) checkEliminationWin();
      if (type == PartyGameType.DIG_DOWN && allDigDownFinished()) finish(bestDigDown());
   }

   /** A finish counts only once after crossing the line from its own start side. */
   private boolean tickHorseFinish(ServerPlayer player, Fighter fighter) {
      if (fighter.horse == null || player.getVehicle() != fighter.horse) return false;
      double x = fighter.horse.getX();
      if (fighter.horseFinishCooldown > 0) fighter.horseFinishCooldown--;
      boolean crossed = fighter.horseLastX < arena.horseFinishX() && x >= arena.horseFinishX();
      fighter.horseLastX = x;
      if (!crossed || fighter.horseFinishCooldown > 0) return false;
      fighter.horseLaps++;
      fighter.progress = fighter.horseLaps;
      fighter.horseFinishCooldown = HORSE_FINISH_COOLDOWN_TICKS;
      if (fighter.horseLaps >= HORSE_LAPS) { finish(player.getUUID()); return true; }
      resetHorseLap(player, fighter);
      return false;
   }

   private void finishDigDown(ServerPlayer player, Fighter fighter) {
      fighter.digFinished = true;
      fighter.finishedTick = ticks;
      player.setGameMode(GameType.SPECTATOR);
      player.setDeltaMovement(Vec3.ZERO);
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.35F);
      ctx.broadcast(room, "&a" + ctx.name(fighter.uuid) + " &7已通关挖挖挖！");
   }

   private boolean allDigDownFinished() { return fighters.values().stream().allMatch(fighter -> fighter.digFinished); }

   /** A shooter may aim and rotate freely, but cannot leave their personal 3x3 firing platform. */
   private void keepOnShootingStand(ServerPlayer player, int index) {
      Vec3 stand = arena.spawn(index, seats.size());
      boolean outside = Math.abs(player.getX() - stand.x) > 1.25
         || Math.abs(player.getZ() - stand.z) > 1.25
         || player.getY() < stand.y - 0.25 || player.getY() > stand.y + 1.4;
      if (!outside) return;
      player.teleportTo(level(), stand.x, stand.y, stand.z, player.getYRot(), player.getXRot());
      player.setDeltaMovement(Vec3.ZERO);
      player.hurtMarked = true;
   }

   private void resetHorseLap(ServerPlayer player, Fighter fighter) {
      if (fighter.horse == null) return;
      Vec3 spawn = horseStable(fighter.horseIndex);
      fighter.horse.teleportTo(spawn.x, spawn.y, spawn.z);
      fighter.horse.setDeltaMovement(Vec3.ZERO);
      fighter.horseLastX = spawn.x;
      fighter.horseFinishCooldown = HORSE_FINISH_COOLDOWN_TICKS;
      if (player.getVehicle() != fighter.horse) player.startRiding(fighter.horse, true);
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.4F);
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color("&6第 " + (fighter.horseLaps + 1) + "/" + HORSE_LAPS + " 段")));
      ctx.send(player, "&a完成第 " + fighter.horseLaps + " 段！&7 还剩 " + (HORSE_LAPS - fighter.horseLaps) + " 段。");
   }

   private void tickCrafting(ServerPlayer player, Fighter fighter) {
      if (fighter.craftingTarget == null) return;
      int current = player.getInventory().countItem(fighter.craftingTarget);
      if (current <= fighter.craftingBaseline) return;
      int crafted = current - fighter.craftingBaseline;
      award(fighter.uuid, crafted);
      fighter.craftingBaseline = current;
      player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.35F);
      player.displayClientMessage(TextUtil.color("&a合成成功！&e+" + crafted + " 分 &7下一件配方已发放"), true);
      assignCraftingRecipe(player, fighter);
   }

   private void assignCraftingRecipe(ServerPlayer player, Fighter fighter) {
      CraftRecipe recipe = randomCraftRecipe(fighter.craftingTarget);
      fighter.craftingTarget = recipe.output();
      for (ItemStack ingredient : recipe.ingredients()) player.getInventory().add(ingredient.copy());
      fighter.craftingBaseline = player.getInventory().countItem(recipe.output());
      ItemStack guide = new ItemStack(Items.PAPER);
      guide.set(DataComponents.CUSTOM_NAME, TextUtil.color("&e当前配方：&f" + recipe.name()));
      player.getInventory().setItem(8, guide);
      ctx.send(player, "&e当前配方：&f" + recipe.name());
   }

   private CraftRecipe randomCraftRecipe(Item previous) {
      List<CraftRecipe> recipes = List.of(
         new CraftRecipe(Items.STICK, "木板 ×2 → 木棍", List.of(new ItemStack(Items.OAK_PLANKS, 2))),
         new CraftRecipe(Items.CRAFTING_TABLE, "木板 ×4 → 工作台", List.of(new ItemStack(Items.OAK_PLANKS, 4))),
         new CraftRecipe(Items.CHEST, "木板 ×8 → 箱子", List.of(new ItemStack(Items.OAK_PLANKS, 8))),
         new CraftRecipe(Items.TORCH, "煤炭 + 木棍 → 火把", List.of(new ItemStack(Items.COAL), new ItemStack(Items.STICK))),
         new CraftRecipe(Items.BOWL, "木板 ×3 → 碗", List.of(new ItemStack(Items.OAK_PLANKS, 3))),
         new CraftRecipe(Items.FURNACE, "圆石 ×8 → 熔炉", List.of(new ItemStack(Items.COBBLESTONE, 8)))
      );
      List<CraftRecipe> candidates = recipes.stream().filter(recipe -> recipe.output() != previous).toList();
      return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   private void tickDelayedBlocks() {
      for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(delayedAir.entrySet())) {
         int left = entry.getValue() - 1;
         if (left <= 0) { level().setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 2); delayedAir.remove(entry.getKey()); }
         else delayedAir.put(entry.getKey(), left);
      }
   }

   private void tickOreRespawns() {
      for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(oreRespawns.entrySet())) {
         int left = entry.getValue() - 1;
         if (left <= 0) {
            level().setBlock(entry.getKey(), randomOre(), 2);
            oreRespawns.remove(entry.getKey());
         } else oreRespawns.put(entry.getKey(), left);
      }
   }

   private void refillShooterArrows(ServerPlayer player) {
      ItemStack slot = player.getInventory().getItem(1);
      if (!slot.is(Items.ARROW) || slot.getCount() < 64) player.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
   }

   private BlockState randomOre() {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll < 38) return Blocks.COAL_ORE.defaultBlockState();
      if (roll < 64) return Blocks.IRON_ORE.defaultBlockState();
      if (roll < 79) return Blocks.REDSTONE_ORE.defaultBlockState();
      if (roll < 92) return Blocks.GOLD_ORE.defaultBlockState();
      if (roll < 98) return Blocks.DIAMOND_ORE.defaultBlockState();
      return Blocks.EMERALD_ORE.defaultBlockState();
   }

   private void heatBlock(BlockPos pos) {
      if (!arena.inPlay(pos)) return;
      BlockState state = level().getBlockState(pos);
      if (state.is(Blocks.YELLOW_CONCRETE)) { level().setBlock(pos, Blocks.ORANGE_CONCRETE.defaultBlockState(), 2); delayedAir.put(pos, 24); }
      else if (state.is(Blocks.ORANGE_CONCRETE)) { level().setBlock(pos, Blocks.RED_CONCRETE.defaultBlockState(), 2); delayedAir.put(pos, 12); }
   }

   private void tickColorful() {
      if (colorWarmupTicks > 0) {
         colorWarmupTicks--;
         if (colorWarmupTicks == 0) ctx.broadcast(room, "&a颜色锁定！&7 站到手中方块对应的区域。");
         return;
      }
      colorTicks++;
      if (colorTicks >= colorIntervalTicks) {
         for (Fighter fighter : fighters.values()) {
            if (!fighter.alive) continue;
            ServerPlayer player = ctx.player(fighter.uuid);
            if (player != null && !player.level().getBlockState(player.blockPosition().below()).equals(safeColor)) eliminate(player, null);
         }
         if (phase != Phase.ENDED) startColorRound();
      }
   }

   /** Give the colour only after the normal ready countdown, then allow a short reaction window. */
   private void startColorRound() {
      colorRound++;
      colorTicks = 0;
      colorIntervalTicks = Math.max(20, 120 - (colorRound - 1) * 10);
      colorWarmupTicks = 40;
      arena.refreshColorRun(level(), template.seed() ^ (long) colorRound * 0x9E3779B97F4A7C15L);
      chooseColor();
      ctx.broadcast(room, "&e新样式已刷新！&7 " + (colorIntervalTicks / 20.0F) + " 秒后判定。" );
   }

   private void chooseColor() {
      int n = ThreadLocalRandom.current().nextInt(6);
      safeColor = switch (n) { case 0 -> Blocks.RED_WOOL.defaultBlockState(); case 1 -> Blocks.BLUE_WOOL.defaultBlockState(); case 2 -> Blocks.GREEN_WOOL.defaultBlockState(); case 3 -> Blocks.YELLOW_WOOL.defaultBlockState(); case 4 -> Blocks.PURPLE_WOOL.defaultBlockState(); default -> Blocks.ORANGE_WOOL.defaultBlockState(); };
      Item item = safeColor.getBlock().asItem();
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null && fighter.alive) player.getInventory().setItem(8, new ItemStack(item));
      }
   }

   private void spawnTarget() {
      ServerLevel level = level();
      if (level == null) return;
      double x = arena.centerX() + ThreadLocalRandom.current().nextDouble(-18, 18);
      double z = arena.centerZ() + ThreadLocalRandom.current().nextDouble(-18, 18);
      Mob mob = switch (type) {
         case PUNCH_THE_BAT -> new Bat(net.minecraft.world.entity.EntityType.BAT, level);
         case ANIMAL_SLAUGHTER -> switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> new Chicken(net.minecraft.world.entity.EntityType.CHICKEN, level);
            case 1 -> new Pig(net.minecraft.world.entity.EntityType.PIG, level);
            case 2 -> new Sheep(net.minecraft.world.entity.EntityType.SHEEP, level);
            default -> new Cow(net.minecraft.world.entity.EntityType.COW, level);
         };
         case MOB_SHOOTER -> shooterTarget(level);
         default -> null;
      };
      if (mob != null) {
         if (type == PartyGameType.MOB_SHOOTER) {
            Vec3 target = arena.shooterTarget(ThreadLocalRandom.current());
            x = target.x; z = target.z;
            mob.setNoAi(true);
            mob.setNoGravity(true);
         }
         double y = type == PartyGameType.PUNCH_THE_BAT
            ? arena.floorY() + ThreadLocalRandom.current().nextDouble(2.5, 6.0)
            : type == PartyGameType.MOB_SHOOTER ? arena.shooterTarget(ThreadLocalRandom.current()).y : arena.floorY() + 1.1;
         mob.moveTo(x, y, z, 180.0F, 0.0F);
         level.addFreshEntity(mob);
         if (type == PartyGameType.MOB_SHOOTER) shooterTargets.add(mob);
         if (type == PartyGameType.PUNCH_THE_BAT) bats.add(mob);
      }
   }

   /** Smaller targets pay more because they are harder to hit on the target screen. */
   private Mob shooterTarget(ServerLevel level) {
      return switch (ThreadLocalRandom.current().nextInt(7)) {
         case 0 -> new Endermite(net.minecraft.world.entity.EntityType.ENDERMITE, level);
         case 1 -> new Rabbit(net.minecraft.world.entity.EntityType.RABBIT, level);
         case 2 -> new Chicken(net.minecraft.world.entity.EntityType.CHICKEN, level);
         case 3 -> new Bat(net.minecraft.world.entity.EntityType.BAT, level);
         case 4 -> new Turtle(net.minecraft.world.entity.EntityType.TURTLE, level);
         case 5 -> new Pig(net.minecraft.world.entity.EntityType.PIG, level);
         default -> new Cow(net.minecraft.world.entity.EntityType.COW, level);
      };
   }

   private void tickBatTargets() {
      bats.removeIf(bat -> {
         if (bat.isRemoved()) return true;
         if (arena.contains(bat.getX(), bat.getY(), bat.getZ())) return false;
         bat.discard();
         return true;
      });
      if (ticks % 20 != 0) return;
      int wanted = Math.min(48, Math.max(12, alive().size() * 4));
      int spawn = Math.min(wanted - bats.size(), Math.max(3, alive().size() * 2));
      for (int i = 0; i < spawn; i++) spawnTarget();
   }

   private void tickShooterTargets() {
      shooterTargets.removeIf(Mob::isRemoved);
      if (ticks % 10 != 0 && !shooterTargets.isEmpty()) return;
      int wanted = Math.min(36, Math.max(10, alive().size() * 4));
      int spawn = shooterTargets.isEmpty() ? wanted : Math.min(wanted - shooterTargets.size(), Math.max(3, alive().size()));
      for (int i = 0; i < spawn; i++) spawnTarget();
   }

   private void explodePotato() {
      UUID holder = potatoHolder;
      if (holder != null) {
         ServerPlayer player = ctx.player(holder);
         if (player != null) eliminate(player, null);
         else { Fighter fighter = fighters.get(holder); if (fighter != null) fighter.alive = false; }
      }
      givePotato(randomAlive());
   }

   private void transferPotato(ServerPlayer target) { givePotato(target == null ? null : target.getUUID()); }
   private void givePotato(UUID uuid) {
      potatoHolder = uuid;
      for (Fighter fighter : fighters.values()) {
         ServerPlayer player = ctx.player(fighter.uuid);
         if (player != null) player.getInventory().removeItem(new ItemStack(Items.BAKED_POTATO));
      }
      ServerPlayer holder = uuid == null ? null : ctx.player(uuid);
      if (holder != null) holder.getInventory().setItem(4, new ItemStack(Items.BAKED_POTATO));
   }

   private UUID randomAlive() {
      List<UUID> alive = new ArrayList<>();
      for (Fighter fighter : fighters.values()) if (fighter.alive) alive.add(fighter.uuid);
      return alive.isEmpty() ? null : alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
   }

   private void eliminate(ServerPlayer player, UUID killer) { eliminate(player, killer, false); }

   private void eliminate(ServerPlayer player, UUID killer, boolean awardArrow) {
      Fighter fighter = fighters.get(player.getUUID());
      if (fighter == null || !fighter.alive) return;
      fighter.alive = false;
      fighter.eliminatedTick = ticks;
      player.getInventory().clearContent();
      player.setGameMode(GameType.SPECTATOR);
      player.teleportTo(level(), arena.centerX() + 0.5, arena.floorY() + 12.0, arena.centerZ() + 0.5, 0, 0);
      if (type == PartyGameType.ONE_IN_CHAMBER && awardArrow && killer != null) {
         ServerPlayer attacker = ctx.player(killer);
         if (attacker != null) attacker.getInventory().add(new ItemStack(Items.ARROW));
      }
      ctx.broadcast(room, "&c" + ctx.name(player.getUUID()) + " 出局了。");
   }

   private void push(ServerPlayer source, ServerPlayer target, double strength) {
      Vec3 delta = target.position().subtract(source.position());
      double length = Math.max(0.01, delta.horizontalDistance());
      target.push(delta.x / length * strength, 0.42, delta.z / length * strength);
      target.hurtMarked = true;
   }

   private void award(UUID uuid, int points) {
      Fighter fighter = fighters.get(uuid);
      if (fighter != null && fighter.alive) { fighter.score += points; fighter.reachedScoreTick = Math.min(fighter.reachedScoreTick, ticks); }
   }

   private void announceShooterScore(UUID uuid, Entity target) {
      int points = shooterScore(target);
      award(uuid, points);
      Fighter fighter = fighters.get(uuid);
      ctx.broadcast(room, "&6✦ &f" + ctx.name(uuid) + " &7命中 " + animalName(target)
         + " &e+" + points + " 分 &8| &7总分 &f" + (fighter == null ? 0 : fighter.score));
      pushHud();
   }

   private void playShooterKillEffects(Entity target) {
      ServerLevel level = level();
      if (level == null) return;
      double x = target.getX(), y = target.getY() + 0.8, z = target.getZ();
      level.sendParticles(ParticleTypes.CRIT, x, y, z, 18, 0.35, 0.45, 0.35, 0.18);
      level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 10, 0.25, 0.35, 0.25, 0.04);
      level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.85F, 1.25F);
   }

   private void playBatHitEffects(Bat bat) {
      ServerLevel level = level();
      if (level == null) return;
      level.sendParticles(ParticleTypes.CRIT, bat.getX(), bat.getY() + 0.4, bat.getZ(), 10, 0.22, 0.22, 0.22, 0.12);
      level.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.5F);
   }

   private void markMine(BlockPos playerPos, int playerIndex) {
      ServerLevel level = level();
      if (level == null) return;
      BlockPos mine = new BlockPos(playerPos.getX(), arena.floorY(), playerPos.getZ());
      level.setBlockAndUpdate(mine, Blocks.RED_CONCRETE.defaultBlockState());
      BlockPos signPos = mine.above();
      level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
      if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
         int neighbours = arena.nearbyMines(mine, playerIndex);
         sign.updateText(text -> text
            .setMessage(0, Component.literal("§c地雷"))
            .setMessage(1, Component.literal("§e附近: " + neighbours)), true);
         sign.setWaxed(true);
      }
   }

   private void finishByMode() {
      if (type.mode() == PartyGameType.Mode.SCORE) finish(bestScore());
      else if (type == PartyGameType.MINE_FIELD) finish(bestProgress());
      else if (type == PartyGameType.DIG_DOWN) finish(bestDigDown());
      else finish(bestAlive());
   }

   private void checkEliminationWin() { if (alive().size() <= 1) finish(alive().isEmpty() ? null : alive().get(0).uuid); }
   private List<Fighter> alive() { return fighters.values().stream().filter(f -> f.alive).toList(); }
   private UUID bestAlive() { return alive().isEmpty() ? null : alive().get(0).uuid; }
   private UUID bestProgress() { return fighters.values().stream().max(Comparator.comparingInt(f -> f.progress)).map(f -> f.uuid).orElse(null); }
   private UUID bestDigDown() {
      return fighters.values().stream().max(Comparator.comparingInt((Fighter f) -> f.finishedTick)
         .thenComparing(Comparator.comparingInt((Fighter f) -> f.progress))).map(f -> f.uuid).orElse(null);
   }
   private UUID bestScore() { return fighters.values().stream().max(Comparator.comparingInt((Fighter f) -> f.score).thenComparingInt(f -> f.reachedScoreTick)).map(f -> f.uuid).orElse(null); }

   private List<Fighter> rankedFighters() {
      if (type.mode() == PartyGameType.Mode.SCORE) {
         return fighters.values().stream()
            .sorted(Comparator.comparingInt((Fighter f) -> f.score).reversed().thenComparingInt(f -> f.reachedScoreTick))
            .toList();
      }
      if (type.mode() == PartyGameType.Mode.RACE) {
         return fighters.values().stream()
            .sorted(Comparator.comparingInt((Fighter f) -> f.finishedTick).reversed()
               .thenComparing(Comparator.comparingInt((Fighter f) -> f.progress).reversed())
               .thenComparingInt(f -> f.attempts))
            .toList();
      }
      return fighters.values().stream()
         .sorted(Comparator.comparingInt((Fighter f) -> f.alive ? 1 : 0).reversed()
            // Ticks count down: a smaller elimination tick means the player survived longer.
            .thenComparingInt(f -> f.eliminatedTick))
         .toList();
   }

   private String rankingValue(Fighter fighter) {
      if (type.mode() == PartyGameType.Mode.SCORE) return "&e" + fighter.score + " 分";
      if (type.mode() == PartyGameType.Mode.RACE) {
         if (fighter.finishedTick >= 0) return "&a完成";
         if (type == PartyGameType.HORSE_RACE) return "&e" + fighter.horseLaps + "/" + HORSE_LAPS + " 段";
         if (type == PartyGameType.DIG_DOWN) return "&e" + fighter.progress + " 格";
         if (type == PartyGameType.DROPPER) return "&b" + fighter.dropperStage + "/" + DROPPER_STAGES + " 关";
         return "&e" + fighter.progress + " 格";
      }
      return fighter.alive ? "&a存活" : "&c淘汰";
   }

   private void announceResults() {
      ctx.broadcast(room, "&6" + type.displayName() + "结束 &8| &e最终排行");
      int place = 1;
      for (Fighter fighter : rankedFighters()) {
         if (place > 8) break;
         ctx.broadcast(room, "&7" + place++ + ". &f" + ctx.name(fighter.uuid) + " &8- " + rankingValue(fighter));
      }
   }

   private void finish(UUID winner) {
      if (phase == Phase.ENDED) return;
      phase = Phase.ENDED;
      if (winner != null && type != PartyGameType.DIG_DOWN && fighters.containsKey(winner)) fighters.get(winner).finishedTick = ticks;
      announceResults();
      for (Mob target : shooterTargets) target.discard();
      shooterTargets.clear();
      for (Mob mob : classicMobs) if (!mob.isRemoved()) mob.discard();
      classicMobs.clear();
      classicPigs.clear();
      classicButtons.clear();
      series200Buttons.clear();
      series200Flags.clear();
      series200CarriedFlags.clear();
      series200Choices.clear();
      series200ChickenTeams.clear();
      series200RecruitTeams.clear();
      teamHockeyPucks.clear();
      series300Buttons.clear();
      series300Roles.clear();
      series300Lives.clear();
      series300MobTeams.clear();
      for (Horse horse : horses) horse.discard();
      horses.clear();
      board.removeAll();
      for (UUID uuid : seats) { ServerPlayer player = ctx.player(uuid); if (player != null) restore(player); }
      ctx.partyGames().arenas().release(arena);
      ctx.rooms().onMatchEnded(id);
      ctx.partyGames().remove(this);
   }

   private void restore(ServerPlayer player) { board.remove(player); Saved value = saved.remove(player.getUUID()); if (value != null) value.apply(player, ctx); }
   private ServerLevel level() { return ctx.partyGames().arenas().level(); }
   private BlockState ownColor(UUID uuid) { return switch (Math.floorMod(uuid.hashCode(), 6)) { case 0 -> Blocks.RED_WOOL.defaultBlockState(); case 1 -> Blocks.BLUE_WOOL.defaultBlockState(); case 2 -> Blocks.GREEN_WOOL.defaultBlockState(); case 3 -> Blocks.YELLOW_WOOL.defaultBlockState(); case 4 -> Blocks.PURPLE_WOOL.defaultBlockState(); default -> Blocks.ORANGE_WOOL.defaultBlockState(); }; }
   private int oreScore(Block block) { if (block == Blocks.DIAMOND_ORE || block == Blocks.EMERALD_ORE) return 8; if (block == Blocks.GOLD_ORE) return 5; if (block == Blocks.IRON_ORE) return 3; if (block == Blocks.REDSTONE_ORE) return 4; return 1; }
   private boolean isOre(Block block) { return block == Blocks.COAL_ORE || block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE || block == Blocks.REDSTONE_ORE || block == Blocks.DIAMOND_ORE || block == Blocks.EMERALD_ORE; }
   private int animalScore(Entity entity) { if (entity instanceof Cow) return 4; if (entity instanceof Pig || entity instanceof Sheep) return 3; if (entity instanceof Chicken) return 1; return 1; }
   private int shooterScore(Entity entity) {
      if (entity instanceof Endermite) return 8;
      if (entity instanceof Rabbit) return 7;
      if (entity instanceof Chicken) return 6;
      if (entity instanceof Bat) return 5;
      if (entity instanceof Turtle) return 4;
      if (entity instanceof Pig) return 3;
      if (entity instanceof Sheep) return 2;
      if (entity instanceof Cow) return 1;
      return 1;
   }
   private String animalName(Entity entity) {
      if (entity instanceof Endermite) return "&5末影螨";
      if (entity instanceof Rabbit) return "&f兔子";
      if (entity instanceof Chicken) return "&e小鸡";
      if (entity instanceof Bat) return "&7蝙蝠";
      if (entity instanceof Turtle) return "&a海龟";
      if (entity instanceof Cow) return "&f奶牛";
      if (entity instanceof Pig) return "&d猪";
      if (entity instanceof Sheep) return "&f绵羊";
      return "&f动物";
   }

   private void giveKit(ServerPlayer player) {
      Inventory inv = player.getInventory();
      switch (type) {
         case MINIONS -> inv.setItem(0, new ItemStack(Items.WOODEN_SWORD));
         case GLADIATOR_FIGHT -> {
            inv.setItem(0, new ItemStack(Items.IRON_SWORD));
            inv.setItem(1, new ItemStack(Items.SHIELD));
            inv.setItem(2, new ItemStack(Items.COOKED_BEEF, 4));
         }
         case GO_FISH -> inv.setItem(0, new ItemStack(Items.FISHING_ROD));
         case BRIDGE_CROSSING -> {
            inv.setItem(0, new ItemStack(Items.STONE_SWORD));
            inv.setItem(1, new ItemStack(Items.BOW));
            inv.setItem(2, new ItemStack(Items.ARROW, 16));
            inv.setItem(3, new ItemStack(Items.OAK_PLANKS, 32));
         }
         case CANNONEERS -> {
            inv.setItem(0, new ItemStack(Items.BOW));
            inv.setItem(1, new ItemStack(Items.ARROW, 24));
         }
         case RPSC -> inv.setItem(0, new ItemStack(Items.STONE_SWORD));
         case TANKS -> { inv.setItem(0, new ItemStack(Items.BOW)); inv.setItem(1, new ItemStack(Items.ARROW, 48)); }
         case CAPTURE_THE_FLAG -> { inv.setItem(0, new ItemStack(Items.STONE_SWORD)); inv.setItem(1, new ItemStack(Items.BOW)); inv.setItem(2, new ItemStack(Items.ARROW, 16)); }
         case MINE_YOUR_BUSINESS -> { inv.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); inv.setItem(1, new ItemStack(Items.STONE_SWORD)); }
         case BOMBS_AWAY -> inv.setItem(0, new ItemStack(Items.SNOWBALL, 5));
         case SNOW_WARS -> inv.setItem(0, new ItemStack(Items.SNOWBALL, 6));
         case WHAT_THE_CLUCK -> inv.setItem(0, new ItemStack(Items.EGG, 10));
         case RECRUITMENT_ROYALE -> inv.setItem(0, new ItemStack(Items.WOODEN_SWORD));
         case HIDE_AND_SEEK -> { if (series300Roles.getOrDefault(player.getUUID(), 0) == 1) inv.setItem(0, new ItemStack(Items.IRON_SWORD)); }
         case BOSS_BRAWL -> inv.setItem(0, new ItemStack(series300Roles.getOrDefault(player.getUUID(), 0) == 1 ? Items.DIAMOND_AXE : Items.IRON_SWORD));
         case GOLD_RUSH -> inv.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
         case BLOCK_BUSTER -> inv.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
         case GHOST_HUNT -> inv.setItem(0, new ItemStack(series300Roles.getOrDefault(player.getUUID(), 0) == 1 ? Items.IRON_SWORD : Items.WOODEN_SWORD));
         case GHAST_BLAST -> inv.setItem(0, new ItemStack(Items.IRON_SWORD));
         case MOUSE_TRAP -> inv.setItem(0, new ItemStack(series300Roles.getOrDefault(player.getUUID(), 0) == 1 ? Items.STONE_AXE : Items.EGG, series300Roles.getOrDefault(player.getUUID(), 0) == 1 ? 1 : 8));
         case ONE_IN_CHAMBER -> { inv.setItem(0, new ItemStack(Items.BOW)); inv.setItem(1, new ItemStack(Items.ARROW)); inv.setItem(2, new ItemStack(Items.WOODEN_SWORD)); }
         case SUMO -> inv.setItem(0, new ItemStack(Items.STICK));
         case PUNCH_THE_BAT -> inv.setItem(0, new ItemStack(Items.AIR));
         case ORE_MINER -> inv.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
         case MOB_SHOOTER -> { inv.setItem(0, new ItemStack(Items.BOW)); inv.setItem(1, new ItemStack(Items.ARROW, 64)); }
         case HOE_HOE_HOE -> inv.setItem(0, new ItemStack(Items.DIAMOND_HOE));
         case CRAFTING_MASTER -> inv.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
         default -> { }
      }
   }

   private String rules() {
      return switch (type) {
         case MINIONS -> "&7攻击中立随从，把它转化为自己的军团；时间结束时随从最多者获胜。";
         case RING_IN_THE_RING -> "&7敲响自己的钟得 1 分，同时让对手失去 1 分；分差达到目标即获胜。";
         case GLADIATOR_FIGHT -> "&7使用铁剑与盾牌击败对手，最后存活者获胜。";
         case TURTLE_HOCKEY -> "&7攻击乌龟冰球，把它推进对手一侧的球门。";
         case GO_FISH -> "&7在中央水池钓鱼，最先钓到任意鱼类者获胜。";
         case DONT_PUSH_MY_BUTTONS -> "&7按自己一侧的按钮占领中间九宫格；随着时间推进，胜利所需方块数会降低。";
         case BRIDGE_CROSSING -> "&7将对手从桥上击落并越过战桥；先抵达对方终点两次者获胜。";
         case PIG_PUSHERS -> "&7用攻击推动自己的猪穿过赛道，先推进猪圈者获胜。";
         case BALANCE_BEAM -> "&7沿独木桥前进；侧向重力每 10 秒反转，先到终点者获胜。";
         case BUTTON_SEARCH -> "&7在场地中寻找对手藏下的按钮，抢先按下对方按钮即可获胜。";
         case BETRIS -> "&7按下与当前方块节奏相符的紫色控制台；累计 12 次正确操作获胜。";
         case DEUCE -> "&7攻击史莱姆排球越过球网；先取得 5 分且领先至少 2 分者获胜。";
         case DECRYPTION -> "&7按个人密码面板中的正确顺序输入三位代码，最快破解者获胜。";
         case CANNONEERS -> "&7使用弓箭射中对手；横风会周期性改变，命中即获胜。";
         case PRISON_PALS -> "&7全队依次穿过五道监狱关卡；每位队员都到终点才算完成。";
         case RPSC -> "&7各队按按钮选择石头、剪刀或布；获胜队在短暂窗口内可攻击对手。";
         case TANKS -> "&7用弓箭削减对方坦克装甲；先击穿对方 5 点装甲的队伍获胜。";
         case CAPTURE_THE_FLAG -> "&7挖走敌方旗帜并带回己方堡垒；受到敌方攻击会让旗帜归位。";
         case MINE_YOUR_BUSINESS -> "&7挖穿地下战场寻找敌方；允许 PvP，最后仍有队员存活的一方获胜。";
         case TEAM_HOCKEY -> "&7攻击场内两枚海龟冰球，将它们推进对方球门；率先攻入两球获胜。";
         case MAZE_NAVIGATOR -> "&7每位队员先找到本队魔珠，再与全部队友靠近汇合。";
         case BOMBS_AWAY -> "&7在浮岛上使用雪球击退对手；掉出场地即淘汰，雪球会自动补给。";
         case LABYRINTH -> "&7在迷宫中挖取两块金块；全队队员都集齐两块即可获胜。";
         case SNOW_WARS -> "&7雪球命中三次淘汰一名对手；每 3 秒补充到 6 枚，最后存活队伍获胜。";
         case SPACE_JUMPERS -> "&7在各自太空跳台赛道前进；第一位抵达对岸的队员为全队赢得比赛。";
         case BOOM_CARTS -> "&7按下己方轨道控制器，让 TNT 矿车驶向对方；先让对方耗尽 5 条命。";
         case WHAT_THE_CLUCK -> "&7用鸡蛋攻击敌方战鸡；率先打掉对方 12 点生命的队伍获胜。";
         case RECRUITMENT_ROYALE -> "&7先按基地按钮招募单位，20 秒后按军团总战力决出胜负。";
         case HIDE_AND_SEEK -> "&7一名搜寻者有 15 秒准备时间后开始追捕；躲藏者撑到时间结束即获胜。";
         case GAME_THEORY -> "&7每轮在合作与背叛之间选择；六轮后个人总分最高者获胜。";
         case BOSS_BRAWL -> "&7首领与挑战者使用不同装备进行非对称对战，消灭另一方获胜。";
         case GOLD_RUSH -> "&7挖取金块与镶金黑石；金块 6 分、镶金黑石 2 分，限时最高分获胜。";
         case BLOCK_BUSTER -> "&7爆破场中的红色方块与 TNT 方块；普通方块 1 分、TNT 3 分。";
         case PAC_CUBE -> "&7吃豆人收集迷宫中的全部能量球；幽灵碰到吃豆人会扣除生命。";
         case GHOST_HUNT -> "&7幽灵将村民逐个惊吓离场，猎人则需消灭所有幽灵守住村庄。";
         case TREETOP_HOP -> "&7沿树冠连续跳跃前进，率先抵达对岸者获胜。";
         case SLIME_TIME -> "&7逃跑者躲避由对手推动的巨型史莱姆；存活到结束即获胜。";
         case IN_THE_ZONE -> "&7站在中央紫珀区域为本队持续积累占领进度，先占满一侧获胜。";
         case GHAST_BLAST -> "&7恶魂方逼近幸存者；幸存者撑到时间结束、恶魂触碰对手则获胜。";
         case EGGCELLENCE -> "&7翻转自己的鸡蛋墙格，使排列与目标一致；最先完成 12 次正确调整者获胜。";
         case RAVAGER_RODEO -> "&7在劫掠兽竞技场中躲开定期冲撞，坚持到时间结束。";
         case MOUSE_TRAP -> "&7捕鼠方用鸡蛋构筑障碍；老鼠方避开陷阱并坚持到时间结束。";
         case ONE_IN_CHAMBER -> "&7弓箭命中秒杀并返还一箭；没箭时可用木剑普通攻击。";
         case SUMO -> "&7把其他玩家击出擂台，最后存活者获胜。";
         case DROPPER -> "&7所有人从同一座塔出发，连续通过 5 关落水点即可获胜。";
         case VOLCANO -> "&7黄橙红方块会消失，不要掉进岩浆。";
         case HOT_POTATO -> "&7用近战把山芋传出，倒计时持有者淘汰。";
         case TNT_RUN -> "&7脚下 TNT 很快消失，保持移动。";
         case COLORFUL_RUN -> "&7站到手中方块同色地板上。";
         case MOB_SHOOTER -> "&7每人独立射击台；击中幕布前的动物即可一击得分。";
         case CRAFTING_MASTER -> "&7每人获得随机配方与材料；时间结束时合成数最多者获胜。";
         case HORSE_RACE -> "&7先选马并自由试跑；开赛后赛道封闭，完成 10 段加长随机障碍赛道。";
         case DIG_DOWN -> "&7用 24 点升级镐、斧、铲；30 秒后挖穿自己的方块柱到底。所有人通关或时间到后按进度排名。";
         case ORE_MINER, ANIMAL_SLAUGHTER, PUNCH_THE_BAT, HOE_HOE_HOE -> "&7限时 90 秒，分数最高者获胜。";
         default -> "&7完成目标，成为最后的胜者。";
      };
   }

   private void pushHud() {
      List<Fighter> ranked = rankedFighters();
      for (Fighter viewer : fighters.values()) {
         ServerPlayer player = ctx.player(viewer.uuid);
         if (player == null) continue;
         List<String> lines = new ArrayList<>();
         lines.add("&7地图 &f" + template.id());
         lines.add("&7" + (phase == Phase.HORSE_SELECTION || phase == Phase.DIG_SELECTION || phase == Phase.INTRO ? "准备" : "剩余") + " &e" + Math.max(0, (ticks + 19) / 20) + " 秒");
         lines.add("&7&m---------------");
         lines.add("&e实时排行");
         int place = 1;
         int ownPlace = 0;
         for (Fighter fighter : ranked) {
            if (fighter.uuid.equals(viewer.uuid)) ownPlace = place;
            if (place <= 8) {
               String marker = fighter.uuid.equals(viewer.uuid) ? "&a" : "&f";
               lines.add("&7" + place + ". " + marker + ctx.name(fighter.uuid) + " &8" + rankingValue(fighter));
            }
            place++;
         }
         lines.add("&7我的名次 &e" + ownPlace);
         lines.add("&7&m---------------");
         board.update(player, lines);
      }
   }

   private static final class Fighter {
      private final UUID uuid;
      private boolean alive = true;
      private int score;
      private int progress;
      private int attempts;
      private int reachedScoreTick = Integer.MAX_VALUE;
      private int eliminatedTick = -1;
      private int finishedTick = -1;
      private int dropperStage;
      private int horseLaps;
      private int horseFinishCooldown;
      private double horseLastX;
      private boolean digFinished;
      private Horse horse;
      private int horseIndex = -1;
      private Item craftingTarget;
      private int craftingBaseline;
      private int digPoints;
      private int pickTier;
      private int axeTier;
      private int shovelTier;
      private int classicValue;
      private Fighter(UUID uuid) { this.uuid = uuid; }
   }

   private record ClassicButton(UUID owner, int value) { }

   private record CraftRecipe(Item output, String name, List<ItemStack> ingredients) { }

   private record Saved(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>(); Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) items.add(inv.getItem(i).copy());
         return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(), items);
      }
      void apply(ServerPlayer player, GameContext ctx) {
         ServerLevel level = ctx.server().getLevel(dimension); if (level == null) level = ctx.server().overworld();
         player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch); player.setGameMode(gameType);
         Inventory inv = player.getInventory(); inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), items.size()); i++) inv.setItem(i, items.get(i).copy());
      }
   }
}
