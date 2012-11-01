/**
 * 
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import de.darkblue.lea.ifaces.ICaptureDevice;

/**
 * No description given.
 * 
 * @author Florian Frankenberger
 */
public class DummyCaptureDevice implements ICaptureDevice {

  /**
	 * 
	 */
  public DummyCaptureDevice() {
    // TODO Auto-generated constructor stub
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.darkblue.lea.ifaces.ICaptureDevice#close()
   */
  @Override
  public void close() {
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.darkblue.lea.ifaces.ICaptureDevice#getFrameRate()
   */
  @Override
  public int getFrameRate() {
    return 15;
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.darkblue.lea.ifaces.ICaptureDevice#getImage()
   */
  @Override
  public BufferedImage getImage() {
    BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = (Graphics2D) image.getGraphics();
    g2d.setColor(new Color(255, 255, 255));
    g2d.fillRect(0, 0, 639, 479);
    return image;
  }

}
