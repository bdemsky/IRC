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

public class ScanArea {

  private Point fromPoint;

  private Point toPoint;

  private float size;

  /**
   * Imagine you want to classify an image with 100px x 100px what would be the scanarea in this
   * kind of image. That size gets automatically scalled to fit bigger images
   * 
   * @param fromPoint
   * @param toPoint
   */
  public ScanArea(Point fromPoint, Point toPoint) {
    this.fromPoint = fromPoint;
    this.toPoint = toPoint;

    this.size = (this.toPoint.x - this.fromPoint.x) * (this.toPoint.y - this.fromPoint.y);
  }

  public ScanArea(Point fromPoint, Point toPoint, float size) {
    this.fromPoint = fromPoint;
    this.toPoint = toPoint;
    this.size = size;
  }

  public ScanArea(int fromX, int fromY, int width, int height) {
    this(new Point(fromX, fromY), new Point(fromX + width, fromY + height));
  }

  public int getFromX(float scaleFactor) {
    return (int) (this.fromPoint.x * scaleFactor);
  }

  public int getFromY(float scaleFactor) {
    return (int) (this.fromPoint.y * scaleFactor);
  }

  public int getToX(float scaleFactor) {
    return (int) (this.toPoint.x * scaleFactor);
  }

  public int getToY(float scaleFactor) {
    return (int) (this.toPoint.y * scaleFactor);
  }

  public int getSize(float scaleFactor) {
    return (int) (this.size * Math.pow(scaleFactor, 2));
  }

  @Override
  public boolean equals(Object o) {
    ScanArea other = (ScanArea) o;

    return pointsWithin(other.fromPoint.x, other.toPoint.x, this.fromPoint.x, this.toPoint.x)
        && pointsWithin(other.fromPoint.y, other.toPoint.y, this.fromPoint.y, this.toPoint.y);
  }

  private static boolean pointsWithin(int pointA1, int pointA2, int pointB1, int pointB2) {
    boolean within = false;
    within = within || (pointB1 >= pointA1 && pointB1 <= pointA2);
    within = within || (pointB2 >= pointA1 && pointB2 <= pointA2);
    within = within || (pointA1 >= pointB1 && pointA1 <= pointB2);
    within = within || (pointA2 >= pointB1 && pointA2 <= pointB2);

    return within;
  }

  // private boolean checkPoints(ScanArea a, ScanArea b) {
  // Point[] pointsToCheck = new Point[] {
  // a.fromPoint, a.toPoint,
  // new Point (a.fromPoint.x, a.toPoint.y),
  // new Point (a.toPoint.x, a.fromPoint.y)
  // };
  // for (Point point: pointsToCheck) {
  // if (point.x >= b.fromPoint.x && point.x <= b.toPoint.x &&
  // point.y >= b.fromPoint.y && point.y <= b.toPoint.y) return true;
  // }
  //
  // return false;
  // }

  public String toString() {
    String str = "";
    str += "fromPoint=(" + fromPoint.x + "," + fromPoint.y + ")";
    str += "toPoint=(" + toPoint.x + "," + toPoint.y + ")";
    str += "size=" + size;
    return str;
  }
}
