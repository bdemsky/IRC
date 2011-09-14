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


import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;

/**
 * No description given.
 * 
 * @author Florian Frankenberger
 */
class EyeDetector {

    private int width;
    private int height;
    private int[] pixelBuffer;

    double percent;

    public EyeDetector(BufferedImage image, Rectangle2D faceRect) {

        percent = 0.15*faceRect.getWidth();
        faceRect = new Rectangle2D.Double(
                    faceRect.getX()+ percent,
                    faceRect.getY()+ percent,
                    faceRect.getWidth() - percent,
                    faceRect.getHeight() - 2*percent
                );
        
        width = (int)faceRect.getWidth() / 2;
        height = (int)faceRect.getHeight() / 2;
        pixelBuffer = new int[width*height];
        PixelGrabber pg = new PixelGrabber(
                image,
                (int)faceRect.getX(), (int)faceRect.getY(),
                width, height,
                pixelBuffer, 0, width);

        try {
            pg.grabPixels();
        } catch (InterruptedException ie) {}
    }

    public Point detectEye() {
        Point eyePosition = null;
        float brightness = 255f;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int position = y*width + x;
                final int[] color = new int[] { (pixelBuffer[position] & 0xFF0000) >> 16, (pixelBuffer[position] & 0x00FF00) >> 8, pixelBuffer[position] & 0x0000FF };
                final float acBrightness = getBrightness(color);

                if (acBrightness < brightness) {
                    eyePosition = new Point(x + (int)percent,  y + (int)percent);
                    brightness = acBrightness;
                }
            }
        }

        return eyePosition;
    }

    private static float getBrightness(int[] color) {
        int min = Math.min(Math.min(color[0], color[1]), color[2]);
        int max = Math.max(Math.max(color[0], color[1]), color[2]);

        return 0.5f * (max + min);
    }
}
