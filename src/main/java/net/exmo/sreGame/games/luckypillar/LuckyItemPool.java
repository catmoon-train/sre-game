package net.exmo.sreGame.games.luckypillar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LuckyItemPool {
   private final Path file;
   private final List<Entry> extras = new CopyOnWriteArrayList<>();
   private List<Item> vanilla;

   public LuckyItemPool(Path configDir) {
      this.file = configDir.resolve("lucky-items.yml");
   }

   public void load() {
      this.extras.clear();
      try {
         Files.createDirectories(this.file.getParent());
         if (!Files.exists(this.file)) {
            Files.writeString(this.file, defaultYaml(), StandardCharsets.UTF_8);
         }
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            Entry entry = parseLine(line);
            if (entry != null) {
               this.extras.add(entry);
            }
         }
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to load lucky-items.yml", e);
      }
      this.vanilla = null;
   }

   public List<Entry> extras() {
      return List.copyOf(this.extras);
   }

   public int extraCount() {
      return this.extras.size();
   }

   public synchronized boolean add(ItemStack stack, HolderLookup.Provider registries) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (id == null) {
         return false;
      }
      String snbt = null;
      try {
         Tag tag = stack.save(registries);
         if (tag instanceof CompoundTag compound) {
            snbt = compound.toString();
         }
      } catch (Exception e) {
         SreGame.LOGGER.warn("Failed to serialize lucky item {}", id, e);
      }
      this.extras.add(new Entry(id.toString(), snbt));
      this.save();
      return true;
   }

   public synchronized boolean remove(int index) {
      if (index < 0 || index >= this.extras.size()) {
         return false;
      }
      this.extras.remove(index);
      this.save();
      return true;
   }

   public ItemStack roll(HolderLookup.Provider registries, java.util.Random random) {
      List<Item> vanillaItems = this.vanilla();
      if (vanillaItems.isEmpty()) {
         return new ItemStack(Items.STONE);
      }
      Item item = vanillaItems.get(random.nextInt(vanillaItems.size()));
      return new ItemStack(item);
   }

   public ItemStack luckyBlock() {
      return net.exmo.sreGame.gui.GuiItems.action("gold_block", "&6幸运方块", List.of(
         "&7放置后打破触发随机事件"
      ), "lucky_block");
   }

   public boolean isLuckyBlock(ItemStack stack) {
      return stack != null && !stack.isEmpty()
         && "lucky_block".equals(net.exmo.sreGame.gui.GuiItems.actionTag(stack));
   }

   private List<Item> vanilla() {
      if (this.vanilla != null) {
         return this.vanilla;
      }
      List<Item> list = new ArrayList<>();
      for (Item item : BuiltInRegistries.ITEM) {
         if (item == Items.AIR || isBanned(item) || !isVanilla(item)) {
            continue;
         }
         list.add(item);
      }
      this.vanilla = list;
      return list;
   }

   private static boolean isVanilla(Item item) {
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      return id != null && ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace());
   }

   private static boolean isBanned(Item item) {
      return item == Items.COMMAND_BLOCK
         || item == Items.CHAIN_COMMAND_BLOCK
         || item == Items.REPEATING_COMMAND_BLOCK
         || item == Items.COMMAND_BLOCK_MINECART
         || item == Items.STRUCTURE_BLOCK
         || item == Items.STRUCTURE_VOID
         || item == Items.JIGSAW
         || item == Items.BARRIER
         || item == Items.DEBUG_STICK
         || item == Items.LIGHT
         || item == Items.KNOWLEDGE_BOOK
         || item == Items.SPAWNER
         || item == Items.TRIAL_SPAWNER
         || item == Items.VAULT
         || item == Items.END_PORTAL_FRAME
         || item == Items.BEDROCK;
   }

   private void save() {
      StringBuilder sb = new StringBuilder();
      sb.append("# SRE-GAME lucky pillar extra items\n");
      sb.append("# id  or  id|{snbt}\n");
      for (Entry entry : this.extras) {
         if (entry.snbt() == null || entry.snbt().isBlank()) {
            sb.append(entry.id()).append('\n');
         } else {
            sb.append(entry.id()).append('|').append(entry.snbt()).append('\n');
         }
      }
      try {
         Files.writeString(this.file, sb.toString(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save lucky-items.yml", e);
      }
   }

   private static Entry parseLine(String line) {
      String trimmed = line == null ? "" : line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
         return null;
      }
      int split = trimmed.indexOf('|');
      if (split < 0) {
         return new Entry(trimmed, null);
      }
      String id = trimmed.substring(0, split).trim();
      String snbt = trimmed.substring(split + 1).trim();
      return id.isEmpty() ? null : new Entry(id, snbt.isEmpty() ? null : snbt);
   }

   private static String defaultYaml() {
      return "# SRE-GAME lucky pillar extra items\n"
         + "# OP 用手持物品在 /sregame luckyitems 里添加。\n"
         + "# 格式：namespace:id 或 namespace:id|{ItemStack SNBT}\n";
   }

   public record Entry(String id, String snbt) {
      public ItemStack toStack(HolderLookup.Provider registries) {
         if (this.snbt != null && !this.snbt.isBlank() && registries != null) {
            try {
               CompoundTag tag = TagParser.parseTag(this.snbt);
               Optional<ItemStack> parsed = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result();
               if (parsed.isPresent() && !parsed.get().isEmpty()) {
                  return parsed.get().copy();
               }
            } catch (Exception e) {
               SreGame.LOGGER.warn("Failed to parse lucky item SNBT for {}", this.id, e);
            }
         }
         ResourceLocation loc = ResourceLocation.tryParse(this.id);
         if (loc == null) {
            return ItemStack.EMPTY;
         }
         Item item = BuiltInRegistries.ITEM.get(loc);
         return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
      }
   }
}
