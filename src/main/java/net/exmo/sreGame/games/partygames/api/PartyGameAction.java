package net.exmo.sreGame.games.partygames.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Normalized input consumed by room-scoped party controllers. */
public record PartyGameAction(Type type, BlockPos block, Direction face, Entity entity, ItemStack stack, int amount, boolean active) {
   public enum Type {
      LEFT_CLICK, RIGHT_CLICK, ATTACK_ENTITY, USE_BLOCK, USE_ENTITY, USE_ITEM,
      DROP_ITEM, HOTBAR_DELTA, JUMP, SNEAK, FORWARD, LOOK_CHANGED, FISH_CAUGHT,
      ITEM_ENTERED_CONTAINER
   }

   public static PartyGameAction simple(Type type) { return new PartyGameAction(type, null, null, null, ItemStack.EMPTY, 0, false); }
   public static PartyGameAction hotbar(int delta) { return new PartyGameAction(Type.HOTBAR_DELTA, null, null, null, ItemStack.EMPTY, delta, false); }
   public static PartyGameAction sneak(boolean active) { return new PartyGameAction(Type.SNEAK, null, null, null, ItemStack.EMPTY, 0, active); }
   public static PartyGameAction block(Type type, BlockPos pos, Direction face, ItemStack stack) { return new PartyGameAction(type, pos, face, null, stack == null ? ItemStack.EMPTY : stack.copy(), 0, false); }
   public static PartyGameAction entity(Type type, Entity entity) { return new PartyGameAction(type, null, null, entity, ItemStack.EMPTY, 0, false); }
   public static PartyGameAction item(Type type, ItemStack stack) { return new PartyGameAction(type, null, null, null, stack == null ? ItemStack.EMPTY : stack.copy(), 0, false); }
}
