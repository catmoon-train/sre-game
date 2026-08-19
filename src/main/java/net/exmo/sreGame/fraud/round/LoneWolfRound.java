package net.exmo.sreGame.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class LoneWolfRound implements RoundHandler {
   private final Map<UUID, Integer> picks = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.LONE_WOLF;
   }

   @Override
   public String rules() {
      return "选 1 / 2 / 3。只有你一人选这个数字：+4。选的人最多的数字：各 +1。其余 0。可以串通，也可以故意拆台。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.picks.clear();
      match.broadcast("&e选一个数字。独食高分，从众保底。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.picks.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已选 &f" + this.picks.get(player.getUUID()),
            List.of("&7等待揭晓")));
         return;
      }
      container.setItem(20, GuiItems.action("red_concrete", "&c1", List.of("&e点击选择"), "choose", "value", "1"));
      container.setItem(22, GuiItems.action("yellow_concrete", "&e2", List.of("&e点击选择"), "choose", "value", "2"));
      container.setItem(24, GuiItems.action("lime_concrete", "&a3", List.of("&e点击选择"), "choose", "value", "3"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      if (!"choose".equals(action) || extra == null || this.picks.containsKey(player.getUUID())) {
         return true;
      }
      int pick = parse(extra);
      this.picks.put(player.getUUID(), pick);
      match.send(player, "&a你选了 &f" + pick);
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         this.picks.putIfAbsent(uuid, 2);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      Map<Integer, Integer> counts = new HashMap<>();
      counts.put(1, 0);
      counts.put(2, 0);
      counts.put(3, 0);
      for (UUID uuid : match.alive()) {
         int pick = this.picks.getOrDefault(uuid, 2);
         counts.merge(pick, 1, Integer::sum);
         match.broadcast(match.label(uuid) + " &7选了 &f" + pick);
      }
      int max = 0;
      for (int count : counts.values()) {
         max = Math.max(max, count);
      }
      for (UUID uuid : match.alive()) {
         int pick = this.picks.getOrDefault(uuid, 2);
         int count = counts.getOrDefault(pick, 0);
         if (count == 1) {
            match.addScore(uuid, 4);
            match.broadcast(match.label(uuid) + " &6独食 +4");
         } else if (count == max) {
            match.addScore(uuid, 1);
         }
      }
   }

   private static int parse(String raw) {
      try {
         return Math.max(1, Math.min(3, Integer.parseInt(raw)));
      } catch (NumberFormatException e) {
         return 2;
      }
   }
}
