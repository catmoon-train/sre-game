package net.exmo.sreGame.buildwar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.draw.DrawSnapshot;

public final class BuildGroup {
   private final int id;
   private final Plot plot;
   private String startWord;
   private final List<UUID> members = new ArrayList<>();
   private final List<String> wordChain = new ArrayList<>();
   private final List<PlotSnapshot> snapshots = new ArrayList<>();
   private final List<DrawSnapshot> drawings = new ArrayList<>();
   private final List<List<UUID>> buildersBySnap = new ArrayList<>();
   private String currentWord;

   public BuildGroup(int id, Plot plot, String startWord) {
      this.id = id;
      this.plot = plot;
      this.startWord = startWord;
      this.currentWord = startWord;
      this.wordChain.add(startWord);
   }

   public int id() {
      return this.id;
   }

   public Plot plot() {
      return this.plot;
   }

   public String startWord() {
      return this.startWord;
   }

   public void setOpeningWord(String word) {
      String safe = word == null ? "" : word.trim();
      if (safe.isEmpty()) {
         return;
      }
      this.startWord = safe;
      this.currentWord = safe;
      this.wordChain.clear();
      this.wordChain.add(safe);
   }

   public String currentWord() {
      return this.currentWord;
   }

   public void setCurrentWord(String word) {
      this.currentWord = word;
   }

   public void addMember(UUID uuid) {
      if (uuid != null && !this.members.contains(uuid)) {
         this.members.add(uuid);
      }
   }

   public boolean contains(UUID uuid) {
      return this.members.contains(uuid);
   }

   public List<UUID> members() {
      return this.members;
   }

   public List<String> wordChain() {
      return this.wordChain;
   }

   public String chainText() {
      return String.join(" → ", this.wordChain);
   }

   public String finalWord() {
      return this.wordChain.isEmpty() ? this.startWord : this.wordChain.get(this.wordChain.size() - 1);
   }

   public List<PlotSnapshot> snapshots() {
      return this.snapshots;
   }

   public void addSnapshot(PlotSnapshot snapshot) {
      if (snapshot != null) {
         this.snapshots.add(snapshot);
      }
   }

   public List<DrawSnapshot> drawings() {
      return this.drawings;
   }

   public void addDrawing(DrawSnapshot snapshot) {
      if (snapshot != null) {
         this.drawings.add(snapshot);
      }
   }

   public void addSnapBuilders(List<UUID> builders) {
      this.buildersBySnap.add(builders == null ? List.of() : List.copyOf(builders));
   }

   public List<UUID> buildersAt(int snap) {
      if (snap < 0 || snap >= this.buildersBySnap.size()) {
         return List.of();
      }
      return this.buildersBySnap.get(snap);
   }

   public void recordWord(String word) {
      this.wordChain.add(word);
      this.currentWord = word;
   }

   public String wordForSnapshot(int index) {
      if (index >= 0 && index < this.wordChain.size()) {
         return this.wordChain.get(index);
      }
      return this.currentWord;
   }
}
