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
public class IntegralImageData {

    private long[][] integral;
    private Dimension dimension;

    public IntegralImageData(BufferedImage bufferedImage) {
        this.integral = new long[bufferedImage.getWidth()][bufferedImage.getHeight()];
        this.dimension =  new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight());

        int[] pixelBuffer = new int[bufferedImage.getWidth()*bufferedImage.getHeight()];
        PixelGrabber pg = new PixelGrabber(
                bufferedImage, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), pixelBuffer, 0, bufferedImage.getWidth());

        try {
            pg.grabPixels();
        } catch (InterruptedException ie) {}

        long[][] s = new long[bufferedImage.getWidth()][bufferedImage.getHeight()];
        for (int y = 0; y < bufferedImage.getHeight(); ++y) {
            for (int x = 0; x < bufferedImage.getWidth(); ++x) {
                s[x][y] = (y-1 < 0 ? 0 : s[x][y-1]) + (pixelBuffer[y*bufferedImage.getWidth() + x] & 0xff);
                this.integral[x][y] = (x-1 < 0 ? 0 : this.integral[x-1][y]) + s[x][y];
            }
        }
        
    }

    public long getIntegralAt(int x, int y) {
        return this.integral[x][y];
    }

    public Dimension getDimension() {
        return this.dimension;
    }

    public int getWidth() {
        return this.dimension.width;
    }

    public int getHeight() {
        return this.dimension.height;
    }

}
