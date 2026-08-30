package net.exmo.sreGame.games.partygames.scene;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/** Loads and rejects incomplete/corrupt/cross-version-incompatible official scenes before a match can start. */
public final class PartySceneStore {
   private static final Gson GSON = new Gson();
   private static final Set<String> FORBIDDEN_BLOCKS = Set.of(
      "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
      "minecraft:structure_block", "minecraft:jigsaw"
   );
   private static final Set<String> STATIC_ENTITIES = Set.of(
      "minecraft:armor_stand", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting",
      "minecraft:text_display", "minecraft:item_display", "minecraft:block_display", "minecraft:interaction"
   );
   /** Explicit source->target aliases for blocks renamed between 1.21.10 and
    * the 1.21.1 runtime. These are audited conversions, never an unknown-ID
    * fallback to air. */
   private static final Map<String, String> BLOCK_ALIASES = Map.of("minecraft:iron_chain", "minecraft:chain");
   private static final Map<String, Set<String>> REQUIRED_ANCHORS = Map.ofEntries(
      Map.entry("101", Set.of("blue_spawn", "red_spawn")),
      Map.entry("102", Set.of("blue_spawn", "red_spawn")),
      Map.entry("103", Set.of("blue_spawn", "red_spawn")),
      Map.entry("104", Set.of("blue_spawn", "red_spawn", "puck", "blue_goal", "red_goal")),
      Map.entry("105", Set.of("blue_spawn", "red_spawn")),
      Map.entry("106", Set.of("blue_spawn", "red_spawn", "button_wall")),
      Map.entry("107", Set.of("blue_spawn", "red_spawn", "blue_goal", "red_goal")),
      Map.entry("108", Set.of("blue_spawn", "red_spawn", "blue_pig", "red_pig", "route_origin", "route_copy", "template_1", "template_2", "template_3")),
      Map.entry("109", Set.of("blue_spawn", "red_spawn", "finish")),
      Map.entry("110", Set.of("blue_spawn", "red_spawn")),
      Map.entry("111", Set.of("blue_spawn", "red_spawn", "blue_board", "red_board")),
      Map.entry("112", Set.of("blue_spawn", "red_spawn", "net_center", "court_min", "court_max")),
      Map.entry("113", Set.of("blue_spawn", "red_spawn", "blue_hopper", "red_hopper", "blue_door", "red_door", "blue_exit", "red_exit")),
      Map.entry("114", Set.of("blue_spawn", "red_spawn", "blue_platform", "red_platform", "wall_origin")),
      Map.entry("201", Set.of("blue_spawn", "red_spawn")),
      Map.entry("202", Set.of("blue_spawn", "red_spawn")),
      Map.entry("203", Set.of("blue_spawn", "red_spawn", "blue_tank", "red_tank")),
      Map.entry("204", Set.of("blue_spawn", "red_spawn", "blue_flag", "red_flag")),
      Map.entry("205", Set.of("blue_spawn", "red_spawn")),
      Map.entry("206", Set.of("blue_spawn", "red_spawn", "puck", "blue_goal", "red_goal")),
      // target_2/meet and the 214 pit markers are optional for older exports;
      // controllers provide deterministic fallbacks while new exports include
      // the richer anchor set.
      Map.entry("207", Set.of("blue_spawn", "red_spawn", "blue_target", "red_target")),
      Map.entry("208", Set.of("blue_spawn", "red_spawn")),
      Map.entry("209", Set.of("blue_spawn", "red_spawn", "blue_gold_1", "blue_gold_2", "red_gold_1", "red_gold_2")),
      Map.entry("210", Set.of("blue_spawn", "red_spawn")),
      Map.entry("211", Set.of("blue_spawn", "red_spawn", "blue_finish", "red_finish")),
      Map.entry("212", Set.of("blue_spawn", "red_spawn", "blue_button", "red_button")),
      Map.entry("213", Set.of("blue_spawn", "red_spawn", "blue_chicken", "red_chicken")),
      Map.entry("214", Set.of("blue_spawn", "red_spawn", "blue_army", "red_army")),
      Map.entry("301", Set.of("blue_spawn", "red_spawn")),
      Map.entry("302", Set.of("blue_spawn", "red_spawn")),
      Map.entry("303", Set.of("blue_spawn", "red_spawn")),
      Map.entry("304", Set.of("blue_spawn", "red_spawn")),
      Map.entry("305", Set.of("blue_spawn", "red_spawn")),
      Map.entry("306", Set.of("blue_spawn", "red_spawn")),
      Map.entry("307", Set.of("blue_spawn", "red_spawn")),
      Map.entry("308", Set.of("blue_spawn", "red_spawn")),
      Map.entry("309", Set.of("blue_spawn", "red_spawn")),
      Map.entry("310", Set.of("blue_spawn", "red_spawn")),
      Map.entry("311", Set.of("blue_spawn", "red_spawn")),
      Map.entry("312", Set.of("blue_spawn", "red_spawn", "blue_board", "red_board")),
      Map.entry("313", Set.of("blue_spawn", "red_spawn")),
      Map.entry("314", Set.of("blue_spawn", "red_spawn"))
   );

   private final Path externalRoot;
   private final Map<PartyGameType, PartySceneBundle> scenes = new ConcurrentHashMap<>();
   private final Map<PartyGameType, String> errors = new ConcurrentHashMap<>();

   public PartySceneStore(Path configDir) { this.externalRoot = configDir.resolve("party-scenes"); }

   public void load() {
      scenes.clear(); errors.clear();
      for (PartyGameType type : PartyGameType.values()) {
         String[] idParts = type.id().split("_", 3);
         if (idParts.length < 2 || !"mp2".equals(idParts[0]) || !isOfficialSceneId(idParts[1])) continue;
         String id = idParts[1];
         try { scenes.put(type, loadOne(id)); }
         catch (Exception e) { errors.put(type, e.getMessage()); SreGame.LOGGER.warn("Official scene {} unavailable: {}", id, e.getMessage()); }
      }
   }

   public PartySceneBundle get(PartyGameType type) { return scenes.get(type); }
   public boolean ready(PartyGameType type) { return scenes.containsKey(type); }
   public String status(PartyGameType type) { return ready(type) ? "场景已校验" : errors.getOrDefault(type, "场景包未安装"); }

   /** Match the complete catalogue ranges; startsWith("mp2_301_") would
    * silently skip 302–314 because it only matches one exact ID. */
   static boolean isOfficialSceneId(String raw) {
      try {
         int id = Integer.parseInt(raw);
         return (id >= 101 && id <= 114) || (id >= 201 && id <= 214) || (id >= 301 && id <= 314);
      } catch (NumberFormatException ignored) {
         return false;
      }
   }

   PartySceneBundle loadOne(String id) throws Exception {
      String resourceBase = "data/sre-game/party_scenes/" + id + "/";
      Path diskBase = externalRoot.resolve(id);
      byte[] manifestBytes = read(diskBase.resolve("manifest.json"), resourceBase + "manifest.json");
      if (manifestBytes == null) throw new IOException("缺少 manifest.json；请运行 /mp2export all 后导入场景");
      PartySceneManifest manifest = GSON.fromJson(new String(manifestBytes, StandardCharsets.UTF_8), PartySceneManifest.class);
      validateManifest(id, manifest);
      List<PartySceneBundle.Shard> shards = new ArrayList<>();
      Set<String> files = new HashSet<>();
      MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
      int totalBlocks = 0, totalBlockEntities = 0, totalEntities = 0;
      for (PartySceneManifest.Shard shard : manifest.shards()) {
         safeName(shard.file());
         if (!files.add(shard.file())) throw new IOException("重复分片 " + shard.file());
         validateShardBounds(manifest, shard);
         byte[] bytes = read(diskBase.resolve(shard.file()), resourceBase + shard.file());
         if (bytes == null) throw new IOException("缺少分片 " + shard.file());
         String hash = sha256(bytes);
         if (!hash.equalsIgnoreCase(shard.sha256())) throw new IOException(shard.file() + " SHA-256 不匹配");
         aggregate.update(bytes);
         CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
         applyKnownAliases(nbt);
         validateNbt(shard, nbt);
         shards.add(new PartySceneBundle.Shard(shard, nbt));
         totalBlocks += shard.blockCount(); totalBlockEntities += shard.blockEntityCount(); totalEntities += shard.entityCount();
      }
      if (totalBlocks != manifest.blockCount() || totalBlockEntities != manifest.blockEntityCount() || totalEntities != manifest.entityCount()) throw new IOException("场景总计数与分片不一致");
      String combined = HexFormat.of().formatHex(aggregate.digest());
      if (manifest.sha256() != null && !manifest.sha256().isBlank() && !combined.equalsIgnoreCase(manifest.sha256())) throw new IOException("场景总 SHA-256 不匹配");
      return new PartySceneBundle(manifest, shards);
   }

   void validateManifest(String id, PartySceneManifest manifest) throws IOException {
      if (manifest == null || manifest.format() != 1 || !id.equals(manifest.gameId())) throw new IOException("不支持的场景清单格式");
      if (!"1.21.10".equals(manifest.minecraftVersion())) throw new IOException("场景源版本必须为 1.21.10");
      if (manifest.size() == null || manifest.size().length != 3 || manifest.size()[0] < 1 || manifest.size()[1] < 1 || manifest.size()[2] < 1
         || manifest.size()[0] > 112 || manifest.size()[1] > 80 || manifest.size()[2] > 112) throw new IOException("场景尺寸越界");
      if (manifest.shards() == null || manifest.shards().isEmpty()) throw new IOException("场景没有分片");
      if (manifest.blockCount() < 0 || manifest.blockEntityCount() < 0 || manifest.entityCount() < 0) throw new IOException("场景计数非法");
      Set<String> required = REQUIRED_ANCHORS.getOrDefault(id, Set.of());
      if (manifest.anchors() == null || !manifest.anchors().keySet().containsAll(required)) throw new IOException("场景缺少必需锚点 " + required.stream().filter(name -> manifest.anchors() == null || !manifest.anchors().containsKey(name)).toList());
      if (manifest.anchors() != null) for (Map.Entry<String, double[]> entry : manifest.anchors().entrySet()) {
         double[] point = entry.getValue();
         if (entry.getKey() == null || entry.getKey().isBlank() || point == null || point.length != 3) throw new IOException("场景锚点格式错误");
         for (int axis = 0; axis < 3; axis++) if (!Double.isFinite(point[axis]) || point[axis] < 0 || point[axis] >= manifest.size()[axis]) throw new IOException("场景锚点越界 " + entry.getKey());
      }
   }

   private void validateShardBounds(PartySceneManifest manifest, PartySceneManifest.Shard shard) throws IOException {
      if (shard.offset() == null || shard.offset().length != 3 || shard.size() == null || shard.size().length != 3) throw new IOException(shard.file() + " 分片尺寸格式错误");
      if (shard.blockCount() < 0 || shard.blockEntityCount() < 0 || shard.entityCount() < 0) throw new IOException(shard.file() + " 分片计数非法");
      for (int axis = 0; axis < 3; axis++) {
         if (shard.offset()[axis] < 0 || shard.size()[axis] < 1 || shard.size()[axis] > 32 || shard.offset()[axis] + shard.size()[axis] > manifest.size()[axis]) throw new IOException(shard.file() + " 分片越界");
      }
   }

   void validateNbt(PartySceneManifest.Shard shard, CompoundTag root) throws IOException {
      ListTag declaredSize = root.getList("size", 3);
      if (declaredSize.size() != 3) throw new IOException(shard.file() + " 结构尺寸缺失");
      for (int axis = 0; axis < 3; axis++) if (declaredSize.getInt(axis) != shard.size()[axis]) throw new IOException(shard.file() + " 结构尺寸与清单不一致");
      ListTag palette = root.getList("palette", 10);
      validatePalette(palette);
      ListTag palettes = root.getList("palettes", 9);
      for (int i = 0; i < palettes.size(); i++) validatePalette(palettes.getList(i));
      ListTag blocks = root.getList("blocks", 10);
      if (shard.blockCount() >= 0 && blocks.size() != shard.blockCount()) throw new IOException(shard.file() + " 方块数不匹配");
      int blockEntities = 0;
      for (int i = 0; i < blocks.size(); i++) {
         CompoundTag block = blocks.getCompound(i);
         if (block.getInt("state") < 0 || block.getInt("state") >= palette.size()) throw new IOException(shard.file() + " 方块 palette 索引越界");
         ListTag position = block.getList("pos", 3);
         if (position.size() != 3) throw new IOException(shard.file() + " 方块坐标缺失");
         for (int axis = 0; axis < 3; axis++) if (position.getInt(axis) < 0 || position.getInt(axis) >= shard.size()[axis]) throw new IOException(shard.file() + " 方块坐标越界");
         if (!block.contains("nbt")) continue;
         blockEntities++;
         String blockEntityName = block.getCompound("nbt").getString("id");
         ResourceLocation blockEntityId = ResourceLocation.tryParse(blockEntityName);
         if (blockEntityId == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(blockEntityId)) throw new IOException(shard.file() + " 包含 1.21.1 未知方块实体 " + blockEntityName);
         validateItems(shard.file(), block.getCompound("nbt").getList("Items", 10));
      }
      if (shard.blockEntityCount() >= 0 && blockEntities != shard.blockEntityCount()) throw new IOException(shard.file() + " 方块实体数不匹配");
      ListTag entities = root.getList("entities", 10);
      if (shard.entityCount() >= 0 && entities.size() != shard.entityCount()) throw new IOException(shard.file() + " 实体数不匹配");
      for (int i = 0; i < entities.size(); i++) {
         CompoundTag entityNbt = entities.getCompound(i).getCompound("nbt"); String entityId = entityNbt.getString("id");
         if (!STATIC_ENTITIES.contains(entityId)) throw new IOException(shard.file() + " 包含非静态实体 " + entityId);
         if (entityNbt.contains("Item")) validateItem(shard.file(), entityNbt.getCompound("Item"));
      }
   }

   private void validateItems(String file, ListTag items) throws IOException { for (int i = 0; i < items.size(); i++) validateItem(file, items.getCompound(i)); }

   static void applyKnownAliases(CompoundTag root) {
      remapPalette(root.getList("palette", 10));
      ListTag palettes = root.getList("palettes", 9);
      for (int i = 0; i < palettes.size(); i++) remapPalette(palettes.getList(i));
   }

   private static void remapPalette(ListTag palette) {
      for (int i = 0; i < palette.size(); i++) {
         CompoundTag state = palette.getCompound(i);
         String source = state.getString("Name");
         String target = BLOCK_ALIASES.get(source);
         if (target != null) state.putString("Name", target);
      }
   }

   private void validateItem(String file, CompoundTag item) throws IOException {
      if (item.isEmpty()) return; String name = item.getString("id"); ResourceLocation id = ResourceLocation.tryParse(name);
      if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IOException(file + " 包含 1.21.1 未知物品 " + name);
   }

   void validatePalette(ListTag palette) throws IOException {
      if (palette.isEmpty()) throw new IOException("结构分片缺少 palette");
      for (int i = 0; i < palette.size(); i++) {
         String name = palette.getCompound(i).getString("Name");
         ResourceLocation id = ResourceLocation.tryParse(name);
         if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) throw new IOException("1.21.1 未知方块 " + name);
         if (FORBIDDEN_BLOCKS.contains(name)) throw new IOException("场景中禁止出现 " + name);
         Block block = BuiltInRegistries.BLOCK.get(id);
         CompoundTag properties = palette.getCompound(i).getCompound("Properties");
         for (String key : properties.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            String value = properties.getString(key);
            if (property == null || property.getValue(value).isEmpty()) throw new IOException("1.21.1 不支持方块状态 " + name + "[" + key + "=" + value + "]");
         }
      }
   }

   private byte[] read(Path disk, String resource) throws IOException {
      if (Files.isRegularFile(disk)) return Files.readAllBytes(disk);
      try (InputStream in = PartySceneStore.class.getClassLoader().getResourceAsStream(resource)) { return in == null ? null : in.readAllBytes(); }
   }
   private static void safeName(String name) throws IOException { if (name == null || !name.matches("[a-zA-Z0-9._-]+\\.nbt")) throw new IOException("非法分片文件名"); }
   private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
