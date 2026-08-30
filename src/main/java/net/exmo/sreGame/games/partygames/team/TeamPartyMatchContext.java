package net.exmo.sreGame.games.partygames.team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.partygames.PartyArena;
import net.exmo.sreGame.games.partygames.api.PartyColor;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Capabilities exposed to one 201-214 team controller. */
public final class TeamPartyMatchContext {
   public interface FinishHandler { void finish(UUID winner, String reason); }

   private final GameContext game;
   private final GameRoom room;
   private final PartyArena arena;
   private final UUID matchId;
   private final List<UUID> seats;
   private final Map<UUID, Integer> teams = new HashMap<>();
   private final Map<UUID, Integer> scores = new HashMap<>();
   private final Map<Integer, Integer> teamScores = new HashMap<>();
   private final Map<UUID, Boolean> alive = new HashMap<>();
   private final List<Entity> ownedEntities = new ArrayList<>();
   private final Random random;
   private final FinishHandler finish;
   private int elapsedTicks;

   public TeamPartyMatchContext(GameContext game, GameRoom room, PartyArena arena, UUID matchId, List<UUID> seats, long seed, FinishHandler finish) {
      this.game = game; this.room = room; this.arena = arena; this.matchId = matchId; this.seats = List.copyOf(seats);
      // The scene/template seed is the sole random input.  This makes a
      // controller replayable in GameTest while the state itself remains
      // room-local (entities, scores and timers are never shared).
      this.random = new Random(seed); this.finish = finish;
      assignTeams();
      for (UUID seat : seats) { scores.put(seat, 0); alive.put(seat, true); }
      teamScores.put(1, 0); teamScores.put(2, 0);
   }

   private void assignTeams() {
      int one = 0, two = 0;
      for (UUID seat : seats) {
         int explicit = room.duelSettings().teamOf(seat);
         if (explicit == 1) { teams.put(seat, 1); one++; }
         else if (explicit == 2) { teams.put(seat, 2); two++; }
      }
      for (UUID seat : seats) if (!teams.containsKey(seat)) {
         int team = one <= two ? 1 : 2; teams.put(seat, team); if (team == 1) one++; else two++;
      }
      // A malformed persisted room may contain everyone on one side. Keep a
      // playable two-sided match rather than silently creating friendly fire.
      if (one == 0 || two == 0) {
         teams.clear();
         for (int i = 0; i < seats.size(); i++) teams.put(seats.get(i), i % 2 == 0 ? 1 : 2);
      }
   }

   public GameContext game() { return game; }
   public GameRoom room() { return room; }
   public PartyArena arena() { return arena; }
   public UUID matchId() { return matchId; }
   public List<UUID> seats() { return seats; }
   public ServerLevel level() { return game.partyGames().arenas().level(); }
   public Random random() { return random; }
   public int elapsedTicks() { return elapsedTicks; }
   public void advanceTick() { elapsedTicks++; }
   public ServerPlayer player(UUID id) { return game.player(id); }
   public int team(UUID id) { return teams.getOrDefault(id, 1); }
   public PartyColor color(UUID id) { return PartyColor.ofTeam(team(id)); }
   public List<UUID> teamMembers(int team) { return seats.stream().filter(id -> team(id) == team).toList(); }
   public int score(UUID id) { return scores.getOrDefault(id, 0); }
   public void score(UUID id, int value) { scores.put(id, value); }
   public int addScore(UUID id, int delta) { int value = score(id) + delta; scores.put(id, value); return value; }
   public int teamScore(int team) { return teamScores.getOrDefault(team, 0); }
   public void teamScore(int team, int value) { teamScores.put(team, value); }
   public int addTeamScore(int team, int delta) { int value = teamScore(team) + delta; teamScores.put(team, value); return value; }
   public boolean alive(UUID id) { return alive.getOrDefault(id, false); }
   public void alive(UUID id, boolean value) { alive.put(id, value); }
   public boolean teamAlive(int team) { return teamMembers(team).stream().anyMatch(this::alive); }
   public int livingCount(int team) { return (int) teamMembers(team).stream().filter(this::alive).count(); }
   public UUID opponent(UUID id) { return seats.stream().filter(value -> team(value) != team(id)).findFirst().orElse(null); }
   public BlockPos local(int x, int y, int z) { return new BlockPos(arena.minX() + x, arena.baseY() + y, arena.minZ() + z); }
   public Vec3 local(double x, double y, double z) { return new Vec3(arena.minX() + x, arena.baseY() + y, arena.minZ() + z); }
   public Vec3 anchor(String name, double fallbackX, double fallbackY, double fallbackZ) {
      double[] value = arena.anchors().get(name);
      return value == null || value.length < 3 ? local(fallbackX, fallbackY, fallbackZ) : local(value[0], value[1], value[2]);
   }
   public BlockPos anchorBlock(String name, int fallbackX, int fallbackY, int fallbackZ) { return BlockPos.containing(anchor(name, fallbackX, fallbackY, fallbackZ)); }
   public boolean inside(Vec3 pos) { return pos != null && arena.contains(pos.x, pos.y, pos.z); }
   public void own(Entity entity) { if (entity != null) { entity.addTag("sre_mp2_" + matchId); arena.ownSceneEntity(entity.getUUID()); ownedEntities.add(entity); } }
   public boolean owns(Entity entity) { return entity != null && (entity.getTags().contains("sre_mp2_" + matchId) || arena.ownsSceneEntity(entity.getUUID())); }
   public void forEachPlayer(Consumer<ServerPlayer> action) { for (UUID id : seats) { ServerPlayer player = player(id); if (player != null) action.accept(player); } }
   public void broadcast(String message) { game.broadcast(room, message); }
   public void send(ServerPlayer player, String message) { game.send(player, message); }
   public void actionbar(ServerPlayer player, String message) { if (player != null) player.displayClientMessage(TextUtil.color(message), true); }
   public void title(ServerPlayer player, String title, String subtitle) {
      if (player == null) return;
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }
   public void sound(SoundEvent sound, float volume, float pitch) { forEachPlayer(player -> player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch)); }
   public void winTeam(int team, String reason) { UUID winner = teamMembers(team).stream().filter(this::alive).findFirst().orElse(teamMembers(team).stream().findFirst().orElse(null)); finish.finish(winner, reason); }
   public void win(UUID winner, String reason) { finish.finish(winner, reason); }
   public void draw(String reason) { finish.finish(null, reason); }
   public void close() { for (Entity entity : List.copyOf(ownedEntities)) if (entity != null && !entity.isRemoved()) entity.discard(); ownedEntities.clear(); }
}
