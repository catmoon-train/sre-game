package net.exmo.sreGame.fakehuman;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeHumanPlayer {
   public enum Death {
      NONE,
      STONE,
      GUN,
      NIGHT,
      OVERTHROW,
      LEAVE
   }

   private final UUID uuid;
   private Role role = Role.HUMAN;
   private final List<PersonaTag> tags = new ArrayList<>();
   private IdCard card;
   private Supply supply = Supply.STONE;
   private Zone zone = Zone.SPECTATE;
   private boolean alive = true;
   private boolean bound;
   private int bedIndex = -1;
   private boolean refusedId;
   private boolean admittedByKeeper;
   private boolean leftPermanently;
   private boolean wronged;
   private int maxImpostorDays;
   private final Set<UUID> suspectedBy = ConcurrentHashMap.newKeySet();
   private final Set<UUID> vouchedBy = ConcurrentHashMap.newKeySet();
   private int daysAlive;
   private Death death = Death.NONE;
   private boolean firstExposedImpostor;

   public FakeHumanPlayer(UUID uuid) {
      this.uuid = uuid;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public Role role() {
      return this.role;
   }

   public void setRole(Role role) {
      this.role = role;
   }

   public List<PersonaTag> tags() {
      return this.tags;
   }

   public IdCard card() {
      return this.card;
   }

   public void setCard(IdCard card) {
      this.card = card;
   }

   public Supply supply() {
      return this.supply;
   }

   public void setSupply(Supply supply) {
      this.supply = supply;
   }

   public String alias() {
      return this.card == null ? "访客" : this.card.name();
   }

   public Zone zone() {
      return this.zone;
   }

   public void setZone(Zone zone) {
      this.zone = zone;
   }

   public boolean alive() {
      return this.alive;
   }

   public boolean bound() {
      return this.bound;
   }

   public void setBound(boolean bound) {
      this.bound = bound;
   }

   public int bedIndex() {
      return this.bedIndex;
   }

   public void setBedIndex(int bedIndex) {
      this.bedIndex = bedIndex;
   }

   public boolean refusedId() {
      return this.refusedId;
   }

   public void setRefusedId(boolean refusedId) {
      this.refusedId = refusedId;
   }

   public boolean admittedByKeeper() {
      return this.admittedByKeeper;
   }

   public void setAdmittedByKeeper(boolean admittedByKeeper) {
      this.admittedByKeeper = admittedByKeeper;
   }

   public boolean leftPermanently() {
      return this.leftPermanently;
   }

   public boolean wronged() {
      return this.wronged;
   }

   public Set<UUID> suspectedBy() {
      return this.suspectedBy;
   }

   public Set<UUID> vouchedBy() {
      return this.vouchedBy;
   }

   public int daysAlive() {
      return this.daysAlive;
   }

   public int maxImpostorDays() {
      return this.maxImpostorDays;
   }

   public void bumpDayAlive() {
      if (this.alive) {
         this.daysAlive++;
         if (this.impostor()) {
            this.maxImpostorDays = Math.max(this.maxImpostorDays, this.daysAlive);
         }
      }
   }

   public Death death() {
      return this.death;
   }

   public boolean firstExposedImpostor() {
      return this.firstExposedImpostor;
   }

   public void setFirstExposedImpostor(boolean firstExposedImpostor) {
      this.firstExposedImpostor = firstExposedImpostor;
   }

   public boolean impostor() {
      return this.role == Role.IMPOSTOR;
   }

   public boolean keeper() {
      return this.role == Role.KEEPER;
   }

   public boolean unarrived() {
      return this.alive && !this.keeper() && this.zone == Zone.SPECTATE;
   }

   public void kill(Death death) {
      this.alive = false;
      this.bound = false;
      this.zone = Zone.DEAD;
      this.death = death;
      if (death == Death.LEAVE || death == Death.OVERTHROW) {
         this.leftPermanently = true;
      }
      if ((death == Death.STONE || death == Death.GUN) && !this.impostor() && !this.keeper()) {
         this.wronged = true;
      }
   }

   public void reincarnate(Role role, IdCard card, Supply supply, List<PersonaTag> tags) {
      this.alive = true;
      this.bound = false;
      this.zone = Zone.SPECTATE;
      this.death = Death.NONE;
      this.admittedByKeeper = false;
      this.refusedId = false;
      this.bedIndex = -1;
      this.daysAlive = 0;
      this.role = role;
      this.card = card;
      this.supply = supply;
      this.tags.clear();
      this.tags.addAll(tags);
      this.suspectedBy.clear();
      this.vouchedBy.clear();
   }
}
