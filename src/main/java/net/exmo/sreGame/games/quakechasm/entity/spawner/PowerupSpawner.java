package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.powerup.Powerup;
import net.exmo.sreGame.games.quakechasm.combat.powerup.PowerupType;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;

/** Spawns a powerup. Ported from PowerupSpawner. */
public class PowerupSpawner extends Spawner {
    private final PowerupType type;
    private final int duration;
    private final boolean isDrop;

    public PowerupSpawner(PowerupType type, ServerLevel level, Vec3 pos, boolean isDrop, int duration) {
        super(new ItemStack(Items.TOTEM_OF_UNDYING), level, pos);
        this.type = type;
        this.duration = duration;
        this.isDrop = isDrop;
        if (!isDrop) QEntityUtil.setEntityType(display, "powerup_spawner");
    }

    @Override
    public void onPickup(ServerPlayer player) {
        if (display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        hideItem();
        doPowerup(player, type, duration);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
        QuakeHud.pickupMessage(player, QuakeTranslator.t("pickup.powerup." + type.name().toLowerCase()));
        if (isDrop) {
            QuakeManager.INSTANCE.triggers.remove(this);
            remove();
        } else {
            scheduleRespawn(60);
        }
    }

    public static void doPowerup(ServerPlayer player, PowerupType type, int time) {
        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        if (st == null) return;
        boolean found = false;
        for (Powerup pu : st.activePowerups) {
            if (pu.getType() == type) {
                pu.extendDuration(time);
                found = true;
                break;
            }
        }
        if (!found) st.activePowerups.add(new Powerup(type, time));
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || isDrop) return;
        display.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TOTEM_OF_UNDYING));
        respawnEffect();
    }
}
