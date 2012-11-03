/*
 * Copyright 2009 (c) Florian Frankenberger (darkblue.de)
 * 
 * This file is part of LEA.
 * 
 * LEA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * LEA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with LEA. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * No description given.
 * 
 * @author Florian Frankenberger
 */
@LATTICE("IMG")
@METHODDEFAULT("OUT<THIS,THIS<IN,OUT*,THISLOC=THIS,RETURNLOC=OUT,GLOBALLOC=THIS")
class EyeDetector {

  @LOC("IMG")
  private int width;
  @LOC("IMG")
  private int height;
  @LOC("IMG")
  private int[] pixelBuffer;
  @LOC("IMG")
  double percent;

  // public EyeDetector(Image image, Rectangle2D faceRect) {
  public EyeDetector(Image image, double fx, double fy, double fwidth, double fheight) {

    percent = 0.15 * fwidth;
    Rectangle2D adjustedFaceRect =
        new Rectangle2D(fx + percent, fy + percent, fwidth - percent, fheight - 2 * percent);
    // percent = 0.15 * faceRect.getWidth();
    // Rectangle2D adjustedFaceRect =
    // new Rectangle2D(faceRect.getX() + percent, faceRect.getY() + percent, faceRect.getWidth()
    // - percent, faceRect.getHeight() - 2 * percent);

    width = (int) adjustedFaceRect.getWidth() / 2;
    height = (int) adjustedFaceRect.getHeight() / 2;
    pixelBuffer = new int[width * height];

    int startX = (int) adjustedFaceRect.getX();
    int startY = (int) adjustedFaceRect.getY();

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixelBuffer[(y * width) + x] = (int) image.getPixel(x + startX, y + startY);
      }
    }

  }

  @LATTICE("OUT<V,V<C,C<THIS,C*,V*,THISLOC=THIS,RETURNLOC=OUT,GLOBALLOC=THIS")
  public Point detectEye() {
    @LOC("OUT") Point eyePosition = null;
    @LOC("V") float brightness = 255f;
    for (@LOC("C") int y = 0; y < height; ++y) {
      for (@LOC("C") int x = 0; x < width; ++x) {
        @LOC("V") final int position = y * width + x;
        @LOC("V") final int[] color =
            new int[] { (pixelBuffer[position] & 0xFF0000) >> 16,
                (pixelBuffer[position] & 0x00FF00) >> 8, pixelBuffer[position] & 0x0000FF };
        // System.out.println("("+x+","+y+")="+color[0]+" "+color[1]+" "+color[2]);
        @LOC("V") final float acBrightness = getBrightness(color);

        if (acBrightness < brightness) {
          eyePosition = new Point(x + (int) percent, y + (int) percent);
          brightness = acBrightness;
        }
      }
    }

    return eyePosition;
  }

  @LATTICE("OUT<V,V<G,G<IN,G<THIS,THISLOC=THIS,GLOBALLOC=G,RETURNLOC=OUT")
  private static float getBrightness(@LOC("IN") int[] color) {
    @LOC("V") int min = Math.min(Math.min(color[0], color[1]), color[2]);
    @LOC("V") int max = Math.max(Math.max(color[0], color[1]), color[2]);

    return 0.5f * (max + min);
  }
}
