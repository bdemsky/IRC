/*
 * PWMControl.java
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
 * This class is the super class of all motor control classes. The class
 * represent a generic interface to such class. In our case the motor controller
 * require for 0 speed 1.5 ms out of 10-20ms cycle and a change between 1 to 2
 * seconds for max reverse and forward speed. These values: 1, 1.5, 2 and 20 ms
 * are thus this class specific
 * 
 * @author Michael Gesundheit
 * @version 1.0
 */

public abstract class PWMControl {

  PWMManager pwmManager;
  boolean DEBUG = false;
  // boolean DEBUG = true;
  static final byte PULSE_HIGH = 1;
  static final byte PULSE_LOW = 0;
  int highMask;
  int m1HighMask;
  int m2HighMask;
  int lowMask;
  int myOwnBit;
  int myBitPos;
  int m1BitPos;
  int m2BitPos;
  int speedFactor;
  int agilityFactor;
  boolean updateTime;
  int pulseWidth;
  Object obj;

  int motorLeftUpTime = 150;
  int motorRightUpTime = 150;
  boolean manualMode = false;

  PWMControl(PWMManager pwmMan, int motor1bit, int motor2bit) {
    pwmManager = pwmMan; // This is papa
    m1BitPos = 0x1 << motor1bit;
    m2BitPos = 0x1 << motor2bit;
    m1HighMask = 0x1 << motor1bit;
    m2HighMask = 0x1 << motor2bit;
    lowMask = 0x0;
    obj = new Object();
  }

  public void setManualMode() {
    if (DEBUG)
      System.out.println("PWMContolr: setManualMode... ");
//    synchronized (obj) {
      if (manualMode == false) {
        manualMode = true;
      }
//    }
  }

  public void setAutomatedMode() {
    if (DEBUG)
      System.out.println("PWMControl: setAutomatedMode... ");
//    synchronized (obj) {
      if (manualMode == true) {
        System.out.println("PWMControl: wake me up... ");
        manualMode = false;
        // obj.notify();
        System.out.println("PWMControl: wake me up...... tried!!!");
      }
//    }
  }

  /**
   * OutPut value to motor control line
   */
  public void outToPortMLeft(byte value) {
    /*
     * System.out.println("PWMControl " + Integer.toString(myOwnBit) +
     * //" bit position = 0x" + Integer.toHexString(myOwnBit) +
     * " output value = 0x" + Integer.toHexString(value));
     */
    if (value == PULSE_HIGH) {
      pwmManager.writeToPort(m1BitPos, m1HighMask);
    } else {
      pwmManager.writeToPort(m1BitPos, lowMask);
    }
  }

  public void outToPortMRight(byte value) {
    /*
     * System.out.println("PWMControl " + Integer.toString(myOwnBit) +
     * //" bit position = 0x" + Integer.toHexString(myOwnBit) +
     * " output value = 0x" + Integer.toHexString(value));
     */
    if (value == PULSE_HIGH) {
      pwmManager.writeToPort(m2BitPos, m2HighMask);
    } else {
      pwmManager.writeToPort(m2BitPos, lowMask);
    }
  }

  /**
   * setSpeedSpin - Set speed for the spin case motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedSpinLeft(int timePosition) {
  }

  /**
   * setSpeedSpinRight - Set speed for the spin case right motor.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedSpinRight(int timePosition) {
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 1
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnLeft(int timePosition) {
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 2
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnRight(int timePosition) {
  }

  /**
   * setSpeedLeft - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedLeft(int timePosition) {
  }

  /**
   * setSpeedRight - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedRight(int timePosition) {
  }

  public void setSpeedAgilityFactors(int speed, int agility) {
//    synchronized (this) {
      speedFactor = speed;
      agilityFactor = agility;
//    }
  }

  public void setUrgentReverse() {
  }

  public void setUrgentStraight() {
  }

  /**
   * Control debug messageing. true - Activate debug messages false - deactivate
   * debug messages
   */
  public void setDebug(boolean debug) {
    DEBUG = debug;
  }
}
