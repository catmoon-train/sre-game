package net.exmo.sreGame.games.fakehuman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.FakeHumanPickGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class FakeHumanManager {
   private final GameContext ctx;
   private final SafehouseManager houses;
   private final Map<UUID, FakeHumanMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, FakeHumanMatch> byId = new ConcurrentHashMap<>();

   public FakeHumanManager(GameContext ctx) {
      this.ctx = ctx;
      this.houses = new SafehouseManager(ctx);
   }

   public SafehouseManager houses() {
      return this.houses;
   }

   public FakeHumanMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public FakeHumanMatch getByPlayer(UUID player) {
      return this.get(player);
   }

   public FakeHumanMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      Safehouse house = this.houses.acquire();
      if (house == null) {
         return null;
      }
      FakeHumanMatch match = new FakeHumanMatch(this.ctx, room, seats, house);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.houses.prepare(house, () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在构建安全屋，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.houses.tick();
      for (FakeHumanMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      FakeHumanMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(FakeHumanMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (FakeHumanMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      FakeHumanMatch match = this.get(player.getUUID());
      return match != null && match.handleChat(player, message);
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      FakeHumanMatch match = this.get(player.getUUID());
      return match != null && match.handleUseItem(player, stack);
   }

   public boolean handleUseEntity(ServerPlayer player, ServerPlayer target, ItemStack stack) {
      FakeHumanMatch match = this.get(player.getUUID());
      return match != null && match.handleUseEntity(player, target, stack);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      FakeHumanMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7谁是伪人进行中。白天隔门盘问，夜晚按人数结算。");
      return true;
   }

   public boolean sameChatZone(ServerPlayer viewer, ServerPlayer sender) {
      FakeHumanMatch send = this.get(sender.getUUID());
      FakeHumanMatch view = this.get(viewer.getUUID());
      if (send == null && view == null) {
         return true;
      }
      if (send == null || view == null || send != view) {
         return false;
      }
      return send.sameVoiceZone(viewer.getUUID(), sender.getUUID());
   }

   public void handlePick(ServerPlayer player, FakeHumanPickGui.Kind kind, UUID target) {
      FakeHumanMatch match = this.get(player.getUUID());
      if (match != null) {
         match.handlePick(player, kind, target);
      }
   }
}
