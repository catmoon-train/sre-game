package net.exmo.sreGame.games.quakechasm.entity.spawner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.entity.QEntityUtil;
import net.exmo.sreGame.games.quakechasm.match.CTFMatch;
import net.exmo.sreGame.games.quakechasm.match.Team;

/** CTF flag pickup. Ported from quakechasm's CTFFlag. */
public class CTFFlag extends Spawner {
    private final Team team;
    private final boolean isDrop;
    private CTFMatch match;

    public CTFFlag(Team team, boolean isDrop, CTFMatch match, ServerLevel level, Vec3 pos) {
        super(bannerItem(team), level, pos);
        this.team = team;
        this.isDrop = isDrop;
        this.match = match;
        QEntityUtil.setEntityType(display, "ctf_flag");
    }

    private static ItemStack bannerItem(Team t) {
        return new ItemStack(t == Team.RED ? Items.RED_BANNER : Items.BLUE_BANNER);
    }

    public Team getTeam() { return team; }
    public boolean isDrop() { return isDrop; }
    /** Hide the flag display while it is being carried. */
    public void hideFlag() { hideItem(); }
    public void prepareForMatch(CTFMatch m) { this.match = m; }

    @Override
    public void onPickup(ServerPlayer player) {
        if (match == null) return;
        Team pt = match.getTeamOfPlayer(player);
        if (isDrop) {
            // dropped flag: own team returns it, enemy re-grabs
            if (pt == team) {
                match.returnFlag(team, player);
            } else {
                match.grabFlag(team, player);
            }
            QuakeManager.INSTANCE.triggers.remove(this);
            remove();
        } else {
            if (pt != team) {
                // enemy steals the flag
                match.grabFlag(team, player);
            } else if (match.getCarryingFlagTeam(player) != null) {
                // own team player carrying enemy flag touches own flag base → capture
                match.captureFlag(team, player);
            }
        }
    }

    @Override
    public void respawn() {
        if (!display.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        display.setItemSlot(EquipmentSlot.HEAD, bannerItem(team));
        respawnEffect();
    }
}
