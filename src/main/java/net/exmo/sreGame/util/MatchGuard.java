package net.exmo.sreGame.util;

import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class MatchGuard {
   private MatchGuard() {
   }

   public static boolean inMinigame(Player player) {
      if (!(player instanceof ServerPlayer sp)) {
         return false;
      }
      GameContext ctx = SreGame.getContext();
      return ctx != null && (ctx.buildWar().isPlaying(sp) || ctx.youGuess().isPlaying(sp)
         || ctx.fraudMaster().isPlaying(sp) || ctx.fakeHuman().isPlaying(sp)
         || ctx.caveGuess().isPlaying(sp) || ctx.chickenHorse().isPlaying(sp)
         || ctx.dontDo().isPlaying(sp) || ctx.luckyPillar().isPlaying(sp)
         || ctx.pillarPummel().isPlaying(sp) || ctx.dodgeball().isPlaying(sp)
         || ctx.digToDeath().isPlaying(sp) || ctx.youBuildRun().isPlaying(sp)
         || ctx.pushTheButton().isPlaying(sp) || ctx.skyWorld().isPlaying(sp)
         || ctx.situationPuzzle().isPlaying(sp)
         || ctx.nameTagWar().isPlaying(sp)
         || ctx.parkour().isPlaying(sp));
   }

   public static boolean lockItemDrops(Player player) {
      if (!(player instanceof ServerPlayer sp)) {
         return false;
      }
      GameContext ctx = SreGame.getContext();
      return ctx != null && (ctx.buildWar().isPlaying(sp) || ctx.youGuess().isPlaying(sp)
         || ctx.fraudMaster().isPlaying(sp) || ctx.fakeHuman().isPlaying(sp)
         || ctx.caveGuess().isPlaying(sp) || ctx.chickenHorse().isPlaying(sp)
         || ctx.dodgeball().isPlaying(sp) || ctx.digToDeath().isPlaying(sp)
         || ctx.youBuildRun().isPlaying(sp) || ctx.pushTheButton().isPlaying(sp)
         || ctx.situationPuzzle().isPlaying(sp)
         || ctx.nameTagWar().isPlaying(sp)
         || ctx.parkour().isPlaying(sp));
   }

   public static boolean lockCreativeInventory(Player player) {
      if (!(player instanceof ServerPlayer sp)) {
         return false;
      }
      GameContext ctx = SreGame.getContext();
      return ctx != null && ctx.chickenHorse().isPlaying(sp);
   }
}
