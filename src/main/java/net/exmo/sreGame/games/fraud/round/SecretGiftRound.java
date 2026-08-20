package net.exmo.sreGame.games.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.games.fraud.ColorCode;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class SecretGiftRound implements RoundHandler {
   private final Map<UUID, UUID> pending = new HashMap<>();
   private final Map<UUID, UUID> targets = new HashMap<>();
   private final Map<UUID, Integer> amounts = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.SECRET_GIFT;
   }

   @Override
   public String rules() {
      return "暗中送 1/2/3 分给一人（自己先扣这么多）。对方收到礼物。若两人互送，再各 +2。可以打电话谈交换，也可以放鸽子。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.pending.clear();
      this.targets.clear();
      this.amounts.clear();
      match.broadcast("&e选一个人送礼。互送有额外奖励。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID self = player.getUUID();
      if (this.targets.containsKey(self)) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已送出 &e" + this.amounts.get(self),
            List.of("给 " + match.label(this.targets.get(self)))));
         return;
      }
      UUID pick = this.pending.get(self);
      if (pick == null) {
         int slot = 10;
         for (UUID uuid : match.alive()) {
            if (uuid.equals(self)) {
               continue;
            }
            ColorCode color = match.color(uuid);
            container.setItem(slot, GuiItems.action(color == null ? "player_head" : color.wool(),
               match.label(uuid), List.of("&e点击选择收礼人"), "pick", "uuid", uuid.toString()));
            slot++;
            if (slot % 9 == 8) {
               slot += 2;
            }
         }
         return;
      }
      container.setItem(4, GuiItems.named("player_head", "&f送给 " + match.label(pick), List.of("&7再选金额")));
      container.setItem(20, GuiItems.action("iron_nugget", "&f1 分", List.of("&7自己 -1，对方 +1"), "amount", "value", "1"));
      container.setItem(22, GuiItems.action("gold_nugget", "&e2 分", List.of("&7自己 -2，对方 +2"), "amount", "value", "2"));
      container.setItem(24, GuiItems.action("emerald", "&a3 分", List.of("&7自己 -3，对方 +3"), "amount", "value", "3"));
      container.setItem(40, GuiItems.action("barrier", "&7重选对象", List.of(), "clear"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID self = player.getUUID();
      if (this.targets.containsKey(self)) {
         return true;
      }
      if ("clear".equals(action)) {
         this.pending.remove(self);
         return true;
      }
      if ("pick".equals(action) && extra != null) {
         try {
            UUID target = UUID.fromString(extra);
            if (match.alive(target) && !target.equals(self)) {
               this.pending.put(self, target);
            }
         } catch (IllegalArgumentException ignored) {
         }
         return true;
      }
      if ("amount".equals(action) && extra != null) {
         UUID target = this.pending.get(self);
         if (target == null || !match.alive(target)) {
            return true;
         }
         int amount = parse(extra);
         this.targets.put(self, target);
         this.amounts.put(self, amount);
         this.pending.remove(self);
         match.send(player, "&a已送给 " + match.label(target) + " &e" + amount + " &a分。");
         return true;
      }
      return false;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      this.pending.clear();
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         UUID target = this.targets.get(uuid);
         int amount = this.amounts.getOrDefault(uuid, 0);
         if (target == null || amount <= 0) {
            match.broadcast(match.label(uuid) + " &7没有送礼");
            continue;
         }
         match.addScore(uuid, -amount);
         match.addScore(target, amount);
         match.broadcast(match.label(uuid) + " &7送了 &e" + amount + " &7给 " + match.label(target));
      }
      for (UUID uuid : match.alive()) {
         UUID aTo = this.targets.get(uuid);
         if (aTo == null) {
            continue;
         }
         if (uuid.equals(this.targets.get(aTo)) && uuid.compareTo(aTo) < 0) {
            match.addScore(uuid, 2);
            match.addScore(aTo, 2);
            match.broadcast(match.label(uuid) + " &a与 " + match.label(aTo) + " &a互送，各 +2");
         }
      }
   }

   private static int parse(String raw) {
      try {
         return Math.max(1, Math.min(3, Integer.parseInt(raw)));
      } catch (NumberFormatException e) {
         return 1;
      }
   }
}
