package net.exmo.sreGame.player;

import com.mojang.authlib.properties.Property;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side player skin manipulation.
 *
 * <p>A player's skin lives in the {@code textures} property of their {@link com.mojang.authlib.GameProfile}.
 * Changing it at runtime requires rewriting that property and re-broadcasting the updated profile to every
 * client so their player models refresh. The original texture is remembered the first time a skin is
 * applied, so {@link #reset} can restore it afterwards.
 */
public final class SkinManager {
   public static final String TEXTURES = "textures";

   private static final Map<UUID, Property> ORIGINALS = new ConcurrentHashMap<>();
   private static final Set<UUID> CAPTURED = ConcurrentHashMap.newKeySet();

   private SkinManager() {
   }

   /** Returns the current {@code textures} property of the player, or {@code null} if they have none. */
   public static Property currentTexture(ServerPlayer player) {
      Collection<Property> props = player.getGameProfile().getProperties().get(TEXTURES);
      if (props != null && !props.isEmpty()) {
         return props.iterator().next();
      }
      return null;
   }

   /** Whether we have stored an original texture for this player (i.e. they have been modified before). */
   public static boolean hasOriginal(ServerPlayer player) {
      return CAPTURED.contains(player.getUUID());
   }

   /**
    * Applies a new skin texture to the player.
    *
    * @param value     base64-encoded texture JSON (must not be empty)
    * @param signature optional texture signature (may be {@code null})
    */
   public static void apply(ServerPlayer player, String value, String signature) {
      if (value == null || value.isEmpty()) {
         reset(player);
         return;
      }
      rememberOriginal(player);
      Property property = new Property(TEXTURES, value, signature == null ? "" : signature);
      player.getGameProfile().getProperties().removeAll(TEXTURES);
      player.getGameProfile().getProperties().put(TEXTURES, property);
      resendProfile(player);
   }

   /** Copies {@code source}'s full texture (value + signature) onto {@code target}. */
   public static boolean copy(ServerPlayer target, ServerPlayer source) {
      Property texture = currentTexture(source);
      if (texture == null) {
         return false;
      }
      apply(target, texture.value(), texture.signature());
      return true;
   }

   /**
    * Restores the player's original texture if it was captured, otherwise clears the skin back to default.
    *
    * @return {@code true} if an original texture was restored, {@code false} if the skin was simply cleared.
    */
   public static boolean reset(ServerPlayer player) {
      CAPTURED.remove(player.getUUID());
      Property original = ORIGINALS.remove(player.getUUID());
      player.getGameProfile().getProperties().removeAll(TEXTURES);
      if (original != null) {
         player.getGameProfile().getProperties().put(TEXTURES, original);
      }
      resendProfile(player);
      return original != null;
   }

   private static void rememberOriginal(ServerPlayer player) {
      if (!CAPTURED.add(player.getUUID())) {
         return;
      }
      Property texture = currentTexture(player);
      if (texture != null) {
         ORIGINALS.put(player.getUUID(), new Property(TEXTURES, texture.value(), texture.signature()));
      }
   }

   private static void resendProfile(ServerPlayer player) {
      var server = player.getServer();
      if (server == null) {
         return;
      }
      for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
         observer.connection.send(new ClientboundPlayerInfoRemovePacket(java.util.List.of(player.getUUID())));
         observer.connection.send(
            new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
      }
   }
}
