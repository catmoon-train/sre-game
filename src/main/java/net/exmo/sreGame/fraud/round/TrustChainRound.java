package net.exmo.sreGame.fraud.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.fraud.gui.ActionGui;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class TrustChainRound implements RoundHandler {
   private final List<UUID> order = new ArrayList<>();
   private int index;
   private int value = 2;
   private boolean ended;
   private UUID winner;

   @Override
   public RoundType type() {
      return RoundType.TRUST_CHAIN;
   }

   @Override
   public String rules() {
      return "按顺序传递包裹，每次 +2。持有者可传递或私吞。传到最后一人自动拿走全部。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.order.clear();
      this.order.addAll(match.alive());
      Collections.shuffle(this.order, ThreadLocalRandom.current());
      this.index = 0;
      this.value = 2;
      this.ended = false;
      this.winner = null;
      StringBuilder chain = new StringBuilder();
      for (int i = 0; i < this.order.size(); i++) {
         if (i > 0) {
            chain.append(" &7→ ");
         }
         chain.append(match.label(this.order.get(i)));
      }
      match.broadcast("&7传递顺序：" + chain);
   }

   @Override
   public void onActionStart(FraudMasterMatch match) {
      this.prompt(match);
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (this.ended) {
         container.setItem(22, GuiItems.named("chest", "&7本回合已结束", List.of()));
         return;
      }
      UUID holder = this.holder();
      container.setItem(13, GuiItems.named("bundle", "&f包裹价值 &e" + this.value,
         List.of("&7持有者 " + (holder == null ? "?" : match.label(holder)))));
      if (!player.getUUID().equals(holder)) {
         container.setItem(22, GuiItems.named("clock", "&7等待持有者决定", List.of()));
         return;
      }
      if (this.last()) {
         container.setItem(22, GuiItems.named("gold_ingot", "&6你是最后一人", List.of("&7自动获得 &e" + this.value)));
         return;
      }
      container.setItem(20, GuiItems.action("lime_concrete", "&a传递",
         List.of("&7交给下一人，价值 +2"), "choose", "value", "pass"));
      container.setItem(24, GuiItems.action("red_concrete", "&c私吞",
         List.of("&7立刻拿走 &e" + this.value + " &7分"), "choose", "value", "steal"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      if (this.ended || !"choose".equals(action) || !player.getUUID().equals(this.holder())) {
         return true;
      }
      if ("steal".equals(extra)) {
         this.steal(match);
      } else if ("pass".equals(extra)) {
         this.pass(match);
      }
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      if (!this.ended) {
         this.steal(match);
         match.broadcast("&7超时，持有者私吞了包裹。");
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      if (!this.ended && this.holder() != null) {
         if (this.last()) {
            this.winner = this.holder();
            match.addScore(this.winner, this.value);
            this.ended = true;
         }
      }
      if (this.winner != null) {
         match.broadcast(match.label(this.winner) + " &6拿走了 &e" + this.value + " &6分包裹。");
      } else {
         match.broadcast("&7无人获得包裹。");
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      UUID holder = this.holder();
      return List.of("&7价值 &e" + this.value, "&7持有 " + (holder == null ? "?" : match.coloredName(holder)));
   }

   @Override
   public String actionBar(FraudMasterMatch match, UUID player) {
      return player.equals(this.holder()) ? "&e轮到你决定" : "&7等待 " + match.coloredName(this.holder());
   }

   @Override
   public void onLeave(FraudMasterMatch match, UUID player) {
      int at = this.order.indexOf(player);
      if (at < 0) {
         return;
      }
      this.order.remove(at);
      if (this.ended) {
         return;
      }
      if (this.order.isEmpty()) {
         this.ended = true;
         return;
      }
      if (at < this.index) {
         this.index--;
      }
      if (this.index >= this.order.size()) {
         this.index = this.order.size() - 1;
      }
      if (this.last()) {
         this.autoLast(match);
      }
   }

   private UUID holder() {
      return this.index < 0 || this.index >= this.order.size() ? null : this.order.get(this.index);
   }

   private boolean last() {
      return this.order.size() > 0 && this.index >= this.order.size() - 1;
   }

   private void steal(FraudMasterMatch match) {
      if (this.ended) {
         return;
      }
      this.winner = this.holder();
      if (this.winner != null) {
         match.addScore(this.winner, this.value);
      }
      this.ended = true;
      match.broadcast(match.label(this.winner) + " &c私吞了价值 &e" + this.value + " &c的包裹！");
      match.skipPhase();
   }

   private void pass(FraudMasterMatch match) {
      if (this.ended || this.last()) {
         this.autoLast(match);
         return;
      }
      this.index++;
      this.value += 2;
      match.broadcast(match.label(this.order.get(this.index - 1)) + " &a传递了包裹，现在价值 &e" + this.value);
      if (this.last()) {
         this.autoLast(match);
      } else {
         this.prompt(match);
      }
   }

   private void autoLast(FraudMasterMatch match) {
      if (this.ended) {
         return;
      }
      this.winner = this.holder();
      this.ended = true;
      if (this.winner != null) {
         match.addScore(this.winner, this.value);
         match.broadcast(match.label(this.winner) + " &6作为最后一人收下 &e" + this.value + " &6分。");
      }
      match.skipPhase();
   }

   private void prompt(FraudMasterMatch match) {
      UUID holder = this.holder();
      ServerPlayer player = match.player(holder);
      if (player != null) {
         ActionGui.open(match, player);
      }
   }
}
