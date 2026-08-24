package net.exmo.sreGame.games.dig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class DigToDeathMatch {
   public enum Phase {
      INTRO,
      FIGHT,
      ENDED
   }

   private static final int INTRO_SECONDS = 5;
   /** 0.1s at 20 tps. */
   private static final int SNOWBALL_COOLDOWN_TICKS = 2;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final DigArena arena;
   private final DigToDeathSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int boardTicks;
   private boolean begun;

   public DigToDeathMatch(GameContext ctx, GameRoom room, List<UUID> seats, DigArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.digToDeathSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&b掘一死战"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      for (UUID uuid : this.seats) {
         this.fighters.put(uuid, new Fighter(uuid));
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
      return this.ctx.digToDeath().arenas().level();
   }

   public int layers() {
      return this.settings.layers();
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.level();
      int i = 0;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&b掘一死战");
         this.boss.addPlayer(player);
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.giveKit(player);
         if (level != null) {
            this.arena.teleport(player, level, this.arena.spawn(i, this.seats.size(), this.layers()));
         }
         i++;
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b&l掘一死战");
      this.ctx.broadcast(this.room, "&7变体 &f" + this.settings.variant().label()
         + " &8| &7层数 &f" + this.layers() + "（底层岩浆）");
      this.ctx.broadcast(this.room, "&7挖空雪层，掉进岩浆即淘汰。最后一人获胜。");
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.beginIntro();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.phase == Phase.INTRO && this.ticksLeft <= 0) {
         this.beginFight();
      }
      this.tickPlayers();
      if (this.boardTicks % 10 == 0) {
         this.refreshBoard();
      }
      this.boss.setProgress(this.phase == Phase.INTRO
         ? Math.max(0f, this.ticksLeft / (INTRO_SECONDS * 20f))
         : 1f);
      this.boss.setName(TextUtil.color(this.phase == Phase.INTRO
         ? "&e准备 " + Math.max(1, (this.ticksLeft + 19) / 20) + "s"
         : "&b掘一死战 · 存活 " + this.aliveCount()));
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || !fighter.alive || this.phase != Phase.FIGHT) {
         return false;
      }
      if (!this.settings.variant().shovel()) {
         return false;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return false;
      }
      return this.arena.isBreakableSnow(pos, this.layers(), level.getBlockState(pos));
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      if (!this.settings.variant().snowballs() || stack == null || !stack.is(Items.SNOWBALL)) {
         return InteractionResult.PASS;
      }
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || !fighter.alive || this.phase != Phase.FIGHT) {
         return InteractionResult.FAIL;
      }
      if (player.getCooldowns().isOnCooldown(Items.SNOWBALL)) {
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public void onSnowballThrown(ServerPlayer player) {
      if (!this.settings.variant().snowballs() || this.phase != Phase.FIGHT) {
         return;
      }
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || !fighter.alive) {
         return;
      }
      player.getCooldowns().addCooldown(Items.SNOWBALL, SNOWBALL_COOLDOWN_TICKS);
   }

   public void onSnowballHitBlock(ServerPlayer thrower, BlockPos pos) {
      Fighter fighter = this.fighter(thrower.getUUID());
      if (fighter == null || !fighter.alive || this.phase != Phase.FIGHT) {
         return;
      }
      if (!this.settings.variant().snowballs()) {
         return;
      }
      this.destroySnow(pos, this.settings.variant().snowRadius());
   }

   public void onSnowballHitPlayer(ServerPlayer thrower, ServerPlayer hit) {
      Fighter attacker = this.fighter(thrower.getUUID());
      Fighter victim = this.fighter(hit.getUUID());
      if (attacker == null || victim == null || !attacker.alive || !victim.alive || this.phase != Phase.FIGHT) {
         return;
      }
      if (!this.settings.variant().snowballs() || thrower.getUUID().equals(hit.getUUID())) {
         return;
      }
      Vec3 dir = hit.position().subtract(thrower.position());
      double len = Math.max(0.001, dir.horizontalDistance());
      hit.push(dir.x / len * 1.15, 0.42, dir.z / len * 1.15);
      hit.hurtMarked = true;
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      return this.fighter(player.getUUID()) != null;
   }

   public boolean handleDeath(ServerPlayer player) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      this.heal(player);
      if (this.phase == Phase.FIGHT && fighter.alive) {
         this.eliminate(player, fighter, "&c阵亡");
      } else if (this.phase == Phase.INTRO) {
         ServerLevel level = this.level();
         if (level != null) {
            this.arena.teleport(player, level, this.arena.spawn(this.seats.indexOf(player.getUUID()), this.seats.size(), this.layers()));
         }
      }
      return true;
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase == Phase.ENDED) {
         return;
      }
      if (fighter != null) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了掘一死战。");
      }
      this.checkWin();
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish(null);
      }
   }

   private void destroySnow(BlockPos center, int radius) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int r2 = radius * radius;
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (dx * dx + dy * dy + dz * dz > r2) {
                  continue;
               }
               cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               if (this.arena.isBreakableSnow(cursor, this.layers(), level.getBlockState(cursor))) {
                  level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   private void beginIntro() {
      this.phase = Phase.INTRO;
      this.ticksLeft = INTRO_SECONDS * 20;
      this.forEachOnline((player, fighter) -> {
         player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 255, false, false, false));
         this.title(player, "&b掘一死战", "&e准备开战");
      });
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.forEachOnline((player, fighter) -> {
         player.removeEffect(MobEffects.SLOW_FALLING);
         this.title(player, "&c开战", "&f" + this.settings.variant().label());
      });
   }

   private void tickPlayers() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null) {
            continue;
         }
         if (!fighter.alive) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            continue;
         }
         if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
         }
         this.refillSnowballs(player);
         if (this.phase == Phase.INTRO) {
            player.fallDistance = 0.0F;
            if (player.getY() < this.arena.topY(this.layers()) - 0.2) {
               this.arena.teleport(player, level, this.arena.spawn(this.seats.indexOf(uuid), this.seats.size(), this.layers()));
            }
            continue;
         }
         boolean inside = player.serverLevel() == level
            && this.arena.contains(player.getX(), player.getY(), player.getZ(), this.layers());
         boolean inLava = player.isInLava() || player.getY() < this.arena.lavaY() + 0.9;
         if (!inside || inLava) {
            this.eliminate(player, fighter, inLava ? "&c掉进岩浆" : "&c掉出场地");
         }
      }
   }

   private void eliminate(ServerPlayer player, Fighter fighter, String title) {
      if (!fighter.alive) {
         this.heal(player);
         return;
      }
      fighter.alive = false;
      player.getInventory().clearContent();
      this.heal(player);
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.watch(this.layers()));
      }
      this.title(player, title, "&7旁观至对局结束");
      this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " 出局了。");
      this.checkWin();
   }

   private void checkWin() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      List<Fighter> alive = this.aliveFighters();
      if (alive.size() <= 1) {
         this.finish(alive.isEmpty() ? null : alive.get(0));
      }
   }

   private void finish(Fighter winner) {
      this.phase = Phase.ENDED;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b掘一死战结束");
      if (winner != null) {
         this.ctx.broadcast(this.room, "&a胜者： &f" + this.ctx.name(winner.uuid));
         ServerPlayer player = this.ctx.player(winner.uuid);
         if (player != null) {
            this.title(player, "&6胜利", "&e掘一死战");
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
      this.ctx.digToDeath().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.digToDeath().remove(this);
   }

   private void giveKit(ServerPlayer player) {
      Inventory inv = player.getInventory();
      DigVariant variant = this.settings.variant();
      if (variant.shovel()) {
         inv.setItem(0, this.shovel(player));
      }
      if (variant.snowballs()) {
         inv.setItem(variant.shovel() ? 1 : 0, new ItemStack(Items.SNOWBALL, 16));
      }
   }

   private ItemStack shovel(ServerPlayer player) {
      ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
      shovel.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      Holder<Enchantment> efficiency = player.registryAccess()
         .registryOrThrow(Registries.ENCHANTMENT)
         .getHolderOrThrow(Enchantments.EFFICIENCY);
      shovel.enchant(efficiency, 5);
      shovel.set(DataComponents.CUSTOM_NAME, TextUtil.color("&b效率五铲子"));
      return shovel;
   }

   private void refillSnowballs(ServerPlayer player) {
      if (!this.settings.variant().snowballs() || this.phase != Phase.FIGHT) {
         return;
      }
      Inventory inv = player.getInventory();
      int count = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(Items.SNOWBALL)) {
            count += stack.getCount();
         }
      }
      if (count < 8) {
         inv.add(new ItemStack(Items.SNOWBALL, 16 - count));
      }
   }

   private void refreshBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7变体 &f" + this.settings.variant().label());
      lines.add("&7存活 &a" + this.aliveCount() + "&7/&f" + this.fighters.size());
      lines.add("&8 ");
      int shown = 0;
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         if (fighter == null) {
            continue;
         }
         lines.add((fighter.alive ? "&a● &f" : "&8○ &7") + this.ctx.name(uuid));
         if (++shown >= 10) {
            break;
         }
      }
      this.forEachOnline((player, fighter) -> this.board.update(player, lines));
   }

   private int aliveCount() {
      return this.aliveFighters().size();
   }

   private List<Fighter> aliveFighters() {
      List<Fighter> out = new ArrayList<>();
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.alive) {
            out.add(fighter);
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
      player.setAirSupply(player.getMaxAirSupply());
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

   private void forEachOnline(PlayerFighter action) {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter != null && player != null) {
            action.accept(player, fighter);
         }
      }
   }

   private Fighter fighter(UUID uuid) {
      return this.fighters.get(uuid);
   }

   @FunctionalInterface
   private interface PlayerFighter {
      void accept(ServerPlayer player, Fighter fighter);
   }

   static final class Fighter {
      final UUID uuid;
      boolean alive = true;

      Fighter(UUID uuid) {
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
