package net.exmo.sreGame.games.quakechasm.combat;

import net.exmo.sreGame.games.quakechasm.hud.Icons;

public enum MedalType {
    EXCELLENT("game.medal.name.excellent", Icons.MEDAL_EXCELLENT),
    IMPRESSIVE("game.medal.name.impressive", Icons.MEDAL_IMPRESSIVE),
    ACCURACY("game.medal.name.accuracy", Icons.MEDAL_ACCURACY),
    HUMILIATION("game.medal.name.humiliation", Icons.MEDAL_HUMILIATION);

    private final String translationKey;
    private final char icon;

    MedalType(String translationKey, char icon) {
        this.translationKey = translationKey;
        this.icon = icon;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public char getIcon() {
        return icon;
    }
}
