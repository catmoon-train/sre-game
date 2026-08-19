package net.exmo.sreGame.fraud.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.fraud.ColorCode;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class ScapegoatRound implements RoundHandler {
   private UUID goat;
   private final Map<UUID, UUID> votes = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.SCAPEGOAT;
   }

   @Override
   public String rules() {
      return "一人是替罪羊（只有自己知道）。投票指认。唯一最高票且正中替罪羊：投中者各 +3；没找中则替罪羊 +4，其他人 -1。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.votes.clear();
      List<UUID> alive = match.alive();
      this.goat = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
      match.broadcast("&e找出替罪羊。他会拼命甩锅。");
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      if (player.equals(this.goat)) {
         return List.of("你是替罪羊。别被投中。");
      }
      return List.of("你不是替罪羊。打电话查谁在心虚。");
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID self = player.getUUID();
      if (this.votes.containsKey(self)) {
         container.setItem(22, GuiItems.named("lime_dye", "&a已指认",
            List.of(match.label(this.votes.get(self)))));
         return;
      }
      int slot = 10;
      for (UUID uuid : match.alive()) {
         if (uuid.equals(self)) {
            continue;
         }
         ColorCode color = match.color(uuid);
         container.setItem(slot, GuiItems.action(color == null ? "player_head" : color.wool(),
            match.label(uuid), List.of("&e点击指认为替罪羊"), "vote", "uuid", uuid.toString()));
         slot++;
         if (slot % 9 == 8) {
            slot += 2;
         }
      }
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      if (!"vote".equals(action) || extra == null || this.votes.containsKey(player.getUUID())) {
         return true;
      }
      UUID target;
      try {
         target = UUID.fromString(extra);
      } catch (IllegalArgumentException e) {
         return true;
      }
      if (!match.alive(target) || target.equals(player.getUUID())) {
         match.send(player, "&c不能投这个人。");
         return true;
      }
      this.votes.put(player.getUUID(), target);
      match.send(player, "&a已指认 " + match.label(target));
      return true;
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      Map<UUID, Integer> counts = new HashMap<>();
      for (UUID uuid : match.alive()) {
         counts.put(uuid, 0);
      }
      for (UUID target : this.votes.values()) {
         if (counts.containsKey(target)) {
            counts.merge(target, 1, Integer::sum);
         }
      }
      int max = 0;
      for (int count : counts.values()) {
         max = Math.max(max, count);
      }
      List<UUID> top = new ArrayList<>();
      if (max > 0) {
         for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == max) {
               top.add(entry.getKey());
            }
         }
      }
      match.broadcast("&6替罪羊是 " + match.label(this.goat));
      boolean found = top.size() == 1 && top.contains(this.goat);
      if (found) {
         match.broadcast("&a唯一最高票正中替罪羊。");
         for (Map.Entry<UUID, UUID> entry : this.votes.entrySet()) {
            if (this.goat.equals(entry.getValue())) {
               match.addScore(entry.getKey(), 3);
            }
         }
      } else {
         match.broadcast("&c没抓到。替罪羊脱身。");
         match.addScore(this.goat, 4);
         for (UUID uuid : match.alive()) {
            if (!uuid.equals(this.goat)) {
               match.addScore(uuid, -1);
            }
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return player.equals(this.goat) ? List.of("&c你是替罪羊") : List.of("&7找出替罪羊");
   }
}
