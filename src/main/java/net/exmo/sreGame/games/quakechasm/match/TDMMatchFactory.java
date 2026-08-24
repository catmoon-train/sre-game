package net.exmo.sreGame.games.quakechasm.match;

import java.util.UUID;

/** Builds a TDMMatch. Ported from quakechasm's TDMMatchFactory. */
public final class TDMMatchFactory implements MatchFactory {
    @Override
    public QuakeMatch createMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        return new TDMMatch(map, ownerId, privacy, password);
    }

    @Override
    public String getNameKey() { return "match.tdm.name"; }
}
