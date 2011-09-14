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
public enum Deviation {
        LEFT_UP(+1, -1),
        UP(0, -1),
        RIGHT_UP(-1, -1),
        LEFT(+1, 0),
        NONE(0, 0),
        RIGHT(-1, 0),
        LEFT_DOWN(+1, +1),
        DOWN(0, +1),
        RIGHT_DOWN(-1, +1);

        int directionX, directionY;
        Deviation(int directionX, int directionY) {
            this.directionX = directionX;
            this.directionY = directionY;
        }

        private boolean concurs(int directionX, int directionY) {
            return (directionX == this.directionX && directionY == this.directionY);
        }


        public static Deviation getDirectionFor(int directionX, int directionY) {
            for (Deviation direction: Deviation.values()) {
                if (direction.concurs(directionX, directionY)) {
                    return direction;
                }
            }

            return null;
        }
  }
