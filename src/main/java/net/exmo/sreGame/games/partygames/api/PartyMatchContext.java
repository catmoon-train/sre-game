package net.exmo.sreGame.games.partygames.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.partygames.PartyArena;
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

/** Narrow capability object supplied to one official game controller. */
public final class PartyMatchContext {
   public interface FinishHandler { void finish(UUID winner, String reason); }

   private final GameContext game;
   private final GameRoom room;
   private final PartyArena arena;
   private final UUID matchId;
   private final List<UUID> seats;
   private final Map<UUID, Integer> scores = new HashMap<>();
   private final List<Entity> ownedEntities = new ArrayList<>();
   private final Random random;
   private final FinishHandler finish;
   private int elapsedTicks;

   public PartyMatchContext(GameContext game, GameRoom room, PartyArena arena, UUID matchId, List<UUID> seats, long seed, FinishHandler finish) {
      this.game = game; this.room = room; this.arena = arena; this.matchId = matchId;
      this.seats = List.copyOf(seats); this.random = new Random(seed ^ matchId.getMostSignificantBits()); this.finish = finish;
      for (UUID seat : seats) scores.put(seat, 0);
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
   public UUID opponent(UUID id) { return seats.stream().filter(value -> !value.equals(id)).findFirst().orElse(null); }
   public int seat(UUID id) { return seats.indexOf(id); }
   public int team(UUID id) {
      // Official 101-114 games are fixed two-seat duels. Keeping the mapping tied
      // to seat order prevents a malformed/custom room team assignment from
      // giving both opponents the same visual ownership colour.
      return seat(id) + 1;
   }
   public PartyColor color(UUID id) { return PartyColor.ofTeam(team(id)); }
   public int score(UUID id) { return scores.getOrDefault(id, 0); }
   public void score(UUID id, int value) { scores.put(id, value); }
   public int addScore(UUID id, int delta) { int value = score(id) + delta; scores.put(id, value); return value; }
   public Map<UUID, Integer> scores() { return Map.copyOf(scores); }
   public BlockPos local(int x, int y, int z) { return new BlockPos(arena.minX() + x, arena.baseY() + y, arena.minZ() + z); }
   public Vec3 local(double x, double y, double z) { return new Vec3(arena.minX() + x, arena.baseY() + y, arena.minZ() + z); }
   public Vec3 anchor(String name, double fallbackX, double fallbackY, double fallbackZ) {
      double[] value = arena.anchors().get(name);
      return value == null || value.length < 3 ? local(fallbackX, fallbackY, fallbackZ) : local(value[0], value[1], value[2]);
   }
   public BlockPos anchorBlock(String name, int fallbackX, int fallbackY, int fallbackZ) {
      Vec3 value = anchor(name, fallbackX, fallbackY, fallbackZ);
      return BlockPos.containing(value);
   }
   public void own(Entity entity) {
      if (entity == null) return;
      entity.addTag("sre_mp2_" + matchId);
      arena.ownSceneEntity(entity.getUUID());
      ownedEntities.add(entity);
   }
   public void forEachPlayer(Consumer<ServerPlayer> action) { for (UUID id : seats) { ServerPlayer player = player(id); if (player != null) action.accept(player); } }
   public void broadcast(String message) { game.broadcast(room, message); }
   public void send(ServerPlayer player, String message) { game.send(player, message); }
   public void actionbar(ServerPlayer player, String message) { player.displayClientMessage(TextUtil.color(message), true); }
   public void title(ServerPlayer player, String title, String subtitle) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }
   public void sound(SoundEvent sound, float volume, float pitch) { forEachPlayer(player -> player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch)); }
   public void win(UUID winner, String reason) { finish.finish(winner, reason); }
   public void draw(String reason) { finish.finish(null, reason); }
   public void close() {
      for (Entity entity : List.copyOf(ownedEntities)) if (entity != null && !entity.isRemoved()) entity.discard();
      ownedEntities.clear();
   }
}
