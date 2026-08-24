package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;

/** Spawns an ammo pickup. Ported from AmmoSpawner. */
public class AmmoSpawner extends Spawner {
    public static final int[] AMOUNTS = {50, 10, 5, 60, 10, 30, 1};
    public static final String[] NAME_KEYS = {
        "pickup.ammo.bullets", "pickup.ammo.shells", "pickup.ammo.rockets",
        "pickup.ammo.battery", "pickup.ammo.slugs", "pickup.ammo.cells", "pickup.ammo.bfg"
    };
    private final int ammoType;

    public AmmoSpawner(int ammoType, ServerLevel level, Vec3 pos) {
        super(ammoItem(ammoType), level, pos);
        this.ammoType = ammoType;
        QEntityUtil.setEntityType(display, "ammo_spawner");
    }

    private static ItemStack ammoItem(int t) {
        ItemStack it = new ItemStack(Items.GUNPOWDER);
        it.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(n -> n.putInt("qc_ammo", t)));
        return it;
    }

    @Override
    public void onPickup(ServerPlayer player) {
        if (display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        if (st == null || st.weaponState.ammo[ammoType] >= 200) return;
        hideItem();
        st.weaponState.ammo[ammoType] = Math.min(200, st.weaponState.ammo[ammoType] + AMOUNTS[ammoType]);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
        QuakeHud.pickupMessage(player, QuakeTranslator.t(NAME_KEYS[ammoType]));
        scheduleRespawn(40);
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        display.setItemSlot(EquipmentSlot.HEAD, ammoItem(ammoType));
        respawnEffect();
    }
}
