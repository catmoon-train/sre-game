package net.exmo.sreGame.games.parkour;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ParkourSession {
   static final int BORDER = 256;
   static final int MIN_Y = 100;
   static final int MAX_Y = 200;
   static final int TRAIL = 2;
   private static final int ISLAND = 4;
   private static final int TEMPLATE_COOLDOWN = 20;

   private final ServerPlayer player;
   private final BlockPos center;
   private final ParkourDirector director;
   private final List<BlockPos> history = new ArrayList<>();
   private final List<ExtraBlock> extras = new ArrayList<>();
   private final List<ItemStack> savedItems = new ArrayList<>();
   private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> savedDim;
   private final Vec3 savedPos;
   private final float savedYaw;
   private final float savedPitch;
   private final GameType savedMode;
   private ParkourStyle style = ParkourStyle.RED;
   private int lead = 4;
   private boolean specials = true;
   private boolean fallMessage = true;
   private double templateDifficulty = 0.5;
   private int templateCooldown;
   private int headingX = 1;
   private int headingZ = 0;
   private int lastIndex;
   private int score;
   private int bestThisRun;
   private long firstScoreAt;
   private long elapsedMs;
   private BlockPos lastStanding;
   private ParkourJumps.Special lastSpecial;
   private boolean active = true;
   int slot;

   public ParkourSession(ServerPlayer player, BlockPos center) {
      this.player = player;
      this.center = center;
      this.director = new ParkourDirector(center, BORDER, MIN_Y, MAX_Y);
      this.savedDim = player.level().dimension();
      this.savedPos = player.position();
      this.savedYaw = player.getYRot();
      this.savedPitch = player.getXRot();
      this.savedMode = player.gameMode.getGameModeForPlayer();
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         this.savedItems.add(inv.getItem(i).copy());
      }
   }

   public ServerPlayer player() {
      return this.player;
   }

   public int score() {
      return this.score;
   }

   public long elapsedMs() {
      return this.elapsedMs;
   }

   public ParkourStyle style() {
      return this.style;
   }

   public int lead() {
      return this.lead;
   }

   public boolean specials() {
      return this.specials;
   }

   public boolean fallMessage() {
      return this.fallMessage;
   }

   public double templateDifficulty() {
      return this.templateDifficulty;
   }

   public void cycleStyle() {
      this.style = this.style.next();
   }

   public void cycleLead() {
      this.lead = this.lead >= 10 ? 1 : this.lead + 1;
      this.generateLead();
   }

   public void toggleSpecials() {
      if (this.score > 0) {
         return;
      }
      this.specials = !this.specials;
   }

   public void toggleFallMessage() {
      this.fallMessage = !this.fallMessage;
   }

   public void cycleTemplates() {
      if (this.score > 0) {
         return;
      }
      this.templateDifficulty = ParkourTemplates.nextDifficulty(this.templateDifficulty);
   }

   public void begin(ServerLevel level) {
      this.buildIsland(level);
      this.player.setGameMode(GameType.ADVENTURE);
      this.player.getInventory().clearContent();
      this.giveHotbar();
      this.player.setHealth(this.player.getMaxHealth());
      this.player.getFoodData().setFoodLevel(20);
      this.player.getFoodData().setSaturation(20.0F);
      this.teleportSpawn(level);
      this.generateLead();
   }

   public void tick(GameContext ctx, ServerLevel level) {
      if (!this.active) {
         return;
      }
      this.player.getFoodData().setFoodLevel(20);
      this.player.getFoodData().setSaturation(20.0F);
      if (this.lastStanding != null && this.player.getY() - this.lastStanding.getY() < -10) {
         this.fall(ctx, level);
         return;
      }
      BlockPos below = BlockPos.containing(this.player.getX(), this.player.getY() - 0.2, this.player.getZ());
      int index = this.indexOf(below);
      if (index < 0) {
         below = this.player.blockPosition().below();
         index = this.indexOf(below);
      }
      if (index < 0) {
         return;
      }
      if (this.lastStanding == null || !this.lastStanding.equals(below)) {
         this.lastStanding = below.immutable();
      }
      int delta = index - this.lastIndex;
      if (delta <= 0) {
         return;
      }
      this.lastIndex = index;
      if (this.firstScoreAt == 0L) {
         this.firstScoreAt = System.currentTimeMillis();
      }
      this.score += 1;
      this.bestThisRun = Math.max(this.bestThisRun, this.score);
      this.elapsedMs = System.currentTimeMillis() - this.firstScoreAt;
      this.clearTrail(level);
      this.generateLead();
      this.player.displayClientMessage(TextUtil.color("&a+" + this.score + "  &7" + formatTime(this.elapsedMs)), true);
   }

   public void destroy(ServerLevel level) {
      this.active = false;
      for (BlockPos pos : this.history) {
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      }
      this.history.clear();
      this.clearExtras(level);
      this.clearIsland(level);
   }

   public void restore(GameContext ctx) {
      ServerLevel level = ctx.server().getLevel(this.savedDim);
      if (level == null) {
         level = ctx.server().overworld();
      }
      this.player.teleportTo(level, this.savedPos.x, this.savedPos.y, this.savedPos.z, this.savedYaw, this.savedPitch);
      this.player.setGameMode(this.savedMode);
      Inventory inv = this.player.getInventory();
      inv.clearContent();
      for (int i = 0; i < Math.min(inv.getContainerSize(), this.savedItems.size()); i++) {
         inv.setItem(i, this.savedItems.get(i).copy());
      }
   }

   private void fall(GameContext ctx, ServerLevel level) {
      if (this.fallMessage) {
         ctx.send(this.player, "&c坠落！分数 &f" + this.score + " &7用时 &f" + formatTime(this.elapsedMs));
      }
      ctx.parkour().scores().record(this.player, this.score, this.elapsedMs);
      this.score = 0;
      this.lastIndex = 0;
      this.firstScoreAt = 0L;
      this.elapsedMs = 0L;
      this.lastSpecial = null;
      this.templateCooldown = 0;
      this.clearExtras(level);
      for (int i = 1; i < this.history.size(); i++) {
         level.setBlock(this.history.get(i), Blocks.AIR.defaultBlockState(), 2);
      }
      if (!this.history.isEmpty()) {
         this.history.subList(1, this.history.size()).clear();
      }
      this.headingX = 1;
      this.headingZ = 0;
      this.teleportSpawn(level);
      this.generateLead();
   }

   private void generateLead() {
      ServerLevel level = this.player.serverLevel();
      while (this.history.size() - this.lastIndex < this.lead + 1) {
         this.placeNext(level);
      }
   }

   private void placeNext(ServerLevel level) {
      BlockPos latest = this.history.get(this.history.size() - 1);
      int[] heading = this.director.heading(latest, this.headingX, this.headingZ);
      this.headingX = heading[0];
      this.headingZ = heading[1];
      if (this.tryPlaceTemplate(level, latest)) {
         return;
      }
      if (this.templateCooldown > 0) {
         this.templateCooldown--;
      }
      int height = this.director.height(latest, ParkourJumps.height());
      int distance = ParkourJumps.distance();
      if (this.lastSpecial == ParkourJumps.Special.SLAB) {
         height = Math.min(height, 0);
      }
      if (this.lastSpecial == ParkourJumps.Special.PANE) {
         distance = Math.min(distance, 3);
      }
      if (height > 0) {
         distance = Math.max(distance - height, 1);
      }
      int side = ParkourJumps.sideOffset(height, distance);
      int nx = latest.getX() + this.headingX * (distance + 1) + (-this.headingZ) * side;
      int ny = latest.getY() + height;
      int nz = latest.getZ() + this.headingZ * (distance + 1) + this.headingX * side;
      if (this.lastSpecial == ParkourJumps.Special.FENCE) {
         ny -= 1;
      }
      BlockPos next = new BlockPos(nx, ny, nz);
      boolean special = ParkourJumps.special(this.specials);
      ParkourJumps.Special kind = special ? ParkourJumps.pickSpecial() : null;
      BlockState state = this.stateFor(kind);
      level.setBlock(next, state, 2);
      this.history.add(next);
      this.lastSpecial = kind;
   }

   private boolean tryPlaceTemplate(ServerLevel level, BlockPos latest) {
      if (this.templateDifficulty <= 0 || this.templateCooldown > 0) {
         return false;
      }
      if (!ParkourTemplates.roll()) {
         return false;
      }
      ParkourTemplate template = ParkourTemplates.pick(this.templateDifficulty);
      if (template == null) {
         return false;
      }
      for (ParkourTemplate.Cell cell : template.cells()) {
         if (!this.director.inZone(template.world(latest, this.headingX, this.headingZ, cell))) {
            return false;
         }
      }
      List<BlockPos> newExtras = new ArrayList<>();
      ParkourJumps.Special last = null;
      for (ParkourTemplate.Cell cell : template.cells()) {
         BlockPos pos = template.world(latest, this.headingX, this.headingZ, cell).immutable();
         level.setBlock(pos, template.state(cell, this.style, this.headingX, this.headingZ), 2);
         if (template.walkable(cell)) {
            this.history.add(pos);
            last = specialOf(cell.kind());
         } else {
            newExtras.add(pos);
         }
      }
      int until = this.history.size() - 1;
      for (BlockPos pos : newExtras) {
         this.extras.add(new ExtraBlock(until, pos));
      }
      this.lastSpecial = last;
      this.templateCooldown = TEMPLATE_COOLDOWN;
      return true;
   }

   private static ParkourJumps.Special specialOf(ParkourTemplate.Kind kind) {
      return switch (kind) {
         case ICE -> ParkourJumps.Special.ICE;
         case SLAB -> ParkourJumps.Special.SLAB;
         case PANE -> ParkourJumps.Special.PANE;
         case FENCE -> ParkourJumps.Special.FENCE;
         default -> null;
      };
   }

   private BlockState stateFor(ParkourJumps.Special kind) {
      if (kind == null) {
         return this.style.randomBlock().defaultBlockState();
      }
      return switch (kind) {
         case ICE -> Blocks.PACKED_ICE.defaultBlockState();
         case SLAB -> Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
         case PANE -> Blocks.GLASS_PANE.defaultBlockState();
         case FENCE -> Blocks.OAK_FENCE.defaultBlockState();
      };
   }

   private void clearTrail(ServerLevel level) {
      int keep = Math.max(0, this.lastIndex - TRAIL);
      for (int i = 1; i < keep && i < this.history.size(); i++) {
         BlockPos pos = this.history.get(i);
         if (pos != null && !level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         }
      }
      for (ExtraBlock extra : this.extras) {
         if (extra.untilIndex < keep && extra.pos != null && !level.getBlockState(extra.pos).isAir()) {
            level.setBlock(extra.pos, Blocks.AIR.defaultBlockState(), 2);
         }
      }
   }

   private void clearExtras(ServerLevel level) {
      for (ExtraBlock extra : this.extras) {
         if (extra.pos != null) {
            level.setBlock(extra.pos, Blocks.AIR.defaultBlockState(), 2);
         }
      }
      this.extras.clear();
   }

   private int indexOf(BlockPos pos) {
      for (int i = 0; i < this.history.size(); i++) {
         if (this.history.get(i).equals(pos)) {
            return i;
         }
      }
      return -1;
   }

   private void buildIsland(ServerLevel level) {
      int y = (MIN_Y + MAX_Y) / 2;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = -ISLAND; x <= ISLAND; x++) {
         for (int z = -ISLAND; z <= ISLAND; z++) {
            pos.set(this.center.getX() + x, y, this.center.getZ() + z);
            level.setBlock(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
         }
      }
      BlockPos start = new BlockPos(this.center.getX() + 3, y, this.center.getZ());
      level.setBlock(start, this.style.randomBlock().defaultBlockState(), 2);
      this.history.add(start.immutable());
      this.lastStanding = start;
   }

   private void clearIsland(ServerLevel level) {
      int y = (MIN_Y + MAX_Y) / 2;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = -ISLAND; x <= ISLAND; x++) {
         for (int z = -ISLAND; z <= ISLAND; z++) {
            pos.set(this.center.getX() + x, y, this.center.getZ() + z);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         }
      }
   }

   private void teleportSpawn(ServerLevel level) {
      Vec3 at = new Vec3(this.center.getX() + 0.5, (MIN_Y + MAX_Y) / 2.0 + 1.0, this.center.getZ() + 0.5);
      this.player.teleportTo(level, at.x, at.y, at.z, -90.0F, 0.0F);
      this.player.fallDistance = 0.0F;
   }

   private void giveHotbar() {
      Inventory inv = this.player.getInventory();
      inv.setItem(0, GuiItems.action("comparator", "&e设置", List.of("&7样式 / 领先 / 特殊方块 / 模版"), "pk_settings"));
      inv.setItem(8, GuiItems.action("barrier", "&c退出", List.of("&7离开无限跑酷"), "pk_leave"));
      ItemStack tip = new ItemStack(Items.PAPER);
      tip.set(DataComponents.CUSTOM_NAME, TextUtil.color("&f无限跑酷"));
      inv.setItem(4, tip);
   }

   private record ExtraBlock(int untilIndex, BlockPos pos) {
   }

   public static String formatTime(long ms) {
      if (ms <= 0) {
         return "00:00";
      }
      long s = ms / 1000;
      return String.format("%02d:%02d", s / 60, s % 60);
   }
}
