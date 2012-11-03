import SSJava.PCLOC;

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
 *   private Point readEyes(@LOC("IN") Image image, @LOC("IN") Rectangle2D rect) {
 @LOC("OUT") EyeDetector ed = new EyeDetector(image, rect);
 return ed.detectEye();
 }
 * You should have received a copy of the GNU Lesser General Public License
 * along with LEA. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * 
 * @author Florian
 */
@LATTICE("C<CS,CS<CUR,CUR<PREV,PREV<SIZE,PREV*,C*,CUR*")
@METHODDEFAULT("OUT<THIS,THIS<IN,THISLOC=THIS,RETURNLOC=OUT")
public class ClassifierTree {

  @LOC("CS")
  private Classifier classifiers[];

  @LOC("CUR")
  double x;
  @LOC("CUR")
  double y;
  @LOC("CUR")
  double width;
  @LOC("CUR")
  double height;

  @LOC("SIZE")
  int size;

  @LATTICE("THIS<IN,THISLOC=THIS")
  public ClassifierTree(@LOC("IN") int size) {
    this.size = size;
    classifiers = new Classifier[size];
    x = -1;
    y = -1;
    width = -1;
    height = -1;
  }

  public void addClassifier(@LOC("IN") int idx, @LOC("IN") Classifier c) {
    classifiers[idx] = c;
  }

  /**
   * Locates a face by searching radial starting at the last known position. If lastCoordinates are
   * null we simply start in the center of the image.
   * <p>
   * TODO: This method could quite possible be tweaked so that face recognition would be much faster
   * 
   * @param image
   *          the image to process
   * @param lastCoordinates
   *          the last known coordinates or null if unknown
   * @return an rectangle representing the actual face position on success or null if no face could
   *         be detected
   */
  @LATTICE("OUT<CXY,CXY<THIS,THIS<V,V<IMG,IMG<C,C<IN,C*,V*,FACTOR*,CXY*,THISLOC=THIS,RETURNLOC=OUT,GLOBALLOC=IN")
  public void locateFaceRadial(@LOC("IN") Image smallImage) {

    @LOC("THIS,ClassifierTree.CUR") double px = x;
    @LOC("THIS,ClassifierTree.CUR") double py = y;
    @LOC("THIS,ClassifierTree.CUR") double pwidth = width;
    @LOC("THIS,ClassifierTree.CUR") double pheight = height;

    x = -1;
    y = -1;
    width = -1;
    height = -1;

    @LOC("IMG") IntegralImageData imageData = new IntegralImageData(smallImage);
    @LOC("IN") float originalImageFactor = 1;
    if (px == -1) {
      // if(true){
      // if we don't have a last coordinate we just begin in the center
      @LOC("THIS,ClassifierTree.PREV") int smallImageMaxDimension =
          Math.min(smallImage.getWidth(), smallImage.getHeight());

      px = (smallImage.getWidth() - smallImageMaxDimension) / 2.0;
      py = (smallImage.getHeight() - smallImageMaxDimension) / 2.0;
      pwidth = smallImageMaxDimension;
      pheight = smallImageMaxDimension;
    } else {
      // first we have to scale the last coodinates back relative to the resized
      // image
      px = px * (1 / originalImageFactor);
      py = py * (1 / originalImageFactor);
      pwidth = pwidth * (1 / originalImageFactor);
      pheight = pheight * (1 / originalImageFactor);
    }


    @LOC("THIS,ClassifierTree.CUR") float startFactor = (float) (pwidth / 100.0f);

    // first we calculate the maximum scale factor for our 200x200 image
    @LOC("THIS,ClassifierTree.CUR") float maxScaleFactor =
        Math.min(imageData.getWidth() / 100f, imageData.getHeight() / 100f);
    // maxScaleFactor = 1.0f;

    // we simply won't recognize faces that are smaller than 40x40 px
    @LOC("THIS,ClassifierTree.CUR") float minScaleFactor = 0.5f;

    @LOC("THIS,ClassifierTree.CUR") float maxScaleDifference =
        Math.max(Math.abs(maxScaleFactor - startFactor), Math.abs(minScaleFactor - startFactor));

    // border for faceYes-possibility must be greater that that
    @LOC("THIS,ClassifierTree.CUR") float maxBorder = 0.999f;

    @LOC("THIS,ClassifierTree.CUR") int startPosX = (int) px;
    @LOC("THIS,ClassifierTree.CUR") int startPosY = (int) py;

    @LOC("THIS,ClassifierTree.CUR") int loopidx = 0;
    TERMINATE: for (@LOC("THIS,ClassifierTree.CUR") float factorDiff = 0.0f; Math.abs(factorDiff) <= maxScaleDifference; factorDiff =
        (factorDiff + sgn(factorDiff) * 0.1f) * -1 // we alternate between
                                                   // negative and positiv
                                                   // factors
    ) {

      if (++loopidx > 1000) {
        px = -1;
        py = -1;
        pwidth = -1;
        pheight = -1;
        return;
      }

      @LOC("THIS,ClassifierTree.CUR") float factor = startFactor + factorDiff;
      if (factor > maxScaleFactor || factor < minScaleFactor)
        continue;

      // now we calculate the actualDimmension
      @LOC("THIS,ClassifierTree.CUR") int actualDimmension = (int) (100 * factor);
      @LOC("THIS,ClassifierTree.CUR") int maxX = imageData.getWidth() - actualDimmension;
      @LOC("THIS,ClassifierTree.CUR") int maxY = imageData.getHeight() - actualDimmension;

      @LOC("THIS,ClassifierTree.CUR") int maxDiffX =
          Math.max(Math.abs(startPosX - maxX), startPosX);
      @LOC("THIS,ClassifierTree.CUR") int maxDiffY =
          Math.max(Math.abs(startPosY - maxY), startPosY);

      @LOC("THIS,ClassifierTree.CUR") int xidx = 0;
      TERMINATE: for (@LOC("THIS,ClassifierTree.CUR") float xDiff = 0.1f; Math.abs(xDiff) <= maxDiffX; xDiff =
          (xDiff + sgn(xDiff) * 0.5f) * -1) {

        if (++xidx > 1000) {
          px = -1;
          py = -1;
          pwidth = -1;
          pheight = -1;
          return;
        }

        @LOC("THIS,ClassifierTree.CUR") int xPos = Math.round((float) (startPosX + xDiff));

        if (xPos < 0 || xPos > maxX)
          continue;

        @LOC("THIS,ClassifierTree.CUR") int yidx = 0;
        // yLines:
        TERMINATE: for (@LOC("THIS,ClassifierTree.CUR") float yDiff = 0.1f; Math.abs(yDiff) <= maxDiffY; yDiff =
            (yDiff + sgn(yDiff) * 0.5f) * -1) {

          if (++yidx > 1000) {
            px = -1;
            py = -1;
            pwidth = -1;
            pheight = -1;
            return;
          }

          @LOC("THIS,ClassifierTree.CUR") int yPos = Math.round(startPosY + yDiff);
          if (yPos < 0 || yPos > maxY)
            continue;

          // by now we should have a valid coordinate to process which we should
          // do now
          @LOC("THIS,ClassifierTree.C") boolean backToYLines = false;
          for (@LOC("THIS,ClassifierTree.CUR") int idx = 0; idx < size; ++idx) {
            @LOC("THIS,ClassifierTree.CUR") float borderline =
                0.8f + (idx / (size - 1)) * (maxBorder - 0.8f);
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

          x = xPos * originalImageFactor;
          y = yPos * originalImageFactor;
          width = actualDimmension * originalImageFactor;
          height = actualDimmension * originalImageFactor;
          return;

        }

      }

    }

    // System.out.println("Time: "+(System.currentTimeMillis()-timeStart)+"ms");
    // return null;

  }

  @LATTICE("OUT<IN,OUT<P,P<THIS,THISLOC=THIS,RETURNLOC=OUT")
  @PCLOC("P")
  private static int sgn(@LOC("IN") float value) {
    return (value < 0 ? -1 : (value > 0 ? +1 : 1));
  }

  @LATTICE("OUT<P,P<ED,ED<V,V<THIS,THIS<IN,V*,THISLOC=THIS,RETURNLOC=OUT,GLOBALLOC=IN")
  public FaceAndEyePosition getEyePosition(@LOC("IN") Image image) {
    if (image == null) {
      return null;
    }

    @LOC("IN") float originalImageFactor = 1;

    locateFaceRadial(image);

    if (width > image.getWidth() || height > image.getHeight()) {
      return null;
    }

    @LOC("OUT") EyePosition eyePosition = null;

    if (x != -1) {
      @LOC("ED") EyeDetector ed = new EyeDetector(image, x, y, width, height);
      @LOC("P") Point point = ed.detectEye();
      if (point != null) {
        eyePosition = new EyePosition(point.getX(), point.getY());
      }
    }

    System.out.println("eyePosition=" + eyePosition);

    @LOC("OUT") FaceAndEyePosition fep = new FaceAndEyePosition(x, y, width, height, eyePosition);

    return fep;
  }


}
