package net.exmo.sreGame.games.quakechasm.match;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Team Deathmatch. Ported from quakechasm's TDMMatch. */
public class TDMMatch extends QuakeMatch {
    @QManageable(name = "fraglimit", min = 1, max = 1000, description = "Team frags needed to win")
    public int fraglimit = 10;

    @QManageable(name = "needPlayers", min = 1, max = 100)
    private int needPlayers = 2;

    private final HashMap<ServerPlayer, Integer> scores = new HashMap<>();
    private final int[] teamScores = {0, 0};
    private boolean started = false;

    public TDMMatch(QMap map) { super(map); }
    public TDMMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) { super(map, ownerId, privacy, password); }

    @Override public String getNameKey() { return "match.tdm.name"; }
    @Override public void setScoreLimit(int v) { this.fraglimit = v; }
    @Override public void setNeedPlayers(int v) { this.needPlayers = v; }
    @Override public List<Team> allowedTeams() { return List.of(Team.RED, Team.BLUE); }

    @Override
    public Team assignTeam(ServerPlayer player) {
        return getPlayersInTeam(Team.RED).size() >= getPlayersInTeam(Team.BLUE).size() ? Team.BLUE : Team.RED;
    }

    @Override
    public void join(ServerPlayer player, Team team) {
        super.join(player, team);
        scores.put(player, 0);
        broadcast(QuakeTranslator.t("match.team.joined", player.getName().getString(), getTeamOfPlayer(player).name()));
        if (players.size() >= needPlayers && !started) warmup();
    }

    @Override
    public void leave(ServerPlayer player) {
        super.leave(player);
        scores.remove(player);
        if (players.isEmpty()) end();
    }

    public void warmup() {
        broadcast(QuakeTranslator.t("match.countdown", 10));
        QuakeManager.INSTANCE.schedule(200, this::start);
    }

    public void start() {
        for (ServerPlayer p : players.keySet()) {
            scores.put(p, 0);
            QuakeUserState st = QuakeManager.INSTANCE.getUserState(p);
            if (st != null) { st.reset(); st.initRespawn(); }
        }
        started = true;
        broadcast(QuakeTranslator.t("match.start"));
        broadcast(QuakeTranslator.t("match.generic.startMessage", fraglimit));
    }

    @Override
    public void onDeath(ServerPlayer victim, Entity attacker, DamageCause cause) {
        super.onDeath(victim, attacker, cause);
        broadcast(getDeathMessage(victim, attacker, cause));
        if (!started) return;

        if (attacker instanceof ServerPlayer ap && victim != attacker) {
            boolean sameTeam = getPlayersInTeam(players.get(ap)).contains(victim);
            scores.merge(ap, sameTeam ? -1 : 1, Integer::sum);
            Team at = players.get(ap);
            int idx = at == Team.RED ? 0 : 1;
            teamScores[idx] += sameTeam ? -1 : 1;
        } else if (attacker == null || victim == attacker) {
            Team vt = players.get(victim);
            int idx = vt == Team.RED ? 0 : 1;
            teamScores[idx] -= 1;
        }
        broadcast("&c红队 " + teamScores[0] + " &9蓝队 " + teamScores[1]);

        if (teamScores[0] >= fraglimit || teamScores[1] >= fraglimit) {
            Team w = teamScores[0] > teamScores[1] ? Team.RED : Team.BLUE;
            broadcast(QuakeTranslator.t("match.team.wins", w.name()));
            end();
        }
    }
}
