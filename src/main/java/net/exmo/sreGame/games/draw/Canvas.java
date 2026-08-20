package net.exmo.sreGame.games.draw;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.games.buildwar.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Canvas {
   public static final double REACH = 32.0;
   public static final int MAX_STROKES = 2800;
   public static final String TAG = "sre_paint";
   public static final String SIZE_PREFIX = "sre_sz_";
   public static final String PLOT_PREFIX = "sre_pl_";

   private final Plot plot;
   private final int minX;
   private final int maxX;
   private final int minY;
   private final int maxY;
   private final int wallZ;

   private Canvas(Plot plot, int minX, int maxX, int minY, int maxY, int wallZ) {
      this.plot = plot;
      this.minX = minX;
      this.maxX = maxX;
      this.minY = minY;
      this.maxY = maxY;
      this.wallZ = wallZ;
   }

   public static Canvas of(Plot plot) {
      return new Canvas(
         plot,
         plot.origin().getX() + 1,
         plot.origin().getX() + plot.size() - 2,
         plot.origin().getY() + 2,
         plot.origin().getY() + plot.height() - 2,
         plot.origin().getZ() + plot.size() - 1
      );
   }

   public static Canvas of(Plot plot, int minX, int maxX, int minY, int maxY, int wallZ) {
      return new Canvas(plot, minX, maxX, minY, maxY, wallZ);
   }

   public Plot plot() {
      return this.plot;
   }

   public int wallZ() {
      return this.wallZ;
   }

   public boolean isCanvasBlock(BlockPos pos) {
      return pos != null
         && pos.getZ() == this.wallZ
         && pos.getX() >= this.minX && pos.getX() <= this.maxX
         && pos.getY() >= this.minY && pos.getY() <= this.maxY;
   }

   public boolean inRange(Vec3 pos) {
      if (pos == null) {
         return false;
      }
      double x = Mth.clamp(pos.x, this.minX, this.maxX + 1.0);
      double y = Mth.clamp(pos.y, this.minY, this.maxY + 1.0);
      return pos.distanceToSqr(x, y, this.wallZ) <= REACH * REACH;
   }

   public Vec3 rayHit(Vec3 eye, Vec3 look) {
      if (eye == null || look == null || Math.abs(look.z) < 1.0E-4) {
         return null;
      }
      double t = (this.wallZ - eye.z) / look.z;
      if (t < 0.08 || t > REACH) {
         return null;
      }
      Vec3 hit = eye.add(look.scale(t));
      if (hit.x < this.minX + 0.04 || hit.x > this.maxX + 0.96
         || hit.y < this.minY + 0.04 || hit.y > this.maxY + 0.96) {
         return null;
      }
      if (eye.distanceToSqr(hit) > REACH * REACH) {
         return null;
      }
      return hit;
   }

   public void install(ServerLevel level) {
      this.setBackground(level, DyeColor.WHITE);
   }

   public void setBackground(ServerLevel level, DyeColor color) {
      if (level == null) {
         return;
      }
      BlockState state = concrete(color).defaultBlockState();
      for (int x = this.minX; x <= this.maxX; x++) {
         for (int y = this.minY; y <= this.maxY; y++) {
            level.setBlock(new BlockPos(x, y, this.wallZ), state, 2);
         }
      }
   }

   public DyeColor backgroundOf(ServerLevel level) {
      if (level == null) {
         return DyeColor.WHITE;
      }
      BlockState state = level.getBlockState(new BlockPos(this.minX, this.minY, this.wallZ));
      DyeColor color = colorOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
      return color == DyeColor.BLACK && state.getBlock() != Blocks.BLACK_CONCRETE ? DyeColor.WHITE : color;
   }

   public int count(ServerLevel level) {
      return this.paints(level).size();
   }

   public void stroke(ServerLevel level, Vec3 from, Vec3 to, DyeColor color, float size) {
      if (to == null) {
         return;
      }
      if (from == null || from.distanceToSqr(to) < 1.0E-6) {
         this.stamp(level, to, color, size);
         return;
      }
      if (from.distanceToSqr(to) > 25.0) {
         this.stamp(level, to, color, size);
         return;
      }
      double dist = from.distanceTo(to);
      double step = Math.max(0.05, size * 0.22);
      int n = Math.min(48, Math.max(1, (int) Math.ceil(dist / step)));
      for (int i = 1; i <= n; i++) {
         double t = i / (double) n;
         this.stamp(level, from.lerp(to, t), color, size);
      }
   }

   public boolean stamp(ServerLevel level, Vec3 hit, DyeColor color, float size) {
      if (level == null || hit == null || color == null) {
         return false;
      }
      float scale = Math.max(0.08f, size);
      double step = Math.max(0.06, scale * 0.42);
      double x = Math.round(hit.x / step) * step;
      double y = Math.round(hit.y / step) * step;
      double z = this.wallZ - 0.04;
      BlockState state = concrete(color).defaultBlockState();
      for (Display.BlockDisplay existing : this.paints(level)) {
         if (existing.distanceToSqr(x, y, z) < 0.004 && existing.getBlockState() == state) {
            return false;
         }
      }
      if (this.count(level) >= MAX_STROKES) {
         return false;
      }
      Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
      if (display == null) {
         return false;
      }
      display.setPos(x, y, z);
      display.setBlockState(state);
      display.setTransformation(new Transformation(
         new Vector3f(-scale / 2.0f, -scale / 2.0f, 0.0f),
         new Quaternionf(),
         new Vector3f(scale, scale, 0.07f),
         new Quaternionf()
      ));
      display.setTransformationInterpolationDuration(0);
      display.setTransformationInterpolationDelay(0);
      display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
      display.setBrightnessOverride(new Brightness(15, 15));
      display.setViewRange(4.0f);
      display.setShadowRadius(0.0f);
      display.setShadowStrength(0.0f);
      display.setNoGravity(true);
      display.setInvulnerable(true);
      display.setSilent(true);
      display.addTag(TAG);
      display.addTag(PLOT_PREFIX + this.plot.slot());
      display.addTag(SIZE_PREFIX + Math.max(1, Math.round(scale * 100.0f)));
      level.addFreshEntity(display);
      return true;
   }

   public int erase(ServerLevel level, Vec3 hit, float size) {
      if (level == null || hit == null) {
         return 0;
      }
      double radius = Math.max(0.12, size * 1.35);
      double r2 = radius * radius;
      int removed = 0;
      for (Display.BlockDisplay display : this.paints(level)) {
         if (display.distanceToSqr(hit.x, hit.y, this.wallZ - 0.04) <= r2) {
            display.discard();
            removed++;
         }
      }
      return removed;
   }

   public void clearPaint(ServerLevel level) {
      for (Display.BlockDisplay display : this.paints(level)) {
         display.discard();
      }
   }

   public DrawSnapshot capture(ServerLevel level) {
      List<DrawSnapshot.Stroke> strokes = new ArrayList<>();
      BlockPos origin = this.plot.origin();
      for (Display.BlockDisplay display : this.paints(level)) {
         float scale = scaleOf(display);
         String block = BuiltInRegistries.BLOCK.getKey(display.getBlockState().getBlock()).toString();
         strokes.add(new DrawSnapshot.Stroke(
            display.getX() - origin.getX(),
            display.getY() - origin.getY(),
            display.getZ() - origin.getZ(),
            scale,
            block
         ));
      }
      return new DrawSnapshot(strokes, BuiltInRegistries.BLOCK.getKey(
         level.getBlockState(new BlockPos(this.minX, this.minY, this.wallZ)).getBlock()).toString());
   }

   public void restore(ServerLevel level, DrawSnapshot snapshot) {
      this.clearPaint(level);
      if (snapshot == null) {
         this.install(level);
         return;
      }
      this.setBackground(level, colorOf(snapshot.background()));
      BlockPos origin = this.plot.origin();
      for (DrawSnapshot.Stroke stroke : snapshot.strokes()) {
         DyeColor color = colorOf(stroke.block());
         Vec3 hit = new Vec3(
            origin.getX() + stroke.relX(),
            origin.getY() + stroke.relY(),
            origin.getZ() + stroke.relZ()
         );
         this.stampExact(level, hit, color, stroke.scale());
      }
   }

   private void stampExact(ServerLevel level, Vec3 pos, DyeColor color, float scale) {
      Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
      if (display == null) {
         return;
      }
      float s = Math.max(0.08f, scale);
      display.setPos(pos.x, pos.y, pos.z);
      display.setBlockState(concrete(color).defaultBlockState());
      display.setTransformation(new Transformation(
         new Vector3f(-s / 2.0f, -s / 2.0f, 0.0f),
         new Quaternionf(),
         new Vector3f(s, s, 0.07f),
         new Quaternionf()
      ));
      display.setTransformationInterpolationDuration(0);
      display.setTransformationInterpolationDelay(0);
      display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
      display.setBrightnessOverride(new Brightness(15, 15));
      display.setViewRange(4.0f);
      display.setShadowRadius(0.0f);
      display.setNoGravity(true);
      display.setInvulnerable(true);
      display.setSilent(true);
      display.addTag(TAG);
      display.addTag(PLOT_PREFIX + this.plot.slot());
      display.addTag(SIZE_PREFIX + Math.max(1, Math.round(s * 100.0f)));
      level.addFreshEntity(display);
   }

   private List<Display.BlockDisplay> paints(ServerLevel level) {
      AABB box = new AABB(
         this.minX - 0.5, this.minY - 0.5, this.wallZ - 1.2,
         this.maxX + 1.5, this.maxY + 1.5, this.wallZ + 0.4
      );
      String plotTag = PLOT_PREFIX + this.plot.slot();
      return level.getEntities(EntityType.BLOCK_DISPLAY, box,
         e -> e.getTags().contains(TAG) && e.getTags().contains(plotTag));
   }

   static float scaleOf(Display.BlockDisplay display) {
      for (String tag : display.getTags()) {
         if (tag.startsWith(SIZE_PREFIX)) {
            try {
               return Integer.parseInt(tag.substring(SIZE_PREFIX.length())) / 100.0f;
            } catch (NumberFormatException ignored) {
               return 0.28f;
            }
         }
      }
      return 0.28f;
   }

   public static Block concrete(DyeColor color) {
      DyeColor safe = color == null ? DyeColor.BLACK : color;
      Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(safe.getName() + "_concrete"));
      return block == null || block == Blocks.AIR ? Blocks.BLACK_CONCRETE : block;
   }

   public static DyeColor colorOf(String blockId) {
      if (blockId == null || blockId.isBlank()) {
         return DyeColor.BLACK;
      }
      String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
      if (path.endsWith("_concrete")) {
         path = path.substring(0, path.length() - "_concrete".length());
      }
      for (DyeColor color : DyeColor.values()) {
         if (color.getName().equals(path)) {
            return color;
         }
      }
      return DyeColor.BLACK;
   }
}
