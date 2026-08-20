package net.exmo.sreGame.games.parkour;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.ParkourMenuGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

public final class ParkourManager {
   private final GameContext ctx;
   private final ParkourLeaderboard scores;
   private final Map<UUID, ParkourSession> sessions = new ConcurrentHashMap<>();
   private final Set<Integer> usedSlots = new HashSet<>();

   public ParkourManager(GameContext ctx) {
      this.ctx = ctx;
      this.scores = new ParkourLeaderboard(java.nio.file.Path.of("config", "sre-game"));
   }

   public ParkourLeaderboard scores() {
      return this.scores;
   }

   public ParkourSession get(UUID player) {
      return this.sessions.get(player);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.sessions.containsKey(player.getUUID());
   }

   public void load() {
      this.scores.load();
   }

   public boolean join(ServerPlayer player) {
      if (player == null) {
         return false;
      }
      if (this.isPlaying(player)) {
         this.ctx.send(player, "&c你已经在无限跑酷中。");
         return false;
      }
      GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
      if (room != null && room.state() != RoomState.WAITING) {
         this.ctx.send(player, "&c对局进行中不能进入跑酷。");
         return false;
      }
      if (this.inOtherMatch(player)) {
         this.ctx.send(player, "&c当前已在其他小游戏中。");
         return false;
      }
      ServerLevel level = this.ctx.config().world(this.ctx.server());
      if (level == null) {
         this.ctx.send(player, "&c世界未就绪。");
         return false;
      }
      int slot = this.nextSlot();
      this.usedSlots.add(slot);
      BlockPos center = this.centerOf(slot);
      ParkourSession session = new ParkourSession(player, center);
      session.slot = slot;
      this.sessions.put(player.getUUID(), session);
      player.closeContainer();
      session.begin(level);
      this.ctx.send(player, "&a已进入无限跑酷。掉落会重置分数。用热键栏退出或打开设置。");
      return true;
   }

   public void leave(ServerPlayer player, boolean restore) {
      if (player == null) {
         return;
      }
      ParkourSession session = this.sessions.remove(player.getUUID());
      if (session == null) {
         return;
      }
      this.usedSlots.remove(session.slot);
      ServerLevel level = player.serverLevel();
      session.destroy(level);
      if (session.score() > 0) {
         this.scores.record(player, session.score(), session.elapsedMs());
      }
      if (restore) {
         session.restore(this.ctx);
      }
      this.ctx.send(player, "&7已离开无限跑酷。本局 &f" + session.score()
         + " &7分，用时 &f" + ParkourSession.formatTime(session.elapsedMs()));
   }

   public void leaveAllRoomMembers(GameRoom room) {
      if (room == null) {
         return;
      }
      for (UUID uuid : List.copyOf(room.members())) {
         ServerPlayer player = this.ctx.player(uuid);
         if (this.isPlaying(player)) {
            this.leave(player, true);
         }
      }
   }

   public void tick() {
      ServerLevel level = this.ctx.config().world(this.ctx.server());
      if (level == null) {
         return;
      }
      for (ParkourSession session : List.copyOf(this.sessions.values())) {
         ServerPlayer player = this.ctx.player(session.player().getUUID());
         if (player == null) {
            this.sessions.remove(session.player().getUUID());
            this.usedSlots.remove(session.slot);
            session.destroy(level);
            continue;
         }
         session.tick(this.ctx, level);
      }
   }

   public void endAll() {
      for (ParkourSession session : List.copyOf(this.sessions.values())) {
         this.leave(session.player(), true);
      }
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      return this.isPlaying(player);
   }

   public boolean handleDeath(ServerPlayer player) {
      ParkourSession session = this.get(player.getUUID());
      if (session == null) {
         return false;
      }
      player.setHealth(player.getMaxHealth());
      player.fallDistance = 0.0F;
      return true;
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      if (!this.isPlaying(player)) {
         return InteractionResult.PASS;
      }
      String action = net.exmo.sreGame.gui.GuiItems.actionTag(stack);
      if ("pk_leave".equals(action)) {
         this.leave(player, true);
         return InteractionResult.FAIL;
      }
      if ("pk_settings".equals(action)) {
         ParkourMenuGui.open(this.ctx, player);
         return InteractionResult.FAIL;
      }
      return InteractionResult.FAIL;
   }

   public boolean openIfPlaying(ServerPlayer player) {
      if (!this.isPlaying(player)) {
         return false;
      }
      ParkourMenuGui.open(this.ctx, player);
      return true;
   }

   private boolean inOtherMatch(ServerPlayer player) {
      return this.ctx.buildWar().isPlaying(player) || this.ctx.youGuess().isPlaying(player)
         || this.ctx.fraudMaster().isPlaying(player) || this.ctx.fakeHuman().isPlaying(player)
         || this.ctx.caveGuess().isPlaying(player) || this.ctx.chickenHorse().isPlaying(player)
         || this.ctx.dontDo().isPlaying(player) || this.ctx.luckyPillar().isPlaying(player)
         || this.ctx.pillarPummel().isPlaying(player) || this.ctx.digToDeath().isPlaying(player)
         || this.ctx.youBuildRun().isPlaying(player) || this.ctx.dodgeball().isPlaying(player)
         || this.ctx.pushTheButton().isPlaying(player);
   }

   private int nextSlot() {
      int slot = 0;
      while (this.usedSlots.contains(slot)) {
         slot++;
      }
      return slot;
   }

   private BlockPos centerOf(int slot) {
      int[] spiral = spiral(slot);
      int y = (ParkourSession.MIN_Y + ParkourSession.MAX_Y) / 2;
      return new BlockPos(
         this.ctx.config().parkourOriginX() + spiral[0] * ParkourSession.BORDER,
         y,
         this.ctx.config().parkourOriginZ() + spiral[1] * ParkourSession.BORDER
      );
   }

   private static int[] spiral(int n) {
      if (n <= 0) {
         return new int[] {0, 0};
      }
      int x = 0;
      int z = 0;
      int dx = 0;
      int dz = -1;
      for (int i = 0; i < n; i++) {
         if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
            int t = dx;
            dx = -dz;
            dz = t;
         }
         x += dx;
         z += dz;
      }
      return new int[] {x, z};
   }
}
