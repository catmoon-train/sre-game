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
import net.exmo.sreGame.games.quakechasm.combat.WeaponType;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;
import net.exmo.sreGame.games.quakechasm.match.Team;
import net.exmo.sreGame.util.TextUtil;

/** Spawns a weapon pickup. Ported from WeaponSpawner. */
public class WeaponSpawner extends Spawner {
    private final int weaponIndex;

    public WeaponSpawner(int weaponIndex, ServerLevel level, Vec3 pos) {
        super(weaponItem(weaponIndex), level, pos);
        this.weaponIndex = weaponIndex;
        QEntityUtil.setEntityType(display, "weapon_spawner");
    }

    public static ItemStack weaponItem(int idx) {
        ItemStack it = new ItemStack(Items.CARROT_ON_A_STICK);
        it.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(t -> t.putInt("qc_weapon", idx)));
        it.set(DataComponents.CUSTOM_NAME, TextUtil.color(QuakeTranslator.t(WeaponType.NAME_KEYS[idx])));
        return it;
    }

    @Override
    public void onPickup(ServerPlayer player) {
        if (display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        hideItem();
        player.getInventory().add(weaponItem(weaponIndex));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
        QuakeHud.pickupMessage(player, QuakeTranslator.t(WeaponType.NAME_KEYS[weaponIndex]));

        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        boolean team = st != null && st.currentMatch != null
                && st.currentMatch.getTeamOfPlayer(player) != Team.FREE;
        scheduleRespawn(team ? 30 : 5);
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        display.setItemSlot(EquipmentSlot.HEAD, weaponItem(weaponIndex));
        respawnEffect();
    }
}
