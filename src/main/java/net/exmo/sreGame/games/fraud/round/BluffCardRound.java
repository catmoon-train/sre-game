package net.exmo.sreGame.games.fraud.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class BluffCardRound implements RoundHandler {
   private final Map<UUID, Integer> cards = new HashMap<>();
   private final Map<UUID, Boolean> play = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.BLUFF_CARD;
   }

   @Override
   public String rules() {
      return "每人一张私密点数 1–13。选择跟注或弃牌。跟注者里点数最高 +4，其余跟注 -1；弃牌 0。可以打电话虚张声势。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.cards.clear();
      this.play.clear();
      for (UUID uuid : match.alive()) {
         this.cards.put(uuid, ThreadLocalRandom.current().nextInt(1, 14));
      }
      match.broadcast("&e看自己的点数，决定跟还是弃。");
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      Integer card = this.cards.get(player);
      return card == null ? List.of() : List.of("你的点数是 " + card);
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID uuid = player.getUUID();
      container.setItem(13, GuiItems.named("paper", "&f你的点数 &e" + this.cards.getOrDefault(uuid, 0),
         List.of("&7不要让别人看到屏幕")));
      if (this.play.containsKey(uuid)) {
         container.setItem(22, GuiItems.named("lime_dye",
            this.play.get(uuid) ? "&a已跟注" : "&7已弃牌", List.of("&7等待开牌")));
         return;
      }
      container.setItem(20, GuiItems.action("gold_ingot", "&a跟注",
         List.of("&7最高者 +4，其余跟注 -1"), "choose", "value", "play"));
      container.setItem(24, GuiItems.action("gray_concrete", "&7弃牌",
         List.of("&7本回合 0 分"), "choose", "value", "fold"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (this.play.containsKey(uuid) || !"choose".equals(action)) {
         return true;
      }
      boolean stay = "play".equals(extra);
      this.play.put(uuid, stay);
      match.send(player, stay ? "&a你跟注了。" : "&7你弃牌了。");
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         this.play.putIfAbsent(uuid, false);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      int best = -1;
      List<UUID> winners = new ArrayList<>();
      for (UUID uuid : match.alive()) {
         int card = this.cards.getOrDefault(uuid, 0);
         boolean stayed = this.play.getOrDefault(uuid, false);
         match.broadcast(match.label(uuid) + " &7点数 &f" + card + (stayed ? " &a跟" : " &8弃"));
         if (!stayed) {
            continue;
         }
         if (card > best) {
            best = card;
            winners.clear();
            winners.add(uuid);
         } else if (card == best) {
            winners.add(uuid);
         }
      }
      if (winners.isEmpty()) {
         match.broadcast("&7全员弃牌。");
         return;
      }
      int prize = winners.size() == 1 ? 4 : 2;
      for (UUID uuid : match.alive()) {
         if (!this.play.getOrDefault(uuid, false)) {
            continue;
         }
         if (winners.contains(uuid)) {
            match.addScore(uuid, prize);
            match.broadcast(match.label(uuid) + " &6赢得 +" + prize);
         } else {
            match.addScore(uuid, -1);
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return List.of("&d点数 &f" + this.cards.getOrDefault(player, 0));
   }
}
