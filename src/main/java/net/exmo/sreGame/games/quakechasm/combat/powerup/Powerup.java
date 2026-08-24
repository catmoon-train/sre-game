package net.exmo.sreGame.games.quakechasm.combat.powerup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;

import java.util.ArrayList;

/**
 * An active powerup on a player. Ported from quakechasm's Powerup.
 * Tick-driven instead of BukkitRunnable; called every tick by QuakeUserState.tick().
 */
public class Powerup {
    private int timeSeconds;
    private final PowerupType type;
    private int tickAccumulator = 0;
    public boolean expired = false;

    public Powerup(PowerupType type, int timeSeconds) {
        this.type = type;
        this.timeSeconds = timeSeconds;
    }

    public PowerupType getType() { return type; }
    public int getTime() { return Math.max(0, timeSeconds); }
    public boolean isExpired() { return expired; }

    public void extendDuration(int seconds) {
        this.timeSeconds += seconds;
    }

    /** Called every server tick by the owning QuakeUserState. */
    public void tick(ServerPlayer player, QuakeUserState state) {
        if (expired) return;
        tickAccumulator++;
        if (tickAccumulator < 20) {
            return;
        }
        tickAccumulator = 0;

        timeSeconds--;
        if (timeSeconds < 0) {
            expired = true;
            state.activePowerups.remove(this);
            state.hud.markPowerupDirty();
            if (type == PowerupType.REGENERATION) {
                state.startHealthDecreaser();
            }
            return;
        }

        if (timeSeconds < 5) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 0.5f, 1.4f);
        }

        // REGENERATION: heal toward 200 (40 * 5 Quake HP)
        if (type == PowerupType.REGENERATION) {
            float hp = player.getHealth();
            double max = player.getAttribute(Attributes.MAX_HEALTH).getValue();
            if (hp < 40) {
                float newHp;
                if (hp < 20) {
                    newHp = Math.min(20, hp + 3);
                } else {
                    newHp = hp + 1;
                }
                if (newHp > 40) newHp = 40;
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(max, newHp));
                player.setHealth(newHp);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.6f);
            }
        }

        state.hud.markPowerupDirty();
    }

    public static boolean hasPowerup(QuakeUserState state, PowerupType type) {
        for (Powerup p : state.activePowerups) {
            if (p.getType() == type) return true;
        }
        return false;
    }

    public static void dropPowerups(ServerPlayer player, QuakeUserState state) {
        // Drop spawned powerups at player location; in the Fabric port we just clear them
        // (spawning pickups requires the PowerupSpawner which is wired in the entity layer).
        for (int i = state.activePowerups.size() - 1; i >= 0; i--) {
            Powerup p = state.activePowerups.get(i);
            p.expired = true;
            state.activePowerups.remove(i);
        }
        state.hud.markPowerupDirty();
    }
}
