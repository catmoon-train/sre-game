package net.exmo.sreGame.games.quakechasm;

/** Chat channels. Ported from quakechasm's Chatroom enum. */
public enum Chatroom {
    GLOBAL(0x55ff55),
    MATCH(0xffaa00),
    TEAM(0x55ffff);

    private final int color;

    Chatroom(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
