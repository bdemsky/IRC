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
public class ClassifierTree {

  private ArrayList classifiers;

  public ClassifierTree() {
    classifiers = new ArrayList();
  }

  public void addClassifier(Classifier c) {
    classifiers.add(c);
  }

  // public static BufferedImage resizeImageFittingInto(BufferedImage image, int
  // dimension) {
  //
  // int newHeight = 0;
  // int newWidth = 0;
  // float factor = 0;
  // if (image.getWidth() > image.getHeight()) {
  // factor = dimension / (float) image.getWidth();
  // newWidth = dimension;
  // newHeight = (int) (factor * image.getHeight());
  // } else {
  // factor = dimension / (float) image.getHeight();
  // newHeight = dimension;
  // newWidth = (int) (factor * image.getWidth());
  // }
  //
  // if (factor > 1) {
  // BufferedImageOp op = new
  // ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
  // BufferedImage tmpImage = op.filter(image, null);
  //
  // return tmpImage;
  // }
  //
  // BufferedImage resizedImage = new BufferedImage(newWidth, newHeight,
  // BufferedImage.TYPE_INT_RGB);
  //
  // Graphics2D g2D = resizedImage.createGraphics();
  // g2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
  // RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  //
  // g2D.drawImage(image, 0, 0, newWidth - 1, newHeight - 1, 0, 0,
  // image.getWidth() - 1,
  // image.getHeight() - 1, null);
  //
  // BufferedImageOp op = new
  // ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
  // BufferedImage tmpImage = op.filter(resizedImage, null);
  //
  // return tmpImage;
  // }
  //
  // /**
  // * Image should have 100x100px and should be in b/w
  // *
  // * @param image
  // */
  // public void learn(BufferedImage image, boolean isFace) {
  // IntegralImageData imageData = new IntegralImageData(image);
  // for (Classifier classifier : this.classifiers) {
  // classifier.learn(imageData, isFace);
  // }
  // }
  //
  // public int getLearnedFacesYes() {
  // return this.classifiers.get(0).getLearnedFacesYes();
  // }
  //
  // public int getLearnedFacesNo() {
  // return this.classifiers.get(0).getLearnedFacesNo();
  // }

  /**
   * Locates a face by linear iteration through all probable face positions
   * 
   * @deprecated use locateFaceRadial instead for improved performance
   * @param image
   * @return an rectangle representing the actual face position on success or
   *         null if no face could be detected
   */
  // public Rectangle2D locateFace(BufferedImage image) {
  // long timeStart = System.currentTimeMillis();
  //
  // int resizeTo = 600;
  //
  // BufferedImage smallImage = resizeImageFittingInto(image, resizeTo);
  // IntegralImageData imageData = new IntegralImageData(smallImage);
  //
  // float factor = image.getWidth() / (float) smallImage.getWidth();
  //
  // int maxIterations = 0;
  //
  // // first we calculate the maximum scale factor for our 200x200 image
  // float maxScaleFactor = Math.min(imageData.getWidth() / 100f,
  // imageData.getHeight() / 100f);
  //
  // // we simply won't recognize faces that are smaller than 40x40 px
  // float minScaleFactor = 0.5f;
  //
  // // border for faceYes-possibility must be greater that that
  // float maxBorder = 0.999f;
  //
  // for (float scale = maxScaleFactor; scale > minScaleFactor; scale -= 0.25) {
  // int actualDimension = (int) (scale * 100);
  // int borderX = imageData.getWidth() - actualDimension;
  // int borderY = imageData.getHeight() - actualDimension;
  // for (int x = 0; x <= borderX; ++x) {
  // yLines: for (int y = 0; y <= borderY; ++y) {
  //
  // for (int iterations = 0; iterations < this.classifiers.size();
  // ++iterations) {
  // Classifier classifier = this.classifiers.get(iterations);
  //
  // float borderline =
  // 0.8f + (iterations / this.classifiers.size() - 1) * (maxBorder - 0.8f);
  // if (iterations > maxIterations)
  // maxIterations = iterations;
  // if (!classifier.classifyFace(imageData, scale, x, y, borderline)) {
  // continue yLines;
  // }
  // }
  //
  // // if we reach here we have a face recognized because our image went
  // // through all
  // // classifiers
  //
  // Rectangle2D faceRect =
  // new Rectangle2D.Float(x * factor, y * factor, actualDimension * factor,
  // actualDimension * factor);
  //
  // System.out.println("Time: " + (System.currentTimeMillis() - timeStart) +
  // "ms");
  // return faceRect;
  //
  // }
  // }
  // }
  //
  // return null;
  // }

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

  public Rectangle2D locateFaceRadial(Image smallImage, Rectangle2D lastCoordinates) {

    IntegralImageData imageData = new IntegralImageData(smallImage);
    float originalImageFactor = 1;

    if (lastCoordinates == null) {
      // if we don't have a last coordinate we just begin in the center
      int smallImageMaxDimension = Math.min(smallImage.getWidth(), smallImage.getHeight());
      lastCoordinates =
          new Rectangle2D((smallImage.getWidth() - smallImageMaxDimension) / 2.0,
              (smallImage.getHeight() - smallImageMaxDimension) / 2.0, smallImageMaxDimension,
              smallImageMaxDimension);
//      System.out.println("lastCoordinates=" + lastCoordinates);
    } else {
      // first we have to scale the last coodinates back relative to the resized
      // image
      lastCoordinates =
          new Rectangle2D((lastCoordinates.getX() * (1 / originalImageFactor)),
              (lastCoordinates.getY() * (1 / originalImageFactor)),
              (lastCoordinates.getWidth() * (1 / originalImageFactor)),
              (lastCoordinates.getHeight() * (1 / originalImageFactor)));
    }

    float startFactor = (float) (lastCoordinates.getWidth() / 100.0f);

    // first we calculate the maximum scale factor for our 200x200 image
    float maxScaleFactor = Math.min(imageData.getWidth() / 100f, imageData.getHeight() / 100f);
    // maxScaleFactor = 1.0f;

    // we simply won't recognize faces that are smaller than 40x40 px
    float minScaleFactor = 0.5f;

    float maxScaleDifference =
        Math.max(Math.abs(maxScaleFactor - startFactor), Math.abs(minScaleFactor - startFactor));

    // border for faceYes-possibility must be greater that that
    float maxBorder = 0.999f;

    int startPosX = (int) lastCoordinates.getX();
    int startPosY = (int) lastCoordinates.getX();

    for (float factorDiff = 0.0f; Math.abs(factorDiff) <= maxScaleDifference; factorDiff =
        (factorDiff + sgn(factorDiff) * 0.1f) * -1 // we alternate between
                                                   // negative and positiv
                                                   // factors
    ) {

      float factor = startFactor + factorDiff;
//      System.out.println("factor=" + factor);
      if (factor > maxScaleFactor || factor < minScaleFactor)
        continue;

      // now we calculate the actualDimmension
      int actualDimmension = (int) (100 * factor);
      int maxX = imageData.getWidth() - actualDimmension;
      int maxY = imageData.getHeight() - actualDimmension;

      int maxDiffX = Math.max(Math.abs(startPosX - maxX), startPosX);
      int maxDiffY = Math.max(Math.abs(startPosY - maxY), startPosY);

      for (float xDiff = 0.1f; Math.abs(xDiff) <= maxDiffX; xDiff =
          (xDiff + sgn(xDiff) * 0.5f) * -1) {
        int xPos = Math.round((float) (startPosX + xDiff));
        if (xPos < 0 || xPos > maxX)
          continue;

        // yLines:
        for (float yDiff = 0.1f; Math.abs(yDiff) <= maxDiffY; yDiff =
            (yDiff + sgn(yDiff) * 0.5f) * -1) {
          int yPos = Math.round(startPosY + yDiff);
          if (yPos < 0 || yPos > maxY)
            continue;

          // by now we should have a valid coordinate to process which we should
          // do now
          boolean backToYLines = false;
          for (int iterations = 0; iterations < classifiers.size(); ++iterations) {
            Classifier classifier = (Classifier) classifiers.get(iterations);

            float borderline = 0.8f + (iterations / (classifiers.size() - 1)) * (maxBorder - 0.8f);
            if (!classifier.classifyFace(imageData, factor, xPos, yPos, borderline)) {
//              System.out.println("continue yLines; ");
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
          Rectangle2D faceRect =
              new Rectangle2D(xPos * originalImageFactor, yPos * originalImageFactor,
                  actualDimmension * originalImageFactor, actualDimmension * originalImageFactor);

          return faceRect;

        }

      }

    }

    // System.out.println("Time: "+(System.currentTimeMillis()-timeStart)+"ms");
    return null;

  }

  // public Rectangle2D locateFaceRadial(BufferedImage image, Rectangle2D
  // lastCoordinates) {
  //
  // int resizeTo = 600;
  //
  // BufferedImage smallImage = resizeImageFittingInto(image, resizeTo);
  // float originalImageFactor = image.getWidth() / (float)
  // smallImage.getWidth();
  // IntegralImageData imageData = new IntegralImageData(smallImage);
  //
  // if (lastCoordinates == null) {
  // // if we don't have a last coordinate we just begin in the center
  // int smallImageMaxDimension = Math.min(smallImage.getWidth(),
  // smallImage.getHeight());
  // lastCoordinates =
  // new Rectangle2D.Float((smallImage.getWidth() - smallImageMaxDimension) /
  // 2.0f,
  // (smallImage.getHeight() - smallImageMaxDimension) / 2.0f,
  // smallImageMaxDimension,
  // smallImageMaxDimension);
  // } else {
  // // first we have to scale the last coodinates back relative to the resized
  // // image
  // lastCoordinates =
  // new Rectangle2D.Float((float) (lastCoordinates.getX() * (1 /
  // originalImageFactor)),
  // (float) (lastCoordinates.getY() * (1 / originalImageFactor)),
  // (float) (lastCoordinates.getWidth() * (1 / originalImageFactor)),
  // (float) (lastCoordinates.getHeight() * (1 / originalImageFactor)));
  // }
  //
  // float startFactor = (float) (lastCoordinates.getWidth() / 100.0f);
  //
  // // first we calculate the maximum scale factor for our 200x200 image
  // float maxScaleFactor = Math.min(imageData.getWidth() / 100f,
  // imageData.getHeight() / 100f);
  // // maxScaleFactor = 1.0f;
  //
  // // we simply won't recognize faces that are smaller than 40x40 px
  // float minScaleFactor = 0.5f;
  //
  // float maxScaleDifference =
  // Math.max(Math.abs(maxScaleFactor - startFactor), Math.abs(minScaleFactor -
  // startFactor));
  //
  // // border for faceYes-possibility must be greater that that
  // float maxBorder = 0.999f;
  //
  // int startPosX = (int) lastCoordinates.getX();
  // int startPosY = (int) lastCoordinates.getX();
  //
  // for (float factorDiff = 0.0f; Math.abs(factorDiff) <= maxScaleDifference;
  // factorDiff =
  // (factorDiff + sgn(factorDiff) * 0.1f) * -1 // we alternate between
  // // negative and positiv
  // // factors
  // ) {
  //
  // float factor = startFactor + factorDiff;
  // if (factor > maxScaleFactor || factor < minScaleFactor)
  // continue;
  //
  // // now we calculate the actualDimmension
  // int actualDimmension = (int) (100 * factor);
  // int maxX = imageData.getWidth() - actualDimmension;
  // int maxY = imageData.getHeight() - actualDimmension;
  //
  // int maxDiffX = Math.max(Math.abs(startPosX - maxX), startPosX);
  // int maxDiffY = Math.max(Math.abs(startPosY - maxY), startPosY);
  //
  // for (float xDiff = 0.1f; Math.abs(xDiff) <= maxDiffX; xDiff =
  // (xDiff + sgn(xDiff) * 0.5f) * -1) {
  // int xPos = Math.round(startPosX + xDiff);
  // if (xPos < 0 || xPos > maxX)
  // continue;
  //
  // yLines: for (float yDiff = 0.1f; Math.abs(yDiff) <= maxDiffY; yDiff =
  // (yDiff + sgn(yDiff) * 0.5f) * -1) {
  // int yPos = Math.round(startPosY + yDiff);
  // if (yPos < 0 || yPos > maxY)
  // continue;
  //
  // // by now we should have a valid coordinate to process which we should
  // // do now
  // for (int iterations = 0; iterations < this.classifiers.size();
  // ++iterations) {
  // Classifier classifier = this.classifiers.get(iterations);
  //
  // float borderline =
  // 0.8f + (iterations / (this.classifiers.size() - 1)) * (maxBorder - 0.8f);
  //
  // if (!classifier.classifyFace(imageData, factor, xPos, yPos, borderline)) {
  // continue yLines;
  // }
  // }
  //
  // // if we reach here we have a face recognized because our image went
  // // through all
  // // classifiers
  //
  // Rectangle2D faceRect =
  // new Rectangle2D.Float(xPos * originalImageFactor, yPos *
  // originalImageFactor,
  // actualDimmension * originalImageFactor, actualDimmension *
  // originalImageFactor);
  //
  // return faceRect;
  //
  // }
  //
  // }
  //
  // }
  //
  // //
  // System.out.println("Time: "+(System.currentTimeMillis()-timeStart)+"ms");
  // return null;
  //
  // }

  // public List<Classifier> getClassifiers() {
  // return new ArrayList<Classifier>(this.classifiers);
  // }
  //
  // public static void saveToXml(OutputStream out, ClassifierTree tree) throws
  // IOException {
  // PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
  // writer.write(xStream.toXML(tree));
  // writer.close();
  // }
  //
  // public static ClassifierTree loadFromXml(InputStream in) throws IOException
  // {
  // Reader reader = new InputStreamReader(in, "UTF-8");
  // StringBuilder sb = new StringBuilder();
  //
  // char[] buffer = new char[1024];
  // int read = 0;
  // do {
  // read = reader.read(buffer);
  // if (read > 0) {
  // sb.append(buffer, 0, read);
  // }
  // } while (read > -1);
  // reader.close();
  //
  // return (ClassifierTree) xStream.fromXML(sb.toString());
  // }

  private static int sgn(float value) {
    return (value < 0 ? -1 : (value > 0 ? +1 : 1));
  }

}
