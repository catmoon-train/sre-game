package net.exmo.sreGame.games.partygames.scene;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartySceneStoreTest {
   @TempDir Path temp;

   @BeforeAll static void bootstrapMinecraft() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

   @Test void acceptsKnownStaticStructureAndExactCounts() throws Exception {
      PartySceneStore store = new PartySceneStore(temp);
      CompoundTag root = structure("minecraft:stone", null);
      store.validateNbt(new PartySceneManifest.Shard("a.nbt", new int[]{0,0,0}, new int[]{1,1,1}, 1, 0, 0, "x"), root);
   }

   @Test void recognizesEveryOfficialSceneRange() {
      for (int id = 101; id <= 114; id++) assertTrue(PartySceneStore.isOfficialSceneId(Integer.toString(id)));
      for (int id = 201; id <= 214; id++) assertTrue(PartySceneStore.isOfficialSceneId(Integer.toString(id)));
      for (int id = 301; id <= 314; id++) assertTrue(PartySceneStore.isOfficialSceneId(Integer.toString(id)));
      assertFalse(PartySceneStore.isOfficialSceneId("300"));
      assertFalse(PartySceneStore.isOfficialSceneId("315"));
      assertFalse(PartySceneStore.isOfficialSceneId("not-a-number"));
   }

   @Test void remapsAuditedSourceBlockAliasesBeforeValidation() {
      CompoundTag root = structure("minecraft:iron_chain", null);
      PartySceneStore.applyKnownAliases(root);
      assertEquals("minecraft:chain", root.getList("palette", 10).getCompound(0).getString("Name"));
   }

   @Test void rejectsCommandBlocksAndUnknownIds() {
      PartySceneStore store = new PartySceneStore(temp);
      assertThrows(IOException.class, () -> store.validateNbt(shard(), structure("minecraft:command_block", null)));
      assertThrows(IOException.class, () -> store.validateNbt(shard(), structure("minecraft:not_a_real_block", null)));
   }

   @Test void rejectsUnsupportedBlockProperties() {
      PartySceneStore store = new PartySceneStore(temp);
      assertThrows(IOException.class, () -> store.validateNbt(shard(), structure("minecraft:stone", Map.of("future_property", "yes"))));
   }

   @Test void rejectsOversizedOrWrongGameManifest() {
      PartySceneStore store = new PartySceneStore(temp);
      var shard = new PartySceneManifest.Shard("a.nbt", new int[]{0,0,0}, new int[]{1,1,1}, 1, 0, 0, "x");
      assertThrows(IOException.class, () -> store.validateManifest("101", new PartySceneManifest(1, "102", "1.21.10", new int[]{1,1,1}, Map.of(), List.of(shard), 1,0,0,"x")));
      assertThrows(IOException.class, () -> store.validateManifest("101", new PartySceneManifest(1, "101", "1.21.10", new int[]{113,1,1}, Map.of(), List.of(shard), 1,0,0,"x")));
      assertThrows(IOException.class, () -> store.validateManifest("101", new PartySceneManifest(1, "101", "1.21.11", new int[]{1,1,1}, Map.of(), List.of(shard), 1,0,0,"x")));
      assertThrows(IOException.class, () -> store.validateManifest("101", new PartySceneManifest(1, "101", "1.21.10", new int[]{1,1,1}, Map.of("spawn", new double[]{1,0,0}), List.of(shard), 1,0,0,"x")));
   }

   @Test void rejectsUnknownBlockEntityAndOutOfRangeBlockPosition() {
      PartySceneStore store = new PartySceneStore(temp);
      CompoundTag unknown = structure("minecraft:chest", null);
      unknown.getList("blocks", 10).getCompound(0).put("nbt", blockEntity("minecraft:not_real"));
      assertThrows(IOException.class, () -> store.validateNbt(new PartySceneManifest.Shard("a.nbt", new int[]{0,0,0}, new int[]{1,1,1}, 1, 1, 0, "x"), unknown));
      CompoundTag outside = structure("minecraft:stone", null);
      outside.getList("blocks", 10).getCompound(0).put("pos", ints(1, 0, 0));
      assertThrows(IOException.class, () -> store.validateNbt(shard(), outside));
   }

   @Test void rejectsUnknownItemsInsideContainers() {
      PartySceneStore store = new PartySceneStore(temp); CompoundTag root = structure("minecraft:chest", null); CompoundTag chest = blockEntity("minecraft:chest");
      CompoundTag item = new CompoundTag(); item.putString("id", "minecraft:future_item"); ListTag items = new ListTag(); items.add(item); chest.put("Items", items); root.getList("blocks", 10).getCompound(0).put("nbt", chest);
      assertThrows(IOException.class, () -> store.validateNbt(new PartySceneManifest.Shard("a.nbt", new int[]{0,0,0}, new int[]{1,1,1}, 1, 1, 0, "x"), root));
   }

   private static PartySceneManifest.Shard shard() { return new PartySceneManifest.Shard("a.nbt", new int[]{0,0,0}, new int[]{1,1,1}, 1, 0, 0, "x"); }

   private static CompoundTag structure(String blockName, Map<String, String> properties) {
      CompoundTag root = new CompoundTag(); ListTag palette = new ListTag(); CompoundTag state = new CompoundTag(); state.putString("Name", blockName);
      if (properties != null) { CompoundTag values = new CompoundTag(); properties.forEach(values::putString); state.put("Properties", values); }
      palette.add(state); root.put("palette", palette);
      root.put("size", ints(1, 1, 1));
      ListTag blocks = new ListTag(); CompoundTag block = new CompoundTag(); block.putInt("state", 0); block.put("pos", ints(0, 0, 0)); blocks.add(block); root.put("blocks", blocks);
      root.put("entities", new ListTag()); return root;
   }

   private static CompoundTag blockEntity(String id) { CompoundTag nbt = new CompoundTag(); nbt.putString("id", id); return nbt; }
   private static ListTag ints(int... values) { ListTag list = new ListTag(); for (int value : values) list.add(net.minecraft.nbt.IntTag.valueOf(value)); return list; }
}
