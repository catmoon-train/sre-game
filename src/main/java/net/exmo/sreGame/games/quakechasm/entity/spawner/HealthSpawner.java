package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;

/** Spawns a health pickup. Ported from HealthSpawner. */
public class HealthSpawner extends Spawner {
    private final int health;

    public HealthSpawner(int health, ServerLevel level, Vec3 pos) {
        super(healthItem(health), level, pos);
        this.health = health;
        QEntityUtil.setEntityType(display, "health_spawner");
    }

    private static ItemStack healthItem(int h) {
        Item i = h == 1 ? Items.CARROT : h == 5 ? Items.BAKED_POTATO : h == 10 ? Items.COOKED_BEEF : Items.GOLDEN_APPLE;
        return new ItemStack(i);
    }

    @Override
    public void onPickup(ServerPlayer player) {
        if (display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        boolean megaOrSmall = health == 20 || health == 1;
        float hp = player.getHealth();
        if ((megaOrSmall && hp >= 40) || (!megaOrSmall && hp >= 20)) return;
        hideItem();
        double total = hp + health;
        if (megaOrSmall) {
            if (total > 40) total = 40;
            if (total > 20) player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(total);
        } else if (total > 20) {
            total = 20;
        }
        player.setHealth((float) total);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1f);
        QuakeHud.pickupMessage(player, QuakeTranslator.t(health == 20 ? "pickup.health.mega" : "pickup.health.generic"));
        scheduleRespawn(35);
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        display.setItemSlot(EquipmentSlot.HEAD, healthItem(health));
        respawnEffect();
    }
}
