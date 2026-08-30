package net.exmo.sreGame.games.partygames;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.partygames.scene.PartySceneBundle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Incremental reset/generation queue for all party-game arena slots. */
public final class PartyArenaManager {
   private static final int MAX_ARENAS = 8;
   private final GameContext ctx;
   private final List<PartyArena> arenas = new ArrayList<>();
   private final Deque<FillTask> queue = new ArrayDeque<>();

   PartyArenaManager(GameContext ctx) { this.ctx = ctx; }

   public PartyArena acquire(MapTemplate template, int playerCount) {
      return acquire(template, playerCount, null);
   }

   public PartyArena acquire(MapTemplate template, int playerCount, PartySceneBundle scene) {
      if (level() == null || template == null) return null;
      for (PartyArena arena : arenas) {
         if (arena.state() == PartyArena.State.IDLE) {
            arena.assign(template, playerCount, scene);
            arena.setState(PartyArena.State.IN_USE);
            forceChunks(arena, true);
            return arena;
         }
      }
      if (arenas.size() >= MAX_ARENAS) return null;
      PartyArena arena = ensure(arenas.size());
      arena.assign(template, playerCount, scene);
      arena.setState(PartyArena.State.IN_USE);
      forceChunks(arena, true);
      return arena;
   }

   public boolean prepare(PartyArena arena, Runnable done) {
      return prepare(arena, done, null);
   }

   public boolean prepare(PartyArena arena, Runnable done, Runnable failed) {
      if (arena == null || level() == null) return false;
      queue.addLast(FillTask.prepare(arena, done, failed));
      return true;
   }

   public void tick() {
      ServerLevel level = level();
      if (level == null) return;
      int budget = Math.max(1000, ctx.config().partyGamesBlocksPerTick());
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      while (budget > 0 && !queue.isEmpty()) {
         FillTask task = queue.peekFirst();
         if (task.arena.state() != task.expectedState) { queue.removeFirst(); continue; }
         int used;
         try { used = task.step(level, budget, pos); }
         catch (RuntimeException error) {
            queue.removeFirst();
            SreGame.LOGGER.error("Failed to place/reset party arena {}", task.arena.slot(), error);
            if (task.failedCallback != null) task.failedCallback.run();
            else if (task.cleanup) {
               forceChunks(task.arena, false); task.arena.clearSceneEntities(); task.arena.assign(null); task.arena.setState(PartyArena.State.IDLE);
            } else release(task.arena);
            continue;
         }
         budget -= Math.max(1, used);
         if (task.done) {
            queue.removeFirst();
            if (task.cleanup || task.arena.scene() == null) clearEntities(level, task.arena);
            if (task.doneCallback != null) task.doneCallback.run();
            if (task.cleanup) { forceChunks(task.arena, false); task.arena.clearSceneEntities(); task.arena.assign(null); task.arena.setState(PartyArena.State.IDLE); }
         }
      }
   }

   public void release(PartyArena arena) {
      if (arena == null) return;
      ServerLevel level = level();
      if (level != null) clearEntities(level, arena);
      queue.removeIf(task -> task.arena == arena);
      arena.setState(PartyArena.State.CLEANING);
      queue.addLast(FillTask.cleanup(arena));
   }

   public ServerLevel level() { return ctx.config().partyGamesWorld(ctx.server()); }

   public boolean contains(Entity entity) {
      if (entity == null) return false;
      for (PartyArena arena : arenas) if (arena.state() == PartyArena.State.IN_USE && arena.contains(entity.getX(), entity.getY(), entity.getZ())) return true;
      return false;
   }

   public void clearEntities(ServerLevel level, PartyArena arena) {
      AABB box = new AABB(arena.minX(), arena.baseY() - 12, arena.minZ(), arena.maxX() + 1, arena.topY() + 8, arena.maxZ() + 1);
      for (Entity entity : level.getEntities((Entity) null, box, entity -> !(entity instanceof Player))) {
         if (arena.scene() == null || arena.ownsSceneEntity(entity.getUUID())) entity.discard();
      }
   }

   private void forceChunks(PartyArena arena, boolean forced) {
      ServerLevel level = level(); if (level == null) return;
      for (int chunkX = arena.minX() >> 4; chunkX <= arena.maxX() >> 4; chunkX++) {
         for (int chunkZ = arena.minZ() >> 4; chunkZ <= arena.maxZ() >> 4; chunkZ++) level.setChunkForced(chunkX, chunkZ, forced);
      }
   }

   private PartyArena ensure(int slot) {
      while (arenas.size() <= slot) {
         int index = arenas.size();
         int columns = 4;
         int x = ctx.config().partyGamesOriginX() + (index % columns) * PartyArena.STRIDE;
         int z = ctx.config().partyGamesOriginZ() + (index / columns) * PartyArena.STRIDE;
         arenas.add(new PartyArena(index, new BlockPos(x, ctx.config().originY(), z)));
      }
      return arenas.get(slot);
   }

   private static final class FillTask {
      private final PartyArena arena;
      private final Runnable doneCallback;
      private final Runnable failedCallback;
      private final PartyArena.State expectedState;
      private final boolean cleanup;
      private boolean placing;
      private boolean done;
      private int shard;
      private int x;
      private int y;
      private int z;

      private FillTask(PartyArena arena, Runnable doneCallback, Runnable failedCallback, PartyArena.State expectedState, boolean cleanup) {
         this.arena = arena;
         this.doneCallback = doneCallback;
         this.failedCallback = failedCallback;
         this.expectedState = expectedState;
         this.cleanup = cleanup;
         this.x = arena.minX(); this.y = arena.baseY(); this.z = arena.minZ();
      }

      static FillTask prepare(PartyArena arena, Runnable doneCallback, Runnable failedCallback) { return new FillTask(arena, doneCallback, failedCallback, PartyArena.State.IN_USE, false); }
      static FillTask cleanup(PartyArena arena) { return new FillTask(arena, null, null, PartyArena.State.CLEANING, true); }

      private int step(ServerLevel level, int budget, BlockPos.MutableBlockPos pos) {
         if (placing && arena.scene() != null) {
            if (shard < arena.scene().shardCount()) {
               int cost = arena.scene().shardCost(shard);
               AABB arenaBox = new AABB(arena.minX(), arena.baseY() - 2, arena.minZ(), arena.maxX() + 1, arena.topY() + 2, arena.maxZ() + 1);
               Set<UUID> before = level.getEntities((Entity) null, arenaBox, entity -> !(entity instanceof Player)).stream().map(Entity::getUUID).collect(Collectors.toSet());
               arena.scene().placeShard(level, arena.origin(), shard++);
               for (Entity entity : level.getEntities((Entity) null, arenaBox, entity -> !(entity instanceof Player) && !before.contains(entity.getUUID()))) arena.ownSceneEntity(entity.getUUID());
               if (shard >= arena.scene().shardCount()) done = true;
               return Math.max(budget, cost); // one structure shard per server tick
            }
            done = true;
            return 1;
         }
         int used = 0;
         while (used < budget && !done) {
            pos.set(x, y, z);
            BlockState want = placing && !cleanup ? arena.structureWant(x, y, z) : Blocks.AIR.defaultBlockState();
            if (!level.getBlockState(pos).equals(want)) level.setBlock(pos, want, 2);
            used++;
            advance();
         }
         return used;
      }

      private void advance() {
         y++;
         if (y <= arena.topY()) return;
         y = arena.baseY(); z++;
         if (z <= arena.maxZ()) return;
         z = arena.minZ(); x++;
         if (x <= arena.maxX()) return;
         if (!placing && !cleanup) { placing = true; x = arena.minX(); y = arena.baseY(); z = arena.minZ(); }
         else done = true;
      }
   }
}
