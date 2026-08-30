package net.exmo.sreGame.room;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.games.partygames.PartyMiniGame;
import com.mcrpvp.duel.fabric.api.DuelApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class RoomManager {
   private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
   private static final SecureRandom RANDOM = new SecureRandom();

   private final GameContext ctx;
   private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
   private final Map<UUID, String> playerRoom = new ConcurrentHashMap<>();
   private final Map<UUID, CreateDraft> drafts = new ConcurrentHashMap<>();
   /** Last-resort return snapshots. Individual games still restore themselves, this closes missed end paths. */
   private final Map<String, Map<UUID, MatchReturnState>> matchReturnStates = new ConcurrentHashMap<>();
   /** Return states for players whose old entity died before the match could finish restoring it. */
   private final Map<UUID, MatchReturnState> pendingRespawnReturnStates = new ConcurrentHashMap<>();

   public RoomManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public GameRoom get(String code) {
      return code == null ? null : this.rooms.get(code.toUpperCase(Locale.ROOT));
   }

   public GameRoom getByPlayer(UUID uuid) {
      String code = this.playerRoom.get(uuid);
      return code == null ? null : this.rooms.get(code);
   }

   public GameRoom getByMatchId(UUID matchId) {
      if (matchId == null) {
         return null;
      }
      for (GameRoom room : this.rooms.values()) {
         if (matchId.equals(room.activeMatchId())) {
            return room;
         }
      }
      return null;
   }

   public List<GameRoom> publicRooms() {
      List<GameRoom> list = new ArrayList<>();
      for (GameRoom room : this.rooms.values()) {
         if (room.publicRoom() && room.state() == RoomState.WAITING) {
            list.add(room);
         }
      }
      list.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
      return list;
   }

   public CreateDraft draft(UUID uuid) {
      return this.drafts.computeIfAbsent(uuid, id -> new CreateDraft());
   }

   public void clearDraft(UUID uuid) {
      this.drafts.remove(uuid);
   }

   public GameRoom createFromDraft(ServerPlayer player) {
      if (this.getByPlayer(player.getUUID()) != null) {
         this.ctx.send(player, "&c你已经在一个房间里了。先用 &f/room leave &c离开。");
         return null;
      }
      CreateDraft draft = this.draft(player.getUUID());
      GameRoom room = this.create(player, draft.name, draft.maxPlayers, draft.publicRoom, draft.password, draft.miniGameId, draft.chatMode);
      this.clearDraft(player.getUUID());
      return room;
   }

   public GameRoom create(ServerPlayer player, String name, int maxPlayers, boolean publicRoom, String password, String miniGameId) {
      return this.create(player, name, maxPlayers, publicRoom, password, miniGameId, RoomChatMode.ROOM_ONLY);
   }

   public GameRoom create(ServerPlayer player, String name, int maxPlayers, boolean publicRoom, String password, String miniGameId, RoomChatMode chatMode) {
      if (this.getByPlayer(player.getUUID()) != null) {
         this.ctx.send(player, "&c你已经在一个房间里了。先用 &f/room leave &c离开。");
         return null;
      }
      String code = this.nextCode();
      String display = name == null || name.isBlank() ? player.getGameProfile().getName() + " 的房间" : name.trim();
      GameRoom room = new GameRoom(code, display, player.getUUID());
      room.setMaxPlayers(maxPlayers);
      room.setPublicRoom(publicRoom);
      room.setPassword(password);
      room.setChatMode(chatMode);
      MiniGame game = this.ctx.games().get(miniGameId);
      if (game == null) {
         game = this.ctx.games().first();
      }
      if (game != null) {
         room.setMiniGameId(game.id());
      }
      this.rooms.put(code, room);
      this.playerRoom.put(player.getUUID(), code);
      this.ctx.send(player, "&a房间已创建 &8[&f" + code + "&8] &7— 用 &f/sregame &7打开面板，或邀请别人 &f/room join " + code);
      return room;
   }

   public boolean join(ServerPlayer player, String code, String password) {
      if (this.getByPlayer(player.getUUID()) != null) {
         this.ctx.send(player, "&c你已经在一个房间里了。");
         return false;
      }
      GameRoom room = this.get(code);
      if (room == null) {
         this.ctx.send(player, "&c找不到房间 &f" + code + "&c。");
         return false;
      }
      if (!room.isJoinable()) {
         this.ctx.send(player, "&c该房间无法加入（已满或正在对局）。");
         return false;
      }
      if (room.hasPassword()) {
         if (password == null || !room.password().equals(password)) {
            this.ctx.send(player, "&c密码错误。用法：&f/room join " + room.id() + " <密码>");
            return false;
         }
      }
      room.members().add(player.getUUID());
      if (room.autoReady()) {
         room.ready().add(player.getUUID());
      }
      room.duelSettings().assignToSmaller(player.getUUID());
      this.playerRoom.put(player.getUUID(), room.id());
      this.ctx.broadcast(room, "&e" + player.getGameProfile().getName() + " &7加入了房间 &8[&f" + room.id() + "&8]");
      return true;
   }

   public void leave(ServerPlayer player) {
      this.leave(player.getUUID(), true);
   }

   public void leave(UUID uuid, boolean announce) {
      GameRoom room = this.getByPlayer(uuid);
      if (room == null) {
         return;
      }
      if (room.state() == RoomState.PLAYING || room.state() == RoomState.STARTING) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            if (room.isBuildWarFamily()) {
               this.ctx.buildWar().onLeave(player);
            } else if (room.isYouGuessFamily()) {
               this.ctx.youGuess().onLeave(player);
            } else if (room.isFraudMaster()) {
               this.ctx.fraudMaster().onLeave(player);
            } else if (room.isFakeHuman()) {
               this.ctx.fakeHuman().onLeave(player);
            } else if (room.isCaveGuess()) {
               this.ctx.caveGuess().onLeave(player);
            } else if (room.isChickenHorse()) {
               this.ctx.chickenHorse().onLeave(player);
            } else if (room.isDontDo()) {
               this.ctx.dontDo().onLeave(player);
            } else if (room.isLuckyPillar()) {
               this.ctx.luckyPillar().onLeave(player);
            } else if (room.isPillarPummel()) {
               this.ctx.pillarPummel().onLeave(player);
            } else if (room.isDodgeball()) {
               this.ctx.dodgeball().onLeave(player);
            } else if (room.isFootball()) {
               this.ctx.football().onLeave(player);
            } else if (room.isDigToDeath()) {
               this.ctx.digToDeath().onLeave(player);
            } else if (room.isYouBuildRun()) {
               this.ctx.youBuildRun().onLeave(player);
            } else if (room.isPushTheButton()) {
               this.ctx.pushTheButton().onLeave(player);
            } else if (room.isSkyWorld()) {
               this.ctx.skyWorld().onLeave(player);
            } else if (room.isBlockedCombat()) {
               this.ctx.blockedCombat().onLeave(player);
            } else if (room.isTunnelRats()) {
               this.ctx.tunnelRats().onLeave(player);
            } else if (room.isSituationPuzzle()) {
               this.ctx.situationPuzzle().onLeave(player);
            } else if (room.isNameTagWar()) {
               this.ctx.nameTagWar().onLeave(player);
            } else if (room.isFillInTheWall()) {
               this.ctx.fillInTheWall().onLeave(player);
            } else if (room.isRhythm()) {
               this.ctx.rhythm().onLeave(player);
            } else if (room.isHypixelSays()) {
               this.ctx.hypixelSays().onLeave(player);
            } else if (room.isPartyGame()) {
               this.ctx.partyGames().onLeave(player);
            } else {
               com.mcrpvp.duel.fabric.api.DuelApi.forceLeave(player);
            }
         }
      }
      String name = this.ctx.name(uuid);
      room.members().remove(uuid);
      room.ready().remove(uuid);
      room.duelSettings().remove(uuid);
      this.playerRoom.remove(uuid);
      if (room.members().isEmpty()) {
         this.disband(room, announce ? "&7房间 &f" + room.id() + " &7已解散。" : null);
         return;
      }
      if (room.isHost(uuid)) {
         UUID next = room.members().get(0);
         room.setHost(next);
         this.ctx.broadcast(room, "&e房主已转让给 &f" + this.ctx.name(next));
      }
      if (announce) {
         this.ctx.broadcast(room, "&7" + name + " 离开了房间。");
      }
   }

   public void kick(ServerPlayer host, UUID target) {
      GameRoom room = this.getByPlayer(host.getUUID());
      if (room == null || !room.isHost(host.getUUID()) || host.getUUID().equals(target)) {
         return;
      }
      if (!room.contains(target)) {
         return;
      }
      if (room.state() != RoomState.WAITING) {
         this.ctx.send(host, "&c对局进行中不能踢人。");
         return;
      }
      ServerPlayer targetPlayer = this.ctx.player(target);
      this.leave(target, false);
      this.ctx.broadcast(room, "&c" + this.ctx.name(target) + " 被踢出房间。");
      if (targetPlayer != null) {
         this.ctx.send(targetPlayer, "&c你被踢出了房间。");
      }
   }

   public void disband(GameRoom room, String message) {
      this.matchReturnStates.remove(room.id());
      for (UUID member : room.members()) {
         this.pendingRespawnReturnStates.remove(member);
      }
      for (UUID member : new ArrayList<>(room.members())) {
         this.playerRoom.remove(member);
         if (message != null) {
            this.ctx.send(this.ctx.player(member), message);
         }
      }
      this.rooms.remove(room.id());
   }

   public void disbandAll() {
      for (GameRoom room : new ArrayList<>(this.rooms.values())) {
         this.disband(room, "&7服务器关闭，房间已解散。");
      }
   }

   public void onDisconnect(ServerPlayer player) {
      this.leave(player.getUUID(), true);
   }

   public void onRejoin(ServerPlayer player) {
      if (player == null) {
         return;
      }
      boolean inRoom = this.getByPlayer(player.getUUID()) != null;
      boolean inBuildWar = this.ctx.buildWar().isPlaying(player);
      boolean inYouGuess = this.ctx.youGuess().isPlaying(player);
      boolean inFraud = this.ctx.fraudMaster().isPlaying(player);
      boolean inFake = this.ctx.fakeHuman().isPlaying(player);
      boolean inCave = this.ctx.caveGuess().isPlaying(player);
      boolean inChicken = this.ctx.chickenHorse().isPlaying(player);
      boolean inDontDo = this.ctx.dontDo().isPlaying(player);
      boolean inLucky = this.ctx.luckyPillar().isPlaying(player);
      boolean inPummel = this.ctx.pillarPummel().isPlaying(player);
      boolean inDodgeball = this.ctx.dodgeball().isPlaying(player);
      boolean inFootball = this.ctx.football().isPlaying(player);
      boolean inDig = this.ctx.digToDeath().isPlaying(player);
      boolean inBuildRun = this.ctx.youBuildRun().isPlaying(player);
      boolean inPtb = this.ctx.pushTheButton().isPlaying(player);
      boolean inSky = this.ctx.skyWorld().isPlaying(player);
      boolean inBlockedCombat = this.ctx.blockedCombat().isPlaying(player);
      boolean inTunnelRats = this.ctx.tunnelRats().isPlaying(player);
      boolean inSituation = this.ctx.situationPuzzle().isPlaying(player);
      boolean inNameTag = this.ctx.nameTagWar().isPlaying(player);
      boolean inFillWall = this.ctx.fillInTheWall().isPlaying(player);
      boolean inRhythm = this.ctx.rhythm().isPlaying(player);
      boolean inParkour = this.ctx.parkour().isPlaying(player);
      if (inParkour) {
         this.ctx.parkour().leave(player, false);
      }
      if (inRoom || inBuildWar || inYouGuess || inFraud || inFake || inCave || inChicken || inDontDo || inLucky || inPummel || inDodgeball || inFootball || inDig || inBuildRun || inPtb || inSky || inBlockedCombat || inTunnelRats || inSituation || inNameTag || inFillWall || inRhythm) {
         this.leave(player.getUUID(), false);
         this.resetLobbyState(player);
         this.ctx.send(player, "&7已退出房间并回到出生点。");
         return;
      }
      if (!DuelApi.isInMatch(player)) {
         this.resetLobbyState(player);
      }
   }

   public void resetLobbyState(ServerPlayer player) {
      if (player == null) {
         return;
      }
      player.closeContainer();
      player.setInvisible(false);
      player.setGameMode(GameType.ADVENTURE);
      DuelApi.teleportToSpawn(player);
   }

   public boolean start(ServerPlayer host) {
      GameRoom room = this.getByPlayer(host.getUUID());
      if (room == null) {
         this.ctx.send(host, "&c你不在房间中。");
         return false;
      }
      if (!room.isHost(host.getUUID())) {
         this.ctx.send(host, "&c只有房主可以开始对局。");
         return false;
      }
      if (room.state() != RoomState.WAITING) {
         this.ctx.send(host, "&c房间正在对局中。");
         return false;
      }
      MiniGame game = this.ctx.games().get(room.miniGameId());
      if (game == null) {
         this.ctx.send(host, "&c未选择小游戏。");
         return false;
      }
      if (!(game instanceof PartyMiniGame) && !this.ctx.config().isGameEnabled(game.id())) {
         this.ctx.send(host, "&c该小游戏当前已被管理员禁用。");
         return false;
      }
      if (!game.canStart(room, host)) {
         return false;
      }
      this.ctx.parkour().leaveAllRoomMembers(room);
      this.captureMatchReturnStates(room);
      room.setState(RoomState.STARTING);
      this.ctx.broadcast(room, "&a房主开始了 &f" + game.displayName() + " &a对局…");
      try {
         game.start(room, host);
      } catch (Exception e) {
         this.restoreMatchReturnStates(room);
         room.setState(RoomState.WAITING);
         room.setActiveMatchId(null);
         this.ctx.send(host, "&c开局失败：" + e.getMessage());
         SreGame.LOGGER.warn("Failed to start minigame {}", game.id(), e);
         return false;
      }
      if (room.state() == RoomState.STARTING && room.activeMatchId() == null) {
         this.restoreMatchReturnStates(room);
         room.setState(RoomState.WAITING);
         this.ctx.send(host, "&c开局失败：对局未能创建（检查模式人数与场地）。");
         return false;
      }
      return true;
   }

   public void onMatchEnded(UUID matchId) {
      GameRoom room = this.getByMatchId(matchId);
      if (room == null) {
         return;
      }
      this.restoreMatchReturnStates(room);
      room.setActiveMatchId(null);
      room.setState(RoomState.WAITING);
      room.clearReadyExceptHost();
      this.ctx.broadcast(room, "&a对局结束。房主可以再开一局，或解散房间。");
   }

   private void captureMatchReturnStates(GameRoom room) {
      Map<UUID, MatchReturnState> states = new ConcurrentHashMap<>();
      for (UUID uuid : room.members()) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            states.put(uuid, MatchReturnState.capture(player));
         }
      }
      this.matchReturnStates.put(room.id(), states);
   }

   private void restoreMatchReturnStates(GameRoom room) {
      Map<UUID, MatchReturnState> states = this.matchReturnStates.remove(room.id());
      if (states == null) {
         return;
      }
      for (Map.Entry<UUID, MatchReturnState> entry : states.entrySet()) {
         ServerPlayer player = this.ctx.player(entry.getKey());
         if (player != null && this.getByPlayer(player.getUUID()) == room) {
            if (player.isAlive()) {
               entry.getValue().apply(player, this.ctx, room.returnToDuelSpawn());
            } else {
               this.pendingRespawnReturnStates.put(player.getUUID(), entry.getValue());
            }
         }
      }
   }

   /** Called after Minecraft has created a new player entity following a death. */
   public void onPlayerRespawn(ServerPlayer player) {
      if (player == null) {
         return;
      }
      MatchReturnState state = this.pendingRespawnReturnStates.remove(player.getUUID());
      GameRoom room = this.getByPlayer(player.getUUID());
      if (state != null && room != null) {
         // MCRPVPDuel's respawn handler runs first; run on the server queue so this
         // room's original inventory and return destination are the final state.
         this.ctx.server().execute(() -> {
            if (this.getByPlayer(player.getUUID()) == room) {
               state.apply(player, this.ctx, room.returnToDuelSpawn());
            }
         });
      }
   }

   private record MatchReturnState(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch,
                                   GameType gameType, boolean invisible, List<ItemStack> items,
                                   ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, ItemStack offhand) {
      static MatchReturnState capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>();
         Inventory inventory = player.getInventory();
         for (int i = 0; i < inventory.getContainerSize(); i++) {
            items.add(inventory.getItem(i).copy());
         }
         return new MatchReturnState(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
            player.gameMode.getGameModeForPlayer(), player.isInvisible(), items,
            player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getItemBySlot(EquipmentSlot.CHEST).copy(),
            player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getItemBySlot(EquipmentSlot.FEET).copy(),
            player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
      }

      void apply(ServerPlayer player, GameContext ctx, boolean returnToDuelSpawn) {
         ServerLevel level = ctx.server().getLevel(this.dimension);
         if (level == null) {
            level = ctx.server().overworld();
         }
         player.closeContainer();
         if (returnToDuelSpawn) {
            DuelApi.teleportToSpawn(player);
            player.setGameMode(GameType.ADVENTURE);
            player.setInvisible(false);
         } else {
            player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
            // A finished room must never restore a stale spectator mode.
            player.setGameMode(this.gameType == GameType.SPECTATOR ? GameType.ADVENTURE : this.gameType);
            player.setInvisible(this.invisible);
         }
         player.setDeltaMovement(Vec3.ZERO);
         player.fallDistance = 0.0F;
         Inventory inventory = player.getInventory();
         inventory.clearContent();
         for (int i = 0; i < Math.min(inventory.getContainerSize(), this.items.size()); i++) {
            inventory.setItem(i, this.items.get(i).copy());
         }
         player.setItemSlot(EquipmentSlot.HEAD, this.head.copy());
         player.setItemSlot(EquipmentSlot.CHEST, this.chest.copy());
         player.setItemSlot(EquipmentSlot.LEGS, this.legs.copy());
         player.setItemSlot(EquipmentSlot.FEET, this.feet.copy());
         player.setItemSlot(EquipmentSlot.OFFHAND, this.offhand.copy());
      }
   }

   /** 房主在游戏过程中强制终止当前对局（通用，按房间当前小游戏分发）。 */
   public boolean endActiveMatch(ServerPlayer host) {
      if (host == null) {
         return false;
      }
      GameRoom room = this.getByPlayer(host.getUUID());
      if (room == null) {
         this.ctx.send(host, "&c你不在任何房间中。");
         return false;
      }
      if (!room.isHost(host.getUUID())) {
         this.ctx.send(host, "&c只有房主可以终止对局。");
         return false;
      }
      UUID matchId = room.activeMatchId();
      if (matchId == null || room.state() == RoomState.WAITING) {
         this.ctx.send(host, "&c当前没有进行中的对局。");
         return false;
      }
      this.endMatchById(room, matchId);
      this.ctx.broadcast(room, "&c房主终止了当前对局。");
      return true;
   }

   public void endMatchById(GameRoom room, UUID matchId) {
      if (room == null || matchId == null) {
         return;
      }
      if (room.isBuildWarFamily()) {
         var m = this.ctx.buildWar().getById(matchId); if (m != null) m.endNow();
      } else if (room.isYouGuessFamily()) {
         var m = this.ctx.youGuess().getById(matchId); if (m != null) m.endNow();
      } else if (room.isFraudMaster()) {
         var m = this.ctx.fraudMaster().getById(matchId); if (m != null) m.endNow();
      } else if (room.isFakeHuman()) {
         var m = this.ctx.fakeHuman().getById(matchId); if (m != null) m.endNow();
      } else if (room.isCaveGuess()) {
         var m = this.ctx.caveGuess().getById(matchId); if (m != null) m.endNow();
      } else if (room.isChickenHorse()) {
         var m = this.ctx.chickenHorse().getById(matchId); if (m != null) m.endNow();
      } else if (room.isDontDo()) {
         var m = this.ctx.dontDo().getById(matchId); if (m != null) m.endNow();
      } else if (room.isLuckyPillar()) {
         var m = this.ctx.luckyPillar().getById(matchId); if (m != null) m.endNow();
      } else if (room.isPillarPummel()) {
         var m = this.ctx.pillarPummel().getById(matchId); if (m != null) m.endNow();
      } else if (room.isDodgeball()) {
         var m = this.ctx.dodgeball().getById(matchId); if (m != null) m.endNow();
      } else if (room.isFootball()) {
         var m = this.ctx.football().getById(matchId); if (m != null) m.endNow();
      } else if (room.isDigToDeath()) {
         var m = this.ctx.digToDeath().getById(matchId); if (m != null) m.endNow();
      } else if (room.isYouBuildRun()) {
         var m = this.ctx.youBuildRun().getById(matchId); if (m != null) m.endNow();
      } else if (room.isPushTheButton()) {
         var m = this.ctx.pushTheButton().getById(matchId); if (m != null) m.endNow();
      } else if (room.isSkyWorld()) {
         var m = this.ctx.skyWorld().getById(matchId); if (m != null) m.endNow();
      } else if (room.isBlockedCombat()) {
         var m = this.ctx.blockedCombat().getById(matchId); if (m != null) m.endNow();
      } else if (room.isTunnelRats()) {
         var m = this.ctx.tunnelRats().getById(matchId); if (m != null) m.endNow();
      } else if (room.isSituationPuzzle()) {
         var m = this.ctx.situationPuzzle().getById(matchId); if (m != null) m.endNow();
      } else if (room.isNameTagWar()) {
         var m = this.ctx.nameTagWar().getById(matchId); if (m != null) m.endNow();
      } else if (room.isFillInTheWall()) {
         var m = this.ctx.fillInTheWall().getById(matchId); if (m != null) m.endNow();
      } else if (room.isRhythm()) {
         var m = this.ctx.rhythm().getById(matchId); if (m != null) m.endNow();
      } else if (room.isHypixelSays()) {
         var m = this.ctx.hypixelSays().getById(matchId); if (m != null) m.endNow();
      } else if (room.isPartyGame()) {
         var m = this.ctx.partyGames().getById(matchId); if (m != null) m.endNow();
      } else if (room.isQuake()) {
         var m = net.exmo.sreGame.games.quakechasm.QuakeManager.INSTANCE.getById(matchId);
         if (m != null) m.end();
      }
   }

   private String nextCode() {
      for (int i = 0; i < 32; i++) {
         StringBuilder sb = new StringBuilder(4);
         for (int n = 0; n < 4; n++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
         }
         String code = sb.toString();
         if (!this.rooms.containsKey(code)) {
            return code;
         }
      }
      return UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
   }
}
