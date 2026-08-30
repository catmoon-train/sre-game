package net.exmo.sreGame.games.rhythm;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.mixin.BlockDisplayStateInvoker;
import net.exmo.sreGame.mixin.DisplayTransformationInvoker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 单个玩家专属的“音符墙”。用 {@link Display.BlockDisplay} 实体渲染。
 * 支持三种轨道方向（纵向/横向/由远及近），红左蓝右、长按拉伸条、金色特殊音块、
 * 移动音块、乱色（红蓝对调）、粒子雾，以及命中时的方块消散动画。
 */
public final class RhythmWall {

   public static final double LANE_OFFSET = 1.5;
   public static final double WALL_Z_OFFSET = 5.0;
   public static final double JUDGE_Y_OFFSET = 2.0;
   public static final double JUDGE_X_OFFSET = 0.0;
   public static final double JUDGE_Z_OFFSET = 2.0;
   public static final double LANE_V_OFFSET = 1.0;
   public static final float JUDGE_LINE_LENGTH = 7.0f;
   /** 竖直模式音准线向右延展，方便提前观察右侧来块。 */
   public static final float VERTICAL_JUDGE_LINE_LENGTH = 21.0f;

   private final ServerLevel level;
   private final RhythmSettings.Orientation orientation;
   private final double originX;
   private final double originY;
   private final double judgeY;
   private final double judgeX;
   private final double judgeZ;
   private final double wallZ;
   private final double travelBlocks;
   private final boolean pureLeftMode;

   private boolean laneSwap;
   private long animationTick;
   private final List<Display.BlockDisplay> staticEntities = new ArrayList<>();
   private final Map<Integer, NoteEntity> notes = new HashMap<>();
   private final List<Burst> bursts = new ArrayList<>();
   private int nextId = 1;

   private static final class NoteEntity {
      final Display.BlockDisplay entity;
      final RhythmChart.Lane lane;
      final RhythmChart.NoteType type;
      final double phase;

      NoteEntity(Display.BlockDisplay entity, RhythmChart.Lane lane, RhythmChart.NoteType type, double phase) {
         this.entity = entity;
         this.lane = lane;
         this.type = type;
         this.phase = phase;
      }
   }

   private static final class Burst {
      final Display.BlockDisplay entity;
      double vx;
      double vy;
      double vz;
      int life;
      float scale;

      Burst(Display.BlockDisplay entity, double vx, double vy, double vz, int life, float scale) {
         this.entity = entity;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.life = life;
         this.scale = scale;
      }
   }

   public RhythmWall(ServerLevel level, double originX, double originY, double originZ, double travelBlocks,
                     RhythmSettings.Orientation orientation, boolean pureLeftMode) {
      this.level = level;
      this.originX = originX;
      this.originY = originY;
      this.judgeY = originY + JUDGE_Y_OFFSET;
      this.judgeX = originX + JUDGE_X_OFFSET;
      this.judgeZ = originZ + JUDGE_Z_OFFSET;
      this.wallZ = originZ + WALL_Z_OFFSET;
      this.travelBlocks = travelBlocks;
      this.orientation = orientation;
      this.pureLeftMode = pureLeftMode;
      this.spawnStatic();
   }

   /** 判定线在主轴（下落方向）上的坐标。 */
   public double judge() {
      return switch (this.orientation) {
         case HORIZONTAL -> this.judgeX;
         case FRONTAL -> this.judgeZ;
         case VERTICAL -> this.judgeY;
      };
   }

   public double hitX(RhythmChart.Lane lane) {
      return this.orientation == RhythmSettings.Orientation.HORIZONTAL ? this.judgeX : this.laneX(lane);
   }

   public double hitY(RhythmChart.Lane lane) {
      return this.orientation == RhythmSettings.Orientation.HORIZONTAL ? this.laneY(lane) : this.judgeY;
   }

   public double hitZ(RhythmChart.Lane lane) {
      return this.orientation == RhythmSettings.Orientation.FRONTAL ? this.judgeZ : this.wallZ;
   }

   /** 轨道 X：红在 +X（屏幕左），蓝在 -X（屏幕右）。 */
   private double laneX(RhythmChart.Lane lane) {
      return this.originX + (lane == RhythmChart.Lane.RED ? LANE_OFFSET : -LANE_OFFSET);
   }

   private double laneY(RhythmChart.Lane lane) {
      return this.originY + JUDGE_Y_OFFSET + (lane == RhythmChart.Lane.RED ? LANE_V_OFFSET : -LANE_V_OFFSET);
   }

   private void spawnStatic() {
      switch (this.orientation) {
         case VERTICAL -> {
            // 分层背景面板：压低环境噪声，同时保留轨道的颜色和纵深。
            this.spawnBlock(Blocks.BLACK_STAINED_GLASS.defaultBlockState(),
               this.originX, this.judgeY + this.travelBlocks / 2.0, this.wallZ + 0.6,
               VERTICAL_JUDGE_LINE_LENGTH + 1.5f, (float) this.travelBlocks + 6f, 0.25f);
            this.spawnBlock(Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
               this.originX, this.judgeY + this.travelBlocks / 2.0, this.wallZ + 0.45,
               VERTICAL_JUDGE_LINE_LENGTH, (float) this.travelBlocks + 4f, 0.08f);
            // 底部判定线（加长）
            this.spawnBlock(Blocks.YELLOW_CONCRETE.defaultBlockState(),
               this.originX, this.judgeY, this.wallZ, VERTICAL_JUDGE_LINE_LENGTH, 0.14f, 0.14f);
            // 两条垂直轨道（红左蓝右）
            double midY = this.judgeY + this.travelBlocks / 2.0;
            this.spawnBlock(Blocks.RED_STAINED_GLASS.defaultBlockState(),
               this.laneX(RhythmChart.Lane.RED), midY, this.wallZ, 0.55f, (float) this.travelBlocks, 0.3f);
            this.spawnBlock(Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
               this.laneX(RhythmChart.Lane.BLUE), midY, this.wallZ, 0.55f, (float) this.travelBlocks, 0.3f);
         }
         case HORIZONTAL -> {
            this.spawnBlock(Blocks.BLACK_STAINED_GLASS.defaultBlockState(),
               this.judgeX - this.travelBlocks / 2.0, this.originY + JUDGE_Y_OFFSET, this.wallZ + 0.6,
               (float) this.travelBlocks + 6f, JUDGE_LINE_LENGTH, 0.25f);
            this.spawnBlock(Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
               this.judgeX - this.travelBlocks / 2.0, this.originY + JUDGE_Y_OFFSET, this.wallZ + 0.45,
               (float) this.travelBlocks + 4f, JUDGE_LINE_LENGTH - 1.0f, 0.08f);
            this.spawnBlock(Blocks.YELLOW_CONCRETE.defaultBlockState(),
               this.judgeX, this.originY + JUDGE_Y_OFFSET, this.wallZ, 0.14f, 5.2f, 0.14f);
            double midX = this.judgeX - this.travelBlocks / 2.0;
            this.spawnBlock(Blocks.RED_STAINED_GLASS.defaultBlockState(),
               midX, this.laneY(RhythmChart.Lane.RED), this.wallZ, (float) this.travelBlocks, 0.55f, 0.3f);
            this.spawnBlock(Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
               midX, this.laneY(RhythmChart.Lane.BLUE), this.wallZ, (float) this.travelBlocks, 0.55f, 0.3f);
         }
         case FRONTAL -> {
            this.spawnBlock(Blocks.BLACK_STAINED_GLASS.defaultBlockState(),
               this.originX, this.judgeY, this.judgeZ + this.travelBlocks / 2.0,
               JUDGE_LINE_LENGTH + 1.5f, 4.5f, 0.25f);
            this.spawnBlock(Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
               this.originX, this.judgeY, this.judgeZ + this.travelBlocks / 2.0,
               JUDGE_LINE_LENGTH, 3.5f, 0.08f);
            this.spawnBlock(Blocks.YELLOW_CONCRETE.defaultBlockState(),
               this.originX, this.judgeY, this.judgeZ, JUDGE_LINE_LENGTH + 1.0f, 0.14f, 0.14f);
            double midZ = this.judgeZ + this.travelBlocks / 2.0;
            this.spawnBlock(Blocks.RED_STAINED_GLASS.defaultBlockState(),
               this.laneX(RhythmChart.Lane.RED), this.judgeY, midZ, 0.3f, 0.14f, (float) this.travelBlocks);
            this.spawnBlock(Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
               this.laneX(RhythmChart.Lane.BLUE), this.judgeY, midZ, 0.3f, 0.14f, (float) this.travelBlocks);
         }
      }
   }

   private Display.BlockDisplay spawnBlock(BlockState state, double x, double y, double z, float sx, float sy, float sz) {
      Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, this.level);
      ((BlockDisplayStateInvoker) d).sre$setBlockState(state);
      ((DisplayTransformationInvoker) d).sre$setTransformation(this.scale(sx, sy, sz));
      d.setPos(x, y, z);
      this.level.addFreshEntity(d);
      this.staticEntities.add(d);
      return d;
   }

   private Transformation scale(float sx, float sy, float sz) {
      return new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
   }

   public void setLaneSwap(boolean laneSwap) {
      if (this.laneSwap == laneSwap) {
         return;
      }
      this.laneSwap = laneSwap;
      for (NoteEntity ne : this.notes.values()) {
         ((BlockDisplayStateInvoker) ne.entity).sre$setBlockState(this.noteState(ne.lane, ne.type));
      }
   }

   private BlockState noteState(RhythmChart.Lane lane, RhythmChart.NoteType type) {
      if (this.pureLeftMode) {
         if (lane == RhythmChart.Lane.BLUE) {
            return Blocks.GOLD_BLOCK.defaultBlockState();
         }
         return type == RhythmChart.NoteType.MOVING
            ? Blocks.REDSTONE_BLOCK.defaultBlockState()
            : Blocks.RED_CONCRETE.defaultBlockState();
      }
      if (type == RhythmChart.NoteType.GOLD) {
         return Blocks.GOLD_BLOCK.defaultBlockState();
      }
      boolean red = lane == RhythmChart.Lane.RED ? !this.laneSwap : this.laneSwap;
      if (type == RhythmChart.NoteType.ACCENT) {
         return (red ? Blocks.REDSTONE_BLOCK : Blocks.LAPIS_BLOCK).defaultBlockState();
      }
      if (type == RhythmChart.NoteType.MOVING) {
         return (red ? Blocks.REDSTONE_BLOCK : Blocks.LAPIS_BLOCK).defaultBlockState();
      }
      if (type == RhythmChart.NoteType.HOLD) {
         return (red ? Blocks.RED_GLAZED_TERRACOTTA : Blocks.BLUE_GLAZED_TERRACOTTA).defaultBlockState();
      }
      return (red ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE).defaultBlockState();
   }

   /** 生成一个音符显示实体，返回句柄 id。progress 是音符在主轴上的坐标。 */
   public int spawnNote(RhythmChart.Lane lane, RhythmChart.NoteType type, double progress, double holdLength) {
      Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, this.level);
      ((BlockDisplayStateInvoker) d).sre$setBlockState(this.noteState(lane, type));
      double phase = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
      this.applyPosition(d, lane, type, progress, holdLength, phase);
      this.level.addFreshEntity(d);
      int id = this.nextId++;
      this.notes.put(id, new NoteEntity(d, lane, type, phase));
      return id;
   }

   public void moveNote(int id, double progress, double holdLength) {
      NoteEntity ne = this.notes.get(id);
      if (ne == null) {
         return;
      }
      this.applyPosition(ne.entity, ne.lane, ne.type, progress, holdLength, ne.phase);
   }

   private void applyPosition(Display.BlockDisplay d, RhythmChart.Lane lane, RhythmChart.NoteType type,
                              double progress, double holdLength, double phase) {
      boolean hold = type == RhythmChart.NoteType.HOLD;
      double px;
      double py;
      double pz;
      float base = type == RhythmChart.NoteType.ACCENT ? 1.12f : 0.9f;
      float sx = base;
      float sy = base;
      float sz = base;
      switch (this.orientation) {
         case VERTICAL -> {
            px = this.laneX(lane) + this.movingOffset(type, phase);
            py = hold ? progress + holdLength / 2.0 : progress;
            pz = this.wallZ;
            if (hold) {
               sy = (float) Math.max(1.0, holdLength);
            }
         }
         case HORIZONTAL -> {
            px = hold ? progress - holdLength / 2.0 : progress;
            py = this.laneY(lane) + this.movingOffset(type, phase);
            pz = this.wallZ;
            if (hold) {
               sx = (float) Math.max(1.0, holdLength);
            }
         }
         default -> { // FRONTAL
            px = this.laneX(lane) + this.movingOffset(type, phase);
            py = this.judgeY;
            pz = hold ? progress + holdLength / 2.0 : progress;
            if (hold) {
               sz = (float) Math.max(1.0, holdLength);
            }
         }
      }
      ((DisplayTransformationInvoker) d).sre$setTransformation(this.scale(sx, sy, sz));
      d.teleportTo(px, py, pz);
   }

   private double movingOffset(RhythmChart.NoteType type, double phase) {
      if (type != RhythmChart.NoteType.MOVING) {
         return 0.0;
      }
      return Math.sin(this.animationTick * 0.28 + phase) * 0.65;
   }

   public void removeNote(int id) {
      NoteEntity ne = this.notes.remove(id);
      if (ne != null) {
         ne.entity.discard();
      }
   }

   public void despawnAll() {
      for (NoteEntity ne : this.notes.values()) {
         ne.entity.discard();
      }
      this.notes.clear();
      for (Display.BlockDisplay d : this.staticEntities) {
         d.discard();
      }
      this.staticEntities.clear();
      for (Burst b : this.bursts) {
         b.entity.discard();
      }
      this.bursts.clear();
   }

   /** 命中/击打时生成多个同色小方块的消散动画。 */
   public void burstAt(double x, double y, double z, RhythmChart.Lane lane, RhythmChart.NoteType type, int count) {
      BlockState state = this.noteState(lane, type);
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      double spread = type == RhythmChart.NoteType.ACCENT ? 0.62 : 0.5;
      this.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
         x, y, z, Math.min(24, count), 0.28, 0.28, 0.28, 0.08);
      for (int i = 0; i < count; i++) {
         Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, this.level);
         ((BlockDisplayStateInvoker) d).sre$setBlockState(state);
         float s = type == RhythmChart.NoteType.ACCENT ? 0.26f : (type == RhythmChart.NoteType.GOLD ? 0.24f : 0.20f);
         ((DisplayTransformationInvoker) d).sre$setTransformation(this.scale(s, s, s));
         d.setPos(x, y, z);
         this.level.addFreshEntity(d);
         double vx = (rng.nextDouble() - 0.5) * spread;
         double vy = (rng.nextDouble() - 0.2) * spread;
         double vz = (rng.nextDouble() - 0.5) * spread;
         this.bursts.add(new Burst(d, vx, vy, vz, 5 + rng.nextInt(4), s));
      }
   }

   /** 每 tick 推进消散动画。 */
   public void tickEffects() {
      this.animationTick++;
      if (this.bursts.isEmpty()) {
         return;
      }
      Iterator<Burst> it = this.bursts.iterator();
      while (it.hasNext()) {
         Burst b = it.next();
         b.entity.teleportTo(b.entity.getX() + b.vx, b.entity.getY() + b.vy, b.entity.getZ() + b.vz);
         b.vx *= 0.78;
         b.vy = b.vy * 0.78 - 0.02;
         b.vz *= 0.78;
         b.scale *= 0.78f;
         ((DisplayTransformationInvoker) b.entity).sre$setTransformation(this.scale(b.scale, b.scale, b.scale));
         b.life--;
         if (b.life <= 0) {
            b.entity.discard();
            it.remove();
         }
      }
   }

   public void perfectBurst(double x, double y, double z) {
      this.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 16, 0.4, 0.4, 0.4, 0.35);
      this.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.3, 0.3, 0.3, 0.2);
   }

   public void greatBurst(double x, double y, double z) {
      this.level.sendParticles(ParticleTypes.CRIT, x, y, z, 12, 0.35, 0.35, 0.35, 0.25);
   }

   public void missBurst(double x, double y, double z) {
      this.level.sendParticles(ParticleTypes.SMOKE, x, y, z, 16, 0.4, 0.4, 0.4, 0.05);
      this.level.sendParticles(ParticleTypes.ASH, x, y, z, 8, 0.3, 0.3, 0.3, 0.02);
   }

   /** 判定线附近的半透明粒子雾（对战“遮挡”干扰）。 */
   public void spawnFog() {
      for (int i = 0; i < 6; i++) {
         double fx;
         double fy;
         double fz;
         if (this.orientation == RhythmSettings.Orientation.HORIZONTAL) {
            fx = this.judgeX + (Math.random() - 0.5) * 0.4;
            fy = this.judgeY + (Math.random() - 0.5) * 2.0;
            fz = this.wallZ + (Math.random() - 0.5) * 0.4;
         } else if (this.orientation == RhythmSettings.Orientation.FRONTAL) {
            fx = this.originX + (Math.random() - 0.5) * 5.0;
            fy = this.judgeY + (Math.random() - 0.5) * 0.7;
            fz = this.judgeZ + (Math.random() - 0.5) * 0.4;
         } else {
            fx = this.originX + (Math.random() - 0.5) * 5.0;
            fy = this.judgeY + (Math.random() - 0.5) * 1.4;
            fz = this.wallZ + (Math.random() - 0.5) * 0.4;
         }
         this.level.sendParticles(ParticleTypes.CLOUD, fx, fy, fz, 3, 0.4, 0.3, 0.4, 0.0);
      }
   }
}
