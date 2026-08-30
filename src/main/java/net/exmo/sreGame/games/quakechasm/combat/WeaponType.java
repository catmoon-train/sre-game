package net.exmo.sreGame.games.quakechasm.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public abstract class WeaponType {
    public static final int MACHINEGUN = 0;
    public static final int SHOTGUN = 1;
    public static final int ROCKET_LAUNCHER = 2;
    public static final int LIGHTNING_GUN = 3;
    public static final int RAILGUN = 4;
    public static final int PLASMA_GUN = 5;
    public static final int BFG = 6;

    /** Display-name translation keys, indexed by WeaponType constant. */
    public static final String[] NAME_KEYS = {
        "pickup.weapon.machinegun",
        "pickup.weapon.shotgun",
        "pickup.weapon.rocketLauncher",
        "pickup.weapon.lightningGun",
        "pickup.weapon.railgun",
        "pickup.weapon.plasmaGun",
        "pickup.weapon.bfg"
    };

    /**
     * Vanilla item used as the visual model for each weapon (no resource pack needed).
     * All carry a {@code qc_weapon} NBT tag for identification.
     */
    public static final Item[] ITEMS = {
        Items.CARROT_ON_A_STICK, // machinegun
        Items.STONE_HOE,         // shotgun
        Items.BLAZE_ROD,         // rocket launcher
        Items.PRISMARINE_CRYSTALS, // lightning gun
        Items.END_ROD,           // railgun
        Items.GLOWSTONE_DUST,    // plasma gun
        Items.NETHER_STAR        // bfg
    };
}
