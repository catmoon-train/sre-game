package net.exmo.sreGame.games.quakechasm.entity.trigger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.entity.Trigger;
import net.exmo.sreGame.games.quakechasm.util.MiscUtil;

/** Slipgate teleporter. Ported from Portal; uses ArmorStand as carrier. */
public class Portal implements Trigger {
    private final ArmorStand marker;
    private final ServerLevel targetLevel;
    private final Vec3 target;
    private final float yaw;
    private final float pitch;

    public Portal(ServerLevel level, Vec3 pos, ServerLevel targetLevel, Vec3 target, float yaw, float pitch) {
        ArmorStand as = EntityType.ARMOR_STAND.create(level);
        if (as == null) as = new ArmorStand(level, pos.x, pos.y, pos.z);
        this.marker = as;
        marker.moveTo(pos.x, pos.y, pos.z, 0, 0);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setSilent(true);
        QEntityUtil.armorStandFlags(marker, true, true);
        level.addFreshEntity(marker);
        this.targetLevel = targetLevel;
        this.target = target;
        this.yaw = yaw;
        this.pitch = pitch;
        QEntityUtil.setEntityType(marker, "portal");
        QuakeManager.INSTANCE.triggers.add(this);
    }

    @Override
    public void onTrigger(Entity entity) {
        if (entity instanceof ServerPlayer p) {
            p.teleportTo(targetLevel, target.x, target.y, target.z, yaw, pitch);
        } else {
            entity.moveTo(target.x, target.y, target.z, yaw, pitch);
        }
        MiscUtil.teleEffect((ServerLevel) entity.level(), entity.position(), true);
        MiscUtil.teleEffect(targetLevel, target, false);
    }

    @Override public void onUnload() {}
    @Override public Vec3 getLocation() { return marker.position(); }
    @Override public Entity getEntity() { return marker; }
    @Override public AABB getOffsetBoundingBox() { return AABB.ofSize(marker.position(), 1.5, 3, 1.5); }
    @Override public ServerLevel getLevel() { return (ServerLevel) marker.level(); }
    @Override public void remove() { marker.discard(); }
}
