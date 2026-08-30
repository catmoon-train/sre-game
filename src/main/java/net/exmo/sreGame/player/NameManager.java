package net.exmo.sreGame.player;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.mixin.ChunkMapAccessor;
import net.exmo.sreGame.mixin.PlayerGameProfileAccessor;
import net.exmo.sreGame.mixin.TrackedEntityAccessor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runtime player rename. The vanilla profile name is swapped (Chinese allowed, max 16
 * characters per the player-info packet) and then re-broadcast so tab list, nametag and
 * {@code getName()} all pick up the change. The original login name is remembered for
 * {@link #reset} and whitelist checks.
 */
public final class NameManager {
   public static final int MAX_LENGTH = 16;

   private static final Map<UUID, String> ORIGINALS = new ConcurrentHashMap<>();

   private NameManager() {
   }

   public static String originalName(ServerPlayer player) {
      String original = ORIGINALS.get(player.getUUID());
      return original != null ? original : player.getGameProfile().getName();
   }

   public static boolean isRenamed(ServerPlayer player) {
      return ORIGINALS.containsKey(player.getUUID());
   }

   /**
    * @return {@code null} on success, otherwise a color-coded error message
    */
   public static String apply(ServerPlayer player, String raw) {
      String name = sanitize(raw);
      if (name == null) {
         return "&c名字无效：不能为空，最长 " + MAX_LENGTH + " 个字符，且不能包含控制字符。";
      }
      if (name.equals(player.getGameProfile().getName())) {
         return "&c已经是这个名字了。";
      }
      if (taken(player, name)) {
         return "&c名字 &f" + name + " &c已被其他在线玩家使用。";
      }
      ORIGINALS.putIfAbsent(player.getUUID(), player.getGameProfile().getName());
      setProfileName(player, name);
      resend(player);
      return null;
   }

   /**
    * Restores the login name.
    *
    * @return {@code true} if a custom name was cleared
    */
   public static boolean reset(ServerPlayer player) {
      String original = ORIGINALS.remove(player.getUUID());
      if (original == null) {
         return false;
      }
      if (!original.equals(player.getGameProfile().getName())) {
         setProfileName(player, original);
         resend(player);
      }
      return true;
   }

   /** Restore the login name before the player is removed from the list. No packets. */
   public static void onDisconnect(ServerPlayer player) {
      String original = ORIGINALS.remove(player.getUUID());
      if (original == null || original.equals(player.getGameProfile().getName())) {
         return;
      }
      setProfileName(player, original);
   }

   public static String sanitize(String raw) {
      if (raw == null) {
         return null;
      }
      String name = raw.trim();
      if (name.isEmpty() || name.length() > MAX_LENGTH) {
         return null;
      }
      for (int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);
         if (c == '§' || Character.isISOControl(c)) {
            return null;
         }
      }
      return name;
   }

   private static boolean taken(ServerPlayer player, String name) {
      var server = player.getServer();
      if (server == null) {
         return false;
      }
      for (ServerPlayer other : server.getPlayerList().getPlayers()) {
         if (other != player && other.getGameProfile().getName().equalsIgnoreCase(name)) {
            return true;
         }
      }
      return false;
   }

   private static void setProfileName(ServerPlayer player, String name) {
      GameProfile old = player.getGameProfile();
      GameProfile neu = new GameProfile(old.getId(), name);
      neu.getProperties().putAll(old.getProperties());
      ((PlayerGameProfileAccessor) player).sre$setGameProfile(neu);
   }

   private static void resend(ServerPlayer player) {
      var server = player.getServer();
      if (server == null) {
         return;
      }
      boolean hidden = PlayerVisibility.isHidden(player);
      ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
      ClientboundPlayerInfoUpdatePacket add = new ClientboundPlayerInfoUpdatePacket(
         ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player);
      for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
         if (hidden && observer != player) {
            continue;
         }
         observer.connection.send(remove);
         observer.connection.send(add);
         if (PlayerVisibility.isTabHidden(player) && observer != player) {
            observer.connection.send(new ClientboundPlayerInfoUpdatePacket(
               ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, player));
         }
         if (observer != player) {
            refreshEntity(observer, player);
         }
      }
   }

   private static void refreshEntity(ServerPlayer observer, ServerPlayer target) {
      ChunkMap chunkMap = target.serverLevel().getChunkSource().chunkMap;
      Object tracked = ((ChunkMapAccessor) chunkMap).sre$entityMap().get(target.getId());
      if (tracked instanceof TrackedEntityAccessor accessor) {
         accessor.sre$removePlayer(observer);
         accessor.sre$updatePlayer(observer);
      }
   }
}
