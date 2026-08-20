package net.exmo.sreGame.games.draw;

import java.util.List;

public final class DrawSnapshot {
   public record Stroke(double relX, double relY, double relZ, float scale, String block) {
   }

   private final List<Stroke> strokes;
   private final String background;

   public DrawSnapshot(List<Stroke> strokes) {
      this(strokes, "minecraft:white_concrete");
   }

   public DrawSnapshot(List<Stroke> strokes, String background) {
      this.strokes = List.copyOf(strokes);
      this.background = background == null || background.isBlank() ? "minecraft:white_concrete" : background;
   }

   public List<Stroke> strokes() {
      return this.strokes;
   }

   public String background() {
      return this.background;
   }

   public boolean isEmpty() {
      return this.strokes.isEmpty();
   }
}
