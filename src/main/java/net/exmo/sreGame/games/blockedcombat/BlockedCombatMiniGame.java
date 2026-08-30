package net.exmo.sreGame.games.blockedcombat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.BlockedCombatSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class BlockedCombatMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "blocked_combat";
   private final GameContext ctx;

   public BlockedCombatMiniGame(GameContext ctx) { this.ctx = ctx; }
   @Override public String id() { return ID; }
   @Override public String displayName() { return "疯狂惊天矿工团"; }
   @Override public String icon() { return "diamond_pickaxe"; }
   @Override public int minPlayers() { return 1; }
   @Override public int maxPlayers() { return 24; }

   @Override public void openSetup(ServerPlayer host, GameRoom room) {
      BlockedCombatSetupGui.open(this.ctx, host, room);
   }

   @Override public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < minPlayers() || room.size() > maxPlayers()) {
         this.ctx.send(actor, "&c疯狂惊天矿工团需要 &f1–24 &c人（当前 &f" + room.size() + "&c）。");
         return false;
      }
      int capacity = room.blockedCombatSettings().teamSize() * 4;
      if (room.size() > capacity) {
         this.ctx.send(actor, "&c当前每队人数为 &f" + room.blockedCombatSettings().teamSize()
            + "&c，最多只能容纳四队共 &f" + capacity + " &c人。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) if (!room.isReady(uuid)) waiting.add(this.ctx.name(uuid));
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      return true;
   }

   @Override public void start(GameRoom room, ServerPlayer actor) {
      if (this.ctx.blockedCombat().start(room) == null) {
         this.ctx.send(actor, "&c没有空闲的疯狂惊天矿工团场地。");
      }
   }
}
