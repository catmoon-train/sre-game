package net.exmo.sreGame.games.quakechasm.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.exmo.sreGame.games.quakechasm.combat.powerup.Powerup;
import net.exmo.sreGame.games.quakechasm.combat.powerup.PowerupType;

import java.util.Arrays;

/**
 * Per-player weapon state. Ported from quakechasm's WeaponUserState.
 * Tick-driven (no BukkitRunnable): QuakeManager.tick() calls userState.weaponState.tick(player).
 */
public class WeaponUserState {
    public int[] ammo = new int[]{100, 0, 0, 0, 0, 0, 0};
    public int[] cooldowns = new int[]{0, 0, 0, 0, 0, 0, 0};

    private boolean shooting = false;
    private int clickTicks = 0;
    private boolean justStartedShooting = true;

    /** Triggered on right-click with a weapon; arms shooting for a few ticks. */
    public void shoot(ServerPlayer player) {
        int idx = WeaponUtil.getHoldingWeaponIndex(player);
        if (idx < 0 || idx >= WeaponUtil.WEAPONS_NUM) return;
        if (ammo[idx] <= 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_FRAME_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
            return;
        }
        clickTicks = 4;
        justStartedShooting = true;
        shooting = true;
    }

    /** Called every server tick. */
    public void tick(ServerPlayer player) {
        if (clickTicks > 0) clickTicks--;
        else shooting = false;

        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] >= WeaponUtil.PERIODS[i]) {
                cooldowns[i] = 0;
            } else if (cooldowns[i] != 0) {
                cooldowns[i]++;
            }
        }

        if (!shooting) return;

        int idx = WeaponUtil.getHoldingWeaponIndex(player);
        if (idx < 0 || idx >= WeaponUtil.WEAPONS_NUM) return;
        if (ammo[idx] <= 0) return;

        if (cooldowns[idx] == 0) {
            switch (idx) {
                case WeaponType.MACHINEGUN -> WeaponUtil.fireMachinegun(player);
                case WeaponType.SHOTGUN -> WeaponUtil.fireShotgun(player);
                case WeaponType.ROCKET_LAUNCHER -> WeaponUtil.fireRocket(player);
                case WeaponType.LIGHTNING_GUN -> WeaponUtil.fireLightning(player, justStartedShooting);
                case WeaponType.RAILGUN -> WeaponUtil.fireRailgun(player);
                case WeaponType.PLASMA_GUN -> WeaponUtil.firePlasma(player);
                case WeaponType.BFG -> WeaponUtil.fireBFG(player);
            }
            // quad damage fire sound
            boolean quad = Powerup.hasPowerup(
                    net.exmo.sreGame.games.quakechasm.QuakeManager.INSTANCE.getUserState(player), PowerupType.QUAD_DAMAGE);
            boolean lightningOrBfg = idx != WeaponType.LIGHTNING_GUN && idx != WeaponType.BFG;
            if ((quad && lightningOrBfg) || (quad && idx == WeaponType.LIGHTNING_GUN && justStartedShooting)) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f);
            }
            ammo[idx]--;
            cooldowns[idx]++;
            justStartedShooting = false;
        }
    }

    public void reset() {
        Arrays.fill(ammo, 0);
        Arrays.fill(cooldowns, 0);
        shooting = false;
        clickTicks = 0;
    }
}
