package net.exmo.sreGame.fraud.round;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.draw.Canvas;
import net.exmo.sreGame.draw.DrawKit;
import net.exmo.sreGame.draw.DrawSnapshot;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class DrawLieRound implements RoundHandler {
   private static final List<String> FALLBACK = List.of(
      "苦力怕", "钻石剑", "末影龙", "村民", "红石", "活塞", "鞘翅", "凋灵",
      "金苹果", "潜影盒", "火把", "小麦", "僵尸", "骷髅", "末影人", "工作台"
   );
   private UUID painter;
   private String word = "苦力怕";
   private final Map<UUID, String> guesses = new HashMap<>();
   private int lastStrokes = -1;
   private boolean paintWindow = true;

   @Override
   public RoundType type() {
      return RoundType.DRAW_LIE;
   }

   @Override
   public String rules() {
      return "一名画手拿到私密词。前 60 秒在小屋画板上作画并可撒谎描述；后 60 秒可打电话追问。猜中 +3；无人猜中则画手 +2。";
   }

   @Override
   public void onPrepare(FraudMasterMatch match) {
      this.guesses.clear();
      this.lastStrokes = -1;
      this.paintWindow = true;
      List<UUID> alive = match.alive();
      this.painter = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
      List<String> words = match.room().resolvedWords(match.ctx());
      if (words == null || words.isEmpty()) {
         words = FALLBACK;
      }
      this.word = words.get(ThreadLocalRandom.current().nextInt(words.size()));
      match.broadcast("&e画手是 " + match.label(this.painter) + " &7。看小屋南墙画板；其他人会看到镜像。");
      for (UUID uuid : alive) {
         Plot plot = match.plot(uuid);
         ServerLevel level = match.ctx().plots().level();
         Canvas canvas = match.canvas(uuid);
         if (plot != null && canvas != null && level != null) {
            canvas.install(level);
         }
         ServerPlayer player = match.player(uuid);
         if (player != null && plot != null && level != null) {
            net.exmo.sreGame.fraud.BoothHut.teleportCanvas(player, level, plot);
         }
      }
   }

   @Override
   public List<String> privateInfo(FraudMasterMatch match, UUID player) {
      if (player.equals(this.painter)) {
         return List.of("你是画手，词语是「" + this.word + "」。可以撒谎。");
      }
      return List.of("看南墙画板，打电话问画手。操作阶段聊天提交猜测。");
   }

   @Override
   public void onCallTick(FraudMasterMatch match) {
      boolean painting = match.ticksLeft() > 60 * 20;
      if (this.paintWindow && !painting) {
         this.paintWindow = false;
         DrawKit.clear(this.painter);
         match.refreshKits();
         match.broadcast("&e作画结束。继续打电话提问。");
      }
      this.mirror(match);
   }

   @Override
   public boolean canPaint(FraudMasterMatch match, UUID player) {
      return this.paintWindow && player.equals(this.painter) && match.phase() == FraudMasterMatch.Phase.CALL;
   }

   @Override
   public Canvas canvas(FraudMasterMatch match, UUID player) {
      return player.equals(this.painter) ? match.canvas(this.painter) : null;
   }

   @Override
   public void onActionStart(FraudMasterMatch match) {
      this.paintWindow = false;
      DrawKit.clear(this.painter);
      match.refreshKits();
      for (UUID uuid : match.alive()) {
         if (!uuid.equals(this.painter)) {
            match.send(uuid, "&e在聊天中输入你的猜测。");
         }
      }
   }

   @Override
   public boolean handleChat(FraudMasterMatch match, ServerPlayer player, String message) {
      if (player.getUUID().equals(this.painter)) {
         match.send(player, "&c画手不用猜。");
         return true;
      }
      if (this.guesses.containsKey(player.getUUID())) {
         match.send(player, "&7已经提交过了。");
         return true;
      }
      String guess = normalize(message);
      if (guess.isEmpty()) {
         return true;
      }
      this.guesses.put(player.getUUID(), guess);
      match.send(player, "&a已提交猜测： &f" + message.trim());
      return true;
   }

   @Override
   public void fillActionGui(FraudMasterMatch match, ServerPlayer player, SimpleContainer container) {
      if (player.getUUID().equals(this.painter)) {
         container.setItem(22, GuiItems.named("brush", "&7你是画手", List.of("&7等待别人猜")));
         return;
      }
      String guess = this.guesses.get(player.getUUID());
      container.setItem(22, GuiItems.named("writable_book",
         guess == null ? "&e聊天输入猜测" : "&a已提交 &f" + guess,
         List.of("&7关闭界面后直接打字")));
   }

   @Override
   public void onSettle(FraudMasterMatch match) {
      match.broadcast("&6词语是 &e" + this.word);
      String want = normalize(this.word);
      int hits = 0;
      for (UUID uuid : match.alive()) {
         if (uuid.equals(this.painter)) {
            continue;
         }
         String guess = this.guesses.get(uuid);
         boolean correct = guess != null && guess.equals(want);
         match.broadcast(match.label(uuid) + " &7猜了 &f" + (guess == null ? "弃权" : guess)
            + (correct ? " &a✓" : " &c✗"));
         if (correct) {
            match.addScore(uuid, 3);
            hits++;
         }
      }
      if (hits == 0) {
         match.addScore(this.painter, 2);
         match.broadcast(match.label(this.painter) + " &6误导成功 +2");
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
      return List.of(player.equals(this.painter) ? "&a你是画手" : "&7画手 " + match.coloredName(this.painter));
   }

   @Override
   public void onLeave(FraudMasterMatch match, UUID player) {
      if (player.equals(this.painter)) {
         this.paintWindow = false;
      }
   }

   private void mirror(FraudMasterMatch match) {
      if (this.painter == null) {
         return;
      }
      Canvas source = match.canvas(this.painter);
      ServerLevel level = match.ctx().plots().level();
      if (source == null || level == null) {
         return;
      }
      int strokes = source.count(level);
      if (strokes == this.lastStrokes) {
         return;
      }
      this.lastStrokes = strokes;
      DrawSnapshot snapshot = source.capture(level);
      for (UUID uuid : match.alive()) {
         if (uuid.equals(this.painter)) {
            continue;
         }
         Canvas dest = match.canvas(uuid);
         if (dest != null) {
            dest.restore(level, snapshot);
         }
      }
   }

   private static String normalize(String raw) {
      if (raw == null) {
         return "";
      }
      return raw.trim().replace(" ", "").toLowerCase(Locale.ROOT);
   }
}
