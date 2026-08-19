package net.exmo.sreGame.draw;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.BuildWarMatch;
import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.youguess.YouGuessMatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DrawKit {
   public enum Brush {
      S(0.12f, "feather", "细笔刷"),
      M(0.28f, "stick", "中笔刷"),
      L(0.58f, "blaze_rod", "粗笔刷"),
      XL(1.25f, "end_rod", "特大笔刷");

      private final float size;
      private final String item;
      private final String name;

      Brush(float size, String item, String name) {
         this.size = size;
         this.item = item;
         this.name = name;
      }

      public float size() {
         return this.size;
      }
   }

   public static final class State {
      public DyeColor color = DyeColor.BLACK;
      public DyeColor background = DyeColor.WHITE;
      public Brush brush = Brush.M;
   }

   private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
   private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
   private static final Map<UUID, Vec3> LAST_POINT = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> LAST_STROKE_TICK = new ConcurrentHashMap<>();
   /** Vanilla RMB while holding repeats about every 4 ticks; a tap-release-tap is longer. */
   private static final int STROKE_BREAK_TICKS = 5;

   private DrawKit() {
   }

   public static State state(UUID uuid) {
      return STATES.computeIfAbsent(uuid, id -> new State());
   }

   public static void clear(UUID uuid) {
      STATES.remove(uuid);
      LAST_TICK.remove(uuid);
      LAST_POINT.remove(uuid);
      LAST_STROKE_TICK.remove(uuid);
   }

   private static boolean thisTick(ServerPlayer player) {
      long tick = player.level().getGameTime();
      Long last = LAST_TICK.put(player.getUUID(), tick);
      return last != null && last == tick;
   }

   public static boolean isTool(ItemStack stack) {
      String action = GuiItems.actionTag(stack);
      return action != null && action.startsWith("draw_");
   }

   public static void give(ServerPlayer player) {
      State state = state(player.getUUID());
      state.background = DyeColor.WHITE;
      player.getInventory().clearContent();
      int slot = 0;
      for (Brush brush : Brush.values()) {
         player.getInventory().setItem(slot++, GuiItems.action(brush.item, "&b" + brush.name,
            List.of("&7大小 &f" + String.format("%.2f", brush.size), "&e对准画布按住右键连续绘画", "&7松开或换物品停止"),
            "draw_brush", "size", brush.name()));
      }
      player.getInventory().setItem(4, GuiItems.action("sponge", "&f橡皮擦",
         List.of("&7按当前笔刷大小擦除展示方块", "&e对准画布按住右键连续擦除"), "draw_erase"));
      player.getInventory().setItem(5, GuiItems.action("item_frame", "&e调色板",
         List.of("&7左键选笔刷色，右键选背景色", "&e打开 16 色"), "draw_palette"));
      player.getInventory().setItem(6, colorStack(state.color));
      player.getInventory().setItem(7, backgroundStack(state.background));
      player.getInventory().setItem(8, GuiItems.action("cauldron", "&c清空画布",
         List.of("&7删除本画布上的全部笔触", "&7背景色保持不变", "&c右键清空"), "draw_clear"));
      player.getInventory().selected = 1;
      startFlying(player);
   }

   public static void tick(GameContext ctx) {
      if (ctx == null || ctx.server() == null) {
         return;
      }
      for (UUID uuid : List.copyOf(LAST_POINT.keySet())) {
         ServerPlayer player = ctx.player(uuid);
         long now = player != null ? player.level().getGameTime() : ctx.server().overworld().getGameTime();
         Long last = LAST_STROKE_TICK.get(uuid);
         boolean stale = last == null || now - last > STROKE_BREAK_TICKS;
         if (player == null || !holdingTool(player) || stale) {
            breakStroke(uuid);
         }
      }
   }

   private static void breakStroke(UUID uuid) {
      LAST_POINT.remove(uuid);
      LAST_STROKE_TICK.remove(uuid);
   }

   private static boolean holdingTool(ServerPlayer player) {
      String action = GuiItems.actionTag(player.getMainHandItem());
      return "draw_brush".equals(action) || "draw_erase".equals(action);
   }

   private static void applyStroke(ServerLevel level, Canvas canvas, ServerPlayer player, Vec3 point, boolean erase) {
      UUID uuid = player.getUUID();
      State state = state(uuid);
      long now = player.level().getGameTime();
      Long lastTick = LAST_STROKE_TICK.put(uuid, now);
      Vec3 last = LAST_POINT.put(uuid, point);
      boolean connected = last != null && lastTick != null && now - lastTick <= STROKE_BREAK_TICKS;
      if (!connected) {
         last = null;
      }
      if (erase) {
         if (last != null) {
            double dist = last.distanceTo(point);
            double step = Math.max(0.08, state.brush.size() * 0.28);
            int n = Math.min(36, Math.max(1, (int) Math.ceil(dist / step)));
            for (int i = 1; i <= n; i++) {
               canvas.erase(level, last.lerp(point, i / (double) n), state.brush.size());
            }
         } else {
            canvas.erase(level, point, state.brush.size());
         }
         return;
      }
      if (canvas.count(level) >= Canvas.MAX_STROKES) {
         return;
      }
      canvas.stroke(level, last, point, state.color, state.brush.size());
   }

   public static void startFlying(ServerPlayer player) {
      Abilities abilities = player.getAbilities();
      abilities.mayfly = true;
      abilities.flying = true;
      player.onUpdateAbilities();
   }

   public static void allowFlight(ServerPlayer player) {
      Abilities abilities = player.getAbilities();
      if (abilities.mayfly) {
         return;
      }
      abilities.mayfly = true;
      player.onUpdateAbilities();
   }

   public static void applyBackground(GameContext ctx, ServerPlayer player, DyeColor color) {
      DyeColor safe = color == null ? DyeColor.WHITE : color;
      state(player.getUUID()).background = safe;
      refreshColor(player);
      Session session = session(ctx, player);
      ServerLevel level = ctx.plots().level();
      if (session != null && session.canPaint() && session.canvas() != null && level != null) {
         session.canvas().setBackground(level, safe);
      }
   }

   public static void refreshColor(ServerPlayer player) {
      State state = state(player.getUUID());
      player.getInventory().setItem(6, colorStack(state.color));
      player.getInventory().setItem(7, backgroundStack(state.background));
   }

   public static ItemStack colorStack(DyeColor color) {
      DyeColor safe = color == null ? DyeColor.BLACK : color;
      return GuiItems.action(safe.getName() + "_concrete", "&f笔刷颜色 &e" + colorName(safe),
         List.of("&7左键调色板选此色绘画", "&e右键打开调色板"), "draw_palette");
   }

   public static ItemStack backgroundStack(DyeColor color) {
      DyeColor safe = color == null ? DyeColor.WHITE : color;
      return GuiItems.action(safe.getName() + "_wool", "&f背景颜色 &e" + colorName(safe),
         List.of("&7调色板里右键颜色可改背景", "&e右键打开调色板"), "draw_palette");
   }

   public static String colorName(DyeColor color) {
      return switch (color) {
         case WHITE -> "白色";
         case LIGHT_GRAY -> "淡灰色";
         case GRAY -> "灰色";
         case BLACK -> "黑色";
         case BROWN -> "棕色";
         case RED -> "红色";
         case ORANGE -> "橙色";
         case YELLOW -> "黄色";
         case LIME -> "黄绿色";
         case GREEN -> "绿色";
         case CYAN -> "青色";
         case LIGHT_BLUE -> "淡蓝色";
         case BLUE -> "蓝色";
         case PURPLE -> "紫色";
         case MAGENTA -> "品红色";
         case PINK -> "粉红色";
      };
   }

   public static boolean tryUse(GameContext ctx, ServerPlayer player, ItemStack stack, BlockHitResult hit) {
      if (!isTool(stack)) {
         return false;
      }
      Session session = session(ctx, player);
      if (session == null) {
         return false;
      }
      String action = GuiItems.actionTag(stack);
      if (!session.canPaint()) {
         ctx.send(player, "&c现在不能绘画。");
         return true;
      }
      ServerLevel level = ctx.plots().level();
      if (level == null || player.serverLevel() != level) {
         return true;
      }
      Canvas canvas = session.canvas();
      State state = state(player.getUUID());
      switch (action) {
         case "draw_palette" -> {
            breakStroke(player.getUUID());
            if (thisTick(player)) {
               return true;
            }
            PaletteGui.open(ctx, player);
         }
         case "draw_clear" -> {
            breakStroke(player.getUUID());
            if (thisTick(player)) {
               return true;
            }
            canvas.clearPaint(level);
            canvas.setBackground(level, state.background);
            ctx.send(player, "&a已清空画布。");
         }
         case "draw_brush" -> {
            Brush brush = parseBrush(GuiItems.extraTag(stack, "size"));
            if (state.brush != brush) {
               state.brush = brush;
               breakStroke(player.getUUID());
            }
            Vec3 point = resolveHit(player, canvas, hit);
            if (point == null) {
               if (hit == null) {
                  if (!canvas.inRange(player.position())) {
                     ctx.send(player, "&c请走到画布 32 格内再画。");
                  }
                  return true;
               }
               breakStroke(player.getUUID());
               return true;
            }
            if (thisTick(player)) {
               return true;
            }
            if (canvas.count(level) >= Canvas.MAX_STROKES) {
               breakStroke(player.getUUID());
               ctx.send(player, "&c笔触过多，换粗笔或先用橡皮擦。");
               return true;
            }
            applyStroke(level, canvas, player, point, false);
         }
         case "draw_erase" -> {
            Vec3 point = resolveHit(player, canvas, hit);
            if (point == null) {
               if (hit == null) {
                  return true;
               }
               breakStroke(player.getUUID());
               return true;
            }
            if (thisTick(player)) {
               return true;
            }
            applyStroke(level, canvas, player, point, true);
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   private static Vec3 resolveHit(ServerPlayer player, Canvas canvas, BlockHitResult hit) {
      if (!canvas.inRange(player.position())) {
         return null;
      }
      if (hit != null && canvas.isCanvasBlock(hit.getBlockPos())) {
         Vec3 loc = hit.getLocation();
         return new Vec3(loc.x, loc.y, canvas.wallZ());
      }
      return canvas.rayHit(player.getEyePosition(1.0f), player.getLookAngle());
   }

   private static Brush parseBrush(String raw) {
      if (raw == null) {
         return Brush.M;
      }
      try {
         return Brush.valueOf(raw);
      } catch (IllegalArgumentException e) {
         return Brush.M;
      }
   }

   public static Session session(GameContext ctx, ServerPlayer player) {
      YouGuessMatch guess = ctx.youGuess().get(player.getUUID());
      if (guess != null && guess.drawing()) {
         return new Session(guess.canPaint(player.getUUID()), guess.canvas());
      }
      net.exmo.sreGame.fraud.FraudMasterMatch fraud = ctx.fraudMaster().get(player.getUUID());
      if (fraud != null) {
         return new Session(fraud.canPaint(player.getUUID()), fraud.paintCanvas(player.getUUID()));
      }
      BuildWarMatch war = ctx.buildWar().get(player.getUUID());
      if (war != null && war.drawing()) {
         Plot plot = war.boundPlot(player.getUUID());
         return new Session(war.canPaint(player.getUUID()), plot == null ? null : Canvas.of(plot));
      }
      return null;
   }

   public record Session(boolean allowed, Canvas canvas) {
      public boolean canPaint() {
         return this.allowed && this.canvas != null;
      }
   }
}
