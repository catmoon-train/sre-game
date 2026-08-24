package net.exmo.sreGame.games.quakechasm.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.powerup.Powerup;
import net.exmo.sreGame.games.quakechasm.combat.powerup.PowerupType;
import net.exmo.sreGame.games.quakechasm.match.Team;
import net.exmo.sreGame.mixin.QuakeLivingAccessor;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static net.exmo.sreGame.games.quakechasm.combat.ProjectileUtil.*;

/**
 * Core weapon mechanics. Ported from quakechasm's WeaponUtil.
 * Bukkit rayTrace/Particle/Player replaced with Fabric ClipContext/ParticleTypes/ServerPlayer.
 */
public abstract class WeaponUtil {
    public static final int[] PERIODS = {2, 20, 16, 1, 30, 2, 50};
    public static final int[] DEFAULT_AMMO = {100, 10, 10, 100, 10, 50, 1};
    public static final int WEAPONS_NUM = 7;

    public static int getHoldingWeaponIndex(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        var tag = data.copyTag();
        if (!tag.contains("qc_weapon")) return -1;
        return tag.getInt("qc_weapon");
    }

    // ---- raycast helpers ----

    private static Vec3 applySpread(Vec3 dir, double spread) {
        if (spread == 0) return dir;
        double ry = Math.toRadians(Math.random() * spread - spread / 2);
        double rp = Math.toRadians(Math.random() * spread - spread / 2);
        double cosY = Math.cos(ry), sinY = Math.sin(ry);
        double x1 = dir.x * cosY + dir.z * sinY;
        double z1 = -dir.x * sinY + dir.z * cosY;
        double cosP = Math.cos(rp), sinP = Math.sin(rp);
        double y1 = dir.y * cosP - z1 * sinP;
        double z2 = dir.y * sinP + z1 * cosP;
        return new Vec3(x1, y1, z2).normalize();
    }

    /** Result of a hitscan ray. */
    public static final class HitScan {
        public Vec3 hitPos;
        public LivingEntity hitEntity;
        public boolean hitBlock;
        public Vec3 blockNormal = Vec3.ZERO;
        public boolean missed() { return hitEntity == null && !hitBlock; }
    }

    public static HitScan cast(ServerPlayer player, double spread, double limit) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1f);
        Vec3 dir = applySpread(player.getLookAngle(), spread);
        Vec3 end = eye.add(dir.scale(limit));

        HitScan result = new HitScan();

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockHitPos = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
        double blockDist = eye.distanceToSqr(blockHitPos);

        AABB scanArea = AABB.ofSize(eye, limit + 2, limit + 2, limit + 2);
        LivingEntity nearest = null;
        Vec3 nearestPos = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scanArea)) {
            if (e == player) continue;
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (hit.isEmpty()) continue;
            Vec3 hp = hit.get();
            double d = eye.distanceToSqr(hp);
            if (d < blockDist && d < nearestDist) {
                nearest = e;
                nearestPos = hp;
                nearestDist = d;
            }
        }

        if (nearest != null) {
            result.hitEntity = nearest;
            result.hitPos = nearestPos;
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            result.hitBlock = true;
            result.hitPos = blockHitPos;
            result.blockNormal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
        } else {
            result.hitPos = end;
        }
        return result;
    }

    // ---- damage ----

    public static void damageCustom(LivingEntity victim, double amount, Entity attacker, DamageCause cause) {
        if (cause == null) cause = DamageCause.UNKNOWN;

        if (cause == DamageCause.RAILGUN && attacker instanceof ServerPlayer ap) {
            QuakeUserState st = QuakeManager.INSTANCE.getUserState(ap);
            if (st != null) {
                st.consecutiveRailgunHits++;
                st.checkImpressiveMedal();
            }
        }

        // reset i-frames so rapid-fire weapons (machinegun/lightning) hit every tick
        try { ((QuakeLivingAccessor) victim).quake$setInvulnerableTime(0); } catch (ClassCastException ignored) {}
        if (victim instanceof ServerPlayer sp && !sp.isCreative()) {
            QuakeUserState st = QuakeManager.INSTANCE.getUserState(sp);
            if (st != null) st.lastDamage = new DamageData(attacker, amount, cause);
        }
        var src = attacker instanceof ServerPlayer p
                ? p.damageSources().playerAttack(p)
                : (attacker instanceof LivingEntity le ? victim.damageSources().mobAttack(le) : victim.damageSources().generic());
        victim.hurt(src, (float) amount);
    }

    public static void knockback(Vec3 from, Entity victim, double power) {
        Vec3 dir = victim.position().subtract(from).normalize();
        victim.setDeltaMovement(victim.getDeltaMovement().add(dir.scale(power)));
    }

    public static boolean hasLineOfSight(ServerLevel level, Entity viewer, Entity target) {
        Vec3 a = viewer.getEyePosition(1f);
        Vec3 b = target.getEyePosition(1f);
        Vec3 dir = b.subtract(a).normalize();
        double dist = a.distanceTo(b) + 1;
        BlockHitResult r = level.clip(new ClipContext(a, a.add(dir.scale(dist)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return r.getType() != HitResult.Type.BLOCK;
    }

    // ---- particle helpers ----

    public static void spawnParticlesLine(ServerLevel level, Vec3 a, Vec3 b, double density) {
        double dist = a.distanceTo(b);
        int n = (int) (dist * density);
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            Vec3 p = new Vec3(
                    a.x + t * (b.x - a.x),
                    a.y + t * (b.y - a.y),
                    a.z + t * (b.z - a.z));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void bulletImpact(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 4, 0, 0, 0, 0.25);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    public static void lightningImpact(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 8, 0, 0, 0, 1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    public static void railImpact(ServerLevel level, Vec3 pos, Vec3 normal) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.4f);
        Random r = new Random();
        for (int i = 0; i < 16; i++) {
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1,
                    (r.nextDouble() - 0.5) * 1.5, (r.nextDouble() - 0.5) * 1.5, (r.nextDouble() - 0.5) * 1.5, 0.1);
        }
    }

    // ---- hitscan ----

    /** Fire a hitscan ray, damaging the first entity hit and applying block impact. */
    public static HitScan fireHitscan(ServerPlayer player, double damage, double spread, double limit,
                                      DamageCause cause, boolean pierce) {
        HitScan ray = cast(player, spread, limit);
        ServerLevel level = player.serverLevel();

        if (ray.hitEntity != null) {
            damageCustom(ray.hitEntity, damage, player, cause);
            if (pierce) {
                processPiercing(player, damage, cause, limit, ray);
            }
        } else {
            if (cause == DamageCause.RAILGUN) {
                QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
                if (st != null) st.consecutiveRailgunHits = 0;
            }
        }

        if (ray.hitBlock) {
            bulletImpact(level, ray.hitPos);
        }
        return ray;
    }

    private static void processPiercing(ServerPlayer player, double damage, DamageCause cause, double limit, HitScan ray) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1f);
        Vec3 origin = ray.hitPos;
        Vec3 dir = origin.subtract(eye).normalize();
        double remaining = limit - eye.distanceTo(origin);
        if (remaining <= 0) return;
        AABB scanArea = AABB.ofSize(origin, remaining + 2, remaining + 2, remaining + 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scanArea)) {
            if (e == player || e == ray.hitEntity) continue;
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.35).clip(origin, origin.add(dir.scale(remaining)));
            if (hit.isPresent()) {
                damageCustom(e, damage, player, cause);
            }
        }
    }

    // ---- weapons ----

    public static void fireMachinegun(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f);
        fireHitscan(player, 1.4, 2, 256, DamageCause.MACHINEGUN, false);
    }

    public static void fireShotgun(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3f, 2f);
        for (int i = 0; i < 11; i++) {
            fireHitscan(player, 2, 8, 256, DamageCause.SHOTGUN, false);
        }
    }

    public static void fireRocket(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.5f, 1f);

        // short-range instant hit
        HitScan near = cast(player, 0, player.isSprinting() ? 6 : 2);
        if (near.hitBlock || near.hitEntity != null) {
            explodeRocket(level, near.hitPos, player, near.hitEntity);
            return;
        }

        Snowball proj = new Snowball(level, player);
        Vec3 eye = player.getEyePosition(1f);
        proj.setPos(eye.x, eye.y, eye.z);
        proj.setNoGravity(true);
        Vec3 vel = player.getLookAngle().scale(1.5);
        proj.setDeltaMovement(vel);
        setProjectileType(proj, TAG_ROCKET);
        level.addFreshEntity(proj);
        QuakeManager.INSTANCE.trackProjectile(proj, "rocket", player);
    }

    public static void fireLightning(ServerPlayer player, boolean emitSound) {
        ServerLevel level = player.serverLevel();
        if (emitSound) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        HitScan ray = fireHitscan(player, 1.6, 0, 16, DamageCause.LIGHTNING, false);
        if (ray.hitEntity != null) {
            knockback(player.getEyePosition(1f), ray.hitEntity, 0.25);
        }
        Vec3 eye = player.getEyePosition(1f).add(0, player.getBbHeight() - 0.4, 0);
        if (ray.hitBlock) lightningImpact(level, ray.hitPos);
        spawnParticlesLine(level, eye, ray.hitPos, 4);
    }

    public static void fireRailgun(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 0.8f);
        HitScan ray = fireHitscan(player, 20, 0, 256, DamageCause.RAILGUN, true);
        if (ray.hitBlock) railImpact(level, ray.hitPos, ray.blockNormal);
        if (ray.hitEntity != null) {
            knockback(player.getEyePosition(1f), ray.hitEntity, 1.5);
        }
        Vec3 eye = player.getEyePosition(1f).add(0, player.getBbHeight() - 0.4, 0);
        spawnParticlesLine(level, eye, ray.hitPos, 8);
    }

    public static void firePlasma(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.2f);
        HitScan near = cast(player, 0, player.isSprinting() ? 6 : 2);
        if (near.hitBlock || near.hitEntity != null) {
            explodePlasma(level, near.hitPos, player, near.hitEntity, true);
            return;
        }
        Snowball proj = new Snowball(level, player);
        Vec3 eye = player.getEyePosition(1f);
        proj.setPos(eye.x, eye.y, eye.z);
        proj.setNoGravity(true);
        proj.setDeltaMovement(player.getLookAngle().scale(2.0));
        setProjectileType(proj, TAG_PLASMA);
        level.addFreshEntity(proj);
        QuakeManager.INSTANCE.trackProjectile(proj, "plasma", player);
    }

    public static void fireBFG(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.6f);
        // delayed fire 18 ticks (0.9s) - tracked via a scheduled task in QuakeManager
        QuakeManager.INSTANCE.schedule(18, () -> fireBFGGuts(player));
    }

    private static void fireBFGGuts(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1f).add(0, player.getBbHeight() - 0.35, 0);
        HitScan near = cast(player, 0, player.isSprinting() ? 6 : 2);
        if (near.hitBlock || near.hitEntity != null) {
            explodeBFG(level, near.hitPos, player);
            return;
        }
        Snowball proj = new Snowball(level, player);
        proj.setPos(eye.x, eye.y, eye.z);
        proj.setNoGravity(true);
        proj.setDeltaMovement(player.getLookAngle().scale(0.7));
        setProjectileType(proj, TAG_BFG);
        level.addFreshEntity(proj);
        QuakeManager.INSTANCE.trackProjectile(proj, "bfg", player);
    }

    // ---- armor color helper (used by Match.setArmor) ----

    public static ItemStack teamArmor(net.minecraft.world.item.Item type, Team team) {
        ItemStack stack = new ItemStack(type);
        int rgb = Team.colorOf(team);
        net.minecraft.world.item.component.DyedItemColor dye = new net.minecraft.world.item.component.DyedItemColor(rgb, false);
        stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, dye);
        return stack;
    }

    public static ItemStack[] weaponNames() { return null; }
}
