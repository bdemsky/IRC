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
 *   private Point readEyes( Image image,  Rectangle2D rect) {
  EyeDetector ed = new EyeDetector(image, rect);
 return ed.detectEye();
 }
 * You should have received a copy of the GNU Lesser General Public License
 * along with LEA. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * 
 * @author Florian
 */


public class ClassifierTree {

  
  private Classifier[] classifiers;
  
  double x;
  
  double y;
  
  double width;
  
  double height;

  
  int size;

  
  public ClassifierTree( int size) {
    this.size = size;
    classifiers = new Classifier[size];
    x = -1;
    y = -1;
    width = -1;
    height = -1;
  }

  public void addClassifier( int idx,  Classifier c) {
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
  
  public void locateFaceRadial( Image smallImage) {

     double px = x;
     double py = y;
     double pwidth = width;
     double pheight = height;

    x = -1;
    y = -1;
    width = -1;
    height = -1;

     IntegralImageData imageData = new IntegralImageData(smallImage);
     float originalImageFactor = 1;
    if (px == -1) {
      // if(true){
      // if we don't have a last coordinate we just begin in the center
       int smallImageMaxDimension = Math.min(smallImage.getWidth(), smallImage.getHeight());

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


     float startFactor = (float) (pwidth / 100.0f);

    // first we calculate the maximum scale factor for our 200x200 image
     float maxScaleFactor = Math.min(imageData.getWidth() / 100f, imageData.getHeight() / 100f);
    // maxScaleFactor = 1.0f;

    // we simply won't recognize faces that are smaller than 40x40 px
     float minScaleFactor = 0.5f;

     float maxScaleDifference = Math.max(Math.abs(maxScaleFactor - startFactor), Math.abs(minScaleFactor - startFactor));

    // border for faceYes-possibility must be greater that that
     float maxBorder = 0.999f;

     int startPosX = (int) px;
     int startPosY = (int) py;

     int loopidx = 0;
    TERMINATE: for ( float factorDiff = 0.0f; Math.abs(factorDiff) <= maxScaleDifference; factorDiff =
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

       float factor = startFactor + factorDiff;
      if (factor > maxScaleFactor || factor < minScaleFactor)
        continue;

      // now we calculate the actualDimmension
       int actualDimmension = (int) (100 * factor);
       int maxX = imageData.getWidth() - actualDimmension;
       int maxY = imageData.getHeight() - actualDimmension;

       int maxDiffX = Math.max(Math.abs(startPosX - maxX), startPosX);
       int maxDiffY = Math.max(Math.abs(startPosY - maxY), startPosY);

       int xidx = 0;
      TERMINATE: for ( float xDiff = 0.1f; Math.abs(xDiff) <= maxDiffX; xDiff =
          (xDiff + sgn(xDiff) * 0.5f) * -1) {

        if (++xidx > 1000) {
          px = -1;
          py = -1;
          pwidth = -1;
          pheight = -1;
          return;
        }

         int xPos = Math.round((float) (startPosX + xDiff));

        if (xPos < 0 || xPos > maxX)
          continue;

         int yidx = 0;
        // yLines:
        TERMINATE: for ( float yDiff = 0.1f; Math.abs(yDiff) <= maxDiffY; yDiff =
            (yDiff + sgn(yDiff) * 0.5f) * -1) {

          if (++yidx > 1000) {
            px = -1;
            py = -1;
            pwidth = -1;
            pheight = -1;
            return;
          }

           int yPos = Math.round(startPosY + yDiff);
          if (yPos < 0 || yPos > maxY)
            continue;

          // by now we should have a valid coordinate to process which we should
          // do now
           boolean backToYLines = false;
          for ( int idx = 0; idx < size; ++idx) {
             float borderline = 0.8f + (idx / (size - 1)) * (maxBorder - 0.8f);
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


  }

  
  
  private static int sgn( float value) {
    return (value < 0 ? -1 : (value > 0 ? +1 : 1));
  }

  
  public FaceAndEyePosition getEyePosition( Image image) {
    if (image == null) {
      return null;
    }

     float originalImageFactor = 1;

    locateFaceRadial(image);

    if (width > image.getWidth() || height > image.getHeight()) {
      return null;
    }

     EyePosition eyePosition = null;

    if (x != -1) {
       EyeDetector ed = new EyeDetector(image, x, y, width, height);
       Point point = ed.detectEye();
      if (point != null) {
        eyePosition = new EyePosition(point.getX(), point.getY());
      }
    }

    System.out.println("eyePosition=" + eyePosition);

     FaceAndEyePosition fep = new FaceAndEyePosition(x, y, width, height, eyePosition);


    return fep;
  }


}
