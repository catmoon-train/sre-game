package net.exmo.sreGame.games.quakechasm.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Type tagging for Quake entities. Ported from QEntityUtil; uses scoreboard tags
 * (Entity.addTag/getTags) instead of Bukkit PersistentDataContainer.
 */
public final class QEntityUtil {
    private QEntityUtil() {}

    public static final String PREFIX = "qc_type_";

    public static String getEntityType(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (tag.startsWith(PREFIX)) {
                return tag.substring(PREFIX.length());
            }
        }
        return null;
    }

    public static void setEntityType(Entity entity, String type) {
        if (entity == null) return;
        entity.addTag(PREFIX + type);
    }

    /** setSmall/setMarker are private on ArmorStand in 1.21.1; call via reflection. */
    public static void armorStandFlags(ArmorStand as, boolean small, boolean marker) {
        if (as == null) return;
        try {
            var m = ArmorStand.class.getDeclaredMethod("setSmall", boolean.class);
            m.setAccessible(true); m.invoke(as, small);
        } catch (Exception ignored) {}
        try {
            var m = ArmorStand.class.getDeclaredMethod("setMarker", boolean.class);
            m.setAccessible(true); m.invoke(as, marker);
        } catch (Exception ignored) {}
    }
}
