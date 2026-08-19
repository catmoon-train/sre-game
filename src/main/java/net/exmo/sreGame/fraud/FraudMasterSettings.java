package net.exmo.sreGame.fraud;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class FraudMasterSettings {
   private boolean busyMode;
   private boolean doubleRound = true;
   private boolean callTax;
   private boolean anonymousVote;

   public boolean busyMode() {
      return this.busyMode;
   }

   public void cycleBusyMode() {
      this.busyMode = !this.busyMode;
   }

   public String callModeLabel() {
      return this.busyMode ? "占线" : "来电等待";
   }

   public boolean doubleRound() {
      return this.doubleRound;
   }

   public void cycleDoubleRound() {
      this.doubleRound = !this.doubleRound;
   }

   public boolean callTax() {
      return this.callTax;
   }

   public void cycleCallTax() {
      this.callTax = !this.callTax;
   }

   public boolean anonymousVote() {
      return this.anonymousVote;
   }

   public void cycleAnonymousVote() {
      this.anonymousVote = !this.anonymousVote;
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("busyMode", this.busyMode);
      data.put("doubleRound", this.doubleRound);
      data.put("callTax", this.callTax);
      data.put("anonymousVote", this.anonymousVote);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.busyMode = SettingsIo.asBool(data, "busyMode", this.busyMode);
      this.doubleRound = SettingsIo.asBool(data, "doubleRound", this.doubleRound);
      this.callTax = SettingsIo.asBool(data, "callTax", this.callTax);
      this.anonymousVote = SettingsIo.asBool(data, "anonymousVote", this.anonymousVote);
   }
}
