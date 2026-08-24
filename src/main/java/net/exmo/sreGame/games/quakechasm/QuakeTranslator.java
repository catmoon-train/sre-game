package net.exmo.sreGame.games.quakechasm;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in Chinese translator for Quakechasm. Ported from quakechasm's TranslationManager,
 * but ships a static key→zh map instead of loading lang JSON, and returns legacy
 * &-code strings so it rides on SRE-GAME's TextUtil.color pipeline.
 *
 * <p>Keys mirror quakechasm's lang_en_US.json paths. Unknown keys fall back to the key.</p>
 */
public final class QuakeTranslator {
    private QuakeTranslator() {}

    private static final Map<String, String> ZH = new HashMap<>();

    static {
        // match
        put("match.ffa.name", "&6自由混战 &7FFA");
        put("match.tdm.name", "&9团队死斗 &7TDM");
        put("match.ctf.name", "&c夺旗战 &7CTF");
        put("match.countdown", "&e&l倒计时 &f{0}");
        put("match.start", "&a&l战斗开始！");
        put("match.generic.startMessage", "&c目标分数 &f{0}");
        put("match.generic.wins", "&6{0} &e获胜！");
        put("match.player.joined", "&a{0} &7加入了比赛");
        put("match.player.left", "&7{0} 离开了比赛");
        put("match.end.teleportCountdown", "&7{0} 秒后回到大厅");
        put("match.score.place", "&f分数 &7{0}");
        put("match.score.tiedFor", "&f并列 &7");
        // suffixes
        put("suffix.first", "st");
        put("suffix.second", "nd");
        put("suffix.third", "rd");
        put("suffix.nth", "th");
        // kill / death
        put("game.kill.message", "&a你击杀了 &f{0}");
        put("obituary.unknown", "&7{0} 被 {1} 击杀");
        put("obituary.suicide.unknown", "&7{0} 自杀了");
        // weapons
        put("pickup.weapon.machinegun", "&f机枪");
        put("pickup.weapon.shotgun", "&f霰弹枪");
        put("pickup.weapon.rocketLauncher", "&f火箭筒");
        put("pickup.weapon.lightningGun", "&f闪电枪");
        put("pickup.weapon.railgun", "&f轨道炮");
        put("pickup.weapon.plasmaGun", "&f等离子枪");
        put("pickup.weapon.bfg", "&fBFG");
        put("error.weapon.unknown", "&c未知武器");
        // powerups
        put("pickup.powerup.quad", "&d四倍伤害");
        put("pickup.powerup.regeneration", "&a再生");
        put("pickup.powerup.protection", "&b保护");
        // medals
        put("game.medal.name.excellent", "&6Excellent");
        put("game.medal.name.impressive", "&6Impressive");
        put("game.medal.name.accuracy", "&6Accuracy");
        put("game.medal.name.humiliation", "&6Humiliation");
        put("game.medal.awarded", "&6获得 &e{0}");
        // command / map
        put("command.map.created", "&a地图 &f{0} &a已创建");
        put("command.map.removed", "&c地图 &f{0} &c已删除");
        put("command.entity.jumppad.created", "&a在 &f{0} &a创建跳跃板");
        put("command.entity.jumppad.createdWithPower", "&a跳跃板 &f{0} &a落点 &f{1} &7(倍率 {2}x)");
        put("plugin.reload", "&a配置已重载");
        // chat
        put("command.chat.switch.title", "&7频道切换至 &f{0}");
        put("error.chat.switchNoMatch.title", "&c当前不在比赛中，无法切换");
        put("error.match.notTeam", "&c当前为 FFA，无队伍频道");
        put("command.chat.prefix.match", "&6[比赛]");
        put("command.chat.prefix.team", "&b[队伍]");
        put("command.chat.prefix.global", "&a[全局]");
    }

    private static void put(String key, String val) {
        ZH.put(key, val);
    }

    /** Translate a key with {0},{1},... placeholders replaced by args. Returns a &-code string. */
    public static String t(String key, Object... args) {
        String s = ZH.getOrDefault(key, key);
        if (args == null || args.length == 0) return s;
        for (int i = 0; i < args.length; i++) {
            String token = "{" + i + "}";
            String val = args[i] == null ? "" : args[i].toString();
            s = s.replace(token, val);
        }
        return s;
    }

    /** Legacy single-arg helper matching quakechasm's tLegacy. */
    public static String tLegacy(String key, Object... args) {
        return t(key, args);
    }

    /** Plain key lookup with no formatting. */
    public static String raw(String key) {
        return ZH.getOrDefault(key, key);
    }
}
