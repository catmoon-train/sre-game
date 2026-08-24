package net.exmo.sreGame.games.quakechasm.match;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.exmo.sreGame.games.quakechasm.QuakeManager;
import net.exmo.sreGame.games.quakechasm.QuakeTranslator;
import net.exmo.sreGame.games.quakechasm.QuakeUserState;
import net.exmo.sreGame.games.quakechasm.combat.DamageCause;
import net.exmo.sreGame.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Free-For-All deathmatch. Ported from quakechasm's FFAMatch, tick/schedule-driven.
 */
public class FFAMatch extends QuakeMatch {
    @QManageable(name = "fraglimit", min = 1, max = 1000, description = "Frags needed to win")
    public int fraglimit = 10;

    @QManageable(name = "needPlayers", min = 1, max = 100, description = "Players needed to start")
    private int needPlayers = 2;

    private final HashMap<ServerPlayer, Integer> scores = new HashMap<>();
    private boolean started = false;

    public FFAMatch(QMap map) { super(map); }
    public FFAMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        super(map, ownerId, privacy, password);
    }

    @Override public String getNameKey() { return "match.ffa.name"; }

    @Override public void setScoreLimit(int v) { this.fraglimit = v; }
    @Override public void setNeedPlayers(int v) { this.needPlayers = v; }

    @Override public Team assignTeam(ServerPlayer player) { return Team.FREE; }
    @Override public List<Team> allowedTeams() { return List.of(Team.FREE); }

    @Override
    public void join(ServerPlayer player, Team team) {
        super.join(player, team);
        scores.put(player, 0);
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
        QuakeManager.INSTANCE.schedule(20, () -> broadcast(QuakeTranslator.t("match.countdown", 9)));
        QuakeManager.INSTANCE.schedule(40, () -> broadcast(QuakeTranslator.t("match.countdown", 8)));
        QuakeManager.INSTANCE.schedule(60, () -> broadcast(QuakeTranslator.t("match.countdown", 7)));
        QuakeManager.INSTANCE.schedule(80, () -> broadcast(QuakeTranslator.t("match.countdown", 6)));
        QuakeManager.INSTANCE.schedule(100, () -> broadcast(QuakeTranslator.t("match.countdown", 5)));
        QuakeManager.INSTANCE.schedule(120, () -> broadcast(QuakeTranslator.t("match.countdown", 4)));
        QuakeManager.INSTANCE.schedule(140, () -> broadcast(QuakeTranslator.t("match.countdown", 3)));
        QuakeManager.INSTANCE.schedule(160, () -> broadcast(QuakeTranslator.t("match.countdown", 2)));
        QuakeManager.INSTANCE.schedule(180, () -> broadcast(QuakeTranslator.t("match.countdown", 1)));
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
    public void end() {
        super.end();
    }

    @Override
    public void onDeath(ServerPlayer victim, Entity attacker, DamageCause cause) {
        super.onDeath(victim, attacker, cause);
        broadcast(getDeathMessage(victim, attacker, cause));
        if (!started) return;

        if (attacker instanceof ServerPlayer ap && victim != attacker) {
            scores.merge(ap, 1, Integer::sum);
            ap.displayClientMessage(TextUtil.color(QuakeTranslator.t("game.kill.message", victim.getName().getString())), false);
        } else if (attacker == null || victim == attacker) {
            scores.merge(victim, -1, Integer::sum);
        }

        // check fraglimit
        List<Map.Entry<ServerPlayer, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        if (!sorted.isEmpty() && sorted.get(0).getValue() >= fraglimit) {
            ServerPlayer winner = sorted.get(0).getKey();
            broadcast(QuakeTranslator.t("match.generic.wins", winner.getName().getString()));
            end();
        }
    }
}
