import Analysis.SSJava.Location;

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
 * Representing an eyes deviation
 * 
 * @author Florian Frankenberger
 */
public class Deviation {

  int directionX, directionY;
  String direction;

  public Deviation(String direction, int directionX, int directionY) {
    this.directionX = directionX;
    this.directionY = directionY;
    this.direction = direction;
  }

  public boolean concurs(int directionX, int directionY) {
    return (directionX == this.directionX && directionY == this.directionY);
  }

  public boolean equals(Object o) {
    if (!(o instanceof Deviation)) {
      return false;
    }

    Deviation dev = (Deviation) o;
    if (dev.directionX == directionX && dev.directionY == directionY) {
      return true;
    }

    return false;
  }

}
