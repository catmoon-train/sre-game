package net.exmo.sreGame.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.fakehuman.FakeHumanMatch;
import net.exmo.sreGame.fakehuman.Zone;
import net.exmo.sreGame.fraud.voice.FraudVoicePlugin;

/**
 * 统一 SVC 入口：谁是伪人分区组 + 诈骗大师电话隔离。
 */
public final class SreVoicePlugin implements VoicechatPlugin {
   public static volatile VoicechatServerApi SERVER_API;

   public static boolean missing() {
      return SERVER_API == null;
   }

   @Override
   public String getPluginId() {
      return SreGame.MOD_ID;
   }

   @Override
   public void initialize(VoicechatApi api) {
      VoicechatPlugin.super.initialize(api);
   }

   @Override
   public void registerEvents(EventRegistration registration) {
      registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
         SERVER_API = event.getVoicechat();
         FraudVoicePlugin.SERVER_API = event.getVoicechat();
      });
      registration.registerEvent(MicrophonePacketEvent.class, SreVoicePlugin::onMic);
      registration.registerEvent(LocationalSoundPacketEvent.class, event ->
         isolate(event.getSenderConnection(), event.getReceiverConnection(), event));
      registration.registerEvent(EntitySoundPacketEvent.class, event ->
         isolate(event.getSenderConnection(), event.getReceiverConnection(), event));
      registration.registerEvent(StaticSoundPacketEvent.class, event ->
         isolate(event.getSenderConnection(), event.getReceiverConnection(), event));
   }

   private static void onMic(MicrophonePacketEvent event) {
      UUID sender = uuidOf(event.getSenderConnection());
      FakeHumanMatch fake = fakeOf(sender);
      if (fake != null) {
         if (fake.voiceMuted(sender)) {
            event.cancel();
         }
         return;
      }
      CaveGuessersMatch cave = caveOf(sender);
      if (cave != null) {
         if (cave.voiceMuted(sender)) {
            event.cancel();
         }
         return;
      }
      FraudVoicePlugin.onMic(event);
   }

   private static void isolate(VoicechatConnection sender, VoicechatConnection receiver,
                               de.maxhenkel.voicechat.api.events.SoundPacketEvent<?> event) {
      UUID from = uuidOf(sender);
      UUID to = uuidOf(receiver);
      FakeHumanMatch sendFake = fakeOf(from);
      FakeHumanMatch recvFake = fakeOf(to);
      if (sendFake != null || recvFake != null) {
         if (sendFake == null || recvFake == null || sendFake != recvFake
            || !sendFake.sameVoiceZone(from, to)) {
            event.cancel();
         }
         return;
      }
      FraudVoicePlugin.isolate(sender, receiver, event);
   }

   public static void applyGroup(UUID player, Zone zone, UUID matchId) {
      if (missing() || player == null || zone == null || matchId == null) {
         return;
      }
      VoicechatConnection connection = SERVER_API.getConnectionOf(player);
      if (connection == null) {
         return;
      }
      Group group = groupOf(zone, matchId);
      if (group != null) {
         connection.setGroup(group);
      }
   }

   public static void leave(UUID player) {
      if (missing() || player == null) {
         return;
      }
      VoicechatConnection connection = SERVER_API.getConnectionOf(player);
      if (connection != null) {
         connection.setGroup(null);
      }
   }

   private static Group groupOf(Zone zone, UUID matchId) {
      String name = "fh-" + zone.name().toLowerCase() + "-" + matchId.toString().substring(0, 8);
      UUID id = UUID.nameUUIDFromBytes(("sre-fh-" + zone + matchId).getBytes());
      return SERVER_API.groupBuilder()
         .setHidden(true)
         .setId(id)
         .setName(name)
         .setPersistent(true)
         .setType(Group.Type.ISOLATED)
         .build();
   }

   private static UUID uuidOf(VoicechatConnection connection) {
      if (connection == null || connection.getPlayer() == null) {
         return null;
      }
      return connection.getPlayer().getUuid();
   }

   private static FakeHumanMatch fakeOf(UUID uuid) {
      GameContext ctx = SreGame.getContext();
      return ctx == null || uuid == null ? null : ctx.fakeHuman().get(uuid);
   }

   private static CaveGuessersMatch caveOf(UUID uuid) {
      GameContext ctx = SreGame.getContext();
      return ctx == null || uuid == null ? null : ctx.caveGuess().get(uuid);
   }
}
