package net.exmo.sreGame.games.quakechasm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.quakechasm.combat.powerup.Powerup;
import net.exmo.sreGame.games.quakechasm.combat.powerup.PowerupType;
import net.exmo.sreGame.games.quakechasm.entity.spawner.AmmoSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.ArmorSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.HealthSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.PowerupSpawner;
import net.exmo.sreGame.games.quakechasm.entity.spawner.WeaponSpawner;
import net.exmo.sreGame.games.quakechasm.entity.trigger.Jumppad;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /quake commands. Ported from quakechasm's Commands, rewritten as Fabric Brigadier.
 *   /quake give weapon|ammo|armor|health|powerup <arg>
 *   /quake spawner weapon|ammo|armor|health|powerup <arg> | jumppad
 *   /quake chat global|match|team
 */
public final class QuakeCommands {
    private QuakeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameContext ctx) {
        dispatcher.register(Commands.literal("quake")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.literal("weapon").then(Commands.argument("id", IntegerArgumentType.integer(0, 6)).executes(c -> giveWeapon(c.getSource(), ctx, IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("ammo").then(Commands.argument("id", IntegerArgumentType.integer(0, 6)).executes(c -> giveAmmo(c.getSource(), ctx, IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("armor").then(Commands.argument("v", IntegerArgumentType.integer(0, 200)).executes(c -> giveArmor(c.getSource(), ctx, IntegerArgumentType.getInteger(c, "v")))))
                        .then(Commands.literal("health").then(Commands.argument("v", IntegerArgumentType.integer(1, 20)).executes(c -> giveHealth(c.getSource(), ctx, IntegerArgumentType.getInteger(c, "v")))))
                        .then(Commands.literal("powerup").then(Commands.argument("type", StringArgumentType.string()).executes(c -> givePowerup(c.getSource(), ctx, StringArgumentType.getString(c, "type"))))))
                .then(Commands.literal("spawner")
                        .then(Commands.literal("weapon").then(Commands.argument("id", IntegerArgumentType.integer(0, 6)).executes(c -> place(c.getSource(), ctx, "weapon", IntegerArgumentType.getInteger(c, "id"), 0, null))))
                        .then(Commands.literal("ammo").then(Commands.argument("id", IntegerArgumentType.integer(0, 6)).executes(c -> place(c.getSource(), ctx, "ammo", IntegerArgumentType.getInteger(c, "id"), 0, null))))
                        .then(Commands.literal("armor").then(Commands.argument("v", IntegerArgumentType.integer(0, 200)).executes(c -> place(c.getSource(), ctx, "armor", IntegerArgumentType.getInteger(c, "v"), 0, null))))
                        .then(Commands.literal("health").then(Commands.argument("v", IntegerArgumentType.integer(1, 20)).executes(c -> place(c.getSource(), ctx, "health", IntegerArgumentType.getInteger(c, "v"), 0, null))))
                        .then(Commands.literal("powerup").then(Commands.argument("type", StringArgumentType.string()).executes(c -> place(c.getSource(), ctx, "powerup", 0, 0, StringArgumentType.getString(c, "type")))))
                        .then(Commands.literal("jumppad").executes(c -> place(c.getSource(), ctx, "jumppad", 0, 0, null))))
                .then(Commands.literal("chat")
                        .then(Commands.literal("global").executes(c -> switchChat(c.getSource(), ctx, "GLOBAL")))
                        .then(Commands.literal("match").executes(c -> switchChat(c.getSource(), ctx, "MATCH")))
                        .then(Commands.literal("team").executes(c -> switchChat(c.getSource(), ctx, "TEAM")))));
    }

    private static ServerPlayer player(CommandSourceStack src) {
        return src.getPlayer();
    }

    // ---- give ----
    private static int giveWeapon(CommandSourceStack src, GameContext ctx, int id) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        p.getInventory().add(WeaponSpawner.weaponItem(id));
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7已给予武器 #" + id), false);
        return 1;
    }

    private static int giveAmmo(CommandSourceStack src, GameContext ctx, int id) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        QuakeUserState st = QuakeManager.INSTANCE.getOrCreate(p);
        st.weaponState.ammo[id] = 200;
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7已补满弹药 #" + id), false);
        return 1;
    }

    private static int giveArmor(CommandSourceStack src, GameContext ctx, int v) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        QuakeUserState st = QuakeManager.INSTANCE.getOrCreate(p);
        st.armor = Math.min(200, st.armor + v);
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7护甲 +" + v), false);
        return 1;
    }

    private static int giveHealth(CommandSourceStack src, GameContext ctx, int v) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        p.setHealth(Math.min(40, p.getHealth() + v));
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7血量 +" + v), false);
        return 1;
    }

    private static int givePowerup(CommandSourceStack src, GameContext ctx, String type) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        PowerupType t;
        try { t = PowerupType.valueOf(type.toUpperCase()); }
        catch (Exception e) { src.sendFailure(net.exmo.sreGame.util.TextUtil.color("&c未知 powerup: " + type)); return 0; }
        PowerupSpawner.doPowerup(p, t, 30);
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7已给予 " + t.name() + " 30秒"), false);
        return 1;
    }

    // ---- spawner placement (lightweight map editor) ----
    private static int place(CommandSourceStack src, GameContext ctx, String cat, int intArg, int ignored, String strArg) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 pos = p.position();
        switch (cat) {
            case "weapon" -> new WeaponSpawner(intArg, level, pos);
            case "ammo" -> new AmmoSpawner(intArg, level, pos);
            case "armor" -> new ArmorSpawner(intArg, level, pos);
            case "health" -> new HealthSpawner(intArg, level, pos);
            case "powerup" -> {
                PowerupType t;
                try { t = PowerupType.valueOf(strArg.toUpperCase()); }
                catch (Exception e) { src.sendFailure(net.exmo.sreGame.util.TextUtil.color("&c未知 powerup")); return 0; }
                new PowerupSpawner(t, level, pos, false, 30);
            }
            case "jumppad" -> new Jumppad(level, pos, new Vec3(0, 1.5, 0));
        }
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7已在脚下放置 " + cat), false);
        return 1;
    }

    // ---- chat ----
    private static int switchChat(CommandSourceStack src, GameContext ctx, String name) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        QuakeUserState st = QuakeManager.INSTANCE.getOrCreate(p);
        try { st.currentChat = Chatroom.valueOf(name); }
        catch (Exception ignored) {}
        src.sendSuccess(() -> net.exmo.sreGame.util.TextUtil.color("&a[Quake] &7聊天切换: " + st.currentChat), false);
        return 1;
    }
}
