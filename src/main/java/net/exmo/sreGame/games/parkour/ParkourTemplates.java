package net.exmo.sreGame.games.parkour;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ParkourTemplates {
   private static final List<ParkourTemplate> ALL = List.of(
      ParkourTemplate.of("steps_up", "上阶", 0.25,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(3, 1, 0),
         ParkourTemplate.c(4, 2, 0),
         ParkourTemplate.c(6, 2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("steps_down", "下阶", 0.25,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(3, -1, 0),
         ParkourTemplate.c(4, -2, 0),
         ParkourTemplate.c(6, -2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("stride", "大步", 0.25,
         ParkourTemplate.c(3, 0, 0),
         ParkourTemplate.c(6, 0, 0),
         ParkourTemplate.c(8, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("ice_run", "滑冰", 0.25,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(3, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(4, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("fence_hop", "栅栏", 0.25,
         ParkourTemplate.c(2, -1, 0, ParkourTemplate.Kind.FENCE),
         ParkourTemplate.c(4, 0, 0),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("slab_line", "半砖道", 0.25,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.SLAB),
         ParkourTemplate.c(4, 0, 0, ParkourTemplate.Kind.SLAB),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("zigzag", "之字", 0.5,
         ParkourTemplate.c(2, 0, 1),
         ParkourTemplate.c(4, 0, -1),
         ParkourTemplate.c(6, 0, 1),
         ParkourTemplate.c(8, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("pane_tight", "玻璃板", 0.5,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.PANE),
         ParkourTemplate.c(4, 0, 0, ParkourTemplate.Kind.PANE),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("slime_up", "粘液起跳", 0.5,
         ParkourTemplate.c(2, -1, 0, ParkourTemplate.Kind.SLIME),
         ParkourTemplate.c(4, 2, 0),
         ParkourTemplate.c(6, 2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("pillar_hop", "柱跳", 0.5,
         ParkourTemplate.c(2, 1, 0),
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(4, 1, 0),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("drop_gap", "落差空", 0.5,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(5, -2, 0),
         ParkourTemplate.c(7, -2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("side_shift", "侧移", 0.5,
         ParkourTemplate.c(2, 0, 2),
         ParkourTemplate.c(4, 0, -2),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("ladder_wall", "爬梯", 0.5,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(2, 1, 0),
         ParkourTemplate.c(2, 2, 0),
         ParkourTemplate.c(2, 0, 1, ParkourTemplate.Kind.LADDER),
         ParkourTemplate.c(2, 1, 1, ParkourTemplate.Kind.LADDER),
         ParkourTemplate.c(2, 2, 1, ParkourTemplate.Kind.LADDER),
         ParkourTemplate.c(4, 3, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("three_neo", "三连细跳", 0.75,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(3, 0, 0),
         ParkourTemplate.c(4, 0, 0),
         ParkourTemplate.c(7, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("steep_stair", "陡阶", 0.75,
         ParkourTemplate.c(2, 1, 0),
         ParkourTemplate.c(3, 2, 0),
         ParkourTemplate.c(4, 3, 0),
         ParkourTemplate.c(6, 3, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("ice_gap", "冰后空翻", 0.75,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(3, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(7, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("pane_climb", "板桥爬升", 0.75,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.PANE),
         ParkourTemplate.c(3, 1, 0, ParkourTemplate.Kind.PANE),
         ParkourTemplate.c(5, 1, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("wide_zag", "大之字", 0.75,
         ParkourTemplate.c(2, 0, 3),
         ParkourTemplate.c(4, 1, -3),
         ParkourTemplate.c(7, 0, 2, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("four_gap", "四格空", 1.0,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(7, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("wave", "波浪", 1.0,
         ParkourTemplate.c(2, 1, 0),
         ParkourTemplate.c(4, -1, 0),
         ParkourTemplate.c(6, 1, 0),
         ParkourTemplate.c(8, -1, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("skinny_up", "细柱上升", 1.0,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.PANE),
         ParkourTemplate.c(3, 1, 0, ParkourTemplate.Kind.FENCE),
         ParkourTemplate.c(5, 2, 0),
         ParkourTemplate.c(7, 2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("slime_gap", "粘液跨空", 1.0,
         ParkourTemplate.c(2, -1, 0, ParkourTemplate.Kind.SLIME),
         ParkourTemplate.c(6, 1, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("two_wide", "双宽道", 0.25,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(2, 0, 1),
         ParkourTemplate.c(4, 0, 0),
         ParkourTemplate.c(4, 0, 1),
         ParkourTemplate.c(6, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("stair_side", "侧阶", 0.5,
         ParkourTemplate.c(2, 0, 1),
         ParkourTemplate.c(3, 1, 1),
         ParkourTemplate.c(4, 1, -1),
         ParkourTemplate.c(6, 1, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("ice_zag", "冰之字", 0.5,
         ParkourTemplate.c(2, 0, 0, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(3, 0, 2, ParkourTemplate.Kind.ICE),
         ParkourTemplate.c(5, 0, -1),
         ParkourTemplate.c(7, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("fence_gap", "栅栏空", 0.75,
         ParkourTemplate.c(2, -1, 0, ParkourTemplate.Kind.FENCE),
         ParkourTemplate.c(5, 0, 0),
         ParkourTemplate.c(7, 0, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("twin_slime", "双粘液", 0.75,
         ParkourTemplate.c(2, -1, 0, ParkourTemplate.Kind.SLIME),
         ParkourTemplate.c(4, -1, 0, ParkourTemplate.Kind.SLIME),
         ParkourTemplate.c(7, 2, 0, ParkourTemplate.Kind.END)),
      ParkourTemplate.of("drop_catch", "落差接", 1.0,
         ParkourTemplate.c(2, 0, 0),
         ParkourTemplate.c(5, -3, 0),
         ParkourTemplate.c(7, -2, 0, ParkourTemplate.Kind.END))
   );

   private ParkourTemplates() {
   }

   public static List<ParkourTemplate> all() {
      return ALL;
   }

   public static ParkourTemplate pick(double maxDifficulty) {
      List<ParkourTemplate> pool = new ArrayList<>();
      for (ParkourTemplate template : ALL) {
         if (template.difficulty() <= maxDifficulty + 1e-6) {
            pool.add(template);
         }
      }
      if (pool.isEmpty()) {
         return null;
      }
      return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
   }

   public static boolean roll() {
      return ThreadLocalRandom.current().nextInt(100) < 8;
   }

   public static String difficultyLabel(double value) {
      if (value <= 0) {
         return "关";
      }
      if (value <= 0.25) {
         return "简单";
      }
      if (value <= 0.5) {
         return "中等";
      }
      if (value <= 0.75) {
         return "困难";
      }
      return "极难";
   }

   public static double nextDifficulty(double current) {
      if (current <= 0) {
         return 0.25;
      }
      if (current <= 0.25) {
         return 0.5;
      }
      if (current <= 0.5) {
         return 0.75;
      }
      if (current <= 0.75) {
         return 1.0;
      }
      return 0.0;
   }
}
