package net.exmo.sreGame.games.buildwar;

import net.exmo.sreGame.GameContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class BuildSafety {
   private BuildSafety() {
   }

   public static boolean isWorkstation(Block block) {
      if (block == null) {
         return false;
      }
      return block == Blocks.CRAFTING_TABLE
         || block == Blocks.FURNACE
         || block == Blocks.BLAST_FURNACE
         || block == Blocks.SMOKER
         || block == Blocks.ENCHANTING_TABLE
         || block == Blocks.BREWING_STAND
         || block == Blocks.STONECUTTER
         || block == Blocks.CARTOGRAPHY_TABLE
         || block == Blocks.LOOM
         || block == Blocks.GRINDSTONE
         || block == Blocks.SMITHING_TABLE
         || block == Blocks.LECTERN
         || block == Blocks.ENDER_CHEST
         || block == Blocks.CHEST
         || block == Blocks.BARREL
         || block instanceof AnvilBlock;
   }

   public static void register(GameContext ctx) {
      UseItemCallback.EVENT.register((player, world, hand) -> {
         ItemStack stack = player.getItemInHand(hand);
         if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.rhythm().isPlaying(sp)) {
            return InteractionResultHolder.fail(stack);
         }
         if (net.exmo.sreGame.games.draw.DrawKit.tryUse(ctx, sp, stack, null)) {
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.fraudMaster().isPlaying(sp)) {
            if (ctx.fraudMaster().handleUseItem(sp, stack)) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.fakeHuman().isPlaying(sp)) {
            if (ctx.fakeHuman().handleUseItem(sp, stack)) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.caveGuess().isPlaying(sp)) {
            if (ctx.caveGuess().handleUseItem(sp, stack)) {
               return InteractionResultHolder.fail(stack);
            }
            if (ctx.caveGuess().canBuild(sp)) {
               if (isDangerous(stack) && !isShadowTool(stack)) {
                  ctx.send(sp, "&c禁止使用该物品。");
                  return InteractionResultHolder.fail(stack);
               }
               return InteractionResultHolder.pass(stack);
            }
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.chickenHorse().isPlaying(sp)) {
            ctx.chickenHorse().handleUseItem(sp, stack);
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.dontDo().isPlaying(sp)) {
            ctx.dontDo().handleUseItem(sp, stack);
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.pillarPummel().isPlaying(sp)) {
            InteractionResult result = ctx.pillarPummel().handleUseItem(sp, stack);
            if (result == InteractionResult.FAIL) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.dodgeball().isPlaying(sp)) {
            InteractionResult result = ctx.dodgeball().handleUseItem(sp, stack);
            if (result == InteractionResult.FAIL) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.skyWorld().isPlaying(sp)) {
            InteractionResult result = ctx.skyWorld().handleUseItem(sp, stack);
            if (result == InteractionResult.FAIL) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.football().isPlaying(sp)) {
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.blockedCombat().isPlaying(sp)) {
            InteractionResult result = ctx.blockedCombat().handleUseItem(sp, stack);
            return result == InteractionResult.FAIL ? InteractionResultHolder.fail(stack) : InteractionResultHolder.pass(stack);
         }
         if (ctx.tunnelRats().isPlaying(sp)) {
            InteractionResult result = ctx.tunnelRats().handleUseItem(sp, stack);
            return result == InteractionResult.FAIL ? InteractionResultHolder.fail(stack) : InteractionResultHolder.pass(stack);
         }
         if (ctx.parkour().isPlaying(sp)) {
            ctx.parkour().handleUseItem(sp, stack);
            return InteractionResultHolder.fail(stack);
         }
         if (ctx.digToDeath().isPlaying(sp)) {
            InteractionResult result = ctx.digToDeath().handleUseItem(sp, stack);
            if (result == InteractionResult.FAIL) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.youBuildRun().isPlaying(sp)) {
            if (isDangerous(stack) && !isFluidBucket(stack)) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.pushTheButton().isPlaying(sp)) {
            if (ctx.pushTheButton().handleUseItem(sp, stack)) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.nameTagWar().isPlaying(sp)) {
            if (stack.is(Items.SHEARS)) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.fillInTheWall().isPlaying(sp)) {
            InteractionResult result = ctx.fillInTheWall().handleUseItem(sp, stack);
            if (result == InteractionResult.FAIL) {
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (ctx.youGuess().isPlaying(sp)) {
            var yg = ctx.youGuess().get(sp.getUUID());
            if (yg != null && yg.handlePickItem(sp, stack)) {
               return InteractionResultHolder.fail(stack);
            }
            if (yg != null && yg.drawing()) {
               return InteractionResultHolder.fail(stack);
            }
            if (isDangerous(stack)) {
               ctx.send(sp, "&c禁止使用该物品。");
               return InteractionResultHolder.fail(stack);
            }
            if (yg == null || !yg.isBuilder(sp.getUUID()) || yg.phase() != net.exmo.sreGame.games.youguess.YouGuessMatch.Phase.PLAYING) {
               return InteractionResultHolder.fail(stack);
            }
            if (ThemeNameGuard.leaksTheme(stack, yg.themeWord())) {
               ctx.send(sp, "&c不能放置名称含主题词的方块。");
               return InteractionResultHolder.fail(stack);
            }
            if (isUsableNonPlace(stack)) {
               ctx.send(sp, "&c建造中不能使用该物品。");
               return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
         }
         if (!ctx.buildWar().isPlaying(sp)) {
            return InteractionResultHolder.pass(stack);
         }
         if (isDangerous(stack) && !isFluidBucket(stack)) {
            ctx.send(sp, "&c建筑战争中禁止使用该物品。");
            return InteractionResultHolder.fail(stack);
         }
         BuildWarMatch match = ctx.buildWar().get(sp.getUUID());
         if (match != null && match.handlePickItem(sp, stack)) {
            return InteractionResultHolder.fail(stack);
         }
         if (match != null && match.handleScoreItem(sp, stack)) {
            return InteractionResultHolder.fail(stack);
         }
         if (match != null && match.drawing()) {
            return InteractionResultHolder.fail(stack);
         }
         if (match != null && (match.phase() != BuildWarMatch.Phase.BUILDING || !match.isBuilder(sp.getUUID()))) {
            return InteractionResultHolder.fail(stack);
         }
         if (match != null && ThemeNameGuard.leaksTheme(stack, match.themeWord(sp.getUUID()))) {
            ctx.send(sp, "&c不能放置名称含主题词的方块。");
            return InteractionResultHolder.fail(stack);
         }
         if (isUsableNonPlace(stack) && !isFluidBucket(stack)) {
            ctx.send(sp, "&c建造中不能使用该物品。");
            return InteractionResultHolder.fail(stack);
         }
         return InteractionResultHolder.pass(stack);
      });
      UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
         if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
         }
         ItemStack stack = sp.getItemInHand(hand);
         if (ctx.partyGames().isPlaying(sp)) {
            return ctx.partyGames().handleUseBlock(sp, hit, stack);
         }
         if (ctx.rhythm().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (net.exmo.sreGame.games.draw.DrawKit.tryUse(ctx, sp, stack, hit)) {
            return InteractionResult.FAIL;
         }
         if (ctx.fakeHuman().isPlaying(sp)) {
            ctx.fakeHuman().handleUseItem(sp, stack);
            return InteractionResult.FAIL;
         }
         if (ctx.fraudMaster().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.fakeHuman().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.caveGuess().isPlaying(sp)) {
            if (!ctx.caveGuess().canBuild(sp)) {
               return InteractionResult.FAIL;
            }
            if (isUsableNonPlace(sp.getItemInHand(hand)) && !isShadowTool(sp.getItemInHand(hand))) {
               ctx.send(sp, "&c舞台上不能使用该物品。");
               return InteractionResult.FAIL;
            }
            if (isForbiddenInteract(world.getBlockState(hit.getBlockPos()).getBlock())) {
               return InteractionResult.FAIL;
            }
            BlockPos place = hit.getBlockPos().relative(hit.getDirection());
            if (ctx.caveGuess().isRestrictedPos(sp, place)
               || (isDangerous(sp.getItemInHand(hand)) && !isShadowTool(sp.getItemInHand(hand)))) {
               ctx.send(sp, "&c只能在舞台内摆造型。");
               return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
         }
         if (ctx.chickenHorse().isPlaying(sp)) {
            ctx.chickenHorse().tryPlace(sp, hit, sp.getItemInHand(hand));
            return InteractionResult.FAIL;
         }
         if (ctx.dontDo().isPlaying(sp)) {
            return ctx.dontDo().handleUseBlock(sp, hit, stack) ? InteractionResult.FAIL : InteractionResult.PASS;
         }
         if (ctx.luckyPillar().isPlaying(sp)) {
            return ctx.luckyPillar().handleUseBlock(sp, hit, stack);
         }
         if (ctx.pillarPummel().isPlaying(sp)) {
            return ctx.pillarPummel().handleUseBlock(sp, hit, stack);
         }
         if (ctx.dodgeball().isPlaying(sp)) {
            return ctx.dodgeball().handleUseBlock(sp, hit, stack);
         }
         if (ctx.football().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.skyWorld().isPlaying(sp)) {
            return ctx.skyWorld().handleUseBlock(sp, hit, stack);
         }
         if (ctx.blockedCombat().isPlaying(sp)) {
            return ctx.blockedCombat().handleUseBlock(sp, hit, stack);
         }
         if (ctx.tunnelRats().isPlaying(sp)) {
            return ctx.tunnelRats().handleUseBlock(sp, hit, stack);
         }
         if (ctx.parkour().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.digToDeath().isPlaying(sp)) {
            if (stack.is(Items.SNOWBALL) && ctx.digToDeath().handleUseItem(sp, stack) != InteractionResult.FAIL) {
               return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
         }
         if (ctx.youBuildRun().isPlaying(sp)) {
            return ctx.youBuildRun().handleUseBlock(sp, hit, stack);
         }
         if (ctx.pushTheButton().isPlaying(sp)) {
            return ctx.pushTheButton().handleUseBlock(sp, hit, stack);
         }
         if (ctx.nameTagWar().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.fillInTheWall().isPlaying(sp)) {
            return ctx.fillInTheWall().handleUseBlock(sp, hit, stack);
         }
         if (ctx.youGuess().isPlaying(sp)) {
            var yg = ctx.youGuess().get(sp.getUUID());
            if (yg != null && yg.drawing()) {
               return InteractionResult.FAIL;
            }
            if (yg == null || !yg.isBuilder(sp.getUUID()) || yg.phase() != net.exmo.sreGame.games.youguess.YouGuessMatch.Phase.PLAYING) {
               return InteractionResult.FAIL;
            }
            if (isUsableNonPlace(sp.getItemInHand(hand))) {
               ctx.send(sp, "&c建造中不能使用该物品。");
               return InteractionResult.FAIL;
            }
            if (isForbiddenInteract(world.getBlockState(hit.getBlockPos()).getBlock())) {
               ctx.send(sp, "&c无法使用告示牌或铁砧。");
               return InteractionResult.FAIL;
            }
            if (ThemeNameGuard.leaksTheme(sp.getItemInHand(hand), yg.themeWord())) {
               ctx.send(sp, "&c不能放置名称含主题词的方块。");
               return InteractionResult.FAIL;
            }
            BlockPos place = hit.getBlockPos().relative(hit.getDirection());
            if (ctx.youGuess().isRestrictedPos(sp, place) || isDangerous(sp.getItemInHand(hand))) {
               return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
         }
         if (!ctx.buildWar().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         BuildWarMatch match = ctx.buildWar().get(sp.getUUID());
         if (match != null && match.drawing()) {
            return InteractionResult.FAIL;
         }
         if (match != null && (match.phase() != BuildWarMatch.Phase.BUILDING || !match.isBuilder(sp.getUUID()))) {
            return InteractionResult.FAIL;
         }
         if (isUsableNonPlace(sp.getItemInHand(hand)) && !isFluidBucket(sp.getItemInHand(hand))) {
            ctx.send(sp, "&c建造中不能使用该物品。");
            return InteractionResult.FAIL;
         }
         if (isForbiddenInteract(world.getBlockState(hit.getBlockPos()).getBlock())) {
            ctx.send(sp, "&c无法使用告示牌或铁砧。");
            return InteractionResult.FAIL;
         }
         if (match != null && ThemeNameGuard.leaksTheme(sp.getItemInHand(hand), match.themeWord(sp.getUUID()))) {
            ctx.send(sp, "&c不能放置名称含主题词的方块。");
            return InteractionResult.FAIL;
         }
         BlockPos place = hit.getBlockPos().relative(hit.getDirection());
         if (ctx.buildWar().isRestrictedPos(sp, place) || (isDangerous(sp.getItemInHand(hand)) && !isFluidBucket(sp.getItemInHand(hand)))) {
            ctx.send(sp, "&c不能在场地外放置，也不能使用该物品。");
            return InteractionResult.FAIL;
         }
         return InteractionResult.PASS;
      });
      UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.rhythm().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.fakeHuman().isPlaying(sp)) {
            if (entity instanceof ServerPlayer target) {
               ctx.fakeHuman().handleUseEntity(sp, target, sp.getItemInHand(hand));
            }
            return InteractionResult.FAIL;
         }
         if (ctx.caveGuess().isPlaying(sp)) {
            return ctx.caveGuess().canBuild(sp) ? InteractionResult.PASS : InteractionResult.FAIL;
         }
         if (ctx.chickenHorse().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.dontDo().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.luckyPillar().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.skyWorld().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.blockedCombat().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.pillarPummel().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.dodgeball().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.football().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.nameTagWar().isPlaying(sp)) {
            return ctx.nameTagWar().handleUseEntity(sp, entity, sp.getItemInHand(hand));
         }
         if (ctx.digToDeath().isPlaying(sp) || ctx.parkour().isPlaying(sp) || ctx.youBuildRun().isPlaying(sp)
            || ctx.pushTheButton().isPlaying(sp) || ctx.fillInTheWall().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (!ctx.youGuess().isPlaying(sp) && !ctx.buildWar().isPlaying(sp)
            && !ctx.fraudMaster().isPlaying(sp) && !ctx.fakeHuman().isPlaying(sp)) {
            return InteractionResult.PASS;
         }
         if (ctx.fraudMaster().isPlaying(sp)) {
            return InteractionResult.FAIL;
         }
         if (ctx.fakeHuman().isPlaying(sp) && player instanceof ServerPlayer actor
            && entity instanceof ServerPlayer target) {
            if (ctx.fakeHuman().handleUseEntity(actor, target, actor.getItemInHand(hand))) {
               return InteractionResult.FAIL;
            }
            return InteractionResult.FAIL;
         }
         if (isUsableNonPlace(sp.getItemInHand(hand))) {
            ctx.send(sp, "&c建造中不能使用该物品。");
            return InteractionResult.FAIL;
         }
         return InteractionResult.FAIL;
      });
      PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
         if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return true;
         }
         if (ctx.partyGames().isPlaying(sp)) {
            return ctx.partyGames().tryBreak(sp, pos, state);
         }
         if (ctx.hypixelSays().isPlaying(sp)) {
            return ctx.hypixelSays().tryBreak(sp, pos, state);
         }
         if (ctx.rhythm().isPlaying(sp)) {
            return false;
         }
         if (ctx.fakeHuman().isPlaying(sp) || ctx.fraudMaster().isPlaying(sp)) {
            return false;
         }
         if (ctx.caveGuess().isPlaying(sp)) {
            if (!ctx.caveGuess().canBuild(sp) || ctx.caveGuess().isRestrictedPos(sp, pos)) {
               return false;
            }
            Plot plot = ctx.caveGuess().boundPlot(sp);
            return plot != null && pos.getY() > plot.origin().getY()
               && pos.getY() < plot.origin().getY() + plot.height();
         }
         if (ctx.chickenHorse().isPlaying(sp)) {
            ctx.chickenHorse().tryBreak(sp, pos);
            return false;
         }
         if (ctx.dontDo().isPlaying(sp)) {
            return ctx.dontDo().handleBreak(sp, pos, state);
         }
         if (ctx.luckyPillar().isPlaying(sp)) {
            return ctx.luckyPillar().tryBreak(sp, pos);
         }
         if (ctx.pillarPummel().isPlaying(sp)) {
            return ctx.pillarPummel().tryBreak(sp, pos);
         }
         if (ctx.dodgeball().isPlaying(sp)) {
            return false;
         }
         if (ctx.football().isPlaying(sp)) {
            return false;
         }
         if (ctx.skyWorld().isPlaying(sp)) {
            return ctx.skyWorld().tryBreak(sp, pos);
         }
         if (ctx.blockedCombat().isPlaying(sp)) {
            return ctx.blockedCombat().tryBreak(sp, pos);
         }
         if (ctx.tunnelRats().isPlaying(sp)) {
            return ctx.tunnelRats().tryBreak(sp, pos);
         }
         if (ctx.parkour().isPlaying(sp)) {
            return false;
         }
         if (ctx.digToDeath().isPlaying(sp)) {
            return ctx.digToDeath().tryBreak(sp, pos);
         }
         if (ctx.youBuildRun().isPlaying(sp)) {
            return ctx.youBuildRun().tryBreak(sp, pos);
         }
         if (ctx.pushTheButton().isPlaying(sp)) {
            return ctx.pushTheButton().tryBreak(sp, pos);
         }
         if (ctx.nameTagWar().isPlaying(sp)) {
            return false;
         }
         if (ctx.fillInTheWall().isPlaying(sp)) {
            return ctx.fillInTheWall().tryBreak(sp, pos);
         }
         if (ctx.youGuess().isPlaying(sp)) {
            var yg = ctx.youGuess().get(sp.getUUID());
            if (yg != null && yg.drawing()) {
               return false;
            }
            if (yg == null || !yg.isBuilder(sp.getUUID()) || yg.phase() != net.exmo.sreGame.games.youguess.YouGuessMatch.Phase.PLAYING) {
               return false;
            }
            Plot plot = yg.plot();
            if (plot == null || !plot.contains(pos)
               || pos.getY() <= plot.origin().getY()
               || pos.getY() >= plot.origin().getY() + plot.height()) {
               return false;
            }
            return true;
         }
         if (!ctx.buildWar().isPlaying(sp)) {
            return true;
         }
         BuildWarMatch match = ctx.buildWar().get(sp.getUUID());
         if (match != null && match.drawing()) {
            return false;
         }
         if (match != null && (match.phase() != BuildWarMatch.Phase.BUILDING || !match.isBuilder(sp.getUUID()))) {
            return false;
         }
         Plot plot = match == null ? null : match.boundPlot(sp.getUUID());
         if (plot == null || !plot.contains(pos)
            || pos.getY() <= plot.origin().getY()
            || pos.getY() >= plot.origin().getY() + plot.height()) {
            return false;
         }
         return true;
      });
      ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
         if (!(world instanceof ServerLevel level) || !ctx.plots().isBuildWarLevel(level)) {
            return;
         }
         if (entity instanceof PrimedTnt || entity instanceof MinecartTNT) {
            if (ctx.luckyPillar().containsEntity(entity) || ctx.pillarPummel().containsEntity(entity)
               || ctx.dodgeball().containsEntity(entity) || ctx.digToDeath().containsEntity(entity)
               || ctx.football().containsEntity(entity)
               || ctx.skyWorld().containsEntity(entity) || ctx.nameTagWar().containsEntity(entity)
               || ctx.fillInTheWall().containsEntity(entity)) {
               return;
            }
            entity.discard();
         }
      });
   }

   private static boolean isShadowTool(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      return item == Items.ARMOR_STAND
         || item == Items.CREEPER_SPAWN_EGG
         || item == Items.PIG_SPAWN_EGG
         || item == Items.CHICKEN_SPAWN_EGG
         || item == Items.COW_SPAWN_EGG;
   }

   /** 金苹果、盾牌、弓、药水等“使用”类物品；方块放置仍走 UseBlock。 */
   private static boolean isUsableNonPlace(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      return !(stack.getItem() instanceof BlockItem);
   }

   private static boolean isFluidBucket(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      return item == Items.WATER_BUCKET
         || item == Items.LAVA_BUCKET
         || item == Items.BUCKET
         || item == Items.POWDER_SNOW_BUCKET;
   }

   private static boolean isDangerous(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      if (item instanceof SignItem
         || item instanceof HangingSignItem
         || item == Items.ANVIL
         || item == Items.CHIPPED_ANVIL
         || item == Items.DAMAGED_ANVIL
         || item == Items.TNT
         || item == Items.TNT_MINECART
         || item == Items.FLINT_AND_STEEL
         || item == Items.FIRE_CHARGE
         || item == Items.END_CRYSTAL
         || item == Items.RESPAWN_ANCHOR
         || item == Items.FIREWORK_ROCKET
         || item == Items.COMMAND_BLOCK
         || item == Items.CHAIN_COMMAND_BLOCK
         || item == Items.REPEATING_COMMAND_BLOCK
         || item == Items.COMMAND_BLOCK_MINECART
         || item == Items.STRUCTURE_BLOCK
         || item == Items.STRUCTURE_VOID
         || item == Items.JIGSAW
         || item == Items.BARRIER
         || item == Items.DEBUG_STICK
         || item == Items.LIGHT
         || item == Items.SPAWNER
         || item == Items.TRIAL_SPAWNER
         || item == Items.VAULT
         || item == Items.KNOWLEDGE_BOOK) {
         return true;
      }
      return item instanceof BlockItem blockItem && (
         blockItem.getBlock() == Blocks.TNT
            || blockItem.getBlock() == Blocks.FIRE
            || blockItem.getBlock() == Blocks.RESPAWN_ANCHOR
            || isForbiddenInteract(blockItem.getBlock())
      );
   }

   private static boolean isForbiddenInteract(Block block) {
      if (block instanceof AnvilBlock) {
         return true;
      }
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
      return path.contains("sign") || path.contains("anvil");
   }
}
