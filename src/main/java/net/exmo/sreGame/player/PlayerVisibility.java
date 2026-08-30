package net.exmo.sreGame.player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.mixin.ChunkMapAccessor;
import net.exmo.sreGame.mixin.TrackedEntityAccessor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Global player hiding. A hidden player is removed from every other player's entity tracking (so their
 * model/name-tag disappears) and from the tab list. {@link net.exmo.sreGame.mixin.HiddenRacerTrackingMixin}
 * re-checks {@link #isHiddenFrom} on every tracking update so the player stays hidden while moving around.
 *
 * <p>{@link #hideTab} / {@link #unhideTab} only affect the tab list (the player's entity stays visible).
 * Call {@link #refreshTabFor} when a new viewer joins so already tab-hidden players stay hidden for them.
 */
public final class PlayerVisibility {
   private static final Set<UUID> HIDDEN = ConcurrentHashMap.newKeySet();
   private static final Set<UUID> TAB_HIDDEN = ConcurrentHashMap.newKeySet();

   private PlayerVisibility() {
   }

   public static boolean isHidden(ServerPlayer player) {
      return HIDDEN.contains(player.getUUID());
   }

   public static boolean isHiddenFrom(ServerPlayer viewer, Entity entity) {
      return entity instanceof ServerPlayer other && viewer != other && HIDDEN.contains(other.getUUID());
   }

   public static Set<UUID> hiddenPlayers() {
      return Set.copyOf(HIDDEN);
   }

   public static boolean isTabHidden(ServerPlayer player) {
      return TAB_HIDDEN.contains(player.getUUID());
   }

   public static Set<UUID> tabHiddenPlayers() {
      return Set.copyOf(TAB_HIDDEN);
   }

   public static void hide(ServerPlayer player) {
      HIDDEN.add(player.getUUID());
      var server = player.getServer();
      if (server == null) {
         return;
      }
      for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
         if (viewer == player) {
            continue;
         }
         removeEntity(viewer, player);
         viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
      }
   }

   public static void unhide(ServerPlayer player) {
      HIDDEN.remove(player.getUUID());
      var server = player.getServer();
      if (server == null) {
         return;
      }
      for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
         if (viewer == player) {
            continue;
         }
         viewer.connection.send(
            new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
         updateEntity(viewer, player);
      }
   }

   /**
    * Hides the player from every other viewer's tab list while keeping their entity, skin and name tag
    * visible. Uses the {@code UPDATE_LISTED} action so the client still has a {@code PlayerInfo} entry
    * (which is what the renderer reads for skin/name tag); only the tab-list visibility flag flips.
    */
   public static void hideTab(ServerPlayer player) {
      TAB_HIDDEN.add(player.getUUID());
      var server = player.getServer();
      if (server == null) {
         return;
      }
      ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
         ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, player);
      for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
         if (viewer == player) {
            continue;
         }
         viewer.connection.send(packet);
      }
   }

   /** Restores the player to every other viewer's tab list. */
   public static void unhideTab(ServerPlayer player) {
      TAB_HIDDEN.remove(player.getUUID());
      var server = player.getServer();
      if (server == null) {
         return;
      }
      ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
         ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, player);
      for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
         if (viewer == player) {
            continue;
         }
         viewer.connection.send(packet);
      }
   }

   /** Ensures late joiners receive the correct tab-list state for already hidden players. */
   public static void refreshTabFor(ServerPlayer viewer) {
      var server = viewer.getServer();
      if (server == null) return;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (player != viewer && isTabHidden(player)) {
            viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
               ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, player));
         }
      }
   }

   private static void removeEntity(ServerPlayer viewer, ServerPlayer target) {
      ChunkMap chunkMap = target.serverLevel().getChunkSource().chunkMap;
      Object tracked = ((ChunkMapAccessor) chunkMap).sre$entityMap().get(target.getId());
      if (tracked instanceof TrackedEntityAccessor accessor) {
         accessor.sre$removePlayer(viewer);
      }
   }

   private static void updateEntity(ServerPlayer viewer, ServerPlayer target) {
      ChunkMap chunkMap = target.serverLevel().getChunkSource().chunkMap;
      Object tracked = ((ChunkMapAccessor) chunkMap).sre$entityMap().get(target.getId());
      if (tracked instanceof TrackedEntityAccessor accessor) {
         accessor.sre$updatePlayer(viewer);
      }
   }
}
