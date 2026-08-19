package net.exmo.sreGame.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * 右侧计分板：每人独立 dummy objective，用数据包直发，不污染大厅全局计分板。
 * 实现对照 MCRPVPDuel {@code MatchScoreboard}。
 */
public final class SidebarBoard {
   private static final String[] ENTRIES = {
      "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
      "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
   };

   private final MinecraftServer server;
   private final Map<UUID, String> objectives = new HashMap<>();

   public SidebarBoard(MinecraftServer server) {
      this.server = server;
   }

   public void create(ServerPlayer player, String title) {
      if (player == null || this.objectives.containsKey(player.getUUID())) {
         return;
      }
      ServerScoreboard sb = this.server.getScoreboard();
      String name = "sg_" + player.getUUID().toString().replace("-", "").substring(0, 12);
      Objective old = sb.getObjective(name);
      if (old != null) {
         player.connection.send(new ClientboundSetObjectivePacket(old, 1));
         sb.removeObjective(old);
      }
      Objective obj = sb.addObjective(name, ObjectiveCriteria.DUMMY,
         TextUtil.color(title), ObjectiveCriteria.RenderType.INTEGER, false, null);
      this.objectives.put(player.getUUID(), name);
      player.connection.send(new ClientboundSetObjectivePacket(obj, 0));
      player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
   }

   public void update(ServerPlayer player, List<String> lines) {
      if (player == null) {
         return;
      }
      String objName = this.objectives.get(player.getUUID());
      if (objName == null) {
         return;
      }
      int size = lines == null ? 0 : Math.min(lines.size(), ENTRIES.length);
      for (int i = 0; i < ENTRIES.length; i++) {
         if (i < size) {
            player.connection.send(new ClientboundSetScorePacket(
               ENTRIES[i], objName, size - i, Optional.of(TextUtil.color(lines.get(i))), Optional.empty()));
         } else {
            player.connection.send(new ClientboundSetScorePacket(
               ENTRIES[i], objName, 0, Optional.empty(), Optional.empty()));
         }
      }
   }

   public void remove(ServerPlayer player) {
      if (player != null) {
         this.remove(player.getUUID());
      }
   }

   public void remove(UUID uuid) {
      String name = this.objectives.remove(uuid);
      if (name == null) {
         return;
      }
      ServerScoreboard sb = this.server.getScoreboard();
      Objective obj = sb.getObjective(name);
      if (obj == null) {
         return;
      }
      ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
      if (player != null) {
         player.connection.send(new ClientboundSetObjectivePacket(obj, 1));
      }
      sb.removeObjective(obj);
   }

   public void removeAll() {
      for (UUID uuid : List.copyOf(this.objectives.keySet())) {
         this.remove(uuid);
      }
   }
}
