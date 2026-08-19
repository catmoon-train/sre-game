package net.exmo.sreGame.chicken;

public final class TrackLayout {
   public static final int BASE_LENGTH = 168;
   public static final int SPAWN_LEN = 8;
   public static final int SIDE_PAD = 4;
   public static final int MAX_LENGTH = 216;
   public static final int MAX_LANE_WIDTH = 5;
   public static final int MAX_SIZE_X = MAX_LENGTH;
   public static final int MAX_SIZE_Z = MAX_LANE_WIDTH + SIDE_PAD * 2;

   private final int sizeX;
   private final int laneWidth;

   private TrackLayout(int sizeX, int laneWidth) {
      this.sizeX = sizeX;
      this.laneWidth = laneWidth;
   }

   public static TrackLayout of(int length, int laneWidth) {
      int x = length <= 120 ? 120 : length >= 216 ? 216 : 168;
      int w = laneWidth <= 2 ? 2 : laneWidth >= 5 ? 5 : 3;
      return new TrackLayout(x, w);
   }

   public static TrackLayout defaults() {
      return of(BASE_LENGTH, 3);
   }

   public int sizeX() {
      return this.sizeX;
   }

   public int sizeZ() {
      return this.laneWidth + SIDE_PAD * 2;
   }

   public int laneWidth() {
      return this.laneWidth;
   }

   public int laneZ() {
      return SIDE_PAD;
   }

   public int finishMin() {
      return this.sizeX - SPAWN_LEN;
   }

   public int mapX(int base) {
      int mapped = (int) Math.round(base * (this.sizeX / (double) BASE_LENGTH));
      return Math.max(1, Math.min(this.sizeX - 2, mapped));
   }
}
