package net.exmo.sreGame.games.quakechasm.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.WeaponType;
import net.exmo.sreGame.games.quakechasm.combat.WeaponUtil;
import net.exmo.sreGame.util.TextUtil;

/**
 * Lightweight action-bar HUD. Ported from quakechasm's Hud + PowerupBoard;
 * uses SRE-GAME's TextUtil for &-code coloring instead of adventure/fastboard.
 */
public final class QuakeHud {
    private final QuakeUserState state;
    private final ServerPlayer player;
    private boolean powerupDirty = true;

    public QuakeHud(QuakeUserState state, ServerPlayer player) {
        this.state = state;
        this.player = player;
    }

    public void draw() {
        int held = WeaponUtil.getHoldingWeaponIndex(player);
        String ammo = held >= 0 && held < WeaponType.NAME_KEYS.length ? String.format("%-3d", state.weaponState.ammo[held]) : "   ";
        int hp = (int) Math.round(player.getHealth() * 5);
        String wname = held >= 0 && held < WeaponType.NAME_KEYS.length ? WeaponType.NAME_KEYS[held].split("\\.")[2] : "???";
        String line = "&6[" + wname + "]&f" + ammo + " &c[HP]&f" + hp + " &b[ARM]&f" + state.armor;
        if (!state.activePowerups.isEmpty()) {
            StringBuilder pu = new StringBuilder();
            for (var p : state.activePowerups) {
                pu.append(" &d").append(p.getType().name().charAt(0)).append(p.getTime());
            }
            line += pu;
        }
        player.displayClientMessage(TextUtil.color(line), true);
        powerupDirty = false;
    }

    public void markPowerupDirty() {
        powerupDirty = true;
    }

    public static void pickupMessage(ServerPlayer player, String text) {
        player.displayClientMessage(TextUtil.color(text), true);
    }
}
