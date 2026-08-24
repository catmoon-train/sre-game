package net.exmo.sreGame.games.quakechasm.entity;

import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * A pickup shown as an entity in the world. Ported from DisplayPickup.
 * Uses ArmorStand (helmet item) as the display carrier instead of Bukkit ItemDisplay.
 */
public interface DisplayPickup extends Pickup {
    ArmorStand getDisplay();
}
