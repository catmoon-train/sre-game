package net.exmo.sreGame.games.fraud.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.games.fraud.ColorCode;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class IntelRound implements RoundHandler {
   private ColorCode mole;
   private final Map<UUID, String> intel = new HashMap<>();
   private final Map<UUID, Boolean> fake = new HashMap<>();
   private final Map<UUID, ColorCode> answers = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.INTEL;
   }

   @Override
   public String rules() {
      return "每人一条情报，约一半为假。根据情报推断谁是内鬼。答对 +4；持假情报且至少 2 人答错额外 +2。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.intel.clear();
      this.fake.clear();
      this.answers.clear();
      List<UUID> alive = match.alive();
      this.mole = match.color(alive.get(ThreadLocalRandom.current().nextInt(alive.size())));
      List<String> truths = trueFacts(match, this.mole);
      List<String> lies = falseFacts(match, this.mole);
      Collections.shuffle(truths, ThreadLocalRandom.current());
      Collections.shuffle(lies, ThreadLocalRandom.current());
      int trueCount = (alive.size() + 1) / 2;
      List<Boolean> flags = new ArrayList<>();
      for (int i = 0; i < alive.size(); i++) {
         flags.add(i < trueCount);
      }
      Collections.shuffle(flags, ThreadLocalRandom.current());
      int t = 0;
      int f = 0;
      for (int i = 0; i < alive.size(); i++) {
         UUID uuid = alive.get(i);
         boolean isFake = !flags.get(i);
         String line;
         if (isFake) {
            line = lies.get(f % lies.size());
            f++;
         } else {
            line = truths.get(t % truths.size());
            t++;
         }
         this.fake.put(uuid, isFake);
         this.intel.put(uuid, line);
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      String line = this.intel.get(player);
      return line == null ? List.of() : List.of("情报：" + line + "（真假未知）");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.answers.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已提交",
            List.of("&7内鬼是 " + this.answers.get(player.getUUID()).tagged())));
         return;
      }
      int slot = 10;
      for (UUID uuid : match.alive()) {
         ColorCode color = match.color(uuid);
         if (color == null) {
            continue;
         }
         container.setItem(slot, GuiItems.action(color.wool(), color.tagged() + " &f" + match.ctx().name(uuid),
            List.of("&e点击认定此人是内鬼"), "vote", "value", color.name()));
         slot++;
         if (slot % 9 == 8) {
            slot += 2;
         }
      }
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      if (!"vote".equals(action) || extra == null || this.answers.containsKey(player.getUUID())) {
         return true;
      }
      try {
         ColorCode color = ColorCode.valueOf(extra);
         this.answers.put(player.getUUID(), color);
         match.send(player, "&a你认定内鬼是 " + color.tagged());
      } catch (IllegalArgumentException ignored) {
      }
      return true;
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      match.broadcast("&6内鬼是 " + this.mole.tagged());
      int wrong = 0;
      for (UUID uuid : match.alive()) {
         ColorCode answer = this.answers.get(uuid);
         boolean correct = answer == this.mole;
         match.broadcast(match.label(uuid) + " &7答了 "
            + (answer == null ? "&8弃权" : answer.tagged())
            + (correct ? " &a✓" : " &c✗")
            + (this.fake.getOrDefault(uuid, false) ? " &8(假情报)" : " &8(真情报)"));
         if (correct) {
            match.addScore(uuid, 4);
         } else {
            wrong++;
         }
      }
      if (wrong >= 2) {
         for (UUID uuid : match.alive()) {
            if (this.fake.getOrDefault(uuid, false)) {
               match.addScore(uuid, 2);
               match.broadcast(match.label(uuid) + " &6假情报诈骗奖励 +2");
            }
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      String line = this.intel.get(player);
      return line == null ? List.of() : List.of("&d" + trim(line, 14));
   }

   private static String trim(String text, int max) {
      return text.length() <= max ? text : text.substring(0, max) + "…";
   }

   private static List<String> trueFacts(FraudMasterMatch match, ColorCode mole) {
      List<String> facts = new ArrayList<>();
      facts.add("内鬼是" + mole.display());
      facts.add(mole.warm() ? "内鬼是暖色" : "内鬼是冷色");
      UUID molePlayer = match.byColor(mole);
      List<UUID> alive = match.alive();
      int seat = alive.indexOf(molePlayer);
      if (seat >= 0) {
         facts.add(seat % 2 == 0 ? "内鬼座位号是奇数" : "内鬼座位号是偶数");
         facts.add(seat < (alive.size() + 1) / 2 ? "内鬼在顺序前半" : "内鬼在顺序后半");
      }
      for (ColorCode color : ColorCode.values()) {
         if (color != mole && match.byColor(color) != null) {
            facts.add("内鬼不是" + color.display());
         }
      }
      return facts;
   }

   private static List<String> falseFacts(FraudMasterMatch match, ColorCode mole) {
      List<String> facts = new ArrayList<>();
      facts.add(mole.warm() ? "内鬼是冷色" : "内鬼是暖色");
      facts.add("内鬼不是" + mole.display());
      UUID molePlayer = match.byColor(mole);
      List<UUID> alive = match.alive();
      int seat = alive.indexOf(molePlayer);
      if (seat >= 0) {
         facts.add(seat % 2 == 0 ? "内鬼座位号是偶数" : "内鬼座位号是奇数");
         facts.add(seat < (alive.size() + 1) / 2 ? "内鬼在顺序后半" : "内鬼在顺序前半");
      }
      for (ColorCode color : ColorCode.values()) {
         if (color != mole && match.byColor(color) != null) {
            facts.add("内鬼是" + color.display());
         }
      }
      if (facts.isEmpty()) {
         facts.add("内鬼不存在");
      }
      return facts;
   }
}
