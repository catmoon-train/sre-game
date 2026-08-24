package net.exmo.sreGame;

import net.exmo.sreGame.games.buildrun.YouBuildRunMiniGame;
import net.exmo.sreGame.games.buildwar.BuildWarMiniGame;
import net.exmo.sreGame.games.buildwar.BuildSafety;
import net.exmo.sreGame.games.caveguess.CaveGuessersMiniGame;
import net.exmo.sreGame.games.chicken.ChickenHorseMiniGame;
import net.exmo.sreGame.games.dontdo.DontDoMiniGame;
import net.exmo.sreGame.games.dig.DigToDeathMiniGame;
import net.exmo.sreGame.games.dodgeball.DodgeballMiniGame;
import net.exmo.sreGame.games.draw.DrawGuessMiniGame;
import net.exmo.sreGame.games.draw.DrawWarMiniGame;
import net.exmo.sreGame.games.fakehuman.FakeHumanMiniGame;
import net.exmo.sreGame.games.fillinthewall.FillInTheWallMiniGame;
import net.exmo.sreGame.games.fraud.FraudMasterMiniGame;
import net.exmo.sreGame.games.luckypillar.LuckyPillarMiniGame;
import net.exmo.sreGame.games.nametagwar.NameTagWarMiniGame;
import net.exmo.sreGame.games.pillarpummel.PillarPummelMiniGame;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMiniGame;
import net.exmo.sreGame.games.skyworld.SkyWorldMiniGame;
import net.exmo.sreGame.games.situationpuzzle.SituationPuzzleMiniGame;
import net.exmo.sreGame.games.youguess.YouGuessMiniGame;
import net.exmo.sreGame.command.GameCommands;
import net.exmo.sreGame.game.DuelMiniGame;
import net.exmo.sreGame.input.ChatPrompt;
import com.mcrpvp.duel.fabric.api.DuelApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.exmo.sreGame.games.quakechasm.QuakeCTFMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeTDMMiniGame;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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
      context.games().register(new DigToDeathMiniGame(context));
      context.games().register(new YouBuildRunMiniGame(context));
      context.games().register(new PushTheButtonMiniGame(context));
      context.games().register(new SkyWorldMiniGame(context));
      context.games().register(new SituationPuzzleMiniGame(context));
      context.games().register(new NameTagWarMiniGame(context));
      context.games().register(new FillInTheWallMiniGame(context));
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
            || context.digToDeath().isPlaying(player)
            || context.youBuildRun().isPlaying(player)
            || context.pushTheButton().isPlaying(player)
            || context.skyWorld().isPlaying(player)
            || context.situationPuzzle().isPlaying(player)
            || context.nameTagWar().isPlaying(player)
            || context.fillInTheWall().isPlaying(player)
            || context.parkour().isPlaying(player)));
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
         if (!(entity instanceof ServerPlayer player) || context == null) {
            return true;
         }
         return !context.chickenHorse().handleDamage(player, source)
            && !context.dontDo().handleDamage(player, source, amount)
            && !context.luckyPillar().handleDamage(player, source)
            && !context.pillarPummel().handleDamage(player, source)
            && !context.dodgeball().handleDamage(player, source)
            && !context.digToDeath().handleDamage(player, source)
            && !context.youBuildRun().handleDamage(player, source)
            && !context.pushTheButton().handleDamage(player, source)
            && !context.skyWorld().handleDamage(player, source)
            && !context.parkour().handleDamage(player, source)
            && !context.nameTagWar().handleDamage(player, source)
            && !context.fillInTheWall().handleDamage(player, source);
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
            && !context.digToDeath().handleDeath(player)
            && !context.youBuildRun().handleDeath(player)
            && !context.pushTheButton().handleDeath(player)
            && !context.skyWorld().handleDeath(player, source)
            && !context.parkour().handleDeath(player)
            && !context.nameTagWar().handleDeath(player, source)
            && !context.fillInTheWall().handleDeath(player);
      });
      AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp && context != null) {
            context.dontDo().handleAttack(sp, entity);
            if (context.digToDeath().isPlaying(sp)
               || context.chickenHorse().isPlaying(sp) && entity instanceof ServerPlayer) {
               return net.minecraft.world.InteractionResult.FAIL;
            }
         }
         return net.minecraft.world.InteractionResult.PASS;
      });
      UseItemCallback.EVENT.register((player, world, hand) -> {
         var stack = player.getItemInHand(hand);
         if (world.isClientSide() || !(player instanceof ServerPlayer sp) || QuakeManager.INSTANCE == null) {
            return InteractionResultHolder.pass(stack);
         }
         QuakeUserState qst = QuakeManager.INSTANCE.getUserState(sp);
         if (qst != null && qst.currentMatch != null && stack.getItem() == Items.CARROT_ON_A_STICK) {
            qst.weaponState.shoot(sp);
            return InteractionResultHolder.fail(stack);
         }
         return InteractionResultHolder.pass(stack);
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
            context.parkour().leave(handler.getPlayer(), false);
            context.rooms().onDisconnect(handler.getPlayer());
            if (QuakeManager.INSTANCE != null) QuakeManager.INSTANCE.removePlayer(handler.getPlayer());
         }
      });
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
         if (handler.getPlayer() != null) {
            context.rooms().onRejoin(handler.getPlayer());
            if (QuakeManager.INSTANCE != null) QuakeManager.INSTANCE.initPlayer(handler.getPlayer());
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
      LOGGER.info("SRE-GAME initialized — /sregame to open the room menu.");
   }

   public static GameContext getContext() {
      return context;
   }
}
