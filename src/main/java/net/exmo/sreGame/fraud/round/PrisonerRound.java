package net.exmo.sreGame.fraud.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class PrisonerRound implements RoundHandler {
   private final Map<UUID, UUID> opponent = new HashMap<>();
   private final Map<UUID, Boolean> cooperate = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.PRISONER;
   }

   @Override
   public String rules() {
      return "与配对对手同时选择合作或背叛。双方合作各 +3；单方面背叛 +5/-3；双背叛各 -1。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.opponent.clear();
      this.cooperate.clear();
      List<UUID> pool = new ArrayList<>(match.alive());
      Collections.shuffle(pool, ThreadLocalRandom.current());
      for (int i = 0; i + 1 < pool.size(); i += 2) {
         UUID a = pool.get(i);
         UUID b = pool.get(i + 1);
         this.opponent.put(a, b);
         this.opponent.put(b, a);
      }
      if (pool.size() % 2 == 1) {
         UUID odd = pool.get(pool.size() - 1);
         match.send(odd, "&7本回合轮空，没有对手。");
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      UUID other = this.opponent.get(player);
      return other == null ? List.of("本回合轮空") : List.of("对手是 " + match.label(other));
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID uuid = player.getUUID();
      if (!this.opponent.containsKey(uuid)) {
         container.setItem(22, GuiItems.named("barrier", "&7轮空", List.of("&7本回合 0 分")));
         return;
      }
      if (this.cooperate.containsKey(uuid)) {
         container.setItem(22, GuiItems.named("lime_dye",
            this.cooperate.get(uuid) ? "&a已选合作" : "&c已选背叛", List.of("&7等待对手")));
         return;
      }
      container.setItem(20, GuiItems.action("lime_concrete", "&a合作",
         List.of("&7双方合作各 +3", "&7你合作对方背叛：你 -3"), "choose", "value", "coop"));
      container.setItem(24, GuiItems.action("red_concrete", "&c背叛",
         List.of("&7你背叛对方合作：你 +5", "&7双背叛各 -1"), "choose", "value", "betray"));
      container.setItem(31, GuiItems.named("player_head", "&f对手",
         List.of(match.label(this.opponent.get(uuid)))));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (!this.opponent.containsKey(uuid) || this.cooperate.containsKey(uuid) || !"choose".equals(action)) {
         return this.opponent.containsKey(uuid);
      }
      boolean coop = "coop".equals(extra);
      this.cooperate.put(uuid, coop);
      match.addPrisonerPlay(uuid, coop);
      if (!coop) {
         match.addBetrayal(uuid);
      }
      match.send(player, coop ? "&a你选择了合作。" : "&c你选择了背叛。");
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : this.opponent.keySet()) {
         if (!this.cooperate.containsKey(uuid) && match.alive(uuid)) {
            this.cooperate.put(uuid, true);
            match.addPrisonerPlay(uuid, true);
            match.send(uuid, "&7超时，默认合作。");
         }
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         UUID other = this.opponent.get(uuid);
         if (other == null) {
            match.broadcast(match.label(uuid) + " &7轮空");
            continue;
         }
         if (uuid.compareTo(other) > 0) {
            continue;
         }
         boolean a = this.cooperate.getOrDefault(uuid, true);
         boolean b = this.cooperate.getOrDefault(other, true);
         int scoreA;
         int scoreB;
         if (a && b) {
            scoreA = 3;
            scoreB = 3;
         } else if (a) {
            scoreA = -3;
            scoreB = 5;
         } else if (b) {
            scoreA = 5;
            scoreB = -3;
         } else {
            scoreA = -1;
            scoreB = -1;
         }
         match.addScore(uuid, scoreA);
         if (match.alive(other)) {
            match.addScore(other, scoreB);
         }
         match.broadcast(match.label(uuid) + (a ? " &a合作" : " &c背叛")
            + " &8vs " + match.label(other) + (b ? " &a合作" : " &c背叛"));
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      UUID other = this.opponent.get(player);
      return other == null ? List.of("&7轮空") : List.of("&7对手 " + match.coloredName(other));
   }

   @Override
   public void onLeave(FraudMasterMatch match, UUID player) {
      UUID other = this.opponent.remove(player);
      if (other != null) {
         this.opponent.remove(other);
      }
   }
}
