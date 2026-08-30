package net.exmo.sreGame.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Keeps a player's overworld and dedicated-game-world inventories independent. */
public final class WorldInventoryManager {
   private static final String NBT_KEY = "sre-game:world-inventories";
   private static final Map<UUID, Profiles> PROFILES = new ConcurrentHashMap<>();

   private WorldInventoryManager() {
   }

   /** Called immediately before a cross-dimension teleport takes place. */
   public static void beforeTeleport(ServerPlayer player, ServerLevel destination) {
      if (player == null || destination == null || player.serverLevel() == destination) {
         return;
      }
      InventoryKind source = kind(player.serverLevel());
      InventoryKind target = kind(destination);
      if (source == target) {
         return;
      }

      Profiles profiles = PROFILES.computeIfAbsent(player.getUUID(), ignored -> new Profiles());
      profiles.set(source, InventorySnapshot.capture(player));
      InventorySnapshot targetInventory = profiles.get(target);
      if (targetInventory == null) {
         player.getInventory().clearContent();
      } else {
         targetInventory.apply(player);
      }
   }

   public static void read(ServerPlayer player, CompoundTag playerTag) {
      if (player == null || !playerTag.contains(NBT_KEY, CompoundTag.TAG_COMPOUND)) {
         return;
      }
      Profiles profiles = Profiles.read(player.registryAccess(), playerTag.getCompound(NBT_KEY));
      PROFILES.put(player.getUUID(), profiles);
   }

   public static void write(ServerPlayer player, CompoundTag playerTag) {
      if (player == null) {
         return;
      }
      Profiles profiles = PROFILES.computeIfAbsent(player.getUUID(), ignored -> new Profiles());
      profiles.set(kind(player.serverLevel()), InventorySnapshot.capture(player));
      playerTag.put(NBT_KEY, profiles.write(player.registryAccess()));
   }

   private static InventoryKind kind(ServerLevel level) {
      return SreGame.getContext() != null && SreGame.getContext().config().isGameWorld(level)
         ? InventoryKind.GAME : InventoryKind.OVERWORLD;
   }

   private enum InventoryKind {
      OVERWORLD("overworld"), GAME("game");

      private final String key;

      InventoryKind(String key) {
         this.key = key;
      }
   }

   private static final class Profiles {
      private InventorySnapshot overworld;
      private InventorySnapshot game;

      InventorySnapshot get(InventoryKind kind) {
         return kind == InventoryKind.GAME ? this.game : this.overworld;
      }

      void set(InventoryKind kind, InventorySnapshot value) {
         if (kind == InventoryKind.GAME) {
            this.game = value;
         } else {
            this.overworld = value;
         }
      }

      CompoundTag write(HolderLookup.Provider registries) {
         CompoundTag tag = new CompoundTag();
         if (this.overworld != null) {
            tag.put(InventoryKind.OVERWORLD.key, this.overworld.write(registries));
         }
         if (this.game != null) {
            tag.put(InventoryKind.GAME.key, this.game.write(registries));
         }
         return tag;
      }

      static Profiles read(HolderLookup.Provider registries, CompoundTag tag) {
         Profiles profiles = new Profiles();
         if (tag.contains(InventoryKind.OVERWORLD.key, CompoundTag.TAG_COMPOUND)) {
            profiles.overworld = InventorySnapshot.read(registries, tag.getCompound(InventoryKind.OVERWORLD.key));
         }
         if (tag.contains(InventoryKind.GAME.key, CompoundTag.TAG_COMPOUND)) {
            profiles.game = InventorySnapshot.read(registries, tag.getCompound(InventoryKind.GAME.key));
         }
         return profiles;
      }
   }

   private record InventorySnapshot(List<ItemStack> items, int selectedSlot) {
      static InventorySnapshot capture(ServerPlayer player) {
         Inventory inventory = player.getInventory();
         List<ItemStack> items = new ArrayList<>(inventory.getContainerSize());
         for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            items.add(inventory.getItem(slot).copy());
         }
         return new InventorySnapshot(items, inventory.selected);
      }

      void apply(ServerPlayer player) {
         Inventory inventory = player.getInventory();
         inventory.clearContent();
         for (int slot = 0; slot < Math.min(inventory.getContainerSize(), this.items.size()); slot++) {
            inventory.setItem(slot, this.items.get(slot).copy());
         }
         inventory.selected = Math.clamp(this.selectedSlot, 0, 8);
      }

      CompoundTag write(HolderLookup.Provider registries) {
         CompoundTag tag = new CompoundTag();
         ListTag itemsTag = new ListTag();
         for (ItemStack item : this.items) {
            itemsTag.add(item.saveOptional(registries));
         }
         tag.put("items", itemsTag);
         tag.putInt("selected", this.selectedSlot);
         return tag;
      }

      static InventorySnapshot read(HolderLookup.Provider registries, CompoundTag tag) {
         List<ItemStack> items = new ArrayList<>();
         for (var itemTag : tag.getList("items", CompoundTag.TAG_COMPOUND)) {
            items.add(ItemStack.parseOptional(registries, (CompoundTag)itemTag));
         }
         return new InventorySnapshot(items, tag.getInt("selected"));
      }
   }
}
