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


public class LEAImplementation {

  
  private ClassifierTree classifierTree;


  public LEAImplementation() {
    this.loadFaceData();
  }

  
  
  public FaceAndEyePosition getEyePosition( Image image) {
    return classifierTree.getEyePosition(image);
  }

  public boolean needsCalibration() {
    return false;
  }

  /**
   * This method loads the faceData from a file called facedata.dat which should be within the
   * jar-file
   */
  private void loadFaceData() {

    FileInputStream inputFile = new FileInputStream("facedata.dat");

    int numClassifier = Integer.parseInt(inputFile.readLine());
    classifierTree = new ClassifierTree(numClassifier);
    for (int c = 0; c < numClassifier; c++) {

      int numArea = Integer.parseInt(inputFile.readLine());
      Classifier classifier = new Classifier(numArea);
      // parsing areas
      for (int idx = 0; idx < numArea; idx++) {
        // 54,54,91,62,296.0
        Point fromPoint = new Point();
        Point toPoint = new Point();
        fromPoint.x = Integer.parseInt(inputFile.readLine());
        fromPoint.y = Integer.parseInt(inputFile.readLine());
        toPoint.x = Integer.parseInt(inputFile.readLine());
        toPoint.y = Integer.parseInt(inputFile.readLine());
        float size = Float.parseFloat(inputFile.readLine());
        ScanArea area = new ScanArea(fromPoint, toPoint, size);
        classifier.setScanArea(idx, area);
      }

      // parsing possibilities face yes
      float array[] = new float[numArea];
      for (int idx = 0; idx < numArea; idx++) {
        array[idx] = Float.parseFloat(inputFile.readLine());
      }
      classifier.setPossibilitiesFaceYes(array);

      // parsing possibilities face no
      array = new float[numArea];
      for (int idx = 0; idx < numArea; idx++) {
        array[idx] = Float.parseFloat(inputFile.readLine());
      }
      classifier.setPossibilitiesFaceNo(array);

      classifier.setPossibilityFaceYes(Integer.parseInt(inputFile.readLine()));
      classifier.setPossibilityFaceNo(Integer.parseInt(inputFile.readLine()));

      classifierTree.addClassifier(c, classifier);
    }
  }

}
