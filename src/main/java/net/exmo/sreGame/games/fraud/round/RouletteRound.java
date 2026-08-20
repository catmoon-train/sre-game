package net.exmo.sreGame.games.fraud.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.games.fraud.gui.ActionGui;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class RouletteRound implements RoundHandler {
   private static final int STEPS = 5;
   private static final int STEP_SECONDS = 10;
   private int pool;
   private int step;
   private boolean exploded;
   private boolean finished;
   private final Set<UUID> active = new HashSet<>();
   private final Map<UUID, Boolean> choice = new HashMap<>();
   private int stepTicks;

   @Override
   public RoundType type() {
      return RoundType.ROULETTE;
   }

   @Override
   public String rules() {
      return "5 步轮盘。每步选继续或收手。继续者使奖池 +2 后 20% 爆炸（继续者 -3）。收手者平分当时奖池。";
   }

   @Override
   public int actionSeconds(FraudMasterMatch match) {
      return STEPS * STEP_SECONDS;
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.pool = 0;
      this.step = 0;
      this.exploded = false;
      this.finished = false;
      this.active.clear();
      this.active.addAll(match.alive());
      this.choice.clear();
      this.stepTicks = 0;
   }

   @Override
   public void onActionStart(FraudMasterMatch match) {
      this.beginStep(match);
   }

   @Override
   public void onActionTick(FraudMasterMatch match) {
      if (this.finished) {
         return;
      }
      this.stepTicks++;
      if (this.stepTicks >= STEP_SECONDS * 20) {
         this.resolveStep(match);
      }
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      container.setItem(4, GuiItems.named("gold_ingot", "&6奖池 &e" + this.pool,
         List.of("&7第 " + Math.min(STEPS, this.step + 1) + "/" + STEPS + " 步")));
      if (this.finished || !this.active.contains(player.getUUID())) {
         container.setItem(22, GuiItems.named("clock", this.finished ? "&7本回合结束" : "&7你已收手", List.of()));
         return;
      }
      if (this.choice.containsKey(player.getUUID())) {
         container.setItem(22, GuiItems.named("lime_dye",
            this.choice.get(player.getUUID()) ? "&a已选继续" : "&e已选收手", List.of("&7等待同步")));
         return;
      }
      container.setItem(20, GuiItems.action("lime_concrete", "&a继续",
         List.of("&7奖池 +2，然后 20% 爆炸", "&7爆炸则继续者 -3"), "choose", "value", "go"));
      container.setItem(24, GuiItems.action("yellow_concrete", "&e收手",
         List.of("&7与本步收手者平分当前奖池"), "choose", "value", "fold"));
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (this.finished || !this.active.contains(uuid) || this.choice.containsKey(uuid) || !"choose".equals(action)) {
         return true;
      }
      boolean go = "go".equals(extra);
      this.choice.put(uuid, go);
      match.send(player, go ? "&a你选择继续。" : "&e你选择收手。");
      return true;
   }

   @Override
   public void onActionTimeout(FraudMasterMatch match) {
      if (!this.finished) {
         this.resolveStep(match);
      }
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      if (!this.finished && !this.exploded && !this.active.isEmpty()) {
         this.split(match, new ArrayList<>(this.active), "未爆炸，继续者平分最终奖池");
         this.finished = true;
      }
      if (this.exploded) {
         match.broadcast("&c轮盘爆炸！奖池清零。");
      } else {
         match.broadcast("&7最终奖池 &e" + this.pool);
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return List.of("&7奖池 &e" + this.pool, "&7第 " + Math.min(STEPS, this.step + 1) + " 步");
   }

   @Override
   public String actionBar(FraudMasterMatch match, UUID player) {
      if (!this.active.contains(player)) {
         return "&7已收手";
      }
      return "&e奖池 " + this.pool;
   }

   @Override
   public void onLeave(FraudMasterMatch match, UUID player) {
      this.active.remove(player);
      this.choice.remove(player);
   }

   private void beginStep(FraudMasterMatch match) {
      if (this.finished || this.active.isEmpty() || this.step >= STEPS) {
         this.finished = true;
         return;
      }
      this.choice.clear();
      this.stepTicks = 0;
      match.broadcast("&e第 " + (this.step + 1) + " 步：奖池 &f" + this.pool + " &e。继续或收手。");
      for (UUID uuid : this.active) {
         ServerPlayer player = match.player(uuid);
         if (player != null) {
            ActionGui.open(match, player);
         }
      }
   }

   private void resolveStep(FraudMasterMatch match) {
      if (this.finished) {
         return;
      }
      List<UUID> folders = new ArrayList<>();
      List<UUID> going = new ArrayList<>();
      for (UUID uuid : List.copyOf(this.active)) {
         boolean go = this.choice.getOrDefault(uuid, false);
         if (go) {
            going.add(uuid);
         } else {
            folders.add(uuid);
         }
      }
      if (!folders.isEmpty()) {
         this.split(match, folders, "收手平分");
         this.active.removeAll(folders);
      }
      if (going.isEmpty()) {
         this.finished = true;
         match.broadcast("&7无人继续，轮盘结束。");
         match.skipPhase();
         return;
      }
      this.pool += 2;
      boolean boom = ThreadLocalRandom.current().nextInt(100) < 20;
      if (boom) {
         this.exploded = true;
         this.finished = true;
         for (UUID uuid : going) {
            match.addScore(uuid, -3);
         }
         match.broadcast("&c爆炸！继续者各 -3。");
         match.skipPhase();
         return;
      }
      this.step++;
      if (this.step >= STEPS) {
         this.split(match, going, "五步未爆，平分奖池");
         this.finished = true;
         match.skipPhase();
         return;
      }
      this.beginStep(match);
   }

   private void split(FraudMasterMatch match, List<UUID> people, String reason) {
      if (people.isEmpty()) {
         return;
      }
      int share = this.pool / people.size();
      int spent = share * people.size();
      this.pool -= spent;
      for (UUID uuid : people) {
         match.addScore(uuid, share);
      }
      match.broadcast("&e" + reason + "：每人 &f" + share + " &7（" + people.size() + " 人）");
   }
}
