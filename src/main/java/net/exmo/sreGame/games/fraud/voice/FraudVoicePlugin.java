package net.exmo.sreGame.games.fraud.voice;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;

/**
 * 诈骗大师语音：未通话完全静音；通话中拉进 Isolated SVC 组（对照 MeetingVoice setGroup）。
 */
public final class FraudVoicePlugin implements VoicechatPlugin {
   public static volatile VoicechatServerApi SERVER_API;
   private static final Map<UUID, Group> GROUPS = new ConcurrentHashMap<>();

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
      registration.registerEvent(VoicechatServerStartedEvent.class, event -> SERVER_API = event.getVoicechat());
      registration.registerEvent(MicrophonePacketEvent.class, FraudVoicePlugin::onMic);
      registration.registerEvent(LocationalSoundPacketEvent.class, FraudVoicePlugin::onLocational);
      registration.registerEvent(EntitySoundPacketEvent.class, FraudVoicePlugin::onEntity);
      registration.registerEvent(StaticSoundPacketEvent.class, FraudVoicePlugin::onStatic);
   }

   public static void joinCall(UUID player, UUID callId) {
      if (missing() || player == null || callId == null) {
         return;
      }
      VoicechatConnection connection = SERVER_API.getConnectionOf(player);
      if (connection == null) {
         return;
      }
      Group group = GROUPS.computeIfAbsent(callId, id -> SERVER_API.groupBuilder()
         .setHidden(true)
         .setId(id)
         .setName("电话 " + id.toString().substring(0, 8))
         .setPersistent(true)
         .setType(Group.Type.ISOLATED)
         .build());
      if (group == null) {
         GROUPS.remove(callId);
         return;
      }
      Group current = connection.getGroup();
      if (current != null && callId.equals(current.getId())) {
         return;
      }
      connection.setGroup(group);
   }

   public static void leaveCall(UUID player) {
      if (missing() || player == null) {
         return;
      }
      VoicechatConnection connection = SERVER_API.getConnectionOf(player);
      if (connection != null && connection.isInGroup()) {
         connection.setGroup(null);
      }
   }

   public static void dropCall(UUID callId) {
      if (callId != null) {
         GROUPS.remove(callId);
      }
   }

   public static void onMic(MicrophonePacketEvent event) {
      UUID sender = uuidOf(event.getSenderConnection());
      FraudMasterMatch match = matchOf(sender);
      if (match == null) {
         return;
      }
      if (match.phones().inCall(sender)) {
         return;
      }
      event.cancel();
   }

   private static void onLocational(LocationalSoundPacketEvent event) {
      isolate(event.getSenderConnection(), event.getReceiverConnection(), event);
   }

   private static void onEntity(EntitySoundPacketEvent event) {
      isolate(event.getSenderConnection(), event.getReceiverConnection(), event);
   }

   private static void onStatic(StaticSoundPacketEvent event) {
      isolate(event.getSenderConnection(), event.getReceiverConnection(), event);
   }

   public static void isolate(VoicechatConnection sender, VoicechatConnection receiver, de.maxhenkel.voicechat.api.events.SoundPacketEvent<?> event) {
      UUID from = uuidOf(sender);
      UUID to = uuidOf(receiver);
      FraudMasterMatch sendMatch = matchOf(from);
      FraudMasterMatch recvMatch = matchOf(to);
      if (sendMatch == null && recvMatch == null) {
         return;
      }
      if (sameSvcGroup(sender, receiver)) {
         return;
      }
      if (sendMatch != null && recvMatch != null && sendMatch == recvMatch && sendMatch.phones().sameCall(from, to)) {
         return;
      }
      event.cancel();
   }

   private static boolean sameSvcGroup(VoicechatConnection sender, VoicechatConnection receiver) {
      if (sender == null || receiver == null || !sender.isInGroup() || !receiver.isInGroup()) {
         return false;
      }
      Group left = sender.getGroup();
      Group right = receiver.getGroup();
      return left != null && right != null && left.getId().equals(right.getId());
   }

   private static UUID uuidOf(VoicechatConnection connection) {
      if (connection == null || connection.getPlayer() == null) {
         return null;
      }
      return connection.getPlayer().getUuid();
   }

   private static FraudMasterMatch matchOf(UUID uuid) {
      if (uuid == null) {
         return null;
      }
      GameContext ctx = SreGame.getContext();
      return ctx == null ? null : ctx.fraudMaster().get(uuid);
   }
}
