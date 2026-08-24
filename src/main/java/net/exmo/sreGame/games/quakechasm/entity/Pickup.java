package net.exmo.sreGame.games.quakechasm.entity;

import net.minecraft.server.level.ServerPlayer;

/** A trigger that gives something to a player who steps in. Ported from Pickup. */
public interface Pickup extends Trigger {
    void onPickup(ServerPlayer player);
}
