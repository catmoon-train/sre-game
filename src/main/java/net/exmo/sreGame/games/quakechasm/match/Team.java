package net.exmo.sreGame.games.quakechasm.match;

import java.util.EnumMap;

/**
 * Quake team definition. Ported from quakechasm's matchmaking.Team.
 */
public enum Team {
    RED,
    BLUE,
    FREE,
    SPECTATOR;

    public static final int RED_RGB = 0xff3f3f;
    public static final int BLUE_RGB = 0x3f3fff;
    public static final int FREE_RGB = 0xffff00;
    public static final int SPECTATOR_RGB = 0x00ff00;

    private static final EnumMap<Team, Integer> COLORS = new EnumMap<>(Team.class);

    static {
        COLORS.put(RED, RED_RGB);
        COLORS.put(BLUE, BLUE_RGB);
        COLORS.put(FREE, FREE_RGB);
        COLORS.put(SPECTATOR, SPECTATOR_RGB);
    }

    public static int colorOf(Team team) {
        Integer c = COLORS.get(team);
        return c == null ? 0xffffff : c;
    }

    public Team opposite() {
        return switch (this) {
            case RED -> BLUE;
            case BLUE -> RED;
            default -> throw new IllegalArgumentException("Cannot find opposite of a non-red/non-blue team");
        };
    }
}
