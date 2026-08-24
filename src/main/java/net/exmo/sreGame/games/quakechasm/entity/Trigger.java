package net.exmo.sreGame.games.quakechasm.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

/**
 * An in-world trigger (spawner / portal / jumppad). Ported from quakechasm's Trigger.
 */
public interface Trigger {
    void onTrigger(Entity entity);
    void onUnload();
    Vec3 getLocation();
    Entity getEntity();
    AABB getOffsetBoundingBox();
    ServerLevel getLevel();

    default boolean isDead() {
        return getEntity() == null || !getEntity().isAlive();
    }

    void remove();
}
