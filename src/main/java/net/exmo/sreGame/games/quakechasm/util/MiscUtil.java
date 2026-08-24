package net.exmo.sreGame.games.quakechasm.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Quake utilities. Ported from quakechasm's misc.MiscUtil.
 * Bukkit Location/World/Particle replaced with Fabric Vec3/ServerLevel/ParticleTypes.
 */
public final class MiscUtil {
    private MiscUtil() {}

    public static final double GRAVITY = 0.08;
    public static final double AIR_DRAG = 0.91;

    private static final Gson ENHANCED = new GsonBuilder().setPrettyPrinting().create();

    public static Gson getEnhancedGson() {
        return ENHANCED;
    }

    /** Teleport particle + sound effect. {@code out=false} for arrival, {@code true} for departure. */
    public static void teleEffect(ServerLevel level, Vec3 pos, boolean out) {
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, out ? 1.2f : 0.8f);
        level.sendParticles(out ? ParticleTypes.PORTAL : ParticleTypes.END_ROD,
                pos.x, pos.y, pos.z, 16, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Simulate a ballistic trajectory and return the first hit position, or the
     * last sampled position if nothing was hit within the step budget.
     * Ported from quakechasm's MiscUtil.calculateTrajectory.
     */
    public static Vec3 calculateTrajectory(ServerLevel level, Vec3 start, Vec3 initialVel) {
        Vec3 pos = start;
        Vec3 vel = initialVel;
        double step = 0.1;
        for (int i = 0; i < 600; i++) {
            Vec3 next = pos.add(vel.scale(step));
            BlockPos bp = BlockPos.containing(next);
            if (!level.getBlockState(bp).isAir()) {
                return next;
            }
            pos = next;
            vel = vel.multiply(AIR_DRAG, AIR_DRAG, AIR_DRAG);
            vel = vel.add(0, -GRAVITY * step * 20, 0);
        }
        return pos;
    }

    /** Format a location compactly for chat messages. */
    public static String formatVec(Vec3 v) {
        return String.format("%.1f, %.1f, %.1f", v.x, v.y, v.z);
    }
}
