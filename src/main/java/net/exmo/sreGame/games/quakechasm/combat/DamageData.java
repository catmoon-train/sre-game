package net.exmo.sreGame.games.quakechasm.combat;

import net.minecraft.world.entity.Entity;

/** Records the last damage dealt to a player. Ported from quakechasm's DamageData. */
public final class DamageData {
    private final Entity attacker;
    private final double damage;
    private final DamageCause cause;

    public DamageData(Entity attacker, double damage, DamageCause cause) {
        this.attacker = attacker;
        this.damage = damage;
        this.cause = cause;
    }

    public Entity getAttacker() { return attacker; }
    public double getDamage() { return damage; }
    public DamageCause getCause() { return cause; }
}
