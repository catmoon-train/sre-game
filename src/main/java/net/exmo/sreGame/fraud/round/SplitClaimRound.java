package net.exmo.sreGame.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.fraud.gui.ActionGui;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class SplitClaimRound implements RoundHandler {
   public static final int POT = 8;
   private final Map<UUID, Integer> claims = new HashMap<>();
   private final Map<UUID, String> buffers = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.SPLIT_CLAIM;
   }

   @Override
   public String rules() {
      return "公共赃款 " + POT + " 分。每人暗中申报要拿多少。总和 ≤" + POT + " 则各自拿到；超额则全员 -1，要得最多的人再 -2。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.claims.clear();
      this.buffers.clear();
      match.broadcast("&6赃款 &e" + POT + " &6分。打电话商量怎么分，也可以全独吞。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.claims.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已申报 &f" + this.claims.get(player.getUUID()),
            List.of("&7等待开袋")));
         return;
      }
      ActionGui.numberPad(container, this.buffers.getOrDefault(player.getUUID(), "0"), 0, POT);
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (this.claims.containsKey(uuid)) {
         return true;
      }
      String current = this.buffers.getOrDefault(uuid, "");
      if ("digit".equals(action) && extra != null && current.length() < 2) {
         current = current.equals("0") ? extra : current + extra;
         this.buffers.put(uuid, current);
         return true;
      }
      if ("clear".equals(action)) {
         this.buffers.put(uuid, "");
         return true;
      }
      if ("submit".equals(action)) {
         int claim = parse(current);
         this.claims.put(uuid, claim);
         match.send(player, "&a你要拿 &f" + claim);
         return true;
      }
      return false;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         this.claims.putIfAbsent(uuid, 0);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      int sum = 0;
      int max = -1;
      for (UUID uuid : match.alive()) {
         int claim = this.claims.getOrDefault(uuid, 0);
         sum += claim;
         if (claim > max) {
            max = claim;
         }
         match.broadcast(match.label(uuid) + " &7要 &f" + claim);
      }
      match.broadcast("&7合计 &f" + sum + " &7/ &e" + POT);
      if (sum <= POT) {
         match.broadcast("&a分赃成功。");
         for (UUID uuid : match.alive()) {
            int claim = this.claims.getOrDefault(uuid, 0);
            if (claim > 0) {
               match.addScore(uuid, claim);
            }
         }
         return;
      }
      match.broadcast("&c分赃失败，袋子破了。");
      for (UUID uuid : match.alive()) {
         match.addScore(uuid, -1);
         if (this.claims.getOrDefault(uuid, 0) == max && max > 0) {
            match.addScore(uuid, -2);
            match.broadcast(match.label(uuid) + " &c最贪心额外 -2");
         }
      }
   }

   private static int parse(String raw) {
      try {
         return Math.max(0, Math.min(POT, Integer.parseInt(raw == null || raw.isBlank() ? "0" : raw)));
      } catch (NumberFormatException e) {
         return 0;
      }
   }
}
