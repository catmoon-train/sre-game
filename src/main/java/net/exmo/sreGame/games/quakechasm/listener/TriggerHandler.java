package net.exmo.sreGame.games.quakechasm.listener;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.entity.Trigger;

/**
 * Replaces quakechasm's TriggerListener (PlayerMoveEvent). Fabric has no player-move
 * event, so we scan quake players vs triggers every tick in QuakeManager.tick().
 */
public final class TriggerHandler {
    private TriggerHandler() {}

    public static void checkTriggers(MinecraftServer server) {
        QuakeManager qm = QuakeManager.INSTANCE;
        if (qm == null || qm.triggers.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            QuakeUserState st = qm.getUserState(p);
            if (st == null || st.currentMatch == null) continue;
            AABB pbox = p.getBoundingBox();
            for (Trigger t : qm.triggers) {
                if (t.isDead()) continue;
                if (t.getLevel() != p.serverLevel()) continue;
                try {
                    if (t.getOffsetBoundingBox().intersects(pbox)) {
                        t.onTrigger(p);
                    }
                } catch (Throwable ignored) {
                    // never let one bad trigger kill the tick loop
                }
            }
        }
    }
}
