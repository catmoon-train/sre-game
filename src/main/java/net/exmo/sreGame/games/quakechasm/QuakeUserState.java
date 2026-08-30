package net.exmo.sreGame.games.quakechasm;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.exmo.sreGame.games.quakechasm.combat.DamageData;
import net.exmo.sreGame.games.quakechasm.combat.MedalType;
import net.exmo.sreGame.games.quakechasm.combat.powerup.Powerup;
import net.exmo.sreGame.games.quakechasm.combat.WeaponType;
import net.exmo.sreGame.games.quakechasm.combat.WeaponUserState;
import net.exmo.sreGame.games.quakechasm.combat.WeaponUtil;
import net.exmo.sreGame.games.quakechasm.hud.QuakeHud;
import net.exmo.sreGame.games.quakechasm.match.QuakeMatch;
import net.exmo.sreGame.games.quakechasm.match.Team;
import net.exmo.sreGame.games.quakechasm.movement.StrafeJumpHandler;
import net.exmo.sreGame.games.quakechasm.util.MiscUtil;
import net.exmo.sreGame.util.TextUtil;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Per-player Quake state. Ported from quakechasm's QuakeUserState.
 * Tick-driven (no BukkitRunnable); QuakeManager.tick() calls userState.tick(player).
 */
public class QuakeUserState {
    public final ServerPlayer player;
    public WeaponUserState weaponState;
    public int armor = 0;
    public ArrayList<Powerup> activePowerups = new ArrayList<>(3);
    public QuakeHud hud;
    public QuakeMatch currentMatch;
    public DamageData lastDamage;
    public Chatroom currentChat = Chatroom.GLOBAL;

    // strafe jump
    public int strafeJumpTicks = 0;

    // medals
    public HashMap<MedalType, Integer> medals = new HashMap<>();
    public long lastKillTime = 0;
    public int consecutiveRailgunHits = 0;
    public boolean lastKillWasMidair = false;

    // decreasers (tick-driven)
    private boolean healthDecreaserActive = false;
    private int healthTickAcc = 0;
    private boolean armorDecreaserActive = false;
    private int armorTickAcc = 0;

    public QuakeUserState(ServerPlayer player) {
        this.player = player;
        this.weaponState = new WeaponUserState();
        this.hud = new QuakeHud(this, player);
    }

    public void reset() {
        this.weaponState.reset();
        this.armor = 0;
        for (int i = activePowerups.size() - 1; i >= 0; i--) {
            activePowerups.get(i).expired = true;
            activePowerups.remove(i);
        }
        this.player.setHealth(20);
        this.player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        this.player.getInventory().clearContent();
        this.medals.clear();
        this.lastKillTime = 0;
        this.consecutiveRailgunHits = 0;
        this.lastKillWasMidair = false;
        this.healthDecreaserActive = false;
        this.armorDecreaserActive = false;
    }

    public void initForMatch() {
        reset();
        initRespawn();
    }

    public void initRespawn() {
        // give machinegun
        ItemStack mg = new ItemStack(WeaponType.ITEMS[WeaponType.MACHINEGUN]);
        mg.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(t -> t.putInt("qc_weapon", WeaponType.MACHINEGUN)));
        mg.set(DataComponents.CUSTOM_NAME, TextUtil.color(QuakeTranslator.t("pickup.weapon.machinegun")));
        player.getInventory().setItem(0, mg);
        player.getInventory().selected = 0;
        weaponState.ammo[WeaponType.MACHINEGUN] = WeaponUtil.DEFAULT_AMMO[WeaponType.MACHINEGUN];

        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(25);
        player.setHealth(25);
        startHealthDecreaser();
    }

    public void startArmorDecreaser() {
        armorDecreaserActive = true;
    }

    public void startHealthDecreaser() {
        healthDecreaserActive = true;
        healthTickAcc = 0;
    }

    /** Called every server tick by QuakeManager. */
    public void tick() {
        // 未在比赛中（未开始/已结束/普通玩家）时不刷 Quake HUD、不禁饥饿、不 strafe
        if (currentMatch == null) return;
        weaponState.tick(player);

        for (int i = activePowerups.size() - 1; i >= 0; i--) {
            Powerup p = activePowerups.get(i);
            p.tick(player, this);
            if (p.isExpired()) activePowerups.remove(i);
        }

        if (healthDecreaserActive) {
            healthTickAcc++;
            if (healthTickAcc >= 20) {
                healthTickAcc = 0;
                float hp = player.getHealth();
                if (hp <= 20) {
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
                    healthDecreaserActive = false;
                } else {
                    float nh = Math.round(hp * 5 - 1) / 5f;
                    player.setHealth(nh);
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(nh);
                }
            }
        }

        if (armorDecreaserActive) {
            armorTickAcc++;
            if (armorTickAcc >= 20) {
                armorTickAcc = 0;
                if (armor <= 100) {
                    armorDecreaserActive = false;
                } else {
                    armor -= 1;
                }
            }
        }

        // 禁饥饿 (Quake 玩家保持满饱食，无饥饿损耗)
        player.getFoodData().setFoodLevel(20);
        // strafejump 加速 hook：sprinting 时每 tick 驱动
        if (player.isSprinting()) {
            strafeJumpTicks++;
            StrafeJumpHandler.applyStrafeAcceleration(player, this, player.getDeltaMovement());
        }
        hud.draw();
    }

    public void awardMedal(MedalType medalType) {
        int c = medals.getOrDefault(medalType, 0) + 1;
        medals.put(medalType, c);
        String msg = "&6" + QuakeTranslator.t(medalType.getTranslationKey()) + " x" + c;
        player.displayClientMessage(TextUtil.color(msg), true);
    }

    public void checkExcellentMedal() {
        long now = System.currentTimeMillis();
        if (lastKillTime != 0 && (now - lastKillTime) <= 2000) {
            awardMedal(MedalType.EXCELLENT);
        }
        lastKillTime = now;
    }

    public void checkImpressiveMedal() {
        if (consecutiveRailgunHits >= 2) {
            awardMedal(MedalType.IMPRESSIVE);
        }
    }

    /** Respawn at a team-appropriate spawnpoint. */
    public void respawn() {
        initRespawn();
        if (currentMatch != null) {
            Team team = currentMatch.getTeamOfPlayer(player);
            Vec3 spawn = currentMatch.getMap().getRandomSpawnpoint(team);
            float yaw = currentMatch.getMap().getRandomSpawnpointYaw(team);
            player.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0);
            MiscUtil.teleEffect(player.serverLevel(), spawn, false);
            if (currentMatch.isTeamMatch()) net.exmo.sreGame.games.quakechasm.match.QuakeMatch.setTeamArmor(player, team);
        }
    }
}
