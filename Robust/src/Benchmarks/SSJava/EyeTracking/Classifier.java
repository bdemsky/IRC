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
@LATTICE("V")
@METHODDEFAULT("OUT<V,V<THIS,THIS<C,C<IN,C*,V*,THISLOC=THIS,RETURNLOC=OUT")
public class Classifier {

  @LOC("V")
  private ScanArea[] scanAreas;

  @LOC("V")
  private float[] possibilities_FaceYes;
  @LOC("V")
  private float[] possibilities_FaceNo;
  @LOC("V")
  private int possibilityFaceYes = 0;
  @LOC("V")
  private int possibilityFaceNo = 0;

  public Classifier(int numScanAreas) {
    this.scanAreas = new ScanArea[numScanAreas];
    this.possibilities_FaceYes = new float[numScanAreas];
    this.possibilities_FaceNo = new float[numScanAreas];
  }

  public void setScanArea(int idx, ScanArea area) {
    scanAreas[idx] = area;
  }

  public void setPossibilitiesFaceYes(@DELEGATE float[] arr) {
    this.possibilities_FaceYes = arr;
  }

  public void setPossibilityFaceYes(int v) {
    this.possibilityFaceYes = v;
  }

  public void setPossibilitiesFaceNo(@DELEGATE float[] arr) {
    this.possibilities_FaceNo = arr;
  }

  public void setPossibilityFaceNo(int v) {
    this.possibilityFaceNo = v;
  }

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
  public boolean classifyFace(@LOC("IN") IntegralImageData image, @LOC("IN") float scaleFactor,
      @LOC("IN") int translationX, @LOC("IN") int translationY, @LOC("IN") float borderline) {

    @LOC("V") long values[] = new long[this.scanAreas.length];

    @LOC("V") float avg = 0f;
    @LOC("V") int avgItems = 0;
    for (@LOC("C") int i = 0; i < this.scanAreas.length; ++i) {
      values[i] = 0l;

      values[i] +=
          image.getIntegralAt(translationX + scanAreas[i].getToX(scaleFactor), translationY
              + scanAreas[i].getToY(scaleFactor));
      values[i] +=
          image.getIntegralAt(translationX + scanAreas[i].getFromX(scaleFactor), translationY
              + scanAreas[i].getFromY(scaleFactor));

      values[i] -=
          image.getIntegralAt(translationX + scanAreas[i].getToX(scaleFactor), translationY
              + scanAreas[i].getFromY(scaleFactor));
      values[i] -=
          image.getIntegralAt(translationX + scanAreas[i].getFromX(scaleFactor), translationY
              + scanAreas[i].getToY(scaleFactor));

      values[i] = (long) (values[i] / ((float) scanAreas[i].getSize(scaleFactor)));
      avg = ((avgItems * avg) + values[i]) / (++avgItems);
    }
    // System.out.println("avg=" + avg);

    // int amountYesNo = this.possibilityFaceNo + this.possibilityFaceYes;

    // calculate the possibilites for face=yes and face=no with naive bayes
    // P(Yes | M1 and ... and Mn) = P(Yes) * P(M1 | Yes) * ... * P(Mn | Yes) /xx
    // P(No | M1 and ... and Mn) = P(No) * P(M1 | No) * ... * P(Mn | No) / xx
    // as we just maximize the args we don't actually calculate the accurate
    // possibility

    @LOC("OUT") float isFaceYes = 1.0f;// this.possibilityFaceYes /
                                       // (float)amountYesNo;
    @LOC("OUT") float isFaceNo = 1.0f;// this.possibilityFaceNo /
                                      // (float)amountYesNo;

    for (@LOC("C") int i = 0; i < this.scanAreas.length; ++i) {
      @LOC("V") boolean bright = (values[i] >= avg);
      isFaceYes *= (bright ? this.possibilities_FaceYes[i] : 1 - this.possibilities_FaceYes[i]);
      isFaceNo *= (bright ? this.possibilities_FaceNo[i] : 1 - this.possibilities_FaceNo[i]);
    }
    // System.out.println("avg=" + avg + " yes=" + isFaceYes + " no=" +
    // isFaceNo);

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

    @LOC("OUT") String str = "";
    for (@LOC("C") int i = 0; i < scanAreas.length; i++) {
      str += scanAreas[i].toString() + "\n";
    }

    return str;

  }

}
