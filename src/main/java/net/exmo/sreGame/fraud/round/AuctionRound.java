package net.exmo.sreGame.fraud.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.fraud.gui.ActionGui;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class AuctionRound implements RoundHandler {
   public static final int POT = 10;
   private final Map<UUID, Integer> bids = new HashMap<>();
   private final Map<UUID, String> buffers = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.AUCTION;
   }

   @Override
   public String rules() {
      return "暗标 0–10 分竞拍奖池 +" + POT + "。最高者净得 = 奖池 - 出价；并列则平分奖池再减各自出价。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.bids.clear();
      this.buffers.clear();
      match.broadcast("&6奖池 &e+" + POT + " &6分。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.bids.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已出价 &f" + this.bids.get(player.getUUID()),
            List.of("&7等待开标")));
         return;
      }
      ActionGui.numberPad(container, this.buffers.getOrDefault(player.getUUID(), "0"), 0, 10);
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (this.bids.containsKey(uuid)) {
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
         int bid = parse(current);
         this.bids.put(uuid, bid);
         match.send(player, "&a已暗标 &f" + bid);
         return true;
      }
      return false;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         this.bids.putIfAbsent(uuid, 0);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      int max = -1;
      for (UUID uuid : match.alive()) {
         int bid = this.bids.getOrDefault(uuid, 0);
         match.broadcast(match.label(uuid) + " &7出价 &f" + bid);
         if (bid > max) {
            max = bid;
         }
      }
      if (max < 0) {
         return;
      }
      List<UUID> winners = new ArrayList<>();
      for (UUID uuid : match.alive()) {
         if (this.bids.getOrDefault(uuid, 0) == max) {
            winners.add(uuid);
         }
      }
      if (winners.isEmpty()) {
         return;
      }
      int share = POT / winners.size();
      for (UUID uuid : winners) {
         int net = share - this.bids.getOrDefault(uuid, 0);
         match.addScore(uuid, net);
         match.broadcast(match.label(uuid) + " &6拍得净 &e" + net);
      }
   }

   private static int parse(String raw) {
      try {
         return Math.max(0, Math.min(10, Integer.parseInt(raw == null || raw.isBlank() ? "0" : raw)));
      } catch (NumberFormatException e) {
         return 0;
      }
   }
}
