package net.exmo.sreGame;

import net.exmo.sreGame.games.buildrun.YouBuildRunMiniGame;
import net.exmo.sreGame.games.buildwar.BuildWarMiniGame;
import net.exmo.sreGame.games.buildwar.BuildSafety;
import net.exmo.sreGame.games.caveguess.CaveGuessersMiniGame;
import net.exmo.sreGame.games.chicken.ChickenHorseMiniGame;
import net.exmo.sreGame.games.dontdo.DontDoMiniGame;
import net.exmo.sreGame.games.dig.DigToDeathMiniGame;
import net.exmo.sreGame.games.dodgeball.DodgeballMiniGame;
import net.exmo.sreGame.games.football.FootballMiniGame;
import net.exmo.sreGame.games.draw.DrawGuessMiniGame;
import net.exmo.sreGame.games.draw.DrawWarMiniGame;
import net.exmo.sreGame.games.fakehuman.FakeHumanMiniGame;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallMiniGame;
import net.exmo.sreGame.games.fraud.FraudMasterMiniGame;
import net.exmo.sreGame.games.luckypillar.LuckyPillarMiniGame;
import net.exmo.sreGame.games.nametagwar.NameTagWarMiniGame;
import net.exmo.sreGame.games.pillarpummel.PillarPummelMiniGame;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMiniGame;
import net.exmo.sreGame.games.rhythm.RhythmMiniGame;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.PartyMiniGame;
import net.exmo.sreGame.games.hypixelsays.HypixelSaysMiniGame;
import net.exmo.sreGame.games.skyworld.SkyWorldMiniGame;
import net.exmo.sreGame.games.blockedcombat.BlockedCombatMiniGame;
import net.exmo.sreGame.games.tunnelrats.TunnelRatsMiniGame;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleMiniGame;
import net.exmo.sreGame.games.youguess.YouGuessMiniGame;
import net.exmo.sreGame.command.GameCommands;
import net.exmo.sreGame.command.WhitelistCommands;
import net.exmo.sreGame.player.NameManager;
import net.exmo.sreGame.player.PlayerVisibility;
import net.exmo.sreGame.game.DuelMiniGame;
import net.exmo.sreGame.input.ChatPrompt;
import com.mcrpvp.duel.fabric.api.DuelApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.exmo.sreGame.games.quakechasm.QuakeCTFMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeTDMMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SreGame implements ModInitializer {
   public static final String MOD_ID = "sre-game";
   public static final Logger LOGGER = LoggerFactory.getLogger("SRE-GAME");
   private static GameContext context;

   @Override
   public void onInitialize() {
      context = new GameContext();
      context.games().register(new DuelMiniGame(context));
      context.games().register(new BuildWarMiniGame(context));
      context.games().register(new YouGuessMiniGame(context));
      context.games().register(new DrawWarMiniGame(context));
      context.games().register(new DrawGuessMiniGame(context));
      context.games().register(new FraudMasterMiniGame(context));
      context.games().register(new FakeHumanMiniGame(context));
      context.games().register(new CaveGuessersMiniGame(context));
      context.games().register(new ChickenHorseMiniGame(context));
      context.games().register(new DontDoMiniGame(context));
      context.games().register(new LuckyPillarMiniGame(context));
      context.games().register(new PillarPummelMiniGame(context));
      context.games().register(new DodgeballMiniGame(context));
      context.games().register(new FootballMiniGame(context));
      context.games().register(new DigToDeathMiniGame(context));
      context.games().register(new YouBuildRunMiniGame(context));
      context.games().register(new PushTheButtonMiniGame(context));
      context.games().register(new SkyWorldMiniGame(context));
      context.games().register(new BlockedCombatMiniGame(context));
      context.games().register(new TunnelRatsMiniGame(context));
      context.games().register(new SituationPuzzleMiniGame(context));
      context.games().register(new NameTagWarMiniGame(context));
      context.games().register(new FillInTheWallMiniGame(context));
      context.games().register(new RhythmMiniGame(context));
      context.games().register(new HypixelSaysMiniGame(context));
      for (PartyGameType type : PartyGameType.values()) {
         context.games().register(new PartyMiniGame(context, type));
      }
      context.games().register(new QuakeMiniGame(context));
      context.games().register(new QuakeTDMMiniGame(context));
      context.games().register(new QuakeCTFMiniGame(context));
      new QuakeManager(context);
      BuildSafety.register(context);
      DuelApi.addLobbyProtectionBypass(player -> context != null && (
         context.buildWar().isPlaying(player)
            || context.youGuess().isPlaying(player)
            || context.fraudMaster().isPlaying(player)
            || context.fakeHuman().isPlaying(player)
            || context.caveGuess().isPlaying(player)
            || context.chickenHorse().isPlaying(player)
            || context.dontDo().isPlaying(player)
            || context.luckyPillar().isPlaying(player)
            || context.pillarPummel().isPlaying(player)
            || context.dodgeball().isPlaying(player)
            || context.football().isPlaying(player)
            || context.digToDeath().isPlaying(player)
            || context.youBuildRun().isPlaying(player)
            || context.pushTheButton().isPlaying(player)
            || context.skyWorld().isPlaying(player)
            || context.blockedCombat().isPlaying(player)
            || context.tunnelRats().isPlaying(player)
            || context.situationPuzzle().isPlaying(player)
            || context.nameTagWar().isPlaying(player)
            || context.fillInTheWall().isPlaying(player)
            || context.rhythm().isPlaying(player)
            || context.hypixelSays().isPlaying(player)
            || context.partyGames().isPlaying(player)
            || context.parkour().isPlaying(player)));
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
         if (context != null && context.hypixelSays().handleMobDamage(entity, source)) {
            return false;
         }
         if (context != null && context.partyGames().handleMobDamage(entity, source)) {
            return false;
         }
         if (!(entity instanceof ServerPlayer player) || context == null) {
            return true;
         }
         return !context.chickenHorse().handleDamage(player, source)
            && !context.dontDo().handleDamage(player, source, amount)
            && !context.luckyPillar().handleDamage(player, source)
            && !context.pillarPummel().handleDamage(player, source)
            && !context.dodgeball().handleDamage(player, source)
            && !context.football().handleDamage(player, source)
            && !context.digToDeath().handleDamage(player, source)
            && !context.youBuildRun().handleDamage(player, source)
            && !context.pushTheButton().handleDamage(player, source)
            && !context.skyWorld().handleDamage(player, source)
            && !context.blockedCombat().handleDamage(player, source)
            && !context.tunnelRats().handleDamage(player, source)
            && !context.parkour().handleDamage(player, source)
            && !context.nameTagWar().handleDamage(player, source)
            && !context.fillInTheWall().handleDamage(player, source)
            && !context.hypixelSays().handleDamage(player, source)
            && !context.partyGames().handleDamage(player, source);
      });
      ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
         if (!(entity instanceof ServerPlayer player) || context == null) {
            return true;
         }
         if (QuakeManager.INSTANCE != null) {
            QuakeUserState qst = QuakeManager.INSTANCE.getUserState(player);
            if (qst != null && qst.currentMatch != null) {
               Entity attacker = source.getEntity();
               DamageCause cause = qst.lastDamage != null ? qst.lastDamage.getCause() : DamageCause.UNKNOWN;
               qst.currentMatch.onDeath(player, attacker, cause);
               QuakeManager.INSTANCE.schedule(40, qst::respawn);
               return false;
            }
         }
         return !context.chickenHorse().handleDeath(player)
            && !context.dontDo().handleDeath(player)
            && !context.luckyPillar().handleDeath(player, source)
            && !context.pillarPummel().handleDeath(player)
            && !context.dodgeball().handleDeath(player)
            && !context.football().handleDeath(player)
            && !context.digToDeath().handleDeath(player)
            && !context.youBuildRun().handleDeath(player)
            && !context.pushTheButton().handleDeath(player)
            && !context.skyWorld().handleDeath(player, source)
            && !context.blockedCombat().handleDeath(player, source)
            && !context.tunnelRats().handleDeath(player, source)
            && !context.parkour().handleDeath(player)
            && !context.nameTagWar().handleDeath(player, source)
            && !context.fillInTheWall().handleDeath(player)
            && !context.hypixelSays().handleDeath(player)
            && !context.partyGames().handleDeath(player);
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
         if (context != null) context.partyGames().handleMobDeath(entity, source);
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
         if (context != null) {
            context.rooms().onPlayerRespawn(newPlayer);
         }
      });
      AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp && context != null) {
            if (context.football().handleAttack(sp, entity)) {
               return net.minecraft.world.InteractionResult.FAIL;
            }
            context.dontDo().handleAttack(sp, entity);
            if (context.hypixelSays().handleAttack(sp, entity)) {
               return net.minecraft.world.InteractionResult.FAIL;
            }
            if (context.partyGames().handleAttack(sp, entity)) {
               return net.minecraft.world.InteractionResult.FAIL;
            }
            if (context.digToDeath().isPlaying(sp)
               || context.chickenHorse().isPlaying(sp) && entity instanceof ServerPlayer) {
               return net.minecraft.world.InteractionResult.FAIL;
            }
         }
         return net.minecraft.world.InteractionResult.PASS;
      });
      UseItemCallback.EVENT.register((player, world, hand) -> {
         var stack = player.getItemInHand(hand);
         if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
         }
         if (context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.hypixelSays().handleUseItem(sp, stack) != InteractionResult.PASS) {
            return InteractionResultHolder.fail(stack);
         }
         if (context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.partyGames().handleUseItem(sp, stack) != InteractionResult.PASS) {
            return InteractionResultHolder.fail(stack);
         }
         if (context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.rhythm().isPlaying(sp)) {
            context.rhythm().handleRightClick(sp);
            return InteractionResultHolder.fail(stack);
         }
         if (context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.nameTagWar().handleUseItem(sp, stack) != InteractionResult.PASS) {
            return InteractionResultHolder.fail(stack);
         }
         if (QuakeManager.INSTANCE == null) {
            return InteractionResultHolder.pass(stack);
         }
         QuakeUserState qst = QuakeManager.INSTANCE.getUserState(sp);
         if (qst != null && qst.currentMatch != null
               && net.exmo.sreGame.games.quakechasm.combat.WeaponUtil.getHoldingWeaponIndex(sp) >= 0) {
            qst.weaponState.shoot(sp);
            return InteractionResultHolder.fail(stack);
         }
         return InteractionResultHolder.pass(stack);
      });
      PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
         context == null || !(player instanceof ServerPlayer serverPlayer) || !context.partyGames().isPlaying(serverPlayer)
            || context.partyGames().tryBreak(serverPlayer, pos, state));
      UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.hypixelSays().handleUseBlock(sp, hit, sp.getItemInHand(hand)) != InteractionResult.PASS) {
            return InteractionResult.FAIL;
         }
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.partyGames().handleUseBlock(sp, hit, sp.getItemInHand(hand)) != InteractionResult.PASS) {
            return InteractionResult.FAIL;
         }
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.rhythm().isPlaying(sp)) {
            context.rhythm().handleRightClick(sp);
            return InteractionResult.FAIL;
         }
         return InteractionResult.PASS;
      });
      UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.hypixelSays().handleUseEntity(sp, entity) != InteractionResult.PASS) {
            return InteractionResult.FAIL;
         }
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.partyGames().handleUseEntity(sp, entity) != InteractionResult.PASS) {
            return InteractionResult.FAIL;
         }
         if (!world.isClientSide() && player instanceof ServerPlayer sp
               && context != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND
               && context.rhythm().isPlaying(sp)) {
            context.rhythm().handleRightClick(sp);
            return InteractionResult.FAIL;
         }
         return InteractionResult.PASS;
      });
      ServerLifecycleEvents.SERVER_STARTED.register(context::onServerStarted);
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> context.onServerStopping());
      ServerTickEvents.END_SERVER_TICK.register(server -> {
         context.tick();
         if (QuakeManager.INSTANCE != null) QuakeManager.INSTANCE.tick();
      });
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
         GameCommands.register(dispatcher, context));
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
         if (handler.getPlayer() != null) {
            NameManager.onDisconnect(handler.getPlayer());
            context.parkour().leave(handler.getPlayer(), false);
            context.hypixelSays().onLeave(handler.getPlayer());
            context.partyGames().onLeave(handler.getPlayer());
            context.rooms().onDisconnect(handler.getPlayer());
            if (QuakeManager.INSTANCE != null) QuakeManager.INSTANCE.removePlayer(handler.getPlayer());
         }
      });
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
         if (handler.getPlayer() != null) {
            if (!WhitelistCommands.allows(context, handler.getPlayer())) {
               WhitelistCommands.reject(handler.getPlayer());
               return;
            }
            context.rooms().onRejoin(handler.getPlayer());
            context.partyGames().onJoin(handler.getPlayer());
            if (QuakeManager.INSTANCE != null) QuakeManager.INSTANCE.initPlayer(handler.getPlayer());
            PlayerVisibility.refreshTabFor(handler.getPlayer());
         }
      });
      ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
         !context.youGuess().handleChat(sender, message.decoratedContent().getString())
            && !context.buildWar().handleChat(sender, message.decoratedContent().getString())
            && !context.fraudMaster().handleChat(sender, message.decoratedContent().getString())
            && !context.fakeHuman().handleChat(sender, message.decoratedContent().getString())
            && !context.caveGuess().handleChat(sender, message.decoratedContent().getString())
            && !context.pushTheButton().handleChat(sender, message.decoratedContent().getString())
            && !context.situationPuzzle().handleChat(sender, message.decoratedContent().getString())
            && !ChatPrompt.handle(context, sender, message.decoratedContent().getString()));
      ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) ->
         !context.config().hideJoinLeaveNotifications() || !isJoinLeaveNotification(message));
      LOGGER.info("SRE-GAME initialized — /sregame to open the room menu.");
   }

   private static boolean isJoinLeaveNotification(Component message) {
      if (!(message.getContents() instanceof TranslatableContents contents)) {
         return false;
      }
      return switch (contents.getKey()) {
         case "multiplayer.player.joined", "multiplayer.player.joined.renamed", "multiplayer.player.left" -> true;
         default -> false;
      };
   }

   public static GameContext getContext() {
      return context;
   }

   /** Called by StarRailExpress's /stuck mixin to turn an emergency escape into a game forfeit. */
   public static boolean handleStuck(ServerPlayer player) {
      if (context == null || player == null) {
         return false;
      }
      if (DuelApi.isInMatch(player)) {
         DuelApi.forceLeave(player);
         context.send(player, "&c已认输并退出当前决斗。");
         return true;
      }
      var room = context.rooms().getByPlayer(player.getUUID());
      if (room != null && room.state() != net.exmo.sreGame.room.RoomState.WAITING) {
         context.rooms().leave(player);
         context.send(player, "&c已从本场小游戏出局。");
         return true;
      }
      return false;
   }
}
