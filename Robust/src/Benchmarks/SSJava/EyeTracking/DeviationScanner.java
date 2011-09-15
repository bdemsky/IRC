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
public class DeviationScanner {

  private EyePosition eyePositions[];

  private static final Deviation NONE = new Deviation("NONE", 0, 0);

  public DeviationScanner() {
    eyePositions = new EyePosition[3];
  }

  public void addEyePosition(EyePosition eyePosition) {

    for (int i = eyePositions.length - 2; i >= 0; i--) {
      eyePositions[i + 1] = eyePositions[i];
    }
    eyePositions[0] = eyePosition;
  }

  public Deviation scanForDeviation(Rectangle2D faceRect) {
    Deviation deviation = NONE;
    if (eyePositions.length >= 3) {
      double deviationX = 0;
      double deviationY = 0;

      EyePosition lastEyePosition = null;
      for (int i = 0; i < 3; ++i) {
        EyePosition eyePosition = this.eyePositions[i];
        if (lastEyePosition != null) {
          deviationX += (eyePosition.getX() - lastEyePosition.getX());
          deviationY += (eyePosition.getY() - lastEyePosition.getY());
        }
        lastEyePosition = eyePosition;
      }

      final double deviationPercentX = 0.04;
      final double deviationPercentY = 0.04;

      deviationX /= faceRect.getWidth();
      deviationY /= faceRect.getWidth();

      int deviationAbsoluteX = 0;
      int deviationAbsoluteY = 0;
      if (deviationX > deviationPercentX)
        deviationAbsoluteX = 1;
      if (deviationX < -deviationPercentX)
        deviationAbsoluteX = -1;
      if (deviationY > deviationPercentY)
        deviationAbsoluteY = 1;
      if (deviationY < -deviationPercentY)
        deviationAbsoluteY = -1;

      deviation = getDirectionFor(deviationAbsoluteX, deviationAbsoluteY);
      if (deviation != NONE) {
        eyePositions = new EyePosition[3];
      }
      // System.out.println(String.format("%.2f%% | %.2f%% => %d and %d >>> %s",
      // deviationX*100, deviationY*100, deviationAbsoluteX, deviationAbsoluteY,
      // deviation.toString()));

    }

    return deviation;
  }

  public static Deviation getDirectionFor(int directionX, int directionY) {

    // for (Deviation direction : Deviation.values()) {
    // if (direction.concurs(directionX, directionY)) {
    // return direction;
    // }
    // }
    return null;
  }

  public void clear() {
    System.out.println("CLEAR");
    // this.eyePositions.clear();
  }

  // LEFT_UP(+1, -1), UP(0, -1), RIGHT_UP(-1, -1), LEFT(+1, 0), NONE(0, 0),
  // RIGHT(-1, 0), LEFT_DOWN(
  // +1, +1), DOWN(0, +1), RIGHT_DOWN(-1, +1);
  //

}
