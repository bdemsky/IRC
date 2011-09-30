/*
 * RobotMain.java
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
 * Robot's Main class - instantiates all required classes and resources.
 * 
 * @author Michael Gesundheit
 * @version 1.0
 */
public class RobotMain {

  private static boolean DEBUG1 = true;
  private static boolean DEBUG = false;
  private static final int OFF_MODE = 1;
  private static final int ON_MODE = 2;
  private static final int MANUAL_MODE = 1;
  private static final int AUTONOMUS_MODE = 2;
  private static final int ON_OFF = 128;// 0x80
  private static final int MANUAL_AUTONOMUS = 32; // 0x20
  public static final int LINE_SENSORS = 64; // 0x40
  private static final int SONAR_SENSORS = 96; // 0x60
  public static final int ALL_COMMANDS = 0xe0;

  public static byte LF_FRONT = 0x1;
  public static byte RT_FRONT = 0x2;
  public static byte RT_SIDE = 0x4;
  public static byte BK_SIDE = 0x8;
  public static byte LF_SIDE = 0x10;
  public static byte ALL_SONARS = 0x1f;
  public static byte LF_RT_FRONT = 0x3; // LF_FRONT | RT_FRONT;
  public static byte LS_LEFT = 0x1;
  public static byte LS_RIGHT = 0x2;
  public static byte LS_REAR = 0x4;
  public static byte LS_LEFT_RIGHT = 0x3;
  public static byte LS_LEFT_REAR = 0x5;
  public static byte LS_RIGHT_REAR = 0x6;
  public static byte LS_ALL = 0x7;
  private static int ALL_DATA = 0x1f;

  private static PWMManager pwmm;
  private static StrategyMgr strategyMgr;

  private static int onOffMode = ON_MODE;
  private static int manualAutonomusMode = AUTONOMUS_MODE;
  private static byte lineSensorsMask;
  private static byte sonarSensors;
  private static byte[] command;
  private static byte currentCommand;
  private static Object obj;
  private static Object obj1;
  private static byte privSonars;
  private static byte privLineSensors;
  private static byte currentSonars;
  private static byte currentLineSensors;
  public static String pwmSelection;

  /**
   * Constructor for the class for the robot main thread.
   */
  RobotMain() {
  }

  /**
   * Processes sonar sensor input. This method is called each time new sonar
   * sensor data arrives, and each time that a mode switch occurs between ON/OFF
   * and Manual Override.
   */
  static void processSonars() {
    strategyMgr.processSonars(sonarSensors);
  }

  /**
   * Processes line sensor input. This method is called each time new line
   * sensor data arrives, and each time that a mode switch occurs between ON/OFF
   * and Manual Override.
   */
  static void processLineSensors() {
    strategyMgr.processLineSensors(lineSensorsMask);
    resume();
  }

  /**
   * Resumes motors as per the last sonar command.
   */
  public static void resume() {
    processSonars();
  }

  /**
   * Extracts sonar sensor data from the adjunct sensor controller and from line
   * sensor data from the JStamp line sensor pins.
   */
  private static void processIOCommand() {

    int data = 0;
    int opCode = 0;
    // synchronized (obj1) {
    data = (int) (currentCommand & ALL_DATA);
    opCode = (int) (currentCommand & 0xe0); // ALL_COMMANDS);
    // }

    if (DEBUG) {
      // System.out.println("processIOCommand: Getting in opCode = "
      // + Integer.toHexString((int) opCode) + " data = " +
      // Integer.toHexString((int) data));
    }
    switch ((int) opCode) {
    case ON_OFF:
      if (DEBUG) {
        System.out.println("processIO: ON_OFF....");
      }
      if ((data & 0x1) == 0x1) {
        System.out.println("processIO: ON MODE........");
        onOffMode = ON_MODE;
        processLineSensors();
        processSonars();
      } else {
        System.out.println("processIO: OFF MODE.......");
        onOffMode = OFF_MODE;
        strategyMgr.stop();
      }
      break;
    case MANUAL_AUTONOMUS:
      if (DEBUG) {
        System.out.println("processIO: Setting manual_autonomus mode");
      }
      if ((data & 0x1) == 0x1) {
        manualAutonomusMode = MANUAL_MODE;
        pwmm.setManualMode();
      } else {
        manualAutonomusMode = AUTONOMUS_MODE;
        pwmm.setAutomatedMode();
        processLineSensors();
        processSonars();
      }
      break;
    case LINE_SENSORS:
      if (DEBUG) {
        // System.out.println("processIO: Line Sensors.................."
        // + Integer.toBinaryString((int) (data & LS_ALL)));
      }
      lineSensorsMask = (byte) (data & LS_ALL);

      /*
       * Line sensors with '0' data means the robot moved off the edge line, and
       * there is nothing to do.
       */
      if (((data & LS_ALL) == 0) || ((data & LS_ALL) == privLineSensors)) {
        privLineSensors = (byte) (data & LS_ALL);
        return;
      }
      if ((onOffMode == ON_MODE) && (manualAutonomusMode == AUTONOMUS_MODE)) {
        if (DEBUG)
          System.out.println("processIO: Line Sensors..Process...!!!");
        processLineSensors();
      }
      break;
    case SONAR_SENSORS:
      if (DEBUG) {
        // System.out.println("processIO: SONAR_SENORS: bits = ......"
        // + Integer.toBinaryString((int) (currentCommand & ALL_SONARS)));
      }
      currentSonars = (byte) (data & ALL_SONARS);
      /*
       * No need to synchronized on this variable assignment since all referring
       * code is on the same logical thread
       */
      sonarSensors = (byte) currentSonars;
      if ((onOffMode == ON_MODE) && (manualAutonomusMode == AUTONOMUS_MODE)) {
        processSonars();
      }
      break;
    default:
      System.out.println("processIOCommand: Default: opCode = " + Integer.toString((int) opCode)
          + " data = " + Integer.toString((int) data));
    }
  }

  /**
   * Sets the simulation mode on in the IOManager.
   */
  // static public void setSimulation() {
  // sm.setSimulation();
  // }

  /**
   * Resets the simulation mode in the IOManager.
   */
  // static public void resetSimulation() {
  // sm.resetSimulation();
  // }

  /**
   * Saves the current IOManager command byte locally.
   */
  static public void setIOManagerCommand(byte[] cmd) {
    // synchronized (obj1) {
    currentCommand = cmd[0];
    // }
    // synchronized (obj) {
    try {
      // obj.notify();
    } catch (IllegalMonitorStateException ie) {
      System.out.println("IllegalMonitorStateException caught trying to notify");
    }
    // }
  }

  /**
   * Sets debug messaging state: true - Activate debug messages false -
   * deactivate debug messages
   */
  static public void setDebug(boolean debug) {
    DEBUG = debug;
  }

  /**
   * Runs the robot's main thread.
   */
  public static void main(String args[]) {

    boolean active = true;
    /**
     * RealTime management of the robot behaviour based on sensors and commands
     * input.
     */

    /**
     * Set the low level PWM interface type, e.g. "rtsj" or "native" (ajile
     * library-based).
     */
    pwmSelection = "rtsj";

    System.out.println("PWM Selction is: " + pwmSelection);

    pwmm = new PWMManager(pwmSelection);

    // Pass in the PWMManager for callbacks.
    // sm = new IOManager(pwmm);
    // ram = new RemoteApplicationManager(pwmm);
    strategyMgr = new StrategyMgr(pwmm);

    /*
     * Sets initial values for the speed and agility parameters. Speed and
     * agility are arbitrary scale factors for the overall speed and speed of
     * turns, respectively. These work with PWM via the native ajile libraries,
     * but do not work well with the RTSJ implementation due to limits on the
     * granularity of timing for the PWM pulse (quantization).
     */
    pwmm.setSpeedAgilityFactors(100, 100);

    /*
     * Robot's initial state is "ON" by default. Set this parameter to "OFF" if
     * the robot is to be started over the TCP/IP link.
     */
    // issueCommand("OFF");

    int count = 0;

    while (active && count < 10000) {
      try {
        if (DEBUG) {
          System.out.println("Main: Waiting for remote commands");
        }
        // try {
        // obj.wait();
        // } catch (IllegalMonitorStateException ie) {
        // System.out.println("IllegalMonitorStateException caught in main loop");
        // }
        currentCommand = TestSensorInput.getCommand();
        processIOCommand();
        // Nothing to do
      } catch (Exception e) {
      }
      count++;
    }
    System.exit(0);
  }

}
