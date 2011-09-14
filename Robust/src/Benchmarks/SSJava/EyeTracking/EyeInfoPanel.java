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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 
 * @author Florian
 */
class EyeInfoPanel extends InfoPanel {

  private static final long serialVersionUID = 7681992432092759058L;

  public EyeInfoPanel() {
    super("Eye Status", new String[] { "nolock.png" }, 0, 100, 40);
  }

  public void setDeviation(Deviation deviation) {
    if (deviation == null) {
      this.setImage(null);
    } else {
      this.setImage(loadImage(deviation.toString() + ".png"));
    }
  }

  public void setEyePosition(BufferedImage image, Rectangle2D faceRect, EyePosition eyePosition) {

    BufferedImage faceRectImage = null;
    if (image != null && faceRect != null) {
      int width = 100;
      int height = 40;

      int posX = (int) (faceRect.getX() + eyePosition.getX());
      int posY = (int) (faceRect.getY() + eyePosition.getY());

      int targetWidth = (int) (0.3 * faceRect.getWidth());
      int targetHeight = (int) (height / (float) width * targetWidth);

      faceRectImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

      Graphics2D g2D = faceRectImage.createGraphics();
      g2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

      g2D.drawImage(image, 0, 0, width, height, posX - targetWidth / 2, posY - targetHeight / 2,
          posX + targetWidth / 2, posY + targetHeight / 2, null);

    }
    this.setImage(faceRectImage);
  }

}
