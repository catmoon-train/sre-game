package net.exmo.sreGame.games.quakechasm.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Projectile helpers: type tagging via entity scoreboard tags, and the explosion
 * logic for rocket / plasma / BFG. Ported from quakechasm's ProjectileUtil.
 */
public final class ProjectileUtil {
    private ProjectileUtil() {}

    public static final String TAG_ROCKET = "qc_proj_rocket";
    public static final String TAG_PLASMA = "qc_proj_plasma";
    public static final String TAG_BFG = "qc_proj_bfg";

    public static void setProjectileType(Entity e, String tag) {
        e.addTag(tag);
    }

    public static String getProjectileType(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith("qc_proj_")) return t.substring("qc_proj_".length());
        }
        return null;
    }

    /** Rocket explosion: linear falloff damage + knockback + particles. */
    public static void explodeRocket(ServerLevel level, Vec3 pos, Entity attacker, Entity directHit) {
        AABB area = AABB.ofSize(pos, 12, 12, 12);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity v : victims) {
            if (v == attacker) {
                // self-damage reduced (rocket jump)
                double d = v.position().distanceTo(pos);
                if (d > 6) continue;
                WeaponUtil.damageCustom(v, 40 * (1 - d / 6), attacker, DamageCause.ROCKET_SPLASH);
            } else {
                double d = v.position().distanceTo(pos);
                if (d > 6) continue;
                WeaponUtil.damageCustom(v, 100 * (1 - d / 6), attacker, DamageCause.ROCKET_SPLASH);
                Vec3 kb = v.position().subtract(pos).normalize().scale(0.6 * (1 - d / 6));
                v.push(kb.x, kb.y + 0.3, kb.z);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 12, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** Plasma explosion: smaller radius. */
    public static void explodePlasma(ServerLevel level, Vec3 pos, Entity attacker, Entity directHit, boolean splash) {
        AABB area = AABB.ofSize(pos, 6, 6, 6);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double d = v.position().distanceTo(pos);
            if (d > 3) continue;
            WeaponUtil.damageCustom(v, 80 * (1 - d / 3), attacker, DamageCause.PLASMA_SPLASH);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 16, 0.5, 0.5, 0.5, 1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** BFG explosion: big radius + ray damage to nearby visible entities. */
    public static void explodeBFG(ServerLevel level, Vec3 pos, Entity attacker) {
        AABB area = AABB.ofSize(pos, 16, 16, 16);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (v == attacker) continue;
            double d = v.position().distanceTo(pos);
            if (d > 8) continue;
            WeaponUtil.damageCustom(v, 120 * (1 - d / 8), attacker, DamageCause.BFG_SPLASH);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 0.8f);
    }
}
