package net.exmo.sreGame.games.caveguess.mode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.exmo.sreGame.games.caveguess.CaveGuessersMatch;
import net.exmo.sreGame.games.caveguess.CaveMode;
import net.exmo.sreGame.games.caveguess.CaveTag;
import net.exmo.sreGame.games.caveguess.gui.TagSelectGui;
import net.minecraft.server.level.ServerPlayer;

public final class BreakItDownMode implements CaveModeHandler {
   public static final int MAX_TAGS = 5;
   private final Set<CaveTag> selected = new LinkedHashSet<>();
   private boolean locked;

   @Override
   public CaveMode type() {
      return CaveMode.BREAK_DOWN;
   }

   @Override
   public void onPrepare(CaveGuessersMatch match) {
      this.selected.clear();
      this.locked = false;
      ServerPlayer performer = match.ctx().player(match.performer());
      if (performer != null) {
         performer.getInventory().setItem(0, match.reopenItem("item_frame", "&e标签菜单", "cave-tags"));
         TagSelectGui.open(match, performer);
      }
      match.broadcast("&7描述者正在选择概念标签。");
   }

   public Set<CaveTag> selected() {
      return this.selected;
   }

   public boolean locked() {
      return this.locked;
   }

   public boolean toggle(CaveTag tag) {
      if (this.locked || tag == null) {
         return false;
      }
      if (this.selected.remove(tag)) {
         return true;
      }
      if (this.selected.size() >= MAX_TAGS) {
         return false;
      }
      this.selected.add(tag);
      return true;
   }

   @Override
   public boolean handleChat(CaveGuessersMatch match, ServerPlayer player, String message) {
      if (match.isPerformer(player.getUUID())) {
         match.ctx().send(player, "&7本模式不能打字描述，请用标签菜单。");
         return true;
      }
      return match.tryGuess(player, message);
   }

   @Override
   public boolean handleGui(CaveGuessersMatch match, ServerPlayer player, String action, String extra) {
      if (!match.isPerformer(player.getUUID()) || this.locked) {
         return false;
      }
      if ("tag".equals(action)) {
         try {
            CaveTag tag = CaveTag.valueOf(extra);
            if (!this.toggle(tag) && this.selected.size() >= MAX_TAGS && !this.selected.contains(tag)) {
               match.ctx().send(player, "&c最多选择 " + MAX_TAGS + " 个标签。");
            }
            TagSelectGui.open(match, player);
            return true;
         } catch (IllegalArgumentException ignored) {
            return true;
         }
      }
      if ("confirm".equals(action)) {
         if (this.selected.isEmpty()) {
            match.ctx().send(player, "&c至少选择 1 个标签。");
            TagSelectGui.open(match, player);
            return true;
         }
         this.locked = true;
         player.closeContainer();
         List<String> names = new ArrayList<>();
         for (CaveTag tag : this.selected) {
            names.add(tag.display());
         }
         match.showTags(names);
         match.ctx().send(player, "&a标签已锁定。");
         return true;
      }
      return false;
   }

   @Override
   public boolean handleUseItem(CaveGuessersMatch match, ServerPlayer player, String action) {
      if ("cave-tags".equals(action) && match.isPerformer(player.getUUID()) && !this.locked) {
         TagSelectGui.open(match, player);
         return true;
      }
      return false;
   }

   @Override
   public boolean voiceMute(CaveGuessersMatch match, UUID player) {
      return match.isPerformer(player);
   }

   @Override
   public List<String> boardExtra(CaveGuessersMatch match, UUID player) {
      return List.of(this.locked ? "&b已出标签" : "&e选择标签 " + this.selected.size() + "/" + MAX_TAGS);
   }

   @Override
   public String actionBar(CaveGuessersMatch match, UUID player) {
      if (match.isPerformer(player)) {
         return this.locked ? "&a标签已锁定" : "&e选择最多 " + MAX_TAGS + " 个标签";
      }
      return this.locked ? "&b根据标签抢答" : "&7等待标签";
   }
}
