package net.exmo.sreGame.games.caveguess.gui;

import java.util.List;
import net.exmo.sreGame.games.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class TuneGui {
   private static final int[] NOTE_SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40
   };
   private static final String[] NOTE_NAMES = {
      "F#", "G", "G#", "A", "A#", "B", "C",
      "C#", "D", "D#", "E", "F", "F#", "G",
      "G#", "A", "A#", "B", "C", "C#", "D",
      "D#", "E", "F", "F#"
   };

   private TuneGui() {
   }

   public static void openKeyboard(CaveGuessersMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      for (int i = 0; i < NOTE_SLOTS.length; i++) {
         boolean sharp = NOTE_NAMES[i].contains("#");
         container.setItem(NOTE_SLOTS[i], GuiItems.action(
            sharp ? "black_concrete" : "white_concrete",
            "&e" + NOTE_NAMES[i] + " &8(" + i + ")",
            List.of("&e点击演奏"),
            "note", "value", String.valueOf(i)
         ));
      }
      container.setItem(4, GuiItems.named("note_block", "&f音符键盘", List.of("&7点击方块演奏旋律", "&7不要说话")));
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&d那是什么调 &8· &f键盘")
      ));
   }

   public static void openChoices(CaveGuessersMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      int[] slots = {20, 22, 24, 31};
      List<String> options = match.tuneOptions();
      String[] icons = {"music_disc_cat", "music_disc_blocks", "music_disc_chirp", "music_disc_mall"};
      for (int i = 0; i < 4 && i < options.size(); i++) {
         container.setItem(slots[i], GuiItems.action(icons[i], "&f" + options.get(i),
            List.of("&e点击选择", "&c猜错不能再猜"), "pick", "value", options.get(i)));
      }
      container.setItem(4, GuiItems.named("jukebox", "&f四选一", List.of("&7听旋律后选择")));
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&d那是什么调 &8· &f四选一")
      ));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final CaveGuessersMatch match;

      Menu(int syncId, Inventory inv, SimpleContainer container, CaveGuessersMatch match, ServerPlayer viewer) {
         super(syncId, inv, container, 6, viewer);
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         ItemStack stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         this.match.handleGui(player, action, GuiItems.extraTag(stack, "value"));
      }
   }
}
