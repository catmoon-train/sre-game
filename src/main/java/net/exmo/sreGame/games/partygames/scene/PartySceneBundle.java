package net.exmo.sreGame.games.partygames.scene;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.server.level.ServerLevel;

/** A fully validated, immutable MP2 scene ready to be placed into an arena instance. */
public final class PartySceneBundle {
   public record Shard(PartySceneManifest.Shard manifest, CompoundTag nbt) { }

   private final PartySceneManifest manifest;
   private final List<Shard> shards;

   PartySceneBundle(PartySceneManifest manifest, List<Shard> shards) {
      this.manifest = manifest;
      this.shards = List.copyOf(shards);
   }

   public PartySceneManifest manifest() { return manifest; }
   public int width() { return manifest.size()[0]; }
   public int height() { return manifest.size()[1]; }
   public int depth() { return manifest.size()[2]; }
   public int shardCount() { return shards.size(); }
   public int shardCost(int index) { return Math.max(1, shards.get(index).manifest().blockCount()); }
   public Map<String, double[]> anchors() { return manifest.anchors() == null ? Map.of() : Map.copyOf(manifest.anchors()); }

   public void placeShard(ServerLevel level, BlockPos arenaOrigin, int index) {
      Shard shard = shards.get(index);
      StructureTemplate template = new StructureTemplate();
      HolderGetter<Block> blocks = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
      template.load(blocks, shard.nbt().copy());
      int[] offset = shard.manifest().offset();
      BlockPos target = arenaOrigin.offset(offset[0], offset[1], offset[2]);
      StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false).setFinalizeEntities(true).setKnownShape(false);
      if (!template.placeInWorld(level, target, target, settings, RandomSource.create(), 3)) {
         throw new IllegalStateException("Failed to place scene shard " + shard.manifest().file());
      }
   }
}
