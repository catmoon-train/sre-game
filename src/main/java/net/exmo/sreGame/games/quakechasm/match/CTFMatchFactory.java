package net.exmo.sreGame.games.quakechasm.match;

import java.util.UUID;

/** Builds a CTFMatch. Ported from quakechasm's CTFMatchFactory. */
public final class CTFMatchFactory implements MatchFactory {
    @Override
    public QuakeMatch createMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        return new CTFMatch(map, ownerId, privacy, password);
    }

    @Override
    public String getNameKey() { return "match.ctf.name"; }
}
