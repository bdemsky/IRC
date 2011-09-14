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
class FaceInfoPanel extends InfoPanel {

	private static final long serialVersionUID = 453216951714787407L;

	public FaceInfoPanel() {
        super(
                "Face Detection",
                new String[] {
                    "noface0.png",
                    "noface1.png"
                },
                2,
                100,
                100);
        this.setFace(null, null);
    }

    public void setFace(BufferedImage image, Rectangle2D faceRect) {

        BufferedImage faceRectImage = null;
        if (image != null && faceRect != null) {
            int width = (int)faceRect.getWidth();
            int height = (int)faceRect.getHeight();

            faceRectImage = new BufferedImage(width,
                      height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2D = faceRectImage.createGraphics();
            g2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            g2D.drawImage(image, 0, 0, width, height, (int)faceRect.getX(), (int)faceRect.getY(), (int)faceRect.getX() + width, (int)faceRect.getY() + height, null);

        }
        this.setImage(faceRectImage);
    }

}
