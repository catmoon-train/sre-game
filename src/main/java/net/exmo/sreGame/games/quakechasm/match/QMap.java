package net.exmo.sreGame.games.quakechasm.match;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A Quake arena map. Ported from quakechasm's QMap.
 * Stores raw coordinates for clean Gson serialisation; AABB/Level resolved at runtime.
 */
public final class QMap {
    public String name;
    public String displayName;
    public String worldName;
    /** Bounds stored as raw doubles so Gson works with no adapters. */
    public double minX, minY, minZ, maxX, maxY, maxZ;
    public ArrayList<Spawnpoint> spawnPoints;
    public ArrayList<MatchMode> recommendedModes;
    public int neededPlayers;

    public QMap() {
        this.spawnPoints = new ArrayList<>();
        this.recommendedModes = new ArrayList<>();
    }

    public QMap(String name, String displayName, String worldName,
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ,
                ArrayList<Spawnpoint> spawnPoints,
                ArrayList<MatchMode> recommendedModes, int neededPlayers) {
        this.name = name;
        this.displayName = displayName;
        this.worldName = worldName;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.spawnPoints = spawnPoints == null ? new ArrayList<>() : spawnPoints;
        this.recommendedModes = recommendedModes == null ? new ArrayList<>() : recommendedModes;
        this.neededPlayers = neededPlayers;
    }

    public AABB getBounds() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public ServerLevel getWorld(net.minecraft.server.MinecraftServer server) {
        if (worldName == null) return server.overworld();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().getPath().equals(worldName)
                    || level.dimension().location().toString().equals(worldName)
                    || level.toString().contains(worldName)) {
                return level;
            }
        }
        return server.overworld();
    }

    /** Pick a random spawnpoint valid for the given team. */
    public Vec3 getRandomSpawnpoint(Team team) {
        Predicate<Spawnpoint> belongsToTeam = sp -> sp.allowedTeams.contains(team);
        Predicate<Spawnpoint> freeIfTeam = sp -> (team == Team.RED || team == Team.BLUE) && sp.allowedTeams.contains(Team.FREE);
        Predicate<Spawnpoint> teamIfFree = sp -> team == Team.FREE && (sp.allowedTeams.contains(Team.RED) || sp.allowedTeams.contains(Team.BLUE));

        List<Spawnpoint> allowed = new ArrayList<>();
        for (Spawnpoint sp : spawnPoints) {
            if (belongsToTeam.test(sp) || freeIfTeam.test(sp) || teamIfFree.test(sp)) allowed.add(sp);
        }
        if (allowed.isEmpty()) return new Vec3((minX + maxX) / 2, maxY, (minZ + maxZ) / 2);
        Spawnpoint chosen = allowed.get((int) (Math.random() * allowed.size()));
        return new Vec3(chosen.x, chosen.y, chosen.z);
    }

    /** Yaw for a random spawnpoint matching the team (best-effort). */
    public float getRandomSpawnpointYaw(Team team) {
        List<Spawnpoint> allowed = new ArrayList<>();
        for (Spawnpoint sp : spawnPoints) {
            if (sp.allowedTeams.contains(team) || sp.allowedTeams.contains(Team.FREE)) allowed.add(sp);
        }
        if (allowed.isEmpty()) return 0f;
        return allowed.get((int) (Math.random() * allowed.size())).yaw;
    }

    public void chunkLoad(ServerLevel level) {
        for (long cp : chunkPositions()) {
            int x = (int) (cp >> 32);
            int z = (int) (cp & 0xFFFFFFFFL);
            level.setChunkForced(x, z, true);
        }
    }

    public void chunkUnload(ServerLevel level) {
        for (long cp : chunkPositions()) {
            int x = (int) (cp >> 32);
            int z = (int) (cp & 0xFFFFFFFFL);
            level.setChunkForced(x, z, false);
        }
    }

    private List<Long> chunkPositions() {
        List<Long> out = new ArrayList<>();
        int minXc = (int) Math.floor(minX) >> 4;
        int minZc = (int) Math.floor(minZ) >> 4;
        int maxXc = (int) Math.floor(maxX) >> 4;
        int maxZc = (int) Math.floor(maxZ) >> 4;
        for (int x = minXc; x <= maxXc; x++) {
            for (int z = minZc; z <= maxZc; z++) {
                out.add(((long) x << 32) | (z & 0xFFFFFFFFL));
            }
        }
        return out;
    }
}
