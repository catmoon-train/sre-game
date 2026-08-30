package net.exmo.sreGame.games.partygames.scene;

import java.util.List;
import java.util.Map;

/** Versioned metadata emitted by the 1.21.10 read-only exporter. */
public record PartySceneManifest(
   int format,
   String gameId,
   String minecraftVersion,
   int[] size,
   Map<String, double[]> anchors,
   List<Shard> shards,
   int blockCount,
   int blockEntityCount,
   int entityCount,
   String sha256
) {
   public record Shard(String file, int[] offset, int[] size, int blockCount, int blockEntityCount, int entityCount, String sha256) { }
}
