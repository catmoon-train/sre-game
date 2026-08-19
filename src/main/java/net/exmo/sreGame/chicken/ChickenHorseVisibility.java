package net.exmo.sreGame.chicken;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.mixin.ChunkMapAccessor;
import net.exmo.sreGame.mixin.TrackedEntityAccessor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class ChickenHorseVisibility {
   public static final String ACTION = "ch_hide";

   private ChickenHorseVisibility() {
   }

   public static boolean isHideItem(ItemStack stack) {
      return ACTION.equals(GuiItems.actionTag(stack));
   }

   public static ItemStack item(boolean hiding) {
      return GuiItems.action(
         hiding ? "tinted_glass" : "ender_eye",
         hiding ? "&c他人 &8[&c隐藏&8]" : "&a他人 &8[&a可见&8]",
         List.of("&7右键切换其他人是否可见", hiding ? "&c当前：已隐藏" : "&a当前：可见"),
         ACTION,
         "hide",
         hiding ? "1" : "0"
      );
   }

   public static boolean hiddenFrom(ServerPlayer viewer, Entity entity) {
      if (!(entity instanceof ServerPlayer other) || viewer == other) {
         return false;
      }
      GameContext ctx = SreGame.getContext();
      if (ctx == null) {
         return false;
      }
      ChickenHorseMatch match = ctx.chickenHorse().get(viewer.getUUID());
      return match != null && match == ctx.chickenHorse().get(other.getUUID()) && match.hidesOthers(viewer.getUUID());
   }

   public static void sync(ServerPlayer viewer, boolean hide, Iterable<ServerPlayer> others) {
      for (ServerPlayer other : others) {
         if (other == viewer) {
            continue;
         }
         ChunkMap chunkMap = other.serverLevel().getChunkSource().chunkMap;
         Object tracked = ((ChunkMapAccessor) chunkMap).sre$entityMap().get(other.getId());
         if (!(tracked instanceof TrackedEntityAccessor accessor)) {
            continue;
         }
         accessor.sre$removePlayer(viewer);
         if (!hide) {
            accessor.sre$updatePlayer(viewer);
         }
      }
   }
}
