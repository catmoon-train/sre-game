package net.exmo.sreGame.games.quakechasm.match;

import java.util.UUID;

/** Creates a match of a given mode. Ported from quakechasm's MatchFactory. */
public interface MatchFactory {
    QuakeMatch createMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password);

    default QuakeMatch createMatch(QMap map) {
        return createMatch(map, null, MatchPrivacy.PUBLIC, null);
    }

    String getNameKey();
}
