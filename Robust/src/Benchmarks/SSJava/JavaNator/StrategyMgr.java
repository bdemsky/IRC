/*
 * StragegyMgr.java
 *
 * Copyright (c) 1993-2002 Sun Microsystems, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Sun
 * Microsystems, Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Sun.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

/**
 * StrategyMgr - an isolation class ment for programmers to modify and create
 * thier own code for robot strategy.
 * 
 * @author Michael Gesundheit
 * @version 1.0
 */
public class StrategyMgr {

  private PWMControl pwmControl;
  private int zeroSpeed = 45;
  private Random rand;
  private boolean DEBUGL = true;
  // private boolean DEBUGL = false;

  // private boolean DEBUG = true;
  private boolean DEBUG = false;

  /**
   * Constructor - Invoke communication to remote application thread
   */
  StrategyMgr(PWMManager pwmManager) {
    this.pwmControl = pwmManager.getPWMControl();
    rand = new Random();
  }
  
  

  void processSonars(byte sonarSensors) {

    // 5 sensors. 1,2 are fromnt left and right.
    // Sensor 3 is right side, 4 back and 5 is left side.
    if ((sonarSensors & 0x1f) == 0) {
      // No sensor data may mean dead area between sensors.
      // Continue with the last gesture until any sensor
      // provide data.
      if (DEBUG) {
        System.out.println("sonar: NO SONARS!!!!!!!!");
      }
    } else if ((sonarSensors & (RobotMain.LF_RT_FRONT)) == (RobotMain.LF_RT_FRONT)) {
      if (DEBUG) {
        System.out.println("sonar: straight");
      }
      straight();
    } else if ((sonarSensors & RobotMain.LF_FRONT) == RobotMain.LF_FRONT) {
      if (DEBUG) {
        System.out.println("sonar: bearLeft");
      }
      bearLeft();
    } else if ((sonarSensors & RobotMain.RT_FRONT) == RobotMain.RT_FRONT) {
      if (DEBUG) {
        System.out.println("sonar: bearRight");
      }
      bearRight();
    } else if ((sonarSensors & RobotMain.RT_SIDE) == RobotMain.RT_SIDE) {
      if (DEBUG) {
        System.out.println("sonar: right");
      }
      spinRight();
    } else if ((sonarSensors & RobotMain.LF_SIDE) == RobotMain.LF_SIDE) {
      if (DEBUG) {
        System.out.println("sonar: left");
      }
      spinLeft();
    } else if ((sonarSensors & RobotMain.BK_SIDE) == RobotMain.BK_SIDE) {
      if (DEBUG) {
        System.out.println("sonar: spin180");
      }
      spin180();
    }
  }

  void processLineSensors(byte lineSensorsMask) {
    while ((lineSensorsMask & RobotMain.LS_ALL) != 0) {
      if ((lineSensorsMask & RobotMain.LS_ALL) == RobotMain.LS_ALL) {
        if (DEBUGL)
          System.out.println("Line Sensors - ALL");
        stop();
      } else if ((lineSensorsMask & RobotMain.LS_LEFT_RIGHT) == RobotMain.LS_LEFT_RIGHT) {
        if (DEBUGL)
          System.out.println("Line Sensors - Left & Right");
        back();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
        spin180();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      } else if ((lineSensorsMask & RobotMain.LS_LEFT_REAR) == RobotMain.LS_LEFT_REAR) {
        if (DEBUGL)
          System.out.println("Line Sensors - Left & Rear");
        bearRight();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      } else if ((lineSensorsMask & RobotMain.LS_RIGHT_REAR) == RobotMain.LS_RIGHT_REAR) {
        if (DEBUGL)
          System.out.println("Line Sensors - Right & Rear");
        bearLeft();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      } else if ((lineSensorsMask & RobotMain.LS_LEFT) == RobotMain.LS_LEFT) {
        if (DEBUGL)
          System.out.println("Line Sensors - Left");
        backBearRight();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      } else if ((lineSensorsMask & RobotMain.LS_RIGHT) == RobotMain.LS_RIGHT) {
        if (DEBUGL)
          System.out.println("Line Sensors - Right");
        backBearLeft();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      } else if ((lineSensorsMask & RobotMain.LS_REAR) == RobotMain.LS_REAR) {
        if (DEBUGL)
          System.out.println("Line Sensors - Rear");
        straight();
        try {
          // Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
      }

      // TODO
      // lineSensorsMask = sm.getLineSensorsState();

    }// while loop
  }

  public void stop() {
    if (DEBUG)
      System.out.println("StrageyMgr: stop....");
    pwmControl.setSpeedLeft(zeroSpeed);
    pwmControl.setSpeedRight(zeroSpeed);
  }

  public void search() {
    if (DEBUG)
      System.out.println("StrategyMgr: search....");
    pwmControl.setSpeedLeft(70);
    pwmControl.setSpeedRight(50);
  }

  public void straight() {
    if (DEBUG)
      System.out.println("StrategyMgr: strait....");
    pwmControl.setSpeedLeft(100);
    pwmControl.setSpeedRight(100);
  }

  public void spinRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: spinRight....");
    pwmControl.setSpeedSpinLeft(100);
    pwmControl.setSpeedSpinRight(0);
  }

  public void spinLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: spinLeft....");
    pwmControl.setSpeedSpinLeft(0);
    pwmControl.setSpeedSpinRight(100);
  }

  public void spin180() {
    int mod = (rand.nextInt() % 2);

    if (DEBUG)
      System.out.println("StrategyMgr: spin180....");
    if (mod == 1) {
      pwmControl.setSpeedSpinLeft(0);
      pwmControl.setSpeedSpinRight(100);
    } else {
      pwmControl.setSpeedSpinLeft(100);
      pwmControl.setSpeedSpinRight(0);
    }
  }

  public void right() {
    if (DEBUG)
      System.out.println("StrategyMgr: right....");
    pwmControl.setSpeedTurnLeft(100);
    pwmControl.setSpeedRight(zeroSpeed);
  }

  public void left() {
    if (DEBUG)
      System.out.println("StrategyMgr: left....");
    pwmControl.setSpeedLeft(zeroSpeed);
    pwmControl.setSpeedTurnRight(100);
  }

  public void bearRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: bearRight....");
    pwmControl.setSpeedTurnLeft(100);
    pwmControl.setSpeedTurnRight(60);
  }

  public void bearLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: bearLeft....");
    pwmControl.setSpeedTurnLeft(60);
    pwmControl.setSpeedTurnRight(100);
  }

  public void back() {
    if (DEBUG)
      System.out.println("StrategyMgr: back....");
    pwmControl.setSpeedLeft(0);
    pwmControl.setSpeedRight(0);
  }

  public void backBearLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: backBearLeft....");
    pwmControl.setSpeedLeft(30);
    pwmControl.setSpeedRight(0);
  }

  public void backBearRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: backBearRight....");
    pwmControl.setSpeedLeft(0);
    pwmControl.setSpeedRight(30);
  }
}
