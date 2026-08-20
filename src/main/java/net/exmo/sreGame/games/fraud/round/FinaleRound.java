package net.exmo.sreGame.games.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class FinaleRound implements RoundHandler {
   private UUID target;
   private final Map<UUID, Boolean> trust = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.FINALE;
   }

   @Override
   public String rules() {
      return "当前第一名是靶心。投信任或不信任。信任票不少于半数：第一名 +10、信任者 +2；否则第一名 -5、不信任者 +3。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.trust.clear();
      this.target = match.leader();
      match.broadcast("&6终局：靶心是 " + match.label(this.target));
      match.broadcast("&7当前榜：");
      for (UUID uuid : match.ranked()) {
         match.broadcast(match.label(uuid) + " &e" + match.score(uuid));
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      if (player.equals(this.target)) {
         return List.of("你是靶心。说服大家投信任。");
      }
      return List.of("决定是否让 " + match.ctx().name(this.target) + " 过关。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.trust.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye",
            this.trust.get(player.getUUID()) ? "&a已投信任" : "&c已投不信任", List.of()));
         return;
      }
      container.setItem(13, GuiItems.named("target", "&6靶心",
         List.of(match.label(this.target), "&7分数 &e" + match.score(this.target))));
      container.setItem(20, GuiItems.action("lime_concrete", "&a信任",
         List.of("&7过关：第一名 +10，你 +2"), "choose", "value", "yes"));
      container.setItem(24, GuiItems.action("red_concrete", "&c不信任",
         List.of("&7翻盘：第一名 -5，不信任者 +3"), "choose", "value", "no"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      if (!"choose".equals(action) || this.trust.containsKey(player.getUUID())) {
         return true;
      }
      boolean yes = "yes".equals(extra);
      this.trust.put(player.getUUID(), yes);
      match.send(player, yes ? "&a你投了信任。" : "&c你投了不信任。");
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         this.trust.putIfAbsent(uuid, false);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      int yes = 0;
      int n = match.alive().size();
      for (UUID uuid : match.alive()) {
         boolean trusted = this.trust.getOrDefault(uuid, false);
         match.broadcast(match.label(uuid) + (trusted ? " &a信任" : " &c不信任"));
         if (trusted) {
            yes++;
         }
      }
      boolean pass = yes * 2 >= n;
      match.broadcast("&7信任 " + yes + "/" + n + (pass ? " &a过关" : " &c翻盘"));
      if (pass) {
         if (this.target != null) {
            match.addScore(this.target, 10);
         }
         for (UUID uuid : match.alive()) {
            if (this.trust.getOrDefault(uuid, false)) {
               match.addScore(uuid, 2);
            }
         }
      } else {
         if (this.target != null) {
            match.addScore(this.target, -5);
         }
         for (UUID uuid : match.alive()) {
            if (!this.trust.getOrDefault(uuid, false)) {
               match.addScore(uuid, 3);
            }
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return List.of("&6靶心 " + (this.target == null ? "?" : match.coloredName(this.target)));
   }
}
