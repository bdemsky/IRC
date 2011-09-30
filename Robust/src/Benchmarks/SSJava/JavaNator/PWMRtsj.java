/*
 * PWMRtsj.java
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
 * This class is motor controller specific. In our case the motor controller
 * require for 0 speed 1.5 ms out of 10-20ms cycle and a change between 1 to 2
 * seconds for max reverse and forward speed. These values: 1, 1.5, 2 and 20 ms
 * are thus this class specific
 * 
 * @author Michael Gesundheit
 * @version 1.0
 */

public class PWMRtsj extends PWMControl {

  // RelativeTime zeroSpeedDuration;
  // RelativeTime timeUpDuration;
  // RelativeTime timeDownDuration;
  // RelativeTime cycleTime;
  boolean updateTime;
  int sleepUpDurationNano = 500000;
  long sleepUpDurationMilli = 1;
  int sleepDownDurationNano = 500000;
  long sleepDownDurationMilli = 18;
  int pulseWidth;
  // Object obj;

  private static final int GPIO_A_OER = 0x09002000;
  private static final int GPIO_A_OUT = GPIO_A_OER + 4;
  private static final int GPIO_A_IN = GPIO_A_OER + 8;

  /**
   * Constructor - Start a standard Java thread. The thread is suspended and
   * will wake up evey 18 ms. It will issue a 1-2ms pulse and suspends itself
   * again to 18ms. This is to have a total of 20ms or less yet it's the maxim
   * possible cycle so as to load the cpu as little as possible.
   */
  public PWMRtsj(PWMManager pwmMan, int motor1bit, int motor2bit) {

    super(pwmMan, motor1bit, motor2bit); // This is papa

    motorLeftUpTime = 450000; // Nano seconds
    motorRightUpTime = 450000; // Nano seconds

    if (DEBUG) {
      System.out.println("PWMRtsj: About to start RoboThread...");
    }

    // t2 = new RoboThread();
    // t2.start();
    // t2.setPriority(10);
  }

  // A poor's man ajustimg for the 0 speed which found
  // to be 450000 nano seconds.
  private int normalizeTime(int timePosition) {
    if ((timePosition <= 50) && (timePosition >= 44)) {
      return 45;
    }
    return timePosition;
  }

  /**
   * setSpeedSpin - Set speed for the spin case motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedSpinLeft(int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    /* 100-input to get reverse polarity for this motor */
    /* since it's mounted in reverse direction to the other motor */
    if (DEBUG) {
      System.out.println("setSpeedSpinLeft: input-> " + Integer.toString(timePosition));
    }
    int timePos = normalizeTime(timePosition);

    int motorUpTime = (int) (timePos * agilityFactor * speedFactor);
    // System.out.println("Left Motor UpTime1: " +
    // Integer.toString(motorUpTime));
    // Since the right motor is hanging in reverse position
    // the direction should be opposit
    // Bug in wait. Can't take 0 nanoseconds
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }

    if (DEBUG) {
      System.out.println("setSpeedSpinLeft: output-> = " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in the speed and agility factors */
      motorLeftUpTime = motorUpTime;
//    }
  }

  /**
   * setSpeedSpinRight - Set speed for the spin case right motor.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedSpinRight(int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    /* An input of 50 should result in 0 speed. */
    /* 100 should result in full speed forward */
    /* while 0 should result in full speed backwords */
    if (DEBUG) {
      System.out.println("setSpeedSpinRight: input-> " + Integer.toString(timePosition));
    }
    int timePos = normalizeTime(timePosition);

    int motorUpTime = (int) ((timePos) * agilityFactor * speedFactor);
    // Bug in wait. Cant take 0 nanoseconds
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }

    if (DEBUG) {
      System.out.println("setSpeedSpinRight: output-> = " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in the speed and agility factors */
      motorRightUpTime = motorUpTime;
//    }
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 1
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnLeft(int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    /* 100-input to get reverse polarity for this motor */
    /* since it's mounted in reverse direction to the other motor */
    if (DEBUG) {
      System.out.println("setSpeedTurnLeft: input-> " + Integer.toString(timePosition));
    }
    int timePosLocal = normalizeTime(timePosition);
    int motorUpTime =
        (timePosLocal * 100 + ((100 - timePosLocal) * (100 - agilityFactor))) * speedFactor;
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }
    if (DEBUG) {
      System.out.println("setSpeedTurnLeft: output-> = " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in the speed and agility factors */
      motorLeftUpTime = motorUpTime;
//    }
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 2
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnRight(int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    if (DEBUG) {
      System.out.println("setSpeedTurnRight: input-> " + Integer.toString(timePosition));
    }
    int timePos = normalizeTime(timePosition);
    int motorUpTime = ((timePos * 100) + ((100 - timePos) * (100 - agilityFactor))) * speedFactor;
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }

    if (DEBUG) {
      System.out.println("setSpeedTurnRight: output-> " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in the speed and agility factors */
      motorRightUpTime = motorUpTime;
//    }
  }

  /**
   * setSpeedLeft - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedLeft(int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    /* 100-input to get reverse polarity for this motor */
    /* since it's mounted in reverse direction to the other motor */
    if (DEBUG) {
      System.out.println("setSpeedLeft: input-> " + Integer.toString(timePosition));
    }
    int timePos = normalizeTime(timePosition);
    int motorUpTime = (int) (timePos * 100) * speedFactor;
    /*
     * System.out.println("motorUpTime = " + Integer.toString(motorUpTime) +
     * " timePos = " + Integer.toString((int)timePos) + " timePosition = " +
     * Integer.toString((int)timePosition) + " speedFactor = " +
     * Integer.toString(speedFactor));
     */
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }
    if (DEBUG) {
      System.out.println("setSpeedLeft: output-> " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in speedFactor */
      motorLeftUpTime = motorUpTime;
//    }
  }

  /**
   * setSpeedRight - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedRight(int timePosition) {
    if (DEBUG) {
      System.out.println("setSpeedRight: input-> " + Integer.toString(timePosition));
    }
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    int timePos = normalizeTime(timePosition);
    int motorUpTime = (int) (timePos * 100) * speedFactor;
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }
    if (DEBUG) {
      System.out.println("setSpeedRight: output-> " + Integer.toString(motorUpTime));
    }
//    synchronized (this) {
      /* Factor in speedFactor */
      motorRightUpTime = motorUpTime;
//    }
  }

  public void setSpeedAgilityFactors(int speed, int agility) {
//    synchronized (this) {
      speedFactor = speed;
      agilityFactor = agility;
//    }
  }

  public void setUrgentReverse() {
//    synchronized (this) {
      motorLeftUpTime = 1;
      motorRightUpTime = 1;
//    }
  }

  public void setUrgentStraight() {
//    synchronized (this) {
      motorLeftUpTime = 99;
      motorRightUpTime = 99;
//    }
  }

  public void justSync() {
//    synchronized (this) {
      motorLeftUpTime = motorLeftUpTime;
      motorRightUpTime = motorRightUpTime;
//    }
  }

  /**
   * Control debug messageing. true - Activate debug messages false - deactivate
   * debug messages
   */
  public void setDebug(boolean debug) {
    DEBUG = debug;
  }

}
