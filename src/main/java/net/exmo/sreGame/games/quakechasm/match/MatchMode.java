package net.exmo.sreGame.games.quakechasm.match;

public enum MatchMode {
    FFA("match.ffa.name"),
    TDM("match.tdm.name"),
    CTF("match.ctf.name");

    private final String nameKey;

    MatchMode(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getNameKey() {
        return nameKey;
    }
}
