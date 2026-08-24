package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;

/** Spawns an armor pickup. Ported from ArmorSpawner. */
public class ArmorSpawner extends Spawner {
    private final int armor;

    public ArmorSpawner(int armor, ServerLevel level, Vec3 pos) {
        super(armorItem(armor), level, pos);
        this.armor = armor;
        QEntityUtil.setEntityType(display, "armor_spawner");
    }

    private static ItemStack armorItem(int a) {
        Item i = a == 5 ? Items.IRON_INGOT : a == 50 ? Items.GOLDEN_CHESTPLATE : Items.NETHERITE_CHESTPLATE;
        return new ItemStack(i);
    }

    private String nameKey() {
        return armor == 5 ? "pickup.armor.shard" : armor == 50 ? "pickup.armor.light" : "pickup.armor.heavy";
    }

    @Override
    public void onPickup(ServerPlayer player) {
        if (display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        QuakeUserState st = QuakeManager.INSTANCE.getUserState(player);
        if (st == null || st.armor >= 200) return;
        hideItem();
        st.armor = Math.min(200, st.armor + armor);
        if (st.armor > 100) st.startArmorDecreaser();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
        QuakeHud.pickupMessage(player, QuakeTranslator.t(nameKey()));
        scheduleRespawn(25);
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        display.setItemSlot(EquipmentSlot.HEAD, armorItem(armor));
        respawnEffect();
    }
}
