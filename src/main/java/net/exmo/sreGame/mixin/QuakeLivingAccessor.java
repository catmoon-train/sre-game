package net.exmo.sreGame.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LivingEntity#invulnerableTime so Quake's rapid-fire weapons
 * (machinegun/lightning) can hit every tick instead of being dropped by the
 * vanilla i-frame gate.
 */
@Mixin(Entity.class)
public interface QuakeLivingAccessor {
    @Accessor("invulnerableTime") int quake$getInvulnerableTime();
    @Accessor("invulnerableTime") void quake$setInvulnerableTime(int value);
}
