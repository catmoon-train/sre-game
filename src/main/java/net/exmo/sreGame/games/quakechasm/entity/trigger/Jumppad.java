package net.exmo.sreGame.games.quakechasm.entity.trigger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.entity.Trigger;

/** Launches entities upward/outward. Ported from Jumppad; uses ArmorStand as carrier. */
public class Jumppad implements Trigger {
    private final ArmorStand marker;
    private final Vec3 launchVec;
    private boolean triggered;

    public Jumppad(ServerLevel level, Vec3 pos, Vec3 launchVec) {
        ArmorStand as = EntityType.ARMOR_STAND.create(level);
        if (as == null) as = new ArmorStand(level, pos.x, pos.y, pos.z);
        this.marker = as;
        marker.moveTo(pos.x, pos.y, pos.z, 0, 0);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setSilent(true);
        QEntityUtil.armorStandFlags(marker, true, true);
        level.addFreshEntity(marker);
        this.launchVec = launchVec;
        QEntityUtil.setEntityType(marker, "jumppad");
        QuakeManager.INSTANCE.triggers.add(this);
    }

    @Override
    public void onTrigger(Entity entity) {
        if (triggered) return;
        triggered = true;
        entity.setDeltaMovement(launchVec);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1f, 1f);
        if (entity instanceof ServerPlayer p) p.setSprinting(false);
        QuakeManager.INSTANCE.schedule(10, () -> triggered = false);
    }

    @Override public void onUnload() {}
    @Override public Vec3 getLocation() { return marker.position(); }
    @Override public Entity getEntity() { return marker; }
    @Override public AABB getOffsetBoundingBox() { return AABB.ofSize(marker.position(), 1.5, 1.5, 1.5); }
    @Override public ServerLevel getLevel() { return (ServerLevel) marker.level(); }
    @Override public void remove() { marker.discard(); }

    public Vec3 getLaunchVec() { return launchVec; }
}
