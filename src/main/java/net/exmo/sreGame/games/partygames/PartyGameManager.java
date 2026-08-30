package net.exmo.sreGame.games.partygames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.exmo.sreGame.games.partygames.official.OfficialPartyGames;
import net.exmo.sreGame.games.partygames.official.OfficialPartyMatch;
import net.exmo.sreGame.games.partygames.team.TeamPartyGames;
import net.exmo.sreGame.games.partygames.team.TeamPartyMatch;
import net.exmo.sreGame.games.partygames.scene.PartySceneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PartyGameManager {
   private final GameContext ctx;
   private final MapTemplateStore maps;
   private final PartyArenaManager arenas;
   private final PartySceneStore scenes;
   private final Map<UUID, PartySession> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, PartySession> byId = new ConcurrentHashMap<>();
   private final Map<UUID, Consumer<ServerPlayer>> pendingRestores = new ConcurrentHashMap<>();

   public PartyGameManager(GameContext ctx) {
      this.ctx = ctx;
      this.maps = new MapTemplateStore(ctx.configDir());
      this.arenas = new PartyArenaManager(ctx);
      this.scenes = new PartySceneStore(ctx.configDir());
   }

   public MapTemplateStore maps() { return maps; }
   public PartyArenaManager arenas() { return arenas; }
   public PartySceneStore scenes() { return scenes; }
   public boolean isSceneGame(PartyGameType type) { return OfficialPartyGames.contains(type) || TeamPartyGames.contains(type); }
   public boolean isEnabled(PartyGameType type) {
      return type != null && ctx.config().partyGameEnabled(type) && (!isSceneGame(type) || scenes.ready(type));
   }
   public void setEnabled(PartyGameType type, boolean enabled) { if (type != null) ctx.config().setPartyGameEnabled(type, enabled); }
   public PartySession get(UUID player) { return byPlayer.get(player); }
   public PartySession getById(UUID id) { return byId.get(id); }
   public boolean isPlaying(ServerPlayer player) { return player != null && byPlayer.containsKey(player.getUUID()); }

   public UUID start(GameRoom room, PartyGameType type) {
      if (!isEnabled(type)) return null;
      boolean official = OfficialPartyGames.contains(type);
      boolean teamOfficial = TeamPartyGames.contains(type);
      boolean sceneGame = official || teamOfficial;
      var scene = sceneGame ? scenes.get(type) : null;
      MapTemplate template = sceneGame && scene != null
         ? new MapTemplate(type.id() + "-official", type, java.util.Objects.hashCode(scene.manifest().sha256()), Map.of())
         : maps.choose(type, room.partyGameSettings().mapId(type));
      if (template == null) return null;
      if (sceneGame && scene == null) return null;
      PartyArena arena = arenas.acquire(template, room.members().size(), scene);
      if (arena == null) return null;
      PartySession match = official
         ? new OfficialPartyMatch(ctx, room, type, template, arena)
         : teamOfficial
            ? new TeamPartyMatch(ctx, room, type, template, arena)
            : new PartyMatch(ctx, room, type, template, arena);
      byId.put(match.id(), match);
      for (UUID uuid : room.members()) byPlayer.put(uuid, match);
      room.setActiveMatchId(match.id());
      if (!arenas.prepare(arena, () -> {
         if (byId.containsKey(match.id())) { room.setState(RoomState.PLAYING); match.start(); }
      }, () -> {
         if (byId.containsKey(match.id())) match.endNow();
      })) { remove(match); arenas.release(arena); return null; }
      ctx.broadcast(room, "&e正在生成 " + type.displayName() + " 场地，请稍候…");
      return match.id();
   }

   public void load() { maps.load(); scenes.load(); }
   public void tick() { arenas.tick(); for (PartySession match : List.copyOf(byId.values())) match.tick(); }
   public void endAll() { for (PartySession match : List.copyOf(byId.values())) match.endNow(); }
   public void remove(PartySession match) { byId.remove(match.id()); byPlayer.entrySet().removeIf(entry -> entry.getValue() == match); }
   public void onLeave(ServerPlayer player) { PartySession match = get(player.getUUID()); if (match != null) match.onLeave(player.getUUID()); }
   public void deferRestore(UUID player, Consumer<ServerPlayer> restore) { if (player != null && restore != null) pendingRestores.put(player, restore); }
   public void onJoin(ServerPlayer player) { Consumer<ServerPlayer> restore = player == null ? null : pendingRestores.remove(player.getUUID()); if (restore != null) restore.accept(player); }
   public boolean handleDamage(ServerPlayer player, DamageSource source) { PartySession match = get(player.getUUID()); return match != null && match.handleDamage(player, source); }
   public boolean handleDeath(ServerPlayer player) { PartySession match = get(player.getUUID()); return match != null && match.handleDeath(player); }
   public boolean handleAttack(ServerPlayer player, Entity target) { PartySession match = get(player.getUUID()); return match != null && match.handleAttack(player, target); }
   /** Returns true when a party game handled the hit and vanilla damage must be cancelled. */
   public boolean handleMobDamage(Entity entity, DamageSource source) { for (PartySession match : byId.values()) if (match.handleMobDamage(entity, source)) return true; return false; }
   public boolean handleMobDeath(Entity entity, DamageSource source) { for (PartySession match : byId.values()) if (match.handleMobDeath(entity, source)) return true; return false; }
   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, net.minecraft.world.item.ItemStack stack) { PartySession match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseBlock(player, hit, stack); }
   public InteractionResult handleUseItem(ServerPlayer player, net.minecraft.world.item.ItemStack stack) { PartySession match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack); }
   public InteractionResult handleUseEntity(ServerPlayer player, Entity entity) { PartySession match = get(player.getUUID()); return match == null ? InteractionResult.PASS : match.handleUseEntity(player, entity); }
   public boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state) { PartySession match = get(player.getUUID()); return match != null && match.tryBreak(player, pos, state); }
   public void handleLeftClick(ServerPlayer player) { PartySession match = get(player.getUUID()); if (match != null) match.handleLeftClick(player); }
   public boolean handleJump(ServerPlayer player) { PartySession match = get(player.getUUID()); return match != null && match.handleJump(player); }
   public void handleSneak(ServerPlayer player, boolean sneaking) { PartySession match = get(player.getUUID()); if (match != null) match.handleSneak(player, sneaking); }
   public void handleHotbar(ServerPlayer player, int previousSlot, int newSlot) { PartySession match = get(player.getUUID()); if (match != null) match.handleHotbar(player, previousSlot, newSlot); }
   public void handleDrop(ServerPlayer player, ItemStack stack) { PartySession match = get(player.getUUID()); if (match != null) match.handleDrop(player, stack); }
   public boolean containsEntity(Entity entity) { return arenas.contains(entity); }
}
