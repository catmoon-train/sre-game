package net.exmo.sreGame.games.chicken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

public enum Gadget {
   PLANKS("ch_planks", "oak_planks", "&6木头", "&7铺路", Kind.PATH),
   COBBLE("ch_cobble", "cobblestone", "&7原石", "&7铺路", Kind.PATH),
   SLIME("ch_slime", "slime_block", "&a粘液块", "&7铺路 / 落地弹跳", Kind.PATH),
   HONEY("ch_honey", "honey_block", "&6蜂蜜块", "&7铺路 / 减速黏住", Kind.PATH),
   ICE("ch_ice", "packed_ice", "&b冰面", "&7打滑加速", Kind.TRAP),
   FAKE("ch_fake", "orange_glazed_terracotta", "&6假地板", "&7踩上片刻后碎掉", Kind.TRAP),
   LAUNCH("ch_launch", "emerald_block", "&a弹射板", "&7把人朝终点甩出去", Kind.TRAP),
   MAGMA("ch_magma", "magma_block", "&c岩浆块", "&7碰到算出局", Kind.TRAP),
   CAMPFIRE("ch_campfire", "campfire", "&6营火", "&7点燃过路的人", Kind.TRAP),
   COBWEB("ch_web", "cobweb", "&f蛛网", "&7卡住脚步", Kind.TRAP),
   BERRY("ch_berry", "sweet_berries", "&c刺丛", "&7碰到算出局", Kind.TRAP),
   SOUL("ch_soul", "soul_sand", "&8灵魂沙", "&7陷脚减速", Kind.TRAP),
   SNOW("ch_snow", "powder_snow_bucket", "&f细雪", "&7陷进去", Kind.TRAP),
   SPIKE("ch_spike", "pointed_dripstone", "&c石笋", "&7碰到算出局", Kind.TRAP),
   WIND("ch_wind", "white_wool", "&f逆风扇", "&7把人往回吹", Kind.TRAP),
   LADDER("ch_ladder", "ladder", "&e梯子", "&7可贴着爬", Kind.TRAP),
   BARS("ch_bars", "iron_bars", "&7铁栏杆", "&7薄墙，方便蹬墙", Kind.TRAP),
   PANE("ch_pane", "glass_pane", "&f玻璃板", "&7几乎看不见的墙", Kind.TRAP),
   FENCE("ch_fence", "oak_fence", "&6栅栏", "&7卡脚 / 蹬墙", Kind.TRAP),
   CONVEYOR("ch_conveyor", "cyan_glazed_terracotta", "&b传送带", "&7推向终点", Kind.TRAP),
   SCAFFOLD("ch_scaffold", "scaffolding", "&e脚手架", "&7可爬可走", Kind.TRAP),
   CHAIN("ch_chain", "chain", "&8锁链", "&7细长可爬", Kind.TRAP),
   ROD("ch_rod", "end_rod", "&f末地烛", "&7细杆挡路", Kind.TRAP),
   SAW("ch_saw", "stonecutter", "&c锯片", "&7碰到算出局", Kind.TRAP),
   LASER("ch_laser", "redstone_lamp", "&c激光", "&7间歇朝终点扫射", Kind.TRAP),
   GRAVITY("ch_gravity", "crying_obsidian", "&5反重力", "&7踩上被吸上天", Kind.TRAP),
   WARP("ch_warp", "end_portal_frame", "&d传送门", "&7往前甩一段", Kind.TRAP),
   CANNON("ch_cannon", "dispenser", "&6大炮", "&7强力弹射", Kind.TRAP),
   BEARTRAP("ch_bear", "iron_trapdoor", "&8捕兽夹", "&7夹住再出局", Kind.TRAP),
   BOOST("ch_boost", "redstone_block", "&c冲刺板", "&7猛加速", Kind.TRAP),
   BLIND("ch_blind", "sculk", "&8失明", "&7眼前一黑", Kind.TRAP),
   LEVITATE("ch_levitate", "purpur_block", "&d漂浮", "&7离地漂一会儿", Kind.TRAP),
   CURRENT("ch_current", "prismarine", "&3回流", "&7把人往回推", Kind.TRAP),
   JUMPLOCK("ch_jumplock", "nether_bricks", "&4禁跳", "&7暂时不能跳跃", Kind.TRAP),
   SIDEWIND("ch_sidewind", "magenta_wool", "&d侧风扇", "&7往赛道边上吹", Kind.TRAP),
   SUPERBOUNCE("ch_superbounce", "lime_concrete", "&a超级弹垫", "&7弹得特别高", Kind.TRAP),
   SINKHOLE("ch_sink", "black_glazed_terracotta", "&8陷坑", "&7猛地往下拽", Kind.TRAP),
   LIFTER("ch_lifter", "piston", "&e上顶活塞", "&7把人弹上天", Kind.TRAP),
   WITHER("ch_wither", "nether_wart_block", "&4凋零区", "&7碰到算出局", Kind.TRAP),
   REVERSE("ch_reverse", "purple_glazed_terracotta", "&5倒车板", "&7速度反向", Kind.TRAP),
   FROST("ch_frost", "snow_block", "&b冰封", "&7冻住脚步", Kind.TRAP),
   MAGNET("ch_magnet", "iron_block", "&7磁铁", "&7大范围吸向赛道边墙", Kind.TRAP),
   FLASH("ch_flash", "glowstone", "&e闪光", "&7反胃晃眼", Kind.TRAP),
   BOMB("ch_bomb", "tnt", "&c炸弹", "&7炸掉周围 2 格的机关", Kind.BOMB),
   SRE_FAKE("ch_sre_fake", "noellesroles:fake_block", "&dSRE 假方块", "&7只看得见，踩空", Kind.TRAP, "fake_block"),
   SRE_BRIDGE("ch_sre_bridge", "noellesroles:breaking_bridge", "&6SRE 断桥", "&7踩上去会塌", Kind.TRAP, "breaking_bridge"),
   SRE_POISON("ch_sre_poison", "noellesroles:poison_zone", "&aSRE 毒区", "&7待久了会中毒", Kind.TRAP, "poison_zone"),
   SRE_FOG("ch_sre_fog", "noellesroles:fog_zone", "&fSRE 迷雾", "&7把没职业的人推出", Kind.TRAP, "fog_zone"),
   SRE_STALACTITE("ch_sre_drip", "noellesroles:dripping_stalactite", "&cSRE 滴水石锥", "&7从头顶砸人", Kind.TRAP, "dripping_stalactite"),
   SRE_FLAME("ch_sre_flame", "noellesroles:flamethrower", "&6SRE 喷火", "&7朝终点方向喷火", Kind.TRAP, "flamethrower"),
   SRE_PLATFORM("ch_sre_move", "noellesroles:moving_platform", "&eSRE 移动平台", "&7带着人往返", Kind.TRAP, "moving_platform"),
   SRE_STONE("ch_sre_roll", "noellesroles:rolling_stone_trigger", "&7SRE 滚石板", "&7朝终点滚石", Kind.TRAP, "rolling_stone_trigger"),
   SRE_LOG("ch_sre_log", "noellesroles:rolling_log_trigger", "&6SRE 滚木板", "&7朝终点滚木", Kind.TRAP, "rolling_log_trigger"),
   SRE_KILL("ch_sre_kill", "noellesroles:kill_block", "&4SRE 隐形墙", "&7挡路；SRE 对局里踩到即死", Kind.TRAP, "kill_block"),
   SRE_BUSH("ch_sre_bush", "noellesroles:scene_bush", "&2SRE 灌木", "&7挡路藏人", Kind.TRAP, "scene_bush");

   public static final String ACTION_PREFIX = "ch_";

   public enum Kind {
      PATH,
      TRAP,
      BOMB
   }

   private final String action;
   private final String icon;
   private final String title;
   private final String lore;
   private final Kind kind;
   private final String srePath;

   Gadget(String action, String icon, String title, String lore, Kind kind) {
      this(action, icon, title, lore, kind, null);
   }

   Gadget(String action, String icon, String title, String lore, Kind kind, String srePath) {
      this.action = action;
      this.icon = icon;
      this.title = title;
      this.lore = lore;
      this.kind = kind;
      this.srePath = srePath;
   }

   public String action() {
      return this.action;
   }

   public String icon() {
      return this.icon;
   }

   public String title() {
      return this.title;
   }

   public Kind kind() {
      return this.kind;
   }

   public boolean isPath() {
      return this.kind == Kind.PATH;
   }

   public boolean isBomb() {
      return this.kind == Kind.BOMB;
   }

   public boolean available() {
      return this.srePath == null || SreSceneBlocks.present(this.srePath);
   }

   public ItemStack stack() {
      return this.stack(1);
   }

   public ItemStack stack(int count) {
      String extra = this.isPath() ? "&7铺路，不占机关配额" : "&7消耗 1 个本轮机关";
      ItemStack stack = GuiItems.action(this.icon, this.title, List.of(this.lore, "&e对着方块右键放置", extra), this.action);
      stack.setCount(Math.max(1, count));
      return stack;
   }

   public BlockState blockState() {
      return switch (this) {
         case PLANKS -> Blocks.OAK_PLANKS.defaultBlockState();
         case COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
         case SLIME -> Blocks.SLIME_BLOCK.defaultBlockState();
         case ICE -> Blocks.PACKED_ICE.defaultBlockState();
         case HONEY -> Blocks.HONEY_BLOCK.defaultBlockState();
         case FAKE -> Blocks.ORANGE_GLAZED_TERRACOTTA.defaultBlockState();
         case LAUNCH -> Blocks.EMERALD_BLOCK.defaultBlockState();
         case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
         case CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
         case COBWEB -> Blocks.COBWEB.defaultBlockState();
         case BERRY -> Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
         case SOUL -> Blocks.SOUL_SAND.defaultBlockState();
         case SNOW -> Blocks.POWDER_SNOW.defaultBlockState();
         case SPIKE -> Blocks.POINTED_DRIPSTONE.defaultBlockState()
            .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)
            .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP);
         case WIND -> Blocks.WHITE_WOOL.defaultBlockState();
         case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
         case BARS -> Blocks.IRON_BARS.defaultBlockState();
         case PANE -> Blocks.GLASS_PANE.defaultBlockState();
         case FENCE -> Blocks.OAK_FENCE.defaultBlockState();
         case CONVEYOR -> Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState();
         case SCAFFOLD -> Blocks.SCAFFOLDING.defaultBlockState();
         case CHAIN -> Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
         case ROD -> Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP);
         case SAW -> Blocks.STONECUTTER.defaultBlockState();
         case LASER -> Blocks.REDSTONE_LAMP.defaultBlockState();
         case GRAVITY -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
         case WARP -> Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
         case CANNON -> Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST);
         case BEARTRAP -> Blocks.IRON_TRAPDOOR.defaultBlockState();
         case BOOST -> Blocks.REDSTONE_BLOCK.defaultBlockState();
         case BLIND -> Blocks.SCULK.defaultBlockState();
         case LEVITATE -> Blocks.PURPUR_BLOCK.defaultBlockState();
         case CURRENT -> Blocks.PRISMARINE.defaultBlockState();
         case JUMPLOCK -> Blocks.NETHER_BRICKS.defaultBlockState();
         case SIDEWIND -> Blocks.MAGENTA_WOOL.defaultBlockState();
         case SUPERBOUNCE -> Blocks.LIME_CONCRETE.defaultBlockState();
         case SINKHOLE -> Blocks.BLACK_GLAZED_TERRACOTTA.defaultBlockState();
         case LIFTER -> Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
         case WITHER -> Blocks.NETHER_WART_BLOCK.defaultBlockState();
         case REVERSE -> Blocks.PURPLE_GLAZED_TERRACOTTA.defaultBlockState();
         case FROST -> Blocks.SNOW_BLOCK.defaultBlockState();
         case MAGNET -> Blocks.IRON_BLOCK.defaultBlockState();
         case FLASH -> Blocks.GLOWSTONE.defaultBlockState();
         case BOMB -> Blocks.TNT.defaultBlockState();
         default -> SreSceneBlocks.prepared(this.srePath);
      };
   }

   public boolean lethal() {
      return this == MAGMA || this == CAMPFIRE || this == BERRY || this == SPIKE
         || this == SAW || this == LASER || this == BEARTRAP || this == WITHER
         || this == SRE_KILL || this == SRE_FLAME || this == SRE_STALACTITE || this == SRE_POISON;
   }

   public BlockState liveState(boolean laserLit) {
      if (this == LASER) {
         return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, laserLit);
      }
      return this.blockState();
   }

   public static List<Gadget> pool(Kind kind) {
      ArrayList<Gadget> list = new ArrayList<>();
      for (Gadget gadget : values()) {
         if (gadget.kind == kind && gadget.available()) {
            list.add(gadget);
         }
      }
      return list;
   }

   public static List<Gadget> rollRoundTraps() {
      return rollTraps(6);
   }

   public static List<Gadget> rollTraps(int count) {
      ArrayList<Gadget> pool = new ArrayList<>(pool(Kind.TRAP));
      pool.addAll(pool(Kind.BOMB));
      Collections.shuffle(pool, ThreadLocalRandom.current());
      int n = Math.max(0, Math.min(count, pool.size()));
      return new ArrayList<>(pool.subList(0, n));
   }

   public static List<Gadget> rollPaths() {
      ArrayList<Gadget> out = new ArrayList<>();
      int n = 2 + ThreadLocalRandom.current().nextInt(3);
      for (int i = 0; i < n; i++) {
         out.add(rollPathBlock());
      }
      return out;
   }

   private static Gadget rollPathBlock() {
      int roll = ThreadLocalRandom.current().nextInt(20);
      if (roll < 8) {
         return COBBLE;
      }
      if (roll < 16) {
         return PLANKS;
      }
      if (roll < 18) {
         return SLIME;
      }
      return HONEY;
   }

   public static Gadget fromStack(ItemStack stack) {
      String action = GuiItems.actionTag(stack);
      if (action == null || !action.startsWith(ACTION_PREFIX)) {
         return null;
      }
      for (Gadget gadget : values()) {
         if (gadget.action.equals(action)) {
            return gadget;
         }
      }
      return null;
   }
}
