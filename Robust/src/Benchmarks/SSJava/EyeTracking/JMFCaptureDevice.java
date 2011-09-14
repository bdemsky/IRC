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


import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Vector;

import javax.media.Buffer;
import javax.media.CannotRealizeException;
import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoDataSourceException;
import javax.media.NoPlayerException;
import javax.media.Player;
import javax.media.control.FormatControl;
import javax.media.control.FrameGrabbingControl;
import javax.media.format.RGBFormat;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.util.BufferToImage;

import de.darkblue.lea.ifaces.ICaptureDevice;

/**
 * This class is a proxy to the <code>Java Media Framework</code>. You can use
 * this class to make use of a <code>JMF</code> compatible webcam or
 * other image source.
 * <p>
 * To receive a list of all available image sources just call the static
 * method <code>getImageSources</code>.
 * 
 * @author Florian Frankenberger
 */
public class JMFCaptureDevice implements ICaptureDevice {

    private Player player;
    private FrameGrabbingControl fgc;
	
    /**
     * Initializes the <code>JMF</code> with the first available (<code>YUV</code> compatible) image source. The image
     * source is then tested if it matches certain criteria - if not an <code>IllegalStateException</code>
     * is thrown.
     * <p>
     * Criteria are:
     * <li>provides <code>YUV</code> or <code>RGB</code> images
     * <li>can provide images with <code>640x480</code> pixels
     * 
     * @throws NoDataSourceException
     * @throws NoPlayerException
     * @throws CannotRealizeException
     * @throws IOException
     * @throws IllegalStateException if the data source does not match the criteria
     */
	public JMFCaptureDevice() throws NoDataSourceException, NoPlayerException, CannotRealizeException, IOException {
		CaptureDeviceInfo imageSource = null;
		
        // get all image sources on the system that can supply us with at least YUV-images
        CaptureDeviceInfo[] devices = getImageSourcesAvailable();

        if (devices.length == 0) {
            throw new IllegalStateException("No Webcams found on this system.");
        }

        // we use the first best (most of the time this is the webcam the user wants)
        imageSource = devices[0];
        
		initImageSource(imageSource);
	}
	
	/**
	 * Initializes the <code>JMF</code> with the given image source. An <code>IllegalStateException</code> is thrown
	 * if the image source does not the following criteria:
     * <p>
     * Criteria are:
     * <li>provides <code>YUV</code> or <code>RGB</code> images
     * <li>can provide images with <code>640x480</code> pixels
	 *
	 * @param imageSource the image source to use
	 * @throws NoDataSourceException
	 * @throws NoPlayerException
	 * @throws CannotRealizeException
	 * @throws IOException
     * @throws IllegalStateException if the data source does not match the criteria
	 */
	public JMFCaptureDevice(CaptureDeviceInfo imageSource) throws NoDataSourceException, NoPlayerException, CannotRealizeException, IOException {
		initImageSource(imageSource);
	}
	
    private void initImageSource(CaptureDeviceInfo imageSource) throws NoDataSourceException, IOException, NoPlayerException, CannotRealizeException {
        if (imageSource == null) throw new IllegalStateException("Image source cannot be null");

        // search the right format
        VideoFormat matchingFormat = null;
        VideoFormat alternateMatchingFormat = null;
        for (Format format: imageSource.getFormats()) {
            if (format instanceof VideoFormat) {
                VideoFormat videoFormat = (VideoFormat) format;

                if (videoFormat instanceof RGBFormat) {
                    RGBFormat rgbFormat = (RGBFormat)videoFormat;
                    Dimension size = rgbFormat.getSize();

                    if (size.width == 640 && size.height == 480 &&
                            rgbFormat.getBitsPerPixel() == 24) {
                        matchingFormat = videoFormat;
                        break;
                    }
                }

                if (videoFormat instanceof YUVFormat) {
                    YUVFormat rgbFormat = (YUVFormat)videoFormat;
                    Dimension size = rgbFormat.getSize();

                    if (size.width == 640 && size.height == 480) {
                        alternateMatchingFormat = videoFormat;
                    }
                }
            }
        }

        if (matchingFormat == null && alternateMatchingFormat != null)
            matchingFormat = alternateMatchingFormat;

        if (matchingFormat == null) {
            throw new IllegalStateException ("Your image source does not support the 640x480 RGB/YUV format. This testenvironment relies on the fact, that your cam provides this resolution in this colorspace.");
        }

        MediaLocator mediaLocator = imageSource.getLocator();

        DataSource dataSource = Manager.createDataSource(mediaLocator);
        for (FormatControl formatControl: ((CaptureDevice) dataSource).getFormatControls()) {
            if (formatControl == null) continue;
            formatControl.setFormat(matchingFormat);
        }

        player = Manager.createRealizedPlayer(dataSource);

        player.setRate(15);
        player.start();

        fgc = (FrameGrabbingControl) player.getControl("javax.media.control.FrameGrabbingControl");
    }	
    
    /**
     * Returns a vector of all image sources available on this system
     * @return a vector of image sources
     */
    @SuppressWarnings("unchecked")
	public static CaptureDeviceInfo[] getImageSourcesAvailable() {
        // get all image sources on the system that can supply us with at least YUV-images
        Vector<CaptureDeviceInfo> devices = CaptureDeviceManager.getDeviceList(new VideoFormat(VideoFormat.YUV));
        return devices.toArray(new CaptureDeviceInfo[0]);
    }


	/* (non-Javadoc)
	 * @see de.darkblue.lea.ICaptureDevice#getFrameRate()
	 */
	@Override
	public int getFrameRate() {
		return 15;
	}
    
	/* (non-Javadoc)
	 * @see de.darkblue.lea.ICaptureDevice#getImage()
	 */
	@Override
	public BufferedImage getImage() {
        Buffer buffer = fgc.grabFrame();
        
        // Convert it to an image
        BufferToImage btoi = new BufferToImage((VideoFormat)buffer.getFormat());
        return (BufferedImage)btoi.createImage(buffer);
    }

	/* (non-Javadoc)
	 * @see de.darkblue.lea.ICaptureDevice#close()
	 */
	@Override
	public void close() {
        if (player != null) {
            player.close();
            player.deallocate();
        }		
	}


	
	
}
