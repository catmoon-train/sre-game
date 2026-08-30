package net.exmo.sreGame.games.football;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Server-authoritative football physics plus a small stitched BlockDisplay football. */
public final class FootballBall {
   private static final float CORE = 0.72F;
   private static final float PANEL = 0.25F;
   private static final List<Panel> PANELS = List.of(
      new Panel(Vec3.ZERO, Blocks.WHITE_CONCRETE.defaultBlockState(), CORE),
      new Panel(new Vec3(0.36, 0.0, 0.0), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL),
      new Panel(new Vec3(-0.36, 0.0, 0.0), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL),
      new Panel(new Vec3(0.0, 0.36, 0.0), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL),
      new Panel(new Vec3(0.0, -0.36, 0.0), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL),
      new Panel(new Vec3(0.0, 0.0, 0.36), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL),
      new Panel(new Vec3(0.0, 0.0, -0.36), Blocks.BLACK_CONCRETE.defaultBlockState(), PANEL)
   );

   private final ServerLevel level;
   private final List<Display.BlockDisplay> displays = new ArrayList<>();
   private Vec3 position;
   private Vec3 velocity = Vec3.ZERO;
   private float spin;
   private int age;

   public FootballBall(ServerLevel level, Vec3 position) {
      this.level = level;
      this.position = position;
      for (Panel panel : PANELS) {
         Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
         if (display == null) continue;
         display.setBlockState(panel.state());
         display.setPos(position.x, position.y, position.z);
         display.setTransformation(transform(panel, 0.0F));
         display.setTransformationInterpolationDuration(0);
         display.setTransformationInterpolationDelay(0);
         display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
         display.setBrightnessOverride(new Brightness(15, 15));
         display.setViewRange(1.5F);
         display.setShadowRadius(0.25F);
         display.setShadowStrength(0.75F);
         display.setNoGravity(true);
         display.setInvulnerable(true);
         display.setSilent(true);
         display.addTag("sregame_football_ball");
         level.addFreshEntity(display);
         displays.add(display);
      }
   }

   public Vec3 position() { return position; }
   public Vec3 velocity() { return velocity; }
   public boolean isRemoved() { return displays.isEmpty() || displays.stream().allMatch(Entity::isRemoved); }
   public boolean isPart(Entity entity) { return displays.contains(entity); }
   public void discard() { for (Display.BlockDisplay display : displays) if (!display.isRemoved()) display.discard(); displays.clear(); }

   public void kick(Vec3 direction, double power, double lift) {
      Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z).normalize();
      velocity = new Vec3(horizontal.x * power, lift, horizontal.z * power);
      spin += (float)(power * 4.5);
   }

   public void dribble(Vec3 playerMotion) {
      Vec3 horizontal = new Vec3(playerMotion.x, 0.0, playerMotion.z);
      if (horizontal.lengthSqr() < 0.0025) return;
      Vec3 push = horizontal.normalize().scale(Math.min(0.38, 0.13 + horizontal.length() * 1.9));
      velocity = new Vec3(velocity.x * 0.42 + push.x, Math.max(velocity.y, 0.02), velocity.z * 0.42 + push.z);
      spin += (float)(horizontal.length() * 3.0);
   }

   public void tick(FootballArena arena) {
      velocity = velocity.add(0.0, -0.045, 0.0);
      Vec3 next = position.add(velocity);
      double ground = arena.floorY() + 1.52;
      if (next.y < ground) {
         next = new Vec3(next.x, ground, next.z);
         velocity = new Vec3(velocity.x * 0.82, Math.abs(velocity.y) > 0.10 ? -velocity.y * 0.54 : 0.0, velocity.z * 0.82);
      }
      if (next.y > arena.floorY() + 13.0) {
         next = new Vec3(next.x, arena.floorY() + 13.0, next.z);
         velocity = new Vec3(velocity.x, -Math.abs(velocity.y) * 0.62, velocity.z);
      }
      double minZ = arena.minZ() + 0.48, maxZ = arena.maxZ() + 0.52;
      if (next.z < minZ || next.z > maxZ) {
         next = new Vec3(next.x, Math.max(ground, next.y), Math.max(minZ, Math.min(maxZ, next.z)));
         velocity = new Vec3(velocity.x * 0.86, velocity.y, -velocity.z * 0.62);
      }
      boolean goalMouth = arena.inGoalMouth(next.z, next.y);
      double minX = arena.minX() + 0.48, maxX = arena.maxX() + 0.52;
      if (!goalMouth && (next.x < minX || next.x > maxX)) {
         next = new Vec3(Math.max(minX, Math.min(maxX, next.x)), Math.max(ground, next.y), next.z);
         velocity = new Vec3(-velocity.x * 0.62, velocity.y, velocity.z * 0.86);
      }
      position = next;
      velocity = velocity.scale(next.y <= ground + 0.01 ? 0.985 : 0.994);
      if (velocity.horizontalDistanceSqr() < 0.00005 && Math.abs(velocity.y) < 0.02) velocity = Vec3.ZERO;
      spin += (float)(velocity.horizontalDistance() * 1.4);
      render();
      if (++age % 4 == 0) level.sendParticles(ParticleTypes.CRIT, position.x, position.y, position.z, 1, .08, .08, .08, 0.0);
   }

   private void render() {
      for (int i = 0; i < displays.size(); i++) {
         Display.BlockDisplay display = displays.get(i);
         if (display.isRemoved()) continue;
         display.setPos(position.x, position.y, position.z);
         if (age % 2 == 0) display.setTransformation(transform(PANELS.get(i), spin));
      }
   }

   private static Transformation transform(Panel panel, float spin) {
      float half = panel.scale() / 2.0F;
      Quaternionf rotation = new Quaternionf().rotateZ(spin).rotateY(spin * 0.65F);
      Vector3f offset = new Vector3f((float)panel.offset().x, (float)panel.offset().y, (float)panel.offset().z);
      rotation.transform(offset);
      Vector3f translation = new Vector3f(offset.x - half, offset.y - half, offset.z - half);
      return new Transformation(translation, rotation, new Vector3f(panel.scale(), panel.scale(), panel.scale()), new Quaternionf());
   }

   private record Panel(Vec3 offset, BlockState state, float scale) { }
}
