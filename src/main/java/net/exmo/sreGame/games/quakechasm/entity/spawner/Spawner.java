package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.entity.DisplayPickup;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;

/**
 * Abstract world pickup. Ported from quakechasm's Spawner.
 * Uses an invisible small ArmorStand (helmet = display item) as carrier instead of ItemDisplay.
 */
public abstract class Spawner implements DisplayPickup {
    protected ArmorStand display;
    protected ItemStack itemForRespawn;

    public Spawner(ItemStack displayItem, ServerLevel level, Vec3 pos) {
        ArmorStand as = EntityType.ARMOR_STAND.create(level);
        if (as == null) as = new ArmorStand(level, pos.x, pos.y, pos.z);
        this.display = as;
        this.display.moveTo(pos.x, pos.y, pos.z, 0, 0);
        this.display.setInvisible(true);
        this.display.setNoGravity(true);
        QEntityUtil.armorStandFlags(this.display, true, true);
        this.display.setItemSlot(EquipmentSlot.HEAD, displayItem);
        this.display.setSilent(true);
        level.addFreshEntity(this.display);
        QuakeManager.INSTANCE.triggers.add(this);
    }

    /** For loading an existing ArmorStand as a spawner. */
    protected Spawner(ArmorStand existing) {
        this.display = existing;
        QuakeManager.INSTANCE.triggers.add(this);
    }

    @Override public abstract void onPickup(ServerPlayer player);
    public abstract void respawn();

    @Override public void onTrigger(Entity entity) {
        if (entity instanceof ServerPlayer sp) onPickup(sp);
    }

    @Override public void onUnload() {}
    @Override public Vec3 getLocation() { return display.position(); }
    @Override public ArmorStand getDisplay() { return display; }
    @Override public AABB getOffsetBoundingBox() { return AABB.ofSize(display.position(), 2, 2, 2); }
    @Override public Entity getEntity() { return display; }
    @Override public ServerLevel getLevel() { return (ServerLevel) display.level(); }
    @Override public boolean isDead() { return display == null || display.isRemoved(); }
    @Override public void remove() { if (display != null) display.discard(); }

    protected void hideItem() {
        itemForRespawn = display.getItemBySlot(EquipmentSlot.HEAD);
        display.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    protected void scheduleRespawn(int seconds) {
        QuakeManager.INSTANCE.schedule(seconds * 20, this::respawn);
    }

    protected void respawnEffect() {
        ServerLevel level = getLevel();
        if (level == null || display == null) return;
        level.sendParticles(ParticleTypes.END_ROD, display.getX(), display.getY() + 0.5, display.getZ(), 16, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, display.getX(), display.getY(), display.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
    }
}
