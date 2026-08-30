package net.exmo.sreGame.games.partygames;

import java.util.Random;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.exmo.sreGame.games.partygames.scene.PartySceneBundle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A reusable slot whose terrain is completely reconstructed from its frozen template. */
public final class PartyArena {
   public enum State { IDLE, IN_USE, CLEANING }
   public static final int SIZE = 96;
   public static final int HEIGHT = 52;
   public static final int STRIDE = SIZE + 24;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private MapTemplate template;
   private PartySceneBundle scene;
   private final Set<UUID> sceneEntities = new HashSet<>();
   private int playerCount = 1;

   PartyArena(int slot, BlockPos origin) { this.slot = slot; this.origin = origin; }
   public int slot() { return this.slot; }
   public BlockPos origin() { return this.origin; }
   public State state() { return this.state; }
   public void setState(State state) { this.state = state; }
   public MapTemplate template() { return this.template; }
   public PartyGameType type() { return this.template == null ? null : this.template.type(); }
   public void assign(MapTemplate template) { assign(template, 1); }
   public void assign(MapTemplate template, int playerCount) { assign(template, playerCount, null); }
   public void assign(MapTemplate template, int playerCount, PartySceneBundle scene) {
      this.template = template;
      this.playerCount = Math.max(1, playerCount);
      this.scene = scene;
   }
   public PartySceneBundle scene() { return scene; }
   public Map<String, double[]> anchors() { return scene == null ? Map.of() : scene.anchors(); }
   public void ownSceneEntity(UUID id) { if (id != null) sceneEntities.add(id); }
   public boolean ownsSceneEntity(UUID id) { return sceneEntities.contains(id); }
   public void clearSceneEntities() { sceneEntities.clear(); }
   public int baseY() { return this.origin.getY(); }
   public int floorY() { return this.baseY() + 1; }
   public int topY() { return this.baseY() + (scene == null ? HEIGHT : scene.height() - 1); }
   public int size() { return this.template == null ? 48 : Math.max(24, Math.min(SIZE - 4, this.template.parameter("size", 48))); }
   public int height() { return this.template == null ? 12 : Math.max(4, Math.min(48, this.template.parameter("height", 12))); }
   public int minX() { return this.origin.getX(); }
   public int minZ() { return this.origin.getZ(); }
   public int maxX() { return this.origin.getX() + (scene == null ? SIZE : scene.width()) - 1; }
   public int maxZ() { return this.origin.getZ() + (scene == null ? SIZE : scene.depth()) - 1; }
   public int centerX() { return (minX() + maxX()) / 2; }
   public int centerZ() { return (minZ() + maxZ()) / 2; }

   public boolean contains(double x, double y, double z) {
      return x >= minX() && x <= maxX() + 1 && z >= minZ() && z <= maxZ() + 1
         && y >= baseY() - 12 && y <= topY() + 8;
   }

   public boolean inPlay(BlockPos pos) { return pos.getX() >= minX() + 2 && pos.getX() <= maxX() - 2 && pos.getZ() >= minZ() + 2 && pos.getZ() <= maxZ() - 2; }

   public Vec3 spawn(int index, int players) {
      PartyGameType type = type();
      if (type == PartyGameType.DROPPER) {
         // One shared tower: everyone begins at the same launch platform.
         return new Vec3(centerX() + 0.5, floorY() + height() + 1.0, centerZ() + 0.5);
      }
      if (type == PartyGameType.MINE_FIELD) return mineSpawn(index, players);
      if (type == PartyGameType.HORSE_RACE) return horseSpawn(index, players);
      if (type == PartyGameType.DIG_DOWN) return digDownSpawn(index);
      if (type == PartyGameType.MOB_SHOOTER) {
         int columns = shooterColumns(players);
         int row = index / columns;
         int column = index % columns;
         return new Vec3(centerX() + 0.5 - (columns - 1) * 3.0 + column * 6.0, floorY() + 1.0 + row * 3.0,
            centerZ() - 9.5 - row * 4.0);
      }
      double radius = Math.max(7.0, size() * 0.34);
      double angle = (Math.PI * 2.0 * index) / Math.max(1, players);
      double spawnY = type == PartyGameType.SUMO ? floorY() + 3.0 : floorY() + 1.0;
      return new Vec3(centerX() + Math.cos(angle) * radius + 0.5, spawnY,
         centerZ() + Math.sin(angle) * radius + 0.5);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, 0.0F, 0.0F);
   }

   public BlockState structureWant(int x, int y, int z) {
      if (x < minX() || x > maxX() || z < minZ() || z > maxZ() || y < baseY() || y > topY()) return Blocks.AIR.defaultBlockState();
      PartyGameType type = type();
      if (type == null) return Blocks.AIR.defaultBlockState();
      int lx = x - minX(), lz = z - minZ();
      int cx = x - centerX(), cz = z - centerZ();
      int r2 = cx * cx + cz * cz;
      if (y == baseY()) return Blocks.WHITE_CONCRETE.defaultBlockState();
      if (isOuterWall(x, y, z)) return Blocks.WHITE_CONCRETE.defaultBlockState();
      if (type == PartyGameType.DROPPER) return dropperWant(lx, y, lz);
      if (type == PartyGameType.HORSE_RACE) return laneWant(lx, y, lz);
      if (type == PartyGameType.MINE_FIELD) return mineFieldWant(x, y, z);
      if (type == PartyGameType.MOB_SHOOTER) return shooterWant(x, y, z);
      if (type == PartyGameType.ORE_MINER) return oreMineWant(x, y, z);
      if (type == PartyGameType.DIG_DOWN) return digDownWant(x, y, z);
      if (type == PartyGameType.HOT_POTATO) return hotPotatoWant(x, y, z);
      if (type == PartyGameType.HOE_HOE_HOE) return hoeArenaWant(x, y, z);
      if (type == PartyGameType.SUMO) {
         if (y == floorY() + 2 && r2 <= (size() / 2 - 4) * (size() / 2 - 4)) return Blocks.WHITE_CONCRETE.defaultBlockState();
         return Blocks.AIR.defaultBlockState();
      }
      int radius = size() / 2 - 3;
      boolean inside = r2 <= radius * radius;
      if (type == PartyGameType.SURVIVAL_GAMES) {
         if (inside && y == floorY()) return Blocks.GRASS_BLOCK.defaultBlockState();
         if (inside && y < floorY()) return Blocks.DIRT.defaultBlockState();
         return Blocks.AIR.defaultBlockState();
      }
      if (!inside) return Blocks.AIR.defaultBlockState();
      if (y == floorY()) return floorBlock(type, lx, lz);
      return Blocks.AIR.defaultBlockState();
   }

   /** Shared white-concrete boundary for every procedural party arena. */
   private boolean isOuterWall(int x, int y, int z) {
      if (y < floorY() || y > floorY() + 4) return false;
      return x == minX() + 1 || x == maxX() - 1 || z == minZ() + 1 || z == maxZ() - 1;
   }

   private BlockState dropperWant(int lx, int y, int lz) {
      int cx = centerX() - minX(), cz = centerZ() - minZ();
      int dx = lx - cx, dz = lz - cz;
      int top = floorY() + height();
      if (Math.abs(dx) > 15 || Math.abs(dz) > 15 || y < floorY() || y > top) return Blocks.AIR.defaultBlockState();
      if (y == floorY() && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) return Blocks.WATER.defaultBlockState();
      if (y == top && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) return Blocks.GLASS.defaultBlockState();
      if ((Math.abs(dx) == 15 || Math.abs(dz) == 15) && y <= top) return Blocks.WHITE_CONCRETE.defaultBlockState();
      // Five deterministic, yet seed-randomised, passable obstacle decks inside the common tower.
      int rel = y - floorY();
      int deckSpacing = Math.max(5, (height() - 5) / 4);
      if (rel >= 5 && rel <= 5 + deckSpacing * 4 && (rel - 5) % deckSpacing == 0) {
         // Keep successive gates close enough that a normal falling player can steer through them.
         int gateX = Math.floorMod((int) (template.seed() + rel * 17L), 7) - 3;
         int gateZ = Math.floorMod((int) (template.seed() * 31L + rel * 11L), 7) - 3;
         return Math.abs(dx - gateX) <= 3 && Math.abs(dz - gateZ) <= 3
            ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private BlockState laneWant(int lx, int y, int lz) {
      int laneWidth = horseLaneWidth();
      int lane = (lz - 4) / laneWidth;
      if (lx < 3 || lx >= size() - 3 || lane < 0 || lane >= playerCount) return Blocks.AIR.defaultBlockState();
      int step = lx - 4;
      int obstacle = Math.floorMod((int) template.seed() + lane * 19 + step, 53);
      if (y == floorY()) {
         if (obstacle == 4) return Blocks.SOUL_SAND.defaultBlockState();
         if (obstacle == 10) return Blocks.HONEY_BLOCK.defaultBlockState();
         if (obstacle == 16) return Blocks.SLIME_BLOCK.defaultBlockState();
         if (obstacle == 22) return Blocks.MUD.defaultBlockState();
         if (obstacle == 28) return Blocks.BLUE_ICE.defaultBlockState();
         if (obstacle == 34) return Blocks.COARSE_DIRT.defaultBlockState();
         return Blocks.GRASS_BLOCK.defaultBlockState();
      }
      if (y == floorY() + 1 && obstacle == 39) return Blocks.COBWEB.defaultBlockState();
      if (y == floorY() + 1 && obstacle == 44) return Blocks.OAK_PLANKS.defaultBlockState();
      if (y == floorY() + 1 && obstacle == 48) return Blocks.HAY_BLOCK.defaultBlockState();
      if (y == floorY() + 2 && obstacle == 48) return Blocks.HAY_BLOCK.defaultBlockState();
      return Blocks.AIR.defaultBlockState();
   }

   private Vec3 horseSpawn(int index, int players) {
      int laneWidth = Math.max(3, Math.max(1, size() - 8) / Math.max(1, players) / 2);
      double z = minZ() + 4 + index * laneWidth + (laneWidth - 1) * 0.5;
      return new Vec3(minX() + 5.5, floorY() + 1.0, z);
   }

   /** Half-width lanes leave room for more riders; three blocks remains the safe minimum for horses. */
   private int horseLaneWidth() { return Math.max(3, Math.max(1, size() - 8) / Math.max(1, playerCount) / 2); }
   public int horseFinishX() { return minX() + size() - 4; }

   /** Open lanes are used for horse selection and warmup; dividers appear only at the starting bell. */
   public void lockHorseLanes(ServerLevel level) {
      if (type() != PartyGameType.HORSE_RACE || level == null) return;
      int laneWidth = horseLaneWidth();
      for (int lane = 0; lane <= playerCount; lane++) {
         int z = minZ() + 4 + lane * laneWidth;
         for (int x = minX() + 3; x < minX() + size() - 3; x++) {
            for (int y = floorY(); y <= floorY() + 6; y++) {
               level.setBlock(new BlockPos(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
            }
         }
      }
   }

   /** One protected start lane per player; lane count and width are derived from the room size. */
   private BlockState mineFieldWant(int x, int y, int z) {
      int half = size() / 2;
      int left = centerX() - half, right = centerX() + half;
      int near = centerZ() - half, far = centerZ() + half;
      if (x < left || x > right || z < near || z > far) return Blocks.AIR.defaultBlockState();
      boolean outer = x == left || x == right || z == near || z == far;
      if (outer && y >= floorY() && y <= floorY() + 4) return Blocks.WHITE_CONCRETE.defaultBlockState();
      int laneWidth = mineLaneWidth();
      int lane = (z - (near + 1)) / laneWidth;
      if (lane < 0 || lane >= playerCount) return Blocks.WHITE_CONCRETE.defaultBlockState();
      if (lane > 0 && (z - (near + 1)) % laneWidth == 0) {
         return y >= floorY() && y <= floorY() + 3 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState();
      }
      // A high-contrast board makes the individual mine lanes easy to read.
      if (y == floorY()) {
         return Math.floorMod(x + z, 2) == 0
            ? Blocks.BLACK_CONCRETE.defaultBlockState()
            : Blocks.WHITE_CONCRETE.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private Vec3 mineSpawn(int index, int players) {
      int half = size() / 2;
      int left = centerX() - half, near = centerZ() - half;
      int laneWidth = Math.max(2, Math.max(1, size() - 2) / Math.max(1, players));
      double z = near + 1 + index * laneWidth + laneWidth - 0.5;
      return new Vec3(left + 2.5, floorY() + 1.0, z);
   }

   private int mineLaneWidth() { return Math.max(2, Math.max(1, size() - 2) / Math.max(1, playerCount)); }

   public boolean mineAt(BlockPos pos, int playerIndex) {
      if (type() != PartyGameType.MINE_FIELD) return false;
      Vec3 spawn = mineSpawn(playerIndex, playerCount);
      int laneWidth = mineLaneWidth();
      int near = centerZ() - size() / 2;
      int laneStart = near + 1 + playerIndex * laneWidth;
      if (pos.getZ() < laneStart || pos.getZ() >= laneStart + laneWidth - 1) return false;
      int step = pos.getX() - (int) Math.floor(spawn.x);
      if (step < 4) return false;
      long hash = template.seed() ^ (long) playerIndex * 341873128712L ^ (long) step * 132897987541L;
      return Math.floorMod(hash, 9) == 0;
   }

   /** Number shown on a discovered mine: the neighbouring mines in the runner's own lane. */
   public int nearbyMines(BlockPos pos, int playerIndex) {
      int count = 0;
      for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
         if (dx != 0 || dz != 0) if (mineAt(pos.offset(dx, 0, dz), playerIndex)) count++;
      }
      return count;
   }

   private Vec3 digDownSpawn(int index) {
      BlockPos center = digDownCenter(index);
      return new Vec3(center.getX() + 0.5, topY() + 1.0, center.getZ() + 0.5);
   }

   private BlockPos digDownCenter(int index) {
      // Six-by-four columns fit all 24 players while retaining a two-block shell.
      int columns = 6;
      int spacing = 14;
      return new BlockPos(minX() + 8 + (index % columns) * spacing, baseY(), minZ() + 8 + (index / columns) * spacing);
   }

   /** Returns the owner of a vertical dig column, or -1 outside a column. */
   public int digOwner(BlockPos pos) {
      if (type() != PartyGameType.DIG_DOWN) return -1;
      for (int index = 0; index < playerCount; index++) {
         BlockPos center = digDownCenter(index);
         if (pos.getX() == center.getX() && pos.getZ() == center.getZ()
            && pos.getY() >= floorY() && pos.getY() <= topY()) return index;
      }
      return -1;
   }

   private BlockState digDownWant(int x, int y, int z) {
      for (int index = 0; index < playerCount; index++) {
         BlockPos center = digDownCenter(index);
         int dx = Math.abs(x - center.getX()), dz = Math.abs(z - center.getZ());
         if (dx > 2 || dz > 2 || y < floorY() || y > topY()) continue;
         // The column is boxed in: stepping to either side can no longer bypass digging.
         if (dx != 0 || dz != 0) return Blocks.WHITE_CONCRETE.defaultBlockState();
         if (dx == 0 && dz == 0) return digBlock(index, y);
      }
      return Blocks.AIR.defaultBlockState();
   }

   private BlockState digBlock(int index, int y) {
      int roll = Math.floorMod((int) (template.seed() + index * 101L + y * 37L), 100);
      if (roll < 24) return Blocks.DIRT.defaultBlockState();
      if (roll < 38) return Blocks.SAND.defaultBlockState();
      if (roll < 58) return Blocks.OAK_LOG.defaultBlockState();
      if (roll < 78) return Blocks.STONE.defaultBlockState();
      if (roll < 90) return Blocks.DEEPSLATE.defaultBlockState();
      if (roll < 98) return Blocks.IRON_ORE.defaultBlockState();
      return Blocks.OBSIDIAN.defaultBlockState();
   }

   /** Shared firing deck with a tall backdrop: every animal appears in front of this wall. */
   private BlockState shooterWant(int x, int y, int z) {
      int dx = x - centerX();
      int dz = z - centerZ();
      for (int index = 0; index < playerCount; index++) {
         Vec3 spawn = spawn(index, playerCount);
         int deckY = (int) Math.floor(spawn.y) - 1;
         int deckX = (int) Math.floor(spawn.x);
         int deckZ = (int) Math.floor(spawn.z);
         if (x >= deckX - 1 && x <= deckX + 1 && z >= deckZ - 1 && z <= deckZ + 1) {
            if (y == deckY) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            if (y >= floorY() && y < deckY) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
         }
      }
      if (y == floorY() && dx >= -24 && dx <= 24 && z >= centerZ() + 6 && z <= centerZ() + 11) {
         return Blocks.SMOOTH_STONE.defaultBlockState();
      }
      if (z == centerZ() + 12 && dx >= -26 && dx <= 26 && y >= floorY() && y <= floorY() + 18) {
         if (Math.floorMod(dx + y, 6) == 0) return Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
         return Blocks.WHITE_CONCRETE.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   /** A square mining field with a clear white-concrete perimeter wall. */
   private BlockState oreMineWant(int x, int y, int z) {
      int half = size() / 2;
      int left = centerX() - half, right = centerX() + half;
      int near = centerZ() - half, far = centerZ() + half;
      if (x < left || x > right || z < near || z > far) return Blocks.AIR.defaultBlockState();
      boolean edge = x == left || x == right || z == near || z == far;
      if (edge && y >= floorY() && y <= floorY() + 4) return Blocks.WHITE_CONCRETE.defaultBlockState();
      if (y == floorY()) return ore(x - minX(), z - minZ());
      return Blocks.AIR.defaultBlockState();
   }

   private int shooterColumns(int players) { return Math.min(4, Math.max(1, players)); }

   public Vec3 shooterTarget(Random random) {
      return new Vec3(centerX() + random.nextDouble(-21.0, 21.0), floorY() + random.nextDouble(1.1, 12.0), centerZ() + 10.4);
   }

   private BlockState floorBlock(PartyGameType type, int lx, int lz) {
      return switch (type) {
         case VOLCANO -> Blocks.YELLOW_CONCRETE.defaultBlockState();
         case TNT_RUN -> Blocks.TNT.defaultBlockState();
         case COLORFUL_RUN -> color(lx, lz);
         case ORE_MINER -> ore(lx, lz);
         case HOE_HOE_HOE -> Blocks.DIRT.defaultBlockState();
         case PUNCH_THE_BAT, ANIMAL_SLAUGHTER, MOB_SHOOTER -> Blocks.GRASS_BLOCK.defaultBlockState();
         default -> Blocks.SMOOTH_STONE.defaultBlockState();
      };
   }

   /** A dedicated, enclosed plaza keeps Hot Potato players off the void at match start. */
   private BlockState hotPotatoWant(int x, int y, int z) {
      boolean inside = x >= minX() + 2 && x <= maxX() - 2 && z >= minZ() + 2 && z <= maxZ() - 2;
      if (!inside) return Blocks.AIR.defaultBlockState();
      if (y == floorY()) return ((x + z) & 7) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
      return Blocks.AIR.defaultBlockState();
   }

   /** Hoe Hoe Hoe deliberately uses every playable floor block rather than a small disc. */
   private BlockState hoeArenaWant(int x, int y, int z) {
      return y == floorY() && x >= minX() + 2 && x <= maxX() - 2 && z >= minZ() + 2 && z <= maxZ() - 2
         ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
   }

   public BlockState color(int lx, int lz) {
      return colorByIndex(Math.floorMod(lx * 31 + lz * 17 + (int) template().seed(), 6));
   }

   /** Repaints the colour-run floor into organic regions for the next callout. */
   public void refreshColorRun(ServerLevel level, long layoutSeed) {
      if (type() != PartyGameType.COLORFUL_RUN || level == null) return;
      int radius = size() / 2 - 3;
      int r2 = radius * radius;
      for (int x = centerX() - radius; x <= centerX() + radius; x++) {
         for (int z = centerZ() - radius; z <= centerZ() + radius; z++) {
            int dx = x - centerX(), dz = z - centerZ();
            if (dx * dx + dz * dz > r2) continue;
            BlockPos pos = new BlockPos(x, floorY(), z);
            BlockState next = colorRunColor(x, z, layoutSeed);
            if (!level.getBlockState(pos).equals(next)) level.setBlock(pos, next, 2);
         }
      }
   }

   /** Voronoi-like colour islands look deliberately uneven while guaranteeing every colour appears. */
   private BlockState colorRunColor(int x, int z, long layoutSeed) {
      long best = Long.MAX_VALUE;
      int colour = 0;
      int span = Math.max(8, size() - 8);
      for (int point = 0; point < 18; point++) {
         // One guaranteed island of each colour stays inside the playable circle.
         int px = point < 6 ? centerX() + (int) Math.round(Math.cos(point * Math.PI / 3.0) * size() / 4.0)
            : minX() + 4 + Math.floorMod(hash(layoutSeed, point, 17, 31), span);
         int pz = point < 6 ? centerZ() + (int) Math.round(Math.sin(point * Math.PI / 3.0) * size() / 4.0)
            : minZ() + 4 + Math.floorMod(hash(layoutSeed, point, 43, 71), span);
         long dx = x - px, dz = z - pz;
         long distance = dx * dx + dz * dz;
         if (distance < best) { best = distance; colour = point % 6; }
      }
      return colorByIndex(colour);
   }

   private static BlockState colorByIndex(int index) {
      return switch (index) {
         case 0 -> Blocks.RED_WOOL.defaultBlockState();
         case 1 -> Blocks.BLUE_WOOL.defaultBlockState();
         case 2 -> Blocks.GREEN_WOOL.defaultBlockState();
         case 3 -> Blocks.YELLOW_WOOL.defaultBlockState();
         case 4 -> Blocks.PURPLE_WOOL.defaultBlockState();
         default -> Blocks.ORANGE_WOOL.defaultBlockState();
      };
   }

   private static int hash(long seed, int x, int y, int z) {
      long value = seed + x * 341873128712L + y * 132897987541L + z * 42317861L;
      value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
      value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
      return (int) (value ^ (value >>> 33));
   }

   public BlockState ore(int lx, int lz) {
      return switch (Math.floorMod(lx * 13 + lz * 7 + (int) template().seed(), 12)) {
         case 0 -> Blocks.DIAMOND_ORE.defaultBlockState();
         case 1 -> Blocks.EMERALD_ORE.defaultBlockState();
         case 2, 3 -> Blocks.GOLD_ORE.defaultBlockState();
         case 4, 5 -> Blocks.IRON_ORE.defaultBlockState();
         case 6, 7 -> Blocks.REDSTONE_ORE.defaultBlockState();
         default -> Blocks.COAL_ORE.defaultBlockState();
      };
   }
}
