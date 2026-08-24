package net.exmo.sreGame.games.quakechasm.movement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeConfig;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.util.MiscUtil;

/**
 * Bunnyhop / strafejump acceleration. Ported from quakechasm's StrafeJumpHandler.
 * Called from the player-move hook when the player is sprinting in mid-air.
 */
public final class StrafeJumpHandler {
    private StrafeJumpHandler() {}

    private static final int TICK_INTERVAL = 5;
    private static final double AIR_ACCELERATION = 0.2 * TICK_INTERVAL;
    private static final double MAX_SPEED_MULTIPLIER = 1.8 * TICK_INTERVAL;
    private static final double ANGLE_THRESHOLD = 0.95;

    public static void applyStrafeAcceleration(ServerPlayer player, QuakeUserState state, Vec3 velocity) {
        if (player.isShiftKeyDown() || !player.isSprinting()) {
            return;
        }

        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);
        if (horizontal.lengthSqr() < 0.01) return;

        Vec3 look = player.getLookAngle();
        double alignment = look.dot(horizontal.normalize());

        if (Math.abs(alignment) < ANGLE_THRESHOLD && state.strafeJumpTicks % TICK_INTERVAL == 0) {
            double baseSpeed = QuakeConfig.get().player.walkSpeed * TICK_INTERVAL;
            double currentSpeed = horizontal.length();
            double maxSpeed = baseSpeed * MAX_SPEED_MULTIPLIER;
            if (currentSpeed >= maxSpeed) return;

            double factor = (ANGLE_THRESHOLD - Math.abs(alignment)) / ANGLE_THRESHOLD;
            Vec3 lookH = new Vec3(look.x, 0, look.z).normalize();
            velocity = velocity.add(lookH.scale(AIR_ACCELERATION * factor));

            Vec3 nh = new Vec3(velocity.x, 0, velocity.z);
            if (nh.length() > maxSpeed) {
                nh = nh.normalize().scale(maxSpeed);
                velocity = new Vec3(nh.x, velocity.y, nh.z);
            }
            velocity = velocity.add(0, -MiscUtil.GRAVITY, 0);
            player.setDeltaMovement(velocity);
        }
    }
}
