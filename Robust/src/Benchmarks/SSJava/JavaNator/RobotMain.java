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
@LATTICE("MGR<MASK,PREV<CMD,MASK<CMD,CMD<CCMD,CCMD<T,PREV*,MASK*")
@METHODDEFAULT("THIS,THISLOC=THIS,GLOBALLOC=THIS")
public class RobotMain {

  @LOC("T")
  private static boolean DEBUG1 = true;
  @LOC("T")
  private static boolean DEBUG = true;

  public static final int OFF_MODE = 1;
  public static final int ON_MODE = 2;
  public static final int MANUAL_MODE = 1;
  public static final int AUTONOMUS_MODE = 2;
  public static final int ON_OFF = 128;// 0x80
  public static final int MANUAL_AUTONOMUS = 32; // 0x20
  public static final int LINE_SENSORS = 64; // 0x40
  public static final int SONAR_SENSORS = 96; // 0x60
  public static final int ALL_COMMANDS = 0xe0;

  public static final byte LF_FRONT = 0x1;
  public static final byte RT_FRONT = 0x2;
  public static final byte RT_SIDE = 0x4;
  public static final byte BK_SIDE = 0x8;
  public static final byte LF_SIDE = 0x10;
  public static final byte ALL_SONARS = 0x1f;
  public static final byte LF_RT_FRONT = 0x3; // LF_FRONT | RT_FRONT;
  public static final byte LS_LEFT = 0x1;
  public static final byte LS_RIGHT = 0x2;
  public static final byte LS_REAR = 0x4;
  public static final byte LS_LEFT_RIGHT = 0x3;
  public static final byte LS_LEFT_REAR = 0x5;
  public static final byte LS_RIGHT_REAR = 0x6;
  public static final byte LS_ALL = 0x7;
  public static final int ALL_DATA = 0x1f;

  @LOC("MGR")
  private PWMManager pwmm;
  @LOC("MGR")
  private StrategyMgr strategyMgr;

  @LOC("MASK")
  private int onOffMode = ON_MODE;
  @LOC("MASK")
  private int manualAutonomusMode = AUTONOMUS_MODE;
  @LOC("MASK")
  private byte lineSensorsMask;
  @LOC("MASK")
  private byte sonarSensors;
  @LOC("CCMD")
  private byte currentCommand;
  @LOC("MASK")
  private byte privSonars;
  @LOC("PREV")
  private byte privLineSensors;
  @LOC("MASK")
  private byte currentSonars;
  @LOC("MASK")
  public String pwmSelection;

  /**
   * Constructor for the class for the robot main thread.
   */
  RobotMain() {
  }

  /**
   * Processes sonar sensor input. This method is called each time new sonar sensor data arrives,
   * and each time that a mode switch occurs between ON/OFF and Manual Override.
   */
  void processSonars() {
    strategyMgr.processSonars(sonarSensors);
  }

  /**
   * Processes line sensor input. This method is called each time new line sensor data arrives, and
   * each time that a mode switch occurs between ON/OFF and Manual Override.
   */
  void processLineSensors() {
    strategyMgr.processLineSensors(lineSensorsMask);
    resume();
  }

  /**
   * Resumes motors as per the last sonar command.
   */
  void resume() {
    processSonars();
  }

  /**
   * Extracts sonar sensor data from the adjunct sensor controller and from line sensor data from
   * the JStamp line sensor pins.
   */
  @LATTICE("THIS,THISLOC=THIS,GLOBALLOC=THIS")
  private void processIOCommand() {

    // int data = 0;
    // int opCode = 0;
    // synchronized (obj1) {
    @LOC("THIS,RobotMain.CMD") int data = (int) (currentCommand & ALL_DATA);
    @LOC("THIS,RobotMain.CMD") int opCode = (int) (currentCommand & 0xe0); // ALL_COMMANDS);
    // }

    if (DEBUG) {
      System.out.println("processIOCommand: Default: opCode = " + Integer.toString((int) opCode)
          + " data = " + Integer.toString((int) data));
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
       * Line sensors with '0' data means the robot moved off the edge line, and there is nothing to
       * do.
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
       * No need to synchronized on this variable assignment since all referring code is on the same
       * logical thread
       */
      sonarSensors = (byte) currentSonars;
      if ((onOffMode == ON_MODE) && (manualAutonomusMode == AUTONOMUS_MODE)) {
        processSonars();
      }
      break;
    default:
      strategyMgr.stop();
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
  public void setIOManagerCommand(byte[] cmd) {
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
   * Sets debug messaging state: true - Activate debug messages false - deactivate debug messages
   */
  static public void setDebug(boolean debug) {
    DEBUG = debug;
  }

  /**
   * Runs the robot's main thread.
   */
  public static void main(@LOC("IN") String args[]) {

    TestSensorInput.init();
    RobotMain r = new RobotMain();
    r.doit();
  }

  @LATTICE("THIS<C,THIS<MC,MC<IN,C<IN,IN<T,C*,THISLOC=THIS,GLOBALLOC=THIS")
  public void doit() {
    @LOC("T") boolean active = true;
    /**
     * RealTime management of the robot behaviour based on sensors and commands input.
     */

    /**
     * Set the low level PWM interface type, e.g. "rtsj" or "native" (ajile library-based).
     */
    pwmSelection = "rtsj";

    System.out.println("PWM Selction is: " + pwmSelection);

    pwmm = new PWMManager(pwmSelection);

    // Pass in the PWMManager for callbacks.
    // sm = new IOManager(pwmm);
    // ram = new RemoteApplicationManager(pwmm);
    @LOC("MC") MotorControl mc = new MotorControl(100, 100);
    strategyMgr = new StrategyMgr(mc);

    /*
     * Sets initial values for the speed and agility parameters. Speed and agility are arbitrary
     * scale factors for the overall speed and speed of turns, respectively. These work with PWM via
     * the native ajile libraries, but do not work well with the RTSJ implementation due to limits
     * on the granularity of timing for the PWM pulse (quantization).
     */
    pwmm.setSpeedAgilityFactors(100, 100);

    /*
     * Robot's initial state is "ON" by default. Set this parameter to "OFF" if the robot is to be
     * started over the TCP/IP link.
     */
    // issueCommand("OFF");

    SSJAVA: while (active) {

      @LOC("IN") Command com = HWSimulator.getCommand();
      currentCommand = com.command;
      sonarSensors = com.sonarSensors;
      lineSensorsMask = com.lineSensorsMask;
      manualAutonomusMode = com.manualAutonomusMode;
      onOffMode = com.onOffMode;

      if (currentCommand == -1) {
        break;
      }
      System.out.println("currentCommand=" + currentCommand);
      processIOCommand();
      // erase current settings
      initialize();
    }
    System.exit(0);
  }

  public void initialize() {
    privLineSensors = 0;
    currentCommand = 0;
    privSonars = 0;
    currentSonars = 0;
    pwmSelection = "";
  }

}
