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


import java.awt.image.BufferedImage;

/**
 * Describes a capture device. For now it is only tested
 * with images in <code>640x480</code> at <code>RGB</code> or <code>YUV</code> color space.
 * 
 * @author Florian Frankenberger
 */
public interface ICaptureDevice {

	/**
	 * Returns the frame rate of the image source per second
	 * 
	 * @return the frame rate (e.g. 15 = 15 frames per second)
	 */
	public int getFrameRate();

	/**
	 * Will be called a maximum of getFrameRate()-times in a second and returns
	 * the actual image of the capture device
	 *  
	 * @return the actual image of the capture device 
	 */
	public BufferedImage getImage();
	
	/**
	 * LEA calls this when it cleans up. You should put your own cleanup code in here.
	 */
	public void close();
	
	
}
