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

  // LEFT_UP(+1, -1), UP(0, -1), RIGHT_UP(-1, -1), LEFT(+1, 0), NONE(0, 0),
  // RIGHT(-1, 0), LEFT_DOWN(
  // +1, +1), DOWN(0, +1), RIGHT_DOWN(-1, +1);

  public static final int LEFT_UP = 0;
  public static final int UP = 1;
  public static final int RIGHT_UP = 2;
  public static final int LEFT = 3;
  public static final int NONE = 4;
  public static final int RIGHT = 5;
  public static final int LEFT_DOWN = 6;
  public static final int DOWN = 7;
  public static final int RIGHT_DOWN = 8;

  public DeviationScanner() {
    eyePositions = new EyePosition[3];
  }

  
  public void addEyePosition( EyePosition eyePosition) {

    // for ( int i = 1; i < 3; i++) {
    // eyePositions[i - 1] = eyePositions[i];
    // eyePositions[i] = null;
    // }
    // eyePositions[eyePositions.length - 1] = eyePosition;

    SSJAVA.append(eyePositions, eyePosition);

  }

  // 
  
  
  public int scanForDeviation( Rectangle2D faceRect) {

     int deviation = NONE;

    for ( int i = 0; i < 3; i++) {
      if (eyePositions[i] == null) {
        return deviation;
      }
    }

     double deviationX = 0;
     double deviationY = 0;

     int lastIdx = -1;
    for ( int i = 0; i < 3; ++i) {
      if (lastIdx != -1) {
        deviationX += (eyePositions[i].getX() - eyePositions[lastIdx].getX());
        deviationY += (eyePositions[i].getY() - eyePositions[lastIdx].getY());
      }
      lastIdx = i;
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

    return deviation;
  }

  
  public int getDirectionFor( int directionX,  int directionY) {

    if (directionX == +1 && directionY == -1) {
      return LEFT_UP;
    } else if (directionX == 0 && directionY == -1) {
      return UP;
    } else if (directionX == -1 && directionY == -1) {
      return RIGHT_UP;
    } else if (directionX == +1 && directionY == 0) {
      return LEFT;
    } else if (directionX == 0 && directionY == 0) {
      return NONE;
    } else if (directionX == -1 && directionY == 0) {
      return RIGHT;
    } else if (directionX == +1 && directionY == +1) {
      return LEFT_DOWN;
    } else if (directionX == 0 && directionY == +1) {
      return DOWN;
    } else if (directionX == -1 && directionY == +1) {
      return RIGHT_DOWN;
    }

    return -1;
  }

  public void clear() {
    System.out.println("CLEAR");
    eyePositions = new EyePosition[3];
  }

  public String toStringDeviation( int dev) {
    if (dev == LEFT_UP) {
      return "LEFT_UP";
    } else if (dev == UP) {
      return "UP";
    } else if (dev == RIGHT_UP) {
      return "RIGHT_UP";
    } else if (dev == LEFT) {
      return "LEFT";
    } else if (dev == NONE) {
      return "NONE";
    } else if (dev == RIGHT) {
      return "RIGHT";
    } else if (dev == LEFT_DOWN) {
      return "LEFT_DOWN";
    } else if (dev == DOWN) {
      return "DOWN";
    } else if (dev == RIGHT_DOWN) {
      return "RIGHT_DOWN";
    }
    return "ERROR";
  }

}
