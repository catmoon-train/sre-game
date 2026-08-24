package net.exmo.sreGame.games.quakechasm.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Death-message templates per DamageCause. Ported from quakechasm's DeathMessages.
 * {0} = victim name, {1} = attacker name.
 */
public final class DeathMessages {
    private DeathMessages() {}

    public static final Map<DamageCause, String> FRAG = new HashMap<>();
    public static final Map<DamageCause, String> SUICIDE = new HashMap<>();

    static {
        FRAG.put(DamageCause.GAUNTLET, "&c{1} &7用电锯终结了 &f{0}");
        FRAG.put(DamageCause.MACHINEGUN, "&c{1} &7用机枪击杀了 &f{0}");
        FRAG.put(DamageCause.SHOTGUN, "&c{1} &7用霰弹枪轰杀了 &f{0}");
        FRAG.put(DamageCause.ROCKET, "&c{1} &7的火箭直击 &f{0}");
        FRAG.put(DamageCause.ROCKET_SPLASH, "&c{1} &7的火箭炸飞了 &f{0}");
        FRAG.put(DamageCause.LIGHTNING, "&c{1} &7用闪电烤焦了 &f{0}");
        FRAG.put(DamageCause.PLASMA, "&c{1} &7的等离子球击中 &f{0}");
        FRAG.put(DamageCause.PLASMA_SPLASH, "&c{1} &7的等离子溅射炸到 &f{0}");
        FRAG.put(DamageCause.RAILGUN, "&c{1} &7用轨道炮贯穿了 &f{0}");
        FRAG.put(DamageCause.BFG, "&c{1} &7的 BFG 直击 &f{0}");
        FRAG.put(DamageCause.BFG_SPLASH, "&c{1} &7的 BFG 爆炸吞没 &f{0}");
        FRAG.put(DamageCause.BFG_RAY, "&c{1} &7的 BFG 射线灼烧 &f{0}");
        FRAG.put(DamageCause.TELEFRAG, "&c{1} &7传送挤压了 &f{0}");

        SUICIDE.put(DamageCause.ROCKET_SPLASH, "&f{0} &7把自己炸上了天");
        SUICIDE.put(DamageCause.PLASMA_SPLASH, "&f{0} &7被自己的等离子炸死");
        SUICIDE.put(DamageCause.BFG_SPLASH, "&f{0} &7被自己的 BFG 反噬");
        SUICIDE.put(DamageCause.FALLING, "&f{0} &7摔成了肉泥");
        SUICIDE.put(DamageCause.LAVA, "&f{0} &7跳进了岩浆");
        SUICIDE.put(DamageCause.WATER, "&f{0} &7淹死了");
        SUICIDE.put(DamageCause.SUICIDE, "&f{0} &7自我了断");
        SUICIDE.put(DamageCause.TELEFRAG, "&f{0} &7被传送碾碎");
    }

    public static String frag(DamageCause cause, String victim, String attacker) {
        String tpl = FRAG.get(cause);
        if (tpl == null) tpl = "&c{1} &7击杀了 &f{0}";
        return tpl.replace("{0}", victim).replace("{1}", attacker);
    }

    public static String suicide(DamageCause cause, String victim) {
        String tpl = SUICIDE.get(cause);
        if (tpl == null) tpl = "&f{0} &7死了";
        return tpl.replace("{0}", victim);
    }
}
