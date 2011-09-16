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
 * This is the main class of LEA.
 * <p>
 * It uses a face detection algorithm to find an a face within the provided
 * image(s). Then it searches for the eye in a region where it most likely
 * located and traces its position relative to the face and to the last known
 * position. The movements are estimated by comparing more than one movement. If
 * a movement is distinctly pointing to a direction it is recognized and all
 * listeners get notified.
 * <p>
 * The notification is designed as observer pattern. You simply call
 * <code>addEyeMovementListener(IEyeMovementListener)</code> to add an
 * implementation of <code>IEyeMovementListener</code> to LEA. When a face is
 * recognized/lost or whenever an eye movement is detected LEA will call the
 * appropriate methods of the listener
 * <p>
 * LEA also needs an image source implementing the <code>ICaptureDevice</code>.
 * One image source proxy to the <code>Java Media Framework</code> is included (
 * <code>JMFCaptureDevice</code>).
 * <p>
 * Example (for using LEA with <code>Java Media Framework</code>):
 * <p>
 * <code>
 * LEA lea = new LEA(new JMFCaptureDevice(), true);
 * </code>
 * <p>
 * This will start LEA with the first available JMF datasource with an extra
 * status window showing if face/eye has been detected successfully. Please note
 * that face detection needs about 2 seconds to find a face. After detection the
 * following face detection is much faster.
 * 
 * @author Florian Frankenberger
 */
public class LEA {

  private boolean shutdown = false;
  private LEAImplementation implementation;

  private FaceAndEyePosition lastPositions = new FaceAndEyePosition(null, null);
  private DeviationScanner deviationScanner = new DeviationScanner();
  private int counter = 0;

  // private ImageProcessor imageProcessor;
  //
  // private class ImageProcessor extends TimedThread {
  //
  // private FaceAndEyePosition lastPositions = new FaceAndEyePosition(null,
  // null);
  // private DeviationScanner deviationScanner = new DeviationScanner();
  // private int counter = 0;
  //
  // private int fps;
  //
  // public ImageProcessor(int fps) {
  // super(fps);
  // this.fps = fps;
  // }
  //
  // @Override
  // public void doRun() {
  //
  // BufferedImage image = captureDevice.getImage();
  // if (image == null)
  // return;
  //
  // try {
  // FaceAndEyePosition positions = implementation.getEyePosition(image);
  //
  // if (((lastPositions.getFacePosition() == null &&
  // positions.getFacePosition() != null) || (lastPositions
  // .getFacePosition() != null && positions.getFacePosition() == null)) ||
  // counter++ > fps) {
  //
  // if ((lastPositions.getFacePosition() == null && positions.getFacePosition()
  // != null)
  // || (lastPositions.getFacePosition() != null && positions.getFacePosition()
  // == null)) {
  // if (positions.getFacePosition() != null) {
  // notifyEyeMovementListenerFaceDetected();
  // } else {
  // notifyEyeMovementListenerFaceLost();
  // }
  // }
  // counter = 0;
  // if (statusWindow != null)
  // statusWindow.getFaceInfoPanel().setFace(image,
  // positions.getFacePosition());
  // }
  //
  // if (positions.getEyePosition() != null) {
  // if (statusWindow != null) {
  // statusWindow.getEyeInfoPanel().setEyePosition(image,
  // positions.getFacePosition(),
  // positions.getEyePosition());
  // }
  // deviationScanner.addEyePosition(positions.getEyePosition());
  // Deviation deviation =
  // deviationScanner.scanForDeviation(positions.getFacePosition());//
  // positions.getEyePosition().getDeviation(lastPositions.getEyePosition());
  //
  // if (deviation != Deviation.NONE) {
  // notifyEyeMovementListenerEyeMoved(deviation);
  // }
  //
  // } else {
  // if (statusWindow != null)
  // statusWindow.getEyeInfoPanel().setDeviation(null);
  // }
  //
  // lastPositions = positions;
  // } catch (Exception e) {
  // e.printStackTrace();
  // try {
  // close();
  // } catch (Exception e2) {
  // }
  // }
  // }
  //
  // public synchronized void clearDeviationScanner() {
  // this.deviationScanner.clear();
  // }
  //
  // }

  public LEA() {
    // this.imageProcessor = new
    // ImageProcessor(this.captureDevice.getFrameRate());
    implementation = new LEAImplementation();
  }

  /**
   * Clears the internal movement buffer. If you just capture some of the eye
   * movements you should call this every time you start recording the
   * movements. Otherwise you may get notified for movements that took place
   * BEFORE you started recording.
   */
  public void clear() {
    // this.imageProcessor.clearDeviationScanner();
  }

  /**
   * To test LEA with the first capture device from the
   * <code>Java Media Framework</code> just start from here.
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    LEA lea = new LEA();
    lea.doRun();
  }

  public void doRun() {

    int maxCount = 1;
    int i = 0;

    ImageReader reader = new ImageReader();

    while (i < maxCount) {
      Image image = reader.readImage("data/e" + i + ".bmp");
      i++;
      if (image == null) {
        break;
      }
      processImage(image);
    }

    System.out.println("Done.");

  }

  private void processImage(Image image) {

    FaceAndEyePosition positions = implementation.getEyePosition(image);

    if (positions.getEyePosition() != null) {
      deviationScanner.addEyePosition(positions.getEyePosition());
      Deviation deviation = deviationScanner.scanForDeviation(positions.getFacePosition());// positions.getEyePosition().getDeviation(lastPositions.getEyePosition());
      if (deviation != DeviationScanner.NONE) {
        System.out.println("DEVICATION=" + deviation);
        // notifyEyeMovementListenerEyeMoved(deviation);
      }
    }
    // else {
    // if (statusWindow != null)
    // statusWindow.getEyeInfoPanel().setDeviation(null);
    // }
    lastPositions = positions;
    // } catch (Exception e) {
    // e.printStackTrace();
    // try {
    // close();
    // } catch (Exception e2) {
    // }
    // }
  }

}
