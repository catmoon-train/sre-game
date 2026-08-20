package net.exmo.sreGame.games.fraud.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.games.fraud.ColorCode;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class VoteDeductRound implements RoundHandler {
   private final Map<UUID, UUID> votes = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.VOTE_DEDUCT;
   }

   @Override
   public String rules() {
      return "投给一名其他玩家。得票最多 -5；平票头名各 -3；投中被扣分者 +1；全员平票则无人扣分、全员 +1。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.votes.clear();
      match.broadcast("&7当前分数榜：");
      for (UUID uuid : match.ranked()) {
         match.broadcast(match.label(uuid) + " &e" + match.score(uuid));
      }
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID self = player.getUUID();
      if (this.votes.containsKey(self)) {
         UUID target = this.votes.get(self);
         container.setItem(22, GuiItems.named("lime_dye", "&a已投票",
            List.of(match.settings().anonymousVote() ? "&7已记录" : match.label(target))));
         return;
      }
      int slot = 10;
      for (UUID uuid : match.alive()) {
         if (uuid.equals(self)) {
            continue;
         }
         ColorCode color = match.color(uuid);
         ItemStack item = GuiItems.action(color == null ? "player_head" : color.wool(),
            match.label(uuid),
            List.of("&e点击投票", "&7当前 &e" + match.score(uuid) + " 分"),
            "vote", "uuid", uuid.toString());
         ServerPlayer member = match.player(uuid);
         if (member != null && color == null) {
            item.set(DataComponents.PROFILE, new ResolvableProfile(member.getGameProfile()));
         }
         container.setItem(slot, item);
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
      match.send(player, "&a已投票给 " + match.label(target));
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
         if (count > max) {
            max = count;
         }
      }
      List<UUID> top = new ArrayList<>();
      if (max > 0) {
         for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == max) {
               top.add(entry.getKey());
            }
         }
      }
      boolean allTied = max <= 1 && top.size() == match.alive().size();
      if (!match.settings().anonymousVote()) {
         for (Map.Entry<UUID, UUID> entry : this.votes.entrySet()) {
            match.broadcast(match.label(entry.getKey()) + " &7投了 " + match.label(entry.getValue()));
         }
      } else {
         match.broadcast("&7匿名投票，只公布结果。");
      }
      if (allTied || top.isEmpty()) {
         match.broadcast("&a全员平票，和谐局：每人 +1。");
         for (UUID uuid : match.alive()) {
            match.addScore(uuid, 1);
         }
         return;
      }
      int penalty = top.size() == 1 ? 5 : 3;
      for (UUID uuid : top) {
         match.addScore(uuid, -penalty);
         match.addVoteHit(uuid);
         match.broadcast(match.label(uuid) + " &c得票 " + counts.get(uuid) + "，-" + penalty);
      }
      for (Map.Entry<UUID, UUID> entry : this.votes.entrySet()) {
         if (top.contains(entry.getValue())) {
            match.addScore(entry.getKey(), 1);
         }
      }
   }
}
