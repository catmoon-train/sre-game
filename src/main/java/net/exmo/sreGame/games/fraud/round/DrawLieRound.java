package net.exmo.sreGame.games.fraud.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.games.buildwar.Plot;
import net.exmo.sreGame.games.draw.Canvas;
import net.exmo.sreGame.games.draw.DrawKit;
import net.exmo.sreGame.games.draw.DrawSnapshot;
import net.exmo.sreGame.games.fraud.BoothHut;
import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class DrawLieRound implements RoundHandler {
   private static final String[] SLOT_NAMES = {"甲", "乙", "丙"};

   private UUID target;
   private final Map<UUID, DrawSnapshot> shots = new HashMap<>();
   private final List<UUID> lineup = new ArrayList<>();
   private int answerIndex;
   private final Map<UUID, Integer> votes = new HashMap<>();
   private final Map<UUID, Integer> viewing = new HashMap<>();

   @Override
   public RoundType type() {
      return RoundType.DRAW_LIE;
   }

   @Override
   public String rules() {
      return "全员随便画，没有题目。通话结束后从三幅画里认出指定玩家的那一幅。认对 +3。";
   }

   @Override
   public int actionSeconds(FraudMasterMatch match) {
      return 40;
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.shots.clear();
      this.lineup.clear();
      this.votes.clear();
      this.viewing.clear();
      this.answerIndex = 0;
      List<UUID> alive = match.alive();
      this.target = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
      match.broadcast("&e全员在小屋南墙随便画。稍后要从三幅画里认出 "
         + match.label(this.target) + " &e的作品。打电话打听、撒谎都可以。");
      ServerLevel level = match.ctx().plots().level();
      for (UUID uuid : alive) {
         Plot plot = match.plot(uuid);
         Canvas canvas = match.canvas(uuid);
         if (plot != null && canvas != null && level != null) {
            canvas.install(level);
            canvas.clearPaint(level);
         }
         ServerPlayer player = match.player(uuid);
         if (player != null && plot != null && level != null) {
            BoothHut.teleportCanvas(player, level, plot);
         }
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      if (player.equals(this.target)) {
         return List.of("系统要别人认出你的画。可以打电话乱报自己画了什么。");
      }
      return List.of("打电话打听 " + match.label(this.target) + " 画了什么，也可以骗人。");
   }

   @Override
   public boolean canPaint(FraudMasterMatch match, UUID player) {
      return match.phase() == FraudMasterMatch.Phase.CALL && match.alive(player);
   }

   @Override
   public Canvas canvas(FraudMasterMatch match, UUID player) {
      return match.canvas(player);
   }

   @Override
   public void onActionStart(FraudMasterMatch match) {
      for (UUID uuid : match.alive()) {
         DrawKit.clear(uuid);
      }
      this.capture(match);
      this.pickLineup(match);
      match.refreshKits();
      match.broadcast("&e打开操作界面，从三幅画里选出 "
         + match.label(this.target) + " &e的那一幅。认对 +3。");
      for (UUID uuid : match.alive()) {
         this.showSlot(match, uuid, 0);
      }
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      UUID uuid = player.getUUID();
      container.setItem(4, GuiItems.named("name_tag",
         "&f找出 " + match.label(this.target) + " &f的画",
         List.of("&7点画作投影到南墙，再认定")));
      if (uuid.equals(this.target)) {
         container.setItem(22, GuiItems.named("brush", "&7这是在找你的画",
            List.of("&7你不用投票")));
         return;
      }
      if (this.votes.containsKey(uuid)) {
         int slot = this.votes.get(uuid);
         container.setItem(22, GuiItems.named("lime_dye", "&a已选画作" + nameOf(slot),
            List.of("&7等待结算")));
         return;
      }
      int looking = this.viewing.getOrDefault(uuid, 0);
      int[] slots = {20, 22, 24};
      for (int i = 0; i < this.lineup.size() && i < slots.length; i++) {
         List<String> lore = new ArrayList<>();
         lore.add(looking == i ? "&a正在墙上显示" : "&e点击投影到南墙");
         lore.add("&7看完再点下方认定");
         container.setItem(slots[i], GuiItems.action(
            looking == i ? "painting" : "item_frame",
            "&f画作" + nameOf(i),
            lore,
            "view",
            "value",
            Integer.toString(i)
         ));
      }
      if (!this.lineup.isEmpty()) {
         container.setItem(40, GuiItems.action("lime_concrete",
            "&a认定画作" + nameOf(looking),
            List.of("&7这是 " + match.label(this.target) + " &7画的"),
            "vote",
            "value",
            Integer.toString(looking)));
      }
   }

   @Override
   public boolean handleAction(FraudMasterMatch match, ServerPlayer player, String action, String extra) {
      UUID uuid = player.getUUID();
      if (uuid.equals(this.target)) {
         return true;
      }
      int slot = parseSlot(extra);
      if (slot < 0 || slot >= this.lineup.size()) {
         return true;
      }
      if ("view".equals(action)) {
         this.showSlot(match, uuid, slot);
         match.send(player, "&7墙上现在是画作" + nameOf(slot) + "。");
         return true;
      }
      if ("vote".equals(action)) {
         if (this.votes.containsKey(uuid)) {
            return true;
         }
         this.votes.put(uuid, slot);
         this.showSlot(match, uuid, slot);
         match.send(player, "&a你认定画作" + nameOf(slot) + " 是 "
            + match.label(this.target) + " &a的。");
         return true;
      }
      return false;
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      match.broadcast("&a正确答案：画作" + nameOf(this.answerIndex)
         + " &7是 " + match.label(this.target) + " &7画的。");
      for (int i = 0; i < this.lineup.size(); i++) {
         match.broadcast("&7画作" + nameOf(i) + " ← " + match.label(this.lineup.get(i)));
      }
      for (UUID uuid : match.alive()) {
         if (uuid.equals(this.target)) {
            continue;
         }
         Integer vote = this.votes.get(uuid);
         boolean correct = vote != null && vote == this.answerIndex;
         match.broadcast(match.label(uuid) + " &7选了 "
            + (vote == null ? "&8弃权" : "画作" + nameOf(vote))
            + (correct ? " &a✓" : " &c✗"));
         if (correct) {
            match.addScore(uuid, 3);
         }
      }
      ServerLevel level = match.ctx().plots().level();
      if (level != null) {
         for (UUID uuid : match.alive()) {
            Canvas canvas = match.canvas(uuid);
            if (canvas != null) {
               canvas.clearPaint(level);
            }
         }
      }
   }

   @Override
   public List<String> boardExtra(FraudMasterMatch match, UUID player) {
      return List.of("&e认出 " + match.coloredName(this.target) + " &e的画");
   }

   @Override
   public String actionBar(FraudMasterMatch match, UUID player) {
      if (match.phase() == FraudMasterMatch.Phase.ACTION) {
         if (player.equals(this.target)) {
            return "&7等待别人认你的画";
         }
         if (this.votes.containsKey(player)) {
            return "&a已选画作" + nameOf(this.votes.get(player));
         }
         return "&e三幅里认出 " + match.coloredName(this.target);
      }
      return "&7随便画  &8·  &f稍后认 " + match.coloredName(this.target);
   }

   @Override
   public void onLeave(FraudMasterMatch match, UUID player) {
      this.votes.remove(player);
      this.viewing.remove(player);
   }

   private void capture(FraudMasterMatch match) {
      this.shots.clear();
      ServerLevel level = match.ctx().plots().level();
      if (level == null) {
         return;
      }
      for (UUID uuid : match.alive()) {
         Canvas canvas = match.canvas(uuid);
         if (canvas != null) {
            this.shots.put(uuid, canvas.capture(level));
         }
      }
   }

   private void pickLineup(FraudMasterMatch match) {
      this.lineup.clear();
      List<UUID> others = new ArrayList<>();
      for (UUID uuid : match.alive()) {
         if (!uuid.equals(this.target)) {
            others.add(uuid);
         }
      }
      others.sort(Comparator.comparingInt(this::strokeOf).reversed());
      this.lineup.add(this.target);
      for (UUID uuid : others) {
         if (this.lineup.size() >= 3) {
            break;
         }
         this.lineup.add(uuid);
      }
      Collections.shuffle(this.lineup, ThreadLocalRandom.current());
      this.answerIndex = Math.max(0, this.lineup.indexOf(this.target));
   }

   private int strokeOf(UUID uuid) {
      DrawSnapshot shot = this.shots.get(uuid);
      return shot == null ? 0 : shot.strokes().size();
   }

   private void showSlot(FraudMasterMatch match, UUID viewer, int slot) {
      if (slot < 0 || slot >= this.lineup.size()) {
         return;
      }
      this.viewing.put(viewer, slot);
      ServerLevel level = match.ctx().plots().level();
      Canvas canvas = match.canvas(viewer);
      DrawSnapshot shot = this.shots.get(this.lineup.get(slot));
      if (level != null && canvas != null) {
         canvas.restore(level, shot);
      }
   }

   private static int parseSlot(String extra) {
      try {
         return Integer.parseInt(extra);
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private static String nameOf(int slot) {
      if (slot >= 0 && slot < SLOT_NAMES.length) {
         return SLOT_NAMES[slot];
      }
      return String.valueOf(slot + 1);
   }
}
