package net.exmo.sreGame.games.quakechasm.match;

import java.util.ArrayList;
import java.util.List;

/**
 * A respawn point. Ported from quakechasm's Spawnpoint, but stores raw doubles
 * instead of a Bukkit Location so it serialises cleanly with Gson.
 */
public final class Spawnpoint {
    public double x, y, z;
    public float yaw, pitch;
    public String worldName;
    public ArrayList<Team> allowedTeams;

    public Spawnpoint() {
        this.allowedTeams = new ArrayList<>();
    }

    public Spawnpoint(double x, double y, double z, float yaw, float pitch, String worldName, ArrayList<Team> allowedTeams) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.worldName = worldName;
        this.allowedTeams = allowedTeams == null ? new ArrayList<>() : allowedTeams;
    }

    public List<Team> allowedTeams() {
        return allowedTeams;
    }
}
