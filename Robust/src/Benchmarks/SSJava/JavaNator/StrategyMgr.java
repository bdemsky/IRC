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
@LATTICE("C<V,V<T,V*")
@METHODDEFAULT("THIS<IN,IN*,THISLOC=THIS,GLOBALLOC=THIS")
public class StrategyMgr {

  @LOC("C")
  private MotorControl mc;
  private static final int zeroSpeed = 45;
  @LOC("T")
  private Random rand;
  @LOC("T")
  private boolean DEBUGL = true;
  // private boolean DEBUGL = false;

  // private boolean DEBUG = true;
  @LOC("T")
  private boolean DEBUG = true;

  /**
   * Constructor - Invoke communication to remote application thread
   */
  public StrategyMgr(@DELEGATE MotorControl motorControl) {
    mc = motorControl;
    rand = new Random();
  }

  void processSonars(@LOC("IN") byte sonarSensors) {

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

  void processLineSensors(@LOC("IN") byte lineSensorsMask) {

    @LOC("IN") int count = 0;

    while ((lineSensorsMask & RobotMain.LS_ALL) != 0) {

      if (count > 100) {
        // if the robot fail to get out of weird condition wihtin 100 steps,
        // terminate while loop for stabilizing behavior.
        stop();
        break;
      }
      count++;

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
      lineSensorsMask = TestSensorInput.getCommand();
    }// while loop
  }

  public void stop() {
    if (DEBUG)
      System.out.println("StrageyMgr: stop....");
    mc.setSpeedLeft(zeroSpeed);
    mc.setSpeedRight(zeroSpeed);
  }

  public void search() {
    if (DEBUG)
      System.out.println("StrategyMgr: search....");
    mc.setSpeedLeft(70);
    mc.setSpeedRight(50);
  }

  public void straight() {
    if (DEBUG)
      System.out.println("StrategyMgr: strait....");
    mc.setSpeedLeft(100);
    mc.setSpeedRight(100);
  }

  public void spinRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: spinRight....");
    mc.setSpeedSpinLeft(100);
    mc.setSpeedSpinRight(0);
  }

  public void spinLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: spinLeft....");
    mc.setSpeedSpinLeft(0);
    mc.setSpeedSpinRight(100);
  }

  public void spin180() {
    @LOC("THIS,StrategyMgr.V") int mod = (rand.nextInt() % 2);

    if (DEBUG)
      System.out.println("StrategyMgr: spin180....");
    if (mod == 1) {
      mc.setSpeedSpinLeft(0);
      mc.setSpeedSpinRight(100);
    } else {
      mc.setSpeedSpinLeft(100);
      mc.setSpeedSpinRight(0);
    }
  }

  public void right() {
    if (DEBUG)
      System.out.println("StrategyMgr: right....");
    mc.setSpeedTurnLeft(100);
    mc.setSpeedRight(zeroSpeed);
  }

  public void left() {
    if (DEBUG)
      System.out.println("StrategyMgr: left....");
    mc.setSpeedLeft(zeroSpeed);
    mc.setSpeedTurnRight(100);
  }

  public void bearRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: bearRight....");
    mc.setSpeedTurnLeft(100);
    mc.setSpeedTurnRight(60);
  }

  public void bearLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: bearLeft....");
    mc.setSpeedTurnLeft(60);
    mc.setSpeedTurnRight(100);
  }

  public void back() {
    if (DEBUG)
      System.out.println("StrategyMgr: back....");
    mc.setSpeedLeft(0);
    mc.setSpeedRight(0);
  }

  public void backBearLeft() {
    if (DEBUG)
      System.out.println("StrategyMgr: backBearLeft....");
    mc.setSpeedLeft(30);
    mc.setSpeedRight(0);
  }

  public void backBearRight() {
    if (DEBUG)
      System.out.println("StrategyMgr: backBearRight....");
    mc.setSpeedLeft(0);
    mc.setSpeedRight(30);
  }
}
