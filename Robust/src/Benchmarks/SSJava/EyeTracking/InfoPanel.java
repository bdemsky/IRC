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


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 *
 * @author Florian
 */
abstract class InfoPanel extends JPanel {

	private static final long serialVersionUID = 8311083210432152308L;
	
	private String text = "";
    private BufferedImage image = null;
    private BufferedImage[] noImageImages = null;
    private int imageWidth, imageHeight;
    private int animationTime = 0;

    private AnimationThread animationThread = null;

    private class AnimationThread extends Thread {

        private boolean shutdown = false;
        private int counter = 0;

        public AnimationThread() {
            this.start();
        }

        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public void run() {
            while (!shutdown) {
                counter = (++counter % noImageImages.length);
                image = noImageImages[counter];
                repaint();

                try { Thread.sleep(animationTime); } catch (InterruptedException e) {}
            }
        }

    }
            
    public InfoPanel(String text, String[] noImageImageFileNames, int fps, int imageWidth, int imageHeight) {
        this.text = text;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        if (noImageImageFileNames == null || noImageImageFileNames.length == 0) {
            this.noImageImages = null;
        } else {
            this.noImageImages = new BufferedImage[noImageImageFileNames.length];
            for (int i = 0; i < noImageImageFileNames.length; ++i) {
                this.noImageImages[i] = loadImage(noImageImageFileNames[i]);
            }
        }
        this.animationTime = (fps == 0 ? 0 : 1000/fps);
        this.setImage(null);
    }

    protected static BufferedImage loadImage(String fileName) {
        BufferedImage image = null;
        try {
            InputStream in =
                    FaceInfoPanel.class.getClassLoader().getResourceAsStream(fileName);
            if (in != null) {
                image = ImageIO.read(in);
                in.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();

        }

        return image;
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g2d.setBackground(new Color(0, 0, 0));
        g2d.clearRect(0, 0, this.getWidth(), this.getHeight());

        g2d.setColor(new Color(39, 53, 66));
        g2d.fillRoundRect(2, 2, this.getWidth()-4, this.getHeight()-4, 10, 10);

        g2d.setColor(new Color(118, 149, 174));
        g2d.drawString(text, 10, 20);

        g2d.setColor(new Color(81, 111, 137));
        g2d.drawRect(this.getWidth()-11-imageWidth, 9, imageWidth+1, imageHeight+1);

        if (image != null) {
            g2d.drawImage(image,
                    this.getWidth() - 10 - imageWidth, 10,
                    imageWidth, imageHeight, null);
        }

        g2d.setStroke(new BasicStroke(2.0f));
        g2d.setColor(new Color(81, 111, 137));
        g2d.drawRoundRect(2, 2, this.getWidth()-4, this.getHeight()-4, 10, 10);
    }

    protected synchronized void setImage(BufferedImage image) {
        if (image == null) {
            if (this.noImageImages != null && this.noImageImages.length > 0) {
                image =  this.noImageImages[0];
                if (this.animationTime > 0) {
                    if (this.animationThread == null)
                        this.animationThread = new AnimationThread();
                }
            }
        } else {
            if (this.animationThread != null) {
                this.animationThread.shutdown();
                this.animationThread = null;
            }
        }
        this.image = image;
        this.repaint();
    }


}
