package net.exmo.sreGame.games.football;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public final class FootballManager {
   private final GameContext ctx;
   private final FootballArenaManager arenas;
   private final Map<UUID, FootballMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, FootballMatch> byId = new ConcurrentHashMap<>();
   public FootballManager(GameContext ctx) { this.ctx = ctx; this.arenas = new FootballArenaManager(ctx); }
   public FootballArenaManager arenas() { return arenas; }
   public FootballMatch get(UUID player) { return byPlayer.get(player); }
   public FootballMatch getById(UUID id) { return byId.get(id); }
   public boolean isPlaying(ServerPlayer player) { return player != null && byPlayer.containsKey(player.getUUID()); }
   public UUID start(GameRoom room) {
      FootballArena arena = arenas.acquire(); if (arena == null) return null;
      FootballMatch match = new FootballMatch(ctx, room, new ArrayList<>(room.members()), arena);
      byId.put(match.id(), match); for (UUID id : room.members()) byPlayer.put(id, match); room.setActiveMatchId(match.id());
      boolean queued = arenas.prepare(arena, () -> { if (byId.containsKey(match.id())) { room.setState(RoomState.PLAYING); match.start(); } });
      if (queued) ctx.broadcast(room, "&e正在生成足球场地，请稍候…");
      return match.id();
   }
   public void tick() { arenas.tick(); for (FootballMatch match : List.copyOf(byId.values())) match.tick(); }
   public void onLeave(ServerPlayer player) { FootballMatch match = byPlayer.remove(player.getUUID()); if (match != null) match.onLeave(player.getUUID()); }
   public void remove(FootballMatch match) { byId.remove(match.id()); byPlayer.entrySet().removeIf(e -> e.getValue() == match); }
   public void endAll() { for (FootballMatch match : List.copyOf(byId.values())) match.endNow(); }
   public boolean handleDamage(ServerPlayer player, DamageSource source) { FootballMatch match = get(player.getUUID()); return match != null && match.handleDamage(player); }
   public boolean handleDeath(ServerPlayer player) { FootballMatch match = get(player.getUUID()); return match != null && match.handleDeath(player); }
   public boolean handleAttack(ServerPlayer player, Entity entity) { FootballMatch match = get(player.getUUID()); return match != null && match.handleAttack(player, entity); }
   public void handleSwing(ServerPlayer player) { FootballMatch match = get(player.getUUID()); if (match != null) match.handleSwing(player); }
   public boolean containsEntity(Entity entity) { return arenas.contains(entity); }
   public boolean openIfPlaying(ServerPlayer player) { if (!isPlaying(player)) return false; ctx.send(player, "&7足球大战进行中。撞球带球，空手左键射门。"); return true; }
}
