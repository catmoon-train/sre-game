package net.exmo.sreGame.games.quakechasm.match;

import java.util.UUID;

/** Builds an FFAMatch. Ported from quakechasm's FFAMatchFactory. */
public final class FFAMatchFactory implements MatchFactory {
    @Override
    public QuakeMatch createMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        return new FFAMatch(map, ownerId, privacy, password);
    }

    @Override
    public String getNameKey() { return "match.ffa.name"; }
}
