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
@LATTICE("POS")
@METHODDEFAULT("OUT<THIS,THISLOC=THIS,RETURNLOC=OUT")
public class EyePosition {
  @LOC("POS")
  private int x;
  @LOC("POS")
  private int y;
  @LOC("POS")
  private Rectangle2D faceRect;

  public EyePosition(Point p, Rectangle2D faceRect) {
    this(p.x, p.y, faceRect);
  }

  public EyePosition(int x, int y, Rectangle2D faceRect) {
    this.x = x;
    this.y = y;
    this.faceRect = faceRect;
  }

  public int getX() {
    return this.x;
  }

  public int getY() {
    return this.y;
  }

  public String toString() {
    return "(" + x + "," + y + ")";
  }

  // public Deviation getDeviation(EyePosition oldEyePosition) {
  // if (oldEyePosition == null) return Deviation.NONE;
  //
  // //first we check if the faceRects are corresponding
  // double widthChange = (this.faceRect.getWidth() -
  // oldEyePosition.faceRect.getWidth()) / this.faceRect.getWidth();
  // if (widthChange > 0.1) return Deviation.NONE;
  //
  // int maxDeviationX = (int)Math.round(this.faceRect.getWidth() / 4f);
  // int maxDeviationY = (int)Math.round(this.faceRect.getWidth() / 8f);
  // int minDeviation = (int)Math.round(this.faceRect.getWidth() / 16f);
  //
  // int deviationX = Math.abs(x - oldEyePosition.x);
  // int directionX = sgn(x - oldEyePosition.x);
  // if (deviationX < minDeviation || deviationX > maxDeviationX) directionX =
  // 0;
  //
  // int deviationY = Math.abs(y - oldEyePosition.y);
  // int directionY = sgn(y - oldEyePosition.y);
  // if (deviationY < minDeviation || deviationY > maxDeviationY) directionY =
  // 0;
  //
  // double deviationXPercent = deviationX / this.faceRect.getWidth();
  // double deviationYPercent = deviationY / this.faceRect.getWidth();
  //
  // System.out.println(String.format("devX: %.2f | devY: %.2f",
  // deviationXPercent*100f, deviationYPercent*100f));
  // return Deviation.getDirectionFor(directionX, directionY);
  // }

  private static int sgn(int i) {
    if (i > 0)
      return 1;
    if (i < 0)
      return -1;
    return 0;
  }
}
