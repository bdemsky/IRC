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
 * 
 * @author Florian
 */
@LATTICE("C")
@METHODDEFAULT("OUT<THIS,THIS<IN,THISLOC=THIS,RETURNLOC=OUT")
public class ClassifierTree {

  @LOC("C")
  private Classifier classifiers[];

  public ClassifierTree(int size) {
    classifiers = new Classifier[size];
  }

  public void addClassifier(@LOC("IN") int idx, @LOC("IN") Classifier c) {
    classifiers[idx] = c;
  }

  /**
   * Locates a face by searching radial starting at the last known position. If
   * lastCoordinates are null we simply start in the center of the image.
   * <p>
   * TODO: This method could quite possible be tweaked so that face recognition
   * would be much faster
   * 
   * @param image
   *          the image to process
   * @param lastCoordinates
   *          the last known coordinates or null if unknown
   * @return an rectangle representing the actual face position on success or
   *         null if no face could be detected
   */
  @LATTICE("OUT,THIS<CXY,CXY<V,V<IMG,IMG<C,C<IN,C*,FACTOR*,CXY*,THISLOC=THIS,RETURNLOC=OUT")
  public Rectangle2D locateFaceRadial(@LOC("IN") Image smallImage,
      @LOC("C") Rectangle2D lastCoordinates) {

    @LOC("IMG") IntegralImageData imageData = new IntegralImageData(smallImage);
    @LOC("IN") float originalImageFactor = 1;

    if (lastCoordinates == null) {
      // if we don't have a last coordinate we just begin in the center
      @LOC("C") int smallImageMaxDimension =
          Math.min(smallImage.getWidth(), smallImage.getHeight());
      lastCoordinates =
          new Rectangle2D((smallImage.getWidth() - smallImageMaxDimension) / 2.0,
              (smallImage.getHeight() - smallImageMaxDimension) / 2.0, smallImageMaxDimension,
              smallImageMaxDimension);
      // System.out.println("lastCoordinates=" + lastCoordinates);
    } else {
      // first we have to scale the last coodinates back relative to the resized
      // image
      lastCoordinates =
          new Rectangle2D((lastCoordinates.getX() * (1 / originalImageFactor)),
              (lastCoordinates.getY() * (1 / originalImageFactor)),
              (lastCoordinates.getWidth() * (1 / originalImageFactor)),
              (lastCoordinates.getHeight() * (1 / originalImageFactor)));
    }

    @LOC("V") float startFactor = (float) (lastCoordinates.getWidth() / 100.0f);

    // first we calculate the maximum scale factor for our 200x200 image
    @LOC("V") float maxScaleFactor =
        Math.min(imageData.getWidth() / 100f, imageData.getHeight() / 100f);
    // maxScaleFactor = 1.0f;

    // we simply won't recognize faces that are smaller than 40x40 px
    @LOC("V") float minScaleFactor = 0.5f;

    @LOC("V") float maxScaleDifference =
        Math.max(Math.abs(maxScaleFactor - startFactor), Math.abs(minScaleFactor - startFactor));

    // border for faceYes-possibility must be greater that that
    @LOC("V") float maxBorder = 0.999f;

    @LOC("V") int startPosX = (int) lastCoordinates.getX();
    @LOC("V") int startPosY = (int) lastCoordinates.getX();

    for (@LOC("C") float factorDiff = 0.0f; Math.abs(factorDiff) <= maxScaleDifference; factorDiff =
        (factorDiff + sgn(factorDiff) * 0.1f) * -1 // we alternate between
                                                   // negative and positiv
                                                   // factors
    ) {

      @LOC("V") float factor = startFactor + factorDiff;
      // System.out.println("factor=" + factor);
      if (factor > maxScaleFactor || factor < minScaleFactor)
        continue;

      // now we calculate the actualDimmension
      @LOC("V") int actualDimmension = (int) (100 * factor);
      @LOC("V") int maxX = imageData.getWidth() - actualDimmension;
      @LOC("V") int maxY = imageData.getHeight() - actualDimmension;

      @LOC("V") int maxDiffX = Math.max(Math.abs(startPosX - maxX), startPosX);
      @LOC("V") int maxDiffY = Math.max(Math.abs(startPosY - maxY), startPosY);

      for (@LOC("CXY") float xDiff = 0.1f; Math.abs(xDiff) <= maxDiffX; xDiff =
          (xDiff + sgn(xDiff) * 0.5f) * -1) {
        @LOC("CXY") int xPos = Math.round((float) (startPosX + xDiff));
        if (xPos < 0 || xPos > maxX)
          continue;

        // yLines:
        for (@LOC("CXY") float yDiff = 0.1f; Math.abs(yDiff) <= maxDiffY; yDiff =
            (yDiff + sgn(yDiff) * 0.5f) * -1) {
          @LOC("CXY") int yPos = Math.round(startPosY + yDiff);
          if (yPos < 0 || yPos > maxY)
            continue;

          // by now we should have a valid coordinate to process which we should
          // do now
          @LOC("CXY") boolean backToYLines = false;
          for (@LOC("CXY") int idx = 0; idx < classifiers.length; ++idx) {
            @LOC("CXY") float borderline =
                0.8f + (idx / (classifiers.length - 1)) * (maxBorder - 0.8f);
            if (!classifiers[idx].classifyFace(imageData, factor, xPos, yPos, borderline)) {
              backToYLines = true;
              break;
              // continue yLines;
            }
          }

          // if we reach here we have a face recognized because our image went
          // through all
          // classifiers

          if (backToYLines) {
            continue;
          }
          @LOC("OUT") Rectangle2D faceRect =
              new Rectangle2D(xPos * originalImageFactor, yPos * originalImageFactor,
                  actualDimmension * originalImageFactor, actualDimmension * originalImageFactor);

          return faceRect;

        }

      }

    }

    // System.out.println("Time: "+(System.currentTimeMillis()-timeStart)+"ms");
    return null;

  }

  private static int sgn(@LOC("IN") float value) {
    return (value < 0 ? -1 : (value > 0 ? +1 : 1));
  }

}
