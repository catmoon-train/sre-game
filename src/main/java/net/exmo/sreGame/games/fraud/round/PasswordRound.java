package net.exmo.sreGame.games.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.games.fraud.gui.ActionGui;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class PasswordRound implements RoundHandler {
   private UUID holder;
   private UUID faker;
   private int code;
   private final Map<UUID, Integer> guesses = new HashMap<>();
   private final Map<UUID, String> buffers = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.PASSWORD;
   }

   @Override
   public String rules() {
      return "一人持有两位口令，一人是骗子（没有口令）。打电话打听后提交。猜对 +3；无人猜对则持令者 +3；有人猜对则骗子 -2。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.guesses.clear();
      this.buffers.clear();
      List<UUID> alive = match.alive();
      int holderAt = ThreadLocalRandom.current().nextInt(alive.size());
      int fakerAt = ThreadLocalRandom.current().nextInt(alive.size() - 1);
      if (fakerAt >= holderAt) {
         fakerAt++;
      }
      this.holder = alive.get(holderAt);
      this.faker = alive.get(fakerAt);
      this.code = ThreadLocalRandom.current().nextInt(10, 100);
      match.broadcast("&e有人口中有口令，有人在装。打电话核实。");
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      if (player.equals(this.holder)) {
         return List.of("口令是 " + this.code + "。可以告诉别人，也可以撒谎。");
      }
      if (player.equals(this.faker)) {
         return List.of("你没有口令。让别人以为你才是持令者。");
      }
      return List.of("打电话打听两位口令，操作阶段提交。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID uuid = player.getUUID();
      if (uuid.equals(this.holder) || uuid.equals(this.faker)) {
         container.setItem(22, GuiItems.named("name_tag",
            uuid.equals(this.holder) ? "&a你持有口令" : "&c你是骗子",
            List.of("&7不用提交")));
         return;
      }
      if (this.guesses.containsKey(uuid)) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已提交 &f" + this.guesses.get(uuid),
            List.of("&7等待结算")));
         return;
      }
      ActionGui.numberPad(container, this.buffers.getOrDefault(uuid, ""), 10, 99);
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (uuid.equals(this.holder) || uuid.equals(this.faker) || this.guesses.containsKey(uuid)) {
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
         int value = parse(current);
         this.guesses.put(uuid, value);
         match.send(player, "&a已提交口令 &f" + value);
         return true;
      }
      return false;
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      match.broadcast("&6口令是 &e" + this.code);
      match.broadcast(match.label(this.holder) + " &a持令  ·  " + match.label(this.faker) + " &c骗子");
      int hits = 0;
      for (UUID uuid : match.alive()) {
         if (uuid.equals(this.holder) || uuid.equals(this.faker)) {
            continue;
         }
         Integer guess = this.guesses.get(uuid);
         boolean correct = guess != null && guess == this.code;
         match.broadcast(match.label(uuid) + " &7猜 &f" + (guess == null ? "弃权" : guess)
            + (correct ? " &a✓" : " &c✗"));
         if (correct) {
            match.addScore(uuid, 3);
            hits++;
         }
      }
      if (hits == 0) {
         match.addScore(this.holder, 3);
         match.broadcast(match.label(this.holder) + " &6守住口令 +3");
      } else {
         match.addScore(this.faker, -2);
         match.broadcast(match.label(this.faker) + " &c骗局被拆穿 -2");
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      if (player.equals(this.holder)) {
         return List.of("&a口令 &f" + this.code);
      }
      if (player.equals(this.faker)) {
         return List.of("&c你是骗子");
      }
      return List.of("&7打听口令");
   }

   private static int parse(String raw) {
      try {
         return Math.max(10, Math.min(99, Integer.parseInt(raw == null || raw.isBlank() ? "10" : raw)));
      } catch (NumberFormatException e) {
         return 10;
      }
   }
}
