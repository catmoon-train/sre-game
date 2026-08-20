package net.exmo.sreGame.games.fraud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.games.fraud.voice.FraudVoicePlugin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 拨号先响铃，对方接听后才进一对一通话；未接听保持静音。
 * 开放模式：通话中仍可来电，接听会挂断当前通、改与来电方单聊。
 * 占线模式：通话中直接忙音。
 * 成员表给 SVC 语音线程读，必须线程安全。
 */
public final class PhoneService {
   private static final int RING_TICKS = 15 * 20;

   private final FraudMasterMatch match;
   private final Map<UUID, CallSession> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, CallSession> byId = new ConcurrentHashMap<>();
   private final Map<UUID, Ringing> incoming = new ConcurrentHashMap<>();
   private final Map<UUID, Ringing> outgoing = new ConcurrentHashMap<>();
   private final Map<UUID, Long> talkTicks = new ConcurrentHashMap<>();
   private final Map<UUID, Set<UUID>> incomingDialers = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> roundDials = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> joinedTicks = new ConcurrentHashMap<>();
   private volatile boolean open;
   private boolean silencing;

   public PhoneService(FraudMasterMatch match) {
      this.match = match;
   }

   public boolean open() {
      return this.open;
   }

   public void setOpen(boolean open) {
      this.open = open;
      if (!open) {
         this.hangupAll();
      }
   }

   public void resetRoundDials() {
      this.roundDials.clear();
   }

   public int roundDials(UUID uuid) {
      return this.roundDials.getOrDefault(uuid, 0);
   }

   public long talkTicks(UUID uuid) {
      return this.talkTicks.getOrDefault(uuid, 0L);
   }

   public boolean neverDialed(UUID uuid) {
      Set<UUID> set = this.incomingDialers.get(uuid);
      return set == null || set.isEmpty();
   }

   public boolean inCall(UUID uuid) {
      return this.byPlayer.containsKey(uuid);
   }

   public boolean isIncoming(UUID uuid) {
      return this.incoming.containsKey(uuid);
   }

   public boolean isOutgoing(UUID uuid) {
      return this.outgoing.containsKey(uuid);
   }

   public UUID incomingCaller(UUID callee) {
      Ringing ring = this.incoming.get(callee);
      return ring == null ? null : ring.caller;
   }

   public UUID outgoingCallee(UUID caller) {
      Ringing ring = this.outgoing.get(caller);
      return ring == null ? null : ring.callee;
   }

   public boolean incomingSwitches(UUID callee) {
      return this.incoming.containsKey(callee) && this.inCall(callee);
   }

   public boolean sameCall(UUID a, UUID b) {
      if (a == null || b == null || a.equals(b)) {
         return false;
      }
      CallSession left = this.byPlayer.get(a);
      CallSession right = this.byPlayer.get(b);
      return left != null && left == right;
   }

   public Set<UUID> mates(UUID uuid) {
      CallSession session = this.byPlayer.get(uuid);
      if (session == null) {
         return Set.of();
      }
      Set<UUID> copy = ConcurrentHashMap.newKeySet();
      copy.addAll(session.members);
      copy.remove(uuid);
      return copy;
   }

   public List<UUID> members(UUID uuid) {
      CallSession session = this.byPlayer.get(uuid);
      return session == null ? List.of() : new ArrayList<>(session.members);
   }

   public void tick() {
      if (!this.open) {
         return;
      }
      for (Ringing ring : List.copyOf(this.incoming.values())) {
         ring.ticksLeft--;
         if (ring.ticksLeft <= 0) {
            this.endRing(ring, "&7无人接听。", "&7未接来电。");
            continue;
         }
         if (ring.ticksLeft % 20 == 0) {
            this.pulse(ring);
         }
      }
      for (Map.Entry<UUID, CallSession> entry : this.byPlayer.entrySet()) {
         this.talkTicks.merge(entry.getKey(), 1L, Long::sum);
         FraudVoicePlugin.joinCall(entry.getKey(), entry.getValue().id);
      }
      this.joinedTicks.replaceAll((id, ticks) -> ticks + 1);
   }

   public DialResult dial(ServerPlayer caller, UUID target) {
      UUID from = caller.getUUID();
      if (!this.open) {
         return DialResult.CLOSED;
      }
      if (target == null || from.equals(target) || !this.match.alive(target) || !this.match.alive(from)) {
         return DialResult.INVALID;
      }
      CallSession mine = this.byPlayer.get(from);
      CallSession theirs = this.byPlayer.get(target);
      if (mine != null && mine == theirs) {
         this.match.send(caller, "&7你们已经在同一通话中。");
         return DialResult.ALREADY;
      }
      Ringing existingOut = this.outgoing.get(from);
      if (existingOut != null && existingOut.callee.equals(target)) {
         this.match.send(caller, "&e正在呼叫对方，请等待接听。");
         return DialResult.ALREADY;
      }
      if (this.incoming.containsKey(from)) {
         this.match.send(caller, "&c请先接听或拒绝当前来电。");
         return DialResult.BUSY;
      }
      if (this.incoming.containsKey(target) || this.outgoing.containsKey(target)) {
         this.busyTone(caller);
         this.match.send(caller, "&c占线。对方正在处理另一通电话。");
         return DialResult.BUSY;
      }
      if (theirs != null && this.match.settings().busyMode()) {
         this.busyTone(caller);
         this.match.send(caller, "&c占线。对方正在通话。");
         return DialResult.BUSY;
      }
      if (existingOut != null) {
         this.endRing(existingOut, "&7已改拨其他号码。", "&7对方取消了呼叫。");
      }
      this.roundDials.merge(from, 1, Integer::sum);
      this.incomingDialers.computeIfAbsent(target, id -> ConcurrentHashMap.newKeySet()).add(from);
      Ringing ring = new Ringing(from, target);
      this.incoming.put(target, ring);
      this.outgoing.put(from, ring);
      ServerPlayer callee = this.match.player(target);
      if (theirs != null) {
         this.match.send(caller, "&e正在呼叫 " + this.match.label(target) + " &e（对方通话中，接听会切换）。");
         if (callee != null) {
            this.match.send(callee, "&a来电：" + this.match.label(from) + " &7接听将结束当前通话。");
         }
      } else {
         this.match.send(caller, "&e正在呼叫 " + this.match.label(target) + "&e，等待接听。");
         if (callee != null) {
            this.match.send(callee, "&a来电：" + this.match.label(from) + " &7选择接听或拒绝。");
         }
      }
      this.pulse(ring);
      this.match.refreshPhoneUi(from);
      this.match.refreshPhoneUi(target);
      return DialResult.RINGING;
   }

   public void answer(ServerPlayer player) {
      if (player == null) {
         return;
      }
      Ringing ring = this.incoming.get(player.getUUID());
      if (ring == null) {
         return;
      }
      this.clearRing(ring);
      if (!this.match.alive(ring.caller) || !this.match.alive(ring.callee)) {
         ServerPlayer leftover = this.match.alive(ring.caller)
            ? this.match.player(ring.caller) : this.match.player(ring.callee);
         if (leftover != null) {
            this.match.send(leftover, "&7对方已离开，来电取消。");
         }
         this.match.refreshPhoneUi(ring.caller);
         this.match.refreshPhoneUi(ring.callee);
         return;
      }
      this.connectRing(ring);
      this.connectTone(this.match.player(ring.caller));
      this.connectTone(this.match.player(ring.callee));
      this.match.refreshPhoneUi(ring.caller);
      this.match.refreshPhoneUi(ring.callee);
   }

   public void reject(ServerPlayer player) {
      if (player == null) {
         return;
      }
      Ringing ring = this.incoming.get(player.getUUID());
      if (ring == null) {
         return;
      }
      this.endRing(ring, "&c对方拒绝了来电。", "&7已拒绝来电。");
   }

   public void hangup(UUID uuid) {
      Ringing out = this.outgoing.get(uuid);
      if (out != null) {
         this.endRing(out, "&7已取消呼叫。", "&7对方取消了呼叫。");
         return;
      }
      Ringing in = this.incoming.get(uuid);
      if (in != null) {
         this.endRing(in, "&c对方拒绝了来电。", "&7已拒绝来电。");
         return;
      }
      this.leave(uuid, true);
   }

   public void hangupAll() {
      this.silencing = true;
      try {
         for (Ringing ring : List.copyOf(this.incoming.values())) {
            this.clearRing(ring);
         }
         for (UUID uuid : List.copyOf(this.byPlayer.keySet())) {
            this.leave(uuid, false);
         }
         this.byPlayer.clear();
         this.joinedTicks.clear();
         for (UUID id : List.copyOf(this.byId.keySet())) {
            FraudVoicePlugin.dropCall(id);
         }
         this.byId.clear();
      } finally {
         this.silencing = false;
      }
   }

   private void connectRing(Ringing ring) {
      this.leave(ring.caller, "&7对方去接另一通电话了。");
      this.leave(ring.callee, "&7对方去接另一通电话了。");
      CallSession session = new CallSession();
      session.members.add(ring.caller);
      session.members.add(ring.callee);
      this.byId.put(session.id, session);
      this.byPlayer.put(ring.caller, session);
      this.byPlayer.put(ring.callee, session);
      this.joinedTicks.put(ring.caller, 0);
      this.joinedTicks.put(ring.callee, 0);
      this.syncGroup(ring.caller, session);
      this.syncGroup(ring.callee, session);
      this.announce(session, this.match.label(ring.caller) + " &7与 "
         + this.match.label(ring.callee) + " &7接通。");
   }

   private void leave(UUID uuid, boolean announce) {
      this.leave(uuid, announce, "&7通话已结束。");
   }

   private void leave(UUID uuid, String leftoverMsg) {
      this.leave(uuid, false, leftoverMsg);
   }

   private void leave(UUID uuid, boolean announce, String leftoverMsg) {
      CallSession session = this.byPlayer.remove(uuid);
      this.joinedTicks.remove(uuid);
      FraudVoicePlugin.leaveCall(uuid);
      if (session == null) {
         return;
      }
      session.members.remove(uuid);
      if (announce) {
         this.announce(session, this.match.label(uuid) + " &7挂断了。");
         ServerPlayer player = this.match.player(uuid);
         if (player != null) {
            this.match.send(player, "&7你挂断了电话。");
            this.hangTone(player);
         }
      }
      if (session.members.size() < 2) {
         List<UUID> leftovers = List.copyOf(session.members);
         for (UUID leftover : leftovers) {
            this.byPlayer.remove(leftover);
            this.joinedTicks.remove(leftover);
            FraudVoicePlugin.leaveCall(leftover);
            ServerPlayer other = this.match.player(leftover);
            if (other != null) {
               this.match.send(other, leftoverMsg);
            }
         }
         session.members.clear();
         this.byId.remove(session.id);
         FraudVoicePlugin.dropCall(session.id);
         this.notifyUi(uuid);
         for (UUID leftover : leftovers) {
            this.notifyUi(leftover);
         }
         return;
      }
      if (announce) {
         this.notifyUi(uuid);
         for (UUID mate : session.members) {
            this.notifyUi(mate);
         }
      }
   }

   private void notifyUi(UUID uuid) {
      if (!this.silencing) {
         this.match.refreshPhoneUi(uuid);
      }
   }

   private void endRing(Ringing ring, String callerMsg, String calleeMsg) {
      this.clearRing(ring);
      ServerPlayer caller = this.match.player(ring.caller);
      ServerPlayer callee = this.match.player(ring.callee);
      if (caller != null && callerMsg != null) {
         this.match.send(caller, callerMsg);
         this.hangTone(caller);
      }
      if (callee != null && calleeMsg != null) {
         this.match.send(callee, calleeMsg);
         this.hangTone(callee);
      }
      this.match.refreshPhoneUi(ring.caller);
      this.match.refreshPhoneUi(ring.callee);
   }

   private void clearRing(Ringing ring) {
      this.incoming.remove(ring.callee, ring);
      this.outgoing.remove(ring.caller, ring);
   }

   private void pulse(Ringing ring) {
      ServerPlayer caller = this.match.player(ring.caller);
      ServerPlayer callee = this.match.player(ring.callee);
      if (caller != null) {
         caller.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.45F, 1.6F);
      }
      if (callee != null) {
         callee.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
      }
   }

   private void syncGroup(UUID uuid, CallSession session) {
      if (session != null) {
         FraudVoicePlugin.joinCall(uuid, session.id);
      }
   }

   private void announce(CallSession session, String message) {
      for (UUID uuid : session.members) {
         ServerPlayer player = this.match.player(uuid);
         if (player != null) {
            this.match.send(player, message);
         }
      }
   }

   private void connectTone(ServerPlayer player) {
      if (player == null) {
         return;
      }
      player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
   }

   private void hangTone(ServerPlayer player) {
      if (player == null) {
         return;
      }
      player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
   }

   private void busyTone(ServerPlayer player) {
      player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.9F, 0.5F);
   }

   public enum DialResult {
      RINGING,
      BUSY,
      CLOSED,
      ALREADY,
      INVALID
   }

   private static final class CallSession {
      private final UUID id = UUID.randomUUID();
      private final Set<UUID> members = ConcurrentHashMap.newKeySet();
   }

   private static final class Ringing {
      private final UUID caller;
      private final UUID callee;
      private int ticksLeft = RING_TICKS;

      private Ringing(UUID caller, UUID callee) {
         this.caller = caller;
         this.callee = callee;
      }
   }
}
