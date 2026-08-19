package net.exmo.sreGame.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.fraud.gui.ActionGui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class SumGuessRound implements RoundHandler {
   private final Map<UUID, Integer> secrets = new HashMap<>();
   private final Map<UUID, Integer> guesses = new HashMap<>();
   private final Map<UUID, String> buffers = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.SUM_GUESS;
   }

   @Override
   public String rules() {
      return "每人有一个私密数字 1–8。提交所有人数字之和。猜中 +5，最接近 +2。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.secrets.clear();
      this.guesses.clear();
      this.buffers.clear();
      for (UUID uuid : match.alive()) {
         this.secrets.put(uuid, ThreadLocalRandom.current().nextInt(1, 9));
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      Integer secret = this.secrets.get(player);
      return secret == null ? List.of() : List.of("你的数字是 " + secret);
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.guesses.containsKey(player.getUUID())) {
         container.setItem(22, net.exmo.sreGame.gui.GuiItems.named("lime_dye",
            "&a已提交 &f" + this.guesses.get(player.getUUID()), List.of("&7等待结算")));
         return;
      }
      ActionGui.numberPad(container, this.buffers.getOrDefault(player.getUUID(), ""), 4, 64);
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (this.guesses.containsKey(uuid)) {
         return true;
      }
      String current = this.buffers.getOrDefault(uuid, "");
      if ("digit".equals(action) && extra != null && current.length() < 3) {
         if (current.equals("0")) {
            current = extra;
         } else {
            current += extra;
         }
         this.buffers.put(uuid, current);
         return true;
      }
      if ("clear".equals(action)) {
         this.buffers.put(uuid, "");
         return true;
      }
      if ("submit".equals(action)) {
         int value = parse(current, 0, 64);
         this.guesses.put(uuid, value);
         match.send(player, "&a已提交总和 &f" + value);
         return true;
      }
      return false;
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      int actual = 0;
      for (UUID uuid : match.alive()) {
         actual += this.secrets.getOrDefault(uuid, 0);
      }
      match.broadcast("&7真实总和： &f" + actual);
      int bestError = Integer.MAX_VALUE;
      for (UUID uuid : match.alive()) {
         Integer guess = this.guesses.get(uuid);
         if (guess == null) {
            continue;
         }
         int error = Math.abs(guess - actual);
         match.addSumError(uuid, error);
         if (error < bestError) {
            bestError = error;
         }
      }
      for (UUID uuid : match.alive()) {
         Integer guess = this.guesses.get(uuid);
         String secretLine = match.label(uuid) + " &7数字 &f" + this.secrets.getOrDefault(uuid, 0)
            + " &8| 猜 &f" + (guess == null ? "弃权" : guess);
         match.broadcast(secretLine);
         if (guess == null) {
            continue;
         }
         int error = Math.abs(guess - actual);
         if (error == 0) {
            match.addScore(uuid, 5);
         } else if (error == bestError) {
            match.addScore(uuid, 2);
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      Integer secret = this.secrets.get(player);
      return secret == null ? List.of() : List.of("&d你的数字 &f" + secret);
   }

   private static int parse(String raw, int min, int max) {
      try {
         int value = Integer.parseInt(raw == null || raw.isBlank() ? "0" : raw);
         return Math.max(min, Math.min(max, value));
      } catch (NumberFormatException e) {
         return min;
      }
   }
}
