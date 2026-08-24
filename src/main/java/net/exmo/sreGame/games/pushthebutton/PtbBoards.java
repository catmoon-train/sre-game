package net.exmo.sreGame.games.pushthebutton;

import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PtbBoards {
   public static final String TAG = "sre_ptb_board";
   public static final String JERR_TAG = "sre_ptb_jerr";

   private PtbBoards() {
   }

   public static void showAnswers(ServerLevel level, Ship ship, PushTheButtonMatch match) {
      clear(level, ship);
      if (level == null || ship == null || match == null) {
         return;
      }
      spawn(level, ship.jerrPos(), 90f, "&6JerrBot\n&7舰载智能", JERR_TAG);
      spawn(level, ship.promptBoardPos(), 0f, "&e人类题面\n&f" + wrap(match.lastHumanPrompt(), 16), TAG);
      int i = 0;
      for (PushTheButtonMatch.Answer answer : match.lastAnswers()) {
         String name = match.ctx().name(answer.player);
         String body = "&b" + name + "\n&f" + wrap(answer.text, 14)
            + (answer.sus > 0 ? "\n&c可疑 ×" + answer.sus : "");
         spawn(level, ship.answerBoardPos(i), 0f, body, TAG);
         i++;
      }
   }

   public static void showJerr(ServerLevel level, Ship ship, String line) {
      if (level == null || ship == null) {
         return;
      }
      AABB box = ship.box().inflate(2);
      for (Entity entity : level.getEntities((Entity) null, box, e -> e.getTags().contains(JERR_TAG))) {
         entity.discard();
      }
      spawn(level, ship.jerrPos(), 90f, "&6JerrBot\n&f" + wrap(strip(line), 18), JERR_TAG);
   }

   public static void clear(ServerLevel level, Ship ship) {
      if (level == null || ship == null) {
         return;
      }
      AABB box = ship.box().inflate(2);
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player)
         && (e.getTags().contains(TAG) || e.getTags().contains(JERR_TAG)))) {
         entity.discard();
      }
   }

   private static void spawn(ServerLevel level, Vec3 pos, float yaw, String text, String tag) {
      Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
      if (display == null || pos == null) {
         return;
      }
      display.setPos(pos.x, pos.y, pos.z);
      display.setYRot(yaw);
      display.setText(TextUtil.color(text));
      display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
      display.setBackgroundColor(0xC8000000);
      display.setLineWidth(140);
      display.setBrightnessOverride(new Brightness(15, 15));
      display.setViewRange(1.2F);
      display.addTag(tag);
      level.addFreshEntity(display);
   }

   private static String strip(String line) {
      if (line == null) {
         return "";
      }
      return line.replace("&6<JerrBot> ", "").replaceAll("&[0-9a-fk-or]", "");
   }

   private static String wrap(String text, int width) {
      if (text == null || text.isEmpty() || width < 4) {
         return text == null ? "" : text;
      }
      StringBuilder out = new StringBuilder();
      int col = 0;
      for (int i = 0; i < text.length(); ) {
         int cp = text.codePointAt(i);
         int n = Character.charCount(cp);
         if (cp == '\n') {
            out.append('\n');
            col = 0;
            i += n;
            continue;
         }
         if (col >= width) {
            out.append('\n');
            col = 0;
         }
         out.appendCodePoint(cp);
         col += n > 1 || cp > 127 ? 2 : 1;
         i += n;
      }
      return out.toString();
   }
}
