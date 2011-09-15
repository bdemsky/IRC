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
public class Classifier {

  private ScanArea[] scanAreas;

  // private float possibilityFace = 0f;
  private float[] possibilities_FaceYes;
  private float[] possibilities_FaceNo;
  private int possibilityFaceYes = 0;
  private int possibilityFaceNo = 0;

  public static final long serialVersionUID = 5168971806943656945l;

  public Classifier(int numScanAreas) {
    this.scanAreas = new ScanArea[numScanAreas];
    this.possibilities_FaceYes = new float[numScanAreas];
    this.possibilities_FaceNo = new float[numScanAreas];
  }

  public void setScanArea(int idx, ScanArea area) {
    scanAreas[idx] = area;
  }

  public void setPossibilitiesFaceYes(float[] arr) {
    this.possibilities_FaceYes = arr;
  }

  public void setPossibilityFaceYes(int v) {
    this.possibilityFaceYes = v;
  }

  public void setPossibilitiesFaceNo(float[] arr) {
    this.possibilities_FaceNo = arr;
  }

  public void setPossibilityFaceNo(int v) {
    this.possibilityFaceNo = v;
  }

  // public void learn(IntegralImageData image, boolean isFace) {
  // long values[] = new long[this.scanAreas.length];
  // //
  // System.out.println("HERE:"+image.getIntegralAt(image.getDimension().width-1,
  // // image.getDimension().height-1));
  // // we assume the image is rectangular so we can simply use one side to
  // // calculate
  // // the scale factor
  // float scaleFactor = image.getDimension().width / 100.0f;
  //
  // float avg = 0f;
  // int avgItems = 0;
  // for (int i = 0; i < this.scanAreas.length; ++i) {
  // ScanArea scanArea = this.scanAreas[i];
  // values[i] = 0l;
  //
  // values[i] += image.getIntegralAt(scanArea.getToX(scaleFactor),
  // scanArea.getToY(scaleFactor));
  // values[i] +=
  // image.getIntegralAt(scanArea.getFromX(scaleFactor),
  // scanArea.getFromY(scaleFactor));
  //
  // values[i] -=
  // image.getIntegralAt(scanArea.getToX(scaleFactor),
  // scanArea.getFromY(scaleFactor));
  // values[i] -=
  // image.getIntegralAt(scanArea.getFromX(scaleFactor),
  // scanArea.getToY(scaleFactor));
  //
  // values[i] = (long) (values[i] / ((float) scanArea.getSize(scaleFactor)));
  // avg = ((avgItems * avg) + values[i]) / (++avgItems);
  // }
  //
  // if (isFace) {
  // this.possibilityFaceYes++;
  // } else {
  // this.possibilityFaceNo++;
  // }
  // for (int i = 0; i < this.scanAreas.length; ++i) {
  // boolean bright = (values[i] >= avg);
  //
  // if (isFace) {
  // // here we change the possibility of P(Scanarea_N = (Bright | NotBright)
  // // | Face=Yes)
  // this.possibilities_FaceYes[i] =
  // (((this.possibilityFaceYes - 1) * this.possibilities_FaceYes[i]) + (bright
  // ? 0.999f
  // : 0.001f)) / this.possibilityFaceYes;
  // //
  // System.out.println("P(Scannarea"+i+"=bright|Face=Yes) = "+this.possibilities_FaceYes[i]);
  // //
  // System.out.println("P(Scannarea"+i+"=dark|Face=Yes) = "+(1.0f-this.possibilities_FaceYes[i]));
  // } else {
  // // here we change the possibility of P(Scanarea_N = (Bright | NotBright)
  // // | Face=No)
  // this.possibilities_FaceNo[i] =
  // (((this.possibilityFaceNo - 1) * this.possibilities_FaceNo[i]) + (bright ?
  // 0.999f
  // : 0.001f)) / this.possibilityFaceNo;
  // //
  // System.out.println("P(Scannarea"+i+"=bright|Face=No) = "+this.possibilities_FaceNo[i]);
  // //
  // System.out.println("P(Scannarea"+i+"=dark|Face=No) = "+(1.0f-this.possibilities_FaceNo[i]));
  // }
  //
  // }
  //
  // // System.out.println("Average: "+avg);
  // // System.out.println(this);
  // }

  /**
   * Classifies an images region as face
   * 
   * @param image
   * @param scaleFactor
   *          please be aware of the fact that the scanareas are scaled for use
   *          with 100x100 px images
   * @param translationX
   * @param translationY
   * @return true if this region was classified as face, else false
   */
  public boolean classifyFace(IntegralImageData image, float scaleFactor, int translationX,
      int translationY, float borderline) {

    long values[] = new long[this.scanAreas.length];

    float avg = 0f;
    int avgItems = 0;
    for (int i = 0; i < this.scanAreas.length; ++i) {
      ScanArea scanArea = this.scanAreas[i];
      values[i] = 0l;

      values[i] +=
          image.getIntegralAt(translationX + scanArea.getToX(scaleFactor),
              translationY + scanArea.getToY(scaleFactor));
      values[i] +=
          image.getIntegralAt(translationX + scanArea.getFromX(scaleFactor), translationY
              + scanArea.getFromY(scaleFactor));

      values[i] -=
          image.getIntegralAt(translationX + scanArea.getToX(scaleFactor),
              translationY + scanArea.getFromY(scaleFactor));
      values[i] -=
          image.getIntegralAt(translationX + scanArea.getFromX(scaleFactor), translationY
              + scanArea.getToY(scaleFactor));

      values[i] = (long) (values[i] / ((float) scanArea.getSize(scaleFactor)));
      avg = ((avgItems * avg) + values[i]) / (++avgItems);
    }

    // int amountYesNo = this.possibilityFaceNo + this.possibilityFaceYes;

    // calculate the possibilites for face=yes and face=no with naive bayes
    // P(Yes | M1 and ... and Mn) = P(Yes) * P(M1 | Yes) * ... * P(Mn | Yes) /xx
    // P(No | M1 and ... and Mn) = P(No) * P(M1 | No) * ... * P(Mn | No) / xx
    // as we just maximize the args we don't actually calculate the accurate
    // possibility

    float isFaceYes = 1.0f;// this.possibilityFaceYes / (float)amountYesNo;
    float isFaceNo = 1.0f;// this.possibilityFaceNo / (float)amountYesNo;

    for (int i = 0; i < this.scanAreas.length; ++i) {
      boolean bright = (values[i] >= avg);
      isFaceYes *= (bright ? this.possibilities_FaceYes[i] : 1 - this.possibilities_FaceYes[i]);
      isFaceNo *= (bright ? this.possibilities_FaceNo[i] : 1 - this.possibilities_FaceNo[i]);
    }

    return (isFaceYes >= isFaceNo && (isFaceYes / (isFaceYes + isFaceNo)) > borderline);
  }

  public ScanArea[] getScanAreas() {
    return this.scanAreas;
  }

  public int getLearnedFacesYes() {
    return this.possibilityFaceYes;
  }

  public int getLearnedFacesNo() {
    return this.possibilityFaceNo;
  }

  public float getPossibility(int scanAreaID, boolean faceYes, boolean bright) {
    if (faceYes) {
      return (bright ? this.possibilities_FaceYes[scanAreaID]
          : 1 - this.possibilities_FaceYes[scanAreaID]);
    } else {
      return (bright ? this.possibilities_FaceNo[scanAreaID]
          : 1 - this.possibilities_FaceNo[scanAreaID]);
    }
  }

  public int compareTo(Classifier o) {
    if (o.getScanAreas().length > this.getScanAreas().length) {
      return -1;
    } else if (o.getScanAreas().length < this.getScanAreas().length) {
      return 1;
    } else
      return 0;
  }

  public String toString() {

    String str = "";
    for (int i = 0; i < scanAreas.length; i++) {
      str += scanAreas[i].toString() + "\n";
    }

    return str;

  }
  // @Override
  // public String toString() {
  // StringBuilder sb = new StringBuilder();
  // sb.append("Classifier [ScanAreas: " + this.scanAreas.length);
  // int yesNo = this.possibilityFaceYes + this.possibilityFaceNo;
  // sb.append(String.format("|Yes: %3.2f| No:%3.2f] (",
  // (this.possibilityFaceYes / (float) yesNo) * 100.0f,
  // (this.possibilityFaceNo / (float) yesNo) * 100.0f));
  // for (int i = 0; i < this.scanAreas.length; ++i) {
  // sb.append(String.format("[%3d|Yes: %3.2f| No: %3.2f], ", i + 1,
  // (this.possibilities_FaceYes[i] * 100.0f), (this.possibilities_FaceNo[i] *
  // 100.0f)));
  // }
  // sb.append(")");
  //
  // return sb.toString();
  // }

  /**
   * Generates a new set of classifiers each with more ScanAreas than the last
   * classifier. You can specifiy the amount of classifiers you want to generate
   * 
   * @param amount
   *          amount of classifiers to create
   * @param startAmountScanAreas
   *          the start amount of scanAreas - if your first classifiers should
   *          contain 3 items you should give 3 here
   * @param incAmountScanAreas
   *          the amount of which the scanAreas should increase - a simple 2
   *          will increase them by 2 every step
   * @return a List of classifiers
   */
  // public static List<Classifier> generateNewClassifiers(int amount, int
  // startAmountScanAreas,
  // float incAmountScanAreas) {
  // List<Classifier> classifiers = new ArrayList<Classifier>();
  //
  // int maxDim = 40;
  // Random random = new Random(System.currentTimeMillis());
  // double maxSpace = 2 * Math.PI * Math.pow(50, 2);
  //
  // for (int i = 0; i < amount; ++i) {
  // // we create an odd amount of ScanAreas starting with 1 (3, 5, 7, ...)
  // int scanAreaAmount = startAmountScanAreas + (int)
  // Math.pow(incAmountScanAreas, i);// +
  // // ((i)*incAmountScanAreas+1);
  //
  // int scanAreaSize =
  // randomInt(random, scanAreaAmount * 20, (int) Math.min(maxDim * maxDim,
  // maxSpace))
  // / scanAreaAmount;
  // // System.out.println("scanAreaSize = "+scanAreaSize);
  //
  // List<ScanArea> scanAreas = new ArrayList<ScanArea>();
  //
  // for (int j = 0; j < scanAreaAmount; ++j) {
  //
  // int counter = 0;
  // ScanArea scanArea = null;
  // do {
  // // new the width has the first choice
  // int minWidth = (int) Math.ceil(scanAreaSize / (float) maxDim);
  //
  // int scanAreaWidth = randomInt(random, minWidth, Math.min(maxDim,
  // scanAreaSize / 2));
  // int scanAreaHeight = (int) Math.ceil(scanAreaSize / (float) scanAreaWidth);
  //
  // int radius =
  // randomInt(random, 5, Math.min(50 - scanAreaHeight / 2, 50 - scanAreaWidth /
  // 2));
  // double angle = random.nextFloat() * 2 * Math.PI;
  //
  // int posX = (int) (50 + Math.cos(angle) * radius) - (scanAreaWidth / 2);
  // int posY = (int) (50 + Math.sin(angle) * radius) - (scanAreaHeight / 2);
  //
  // // System.out.println("[Angle: "+(angle /
  // // (Math.PI*2)*180)+" | radius: "+radius+"]");
  // //
  // System.out.println("Area"+j+" is "+posX+", "+posY+" ("+scanAreaWidth+" x "+scanAreaHeight+" = "+((scanAreaWidth*scanAreaHeight))+")");
  //
  // // now we get random position for this area
  // scanArea = new ScanArea(posX, posY, scanAreaWidth, scanAreaHeight);
  //
  // counter++;
  // } while (scanAreas.contains(scanArea) && counter < 30);
  //
  // if (counter == 30) {
  // j -= 1;
  // continue;
  // }
  //
  // scanAreas.add(scanArea);
  // }
  //
  // Classifier classifier = new Classifier(scanAreas.toArray(new ScanArea[0]));
  // classifiers.add(classifier);
  // }
  //
  // return classifiers;
  // }

  // private static int randomInt(Random random, int from, int to) {
  // if (to - from <= 0)
  // to = from + 1;
  // return from + random.nextInt(to - from);
  // }
  //
  // public static List<Classifier> getDefaultClassifier() {
  // List<Classifier> classifier = new ArrayList<Classifier>();
  //
  // classifier.add(new Classifier(new ScanArea(30, 30, 30, 30), new
  // ScanArea(15, 8, 15, 82),
  // new ScanArea(75, 8, 15, 82)));
  //
  // return classifier;
  // }

}
