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


public class FaceAndEyePosition {

  
  private Rectangle2D facePosition;
  
  private EyePosition eyePosition;

  public FaceAndEyePosition(double x, double y, double w, double h, EyePosition eyePosition) {
    this.facePosition = new Rectangle2D(x, y, w, h);
    this.eyePosition = eyePosition;
  }

  public Rectangle2D getFacePosition() {
    return this.facePosition;
  }

  public EyePosition getEyePosition() {
    return this.eyePosition;
  }

}
