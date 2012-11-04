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
 * This class is a wrapper for a PWMControl introduced by porting to SSJava. It
 * maintains two key fields motorLeftUpTime and motorRightUpTime and expects
 * that the control thread who is running from the other side gets the current
 * settings.
 */



public class MotorControl {
  
  boolean DEBUG = true;
  
  int motorLeftUpTime = 150;
  
  int motorRightUpTime = 150;
  
  int speedFactor;
  
  int agilityFactor;

  public MotorControl(int speedFactor, int agilityFactor) {
    this.speedFactor = speedFactor;
    this.agilityFactor = agilityFactor;
  }

  // A poor's man ajustimg for the 0 speed which found
  // to be 450000 nano seconds.
  
  private int normalizeTime( int timePosition) {
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
  public void setSpeedSpinLeft( int timePosition) {
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

    // if (DEBUG) {
    // System.out.println("setSpeedSpinLeft: output-> = " +
    // Integer.toString(motorUpTime));
    // }
    // synchronized (this) {
    /* Factor in the speed and agility factors */
    motorLeftUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setSpeedSpinLeft: output-> " + motorLeftUpTime);
    }
  }

  /**
   * setSpeedSpinRight - Set speed for the spin case right motor.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedSpinRight( int timePosition) {
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

    // if (DEBUG) {
    // System.out.println("setSpeedSpinRight: output-> = " +
    // Integer.toString(motorUpTime));
    // }
    // synchronized (this) {
    /* Factor in the speed and agility factors */
    motorRightUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setSpeedSpinRight: output-> " + motorRightUpTime);
    }
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 1
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnLeft( int timePosition) {
    /* Speed comes in as a number between 0 - 100 */
    /* It represents values between 1 to 2 ms */
    /* 100-input to get reverse polarity for this motor */
    /* since it's mounted in reverse direction to the other motor */
    if (DEBUG) {
      System.out.println("setSpeedTurnLeft: input-> " + Integer.toString(timePosition));
    }
     int timePosLocal = normalizeTime(timePosition);
     int motorUpTime = (timePosLocal * 100 + ((100 - timePosLocal) * (100 - agilityFactor))) * speedFactor;
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }
    // if (DEBUG) {
    // System.out.println("setSpeedTurnLeft: output-> = " +
    // Integer.toString(motorUpTime));
    // }
    // synchronized (this) {
    /* Factor in the speed and agility factors */
    motorLeftUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setSpeedTurnLeft: output-> " + motorLeftUpTime);
    }
  }

  /**
   * setSpeedTurnM1 - set speed considering agility factor for motor 2
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedTurnRight( int timePosition) {
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

    // synchronized (this) {
    /* Factor in the speed and agility factors */
    motorRightUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setSpeedTurnRight: output-> " + motorRightUpTime);
    }
  }

  /**
   * setSpeedLeft - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedLeft( int timePosition) {
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
     * System.out.println("motorUpTime = " + Integer.toStri
     * ng(motorUpTime) + " timePos = " + Integer.toString((int)timePos) +
     * " timePosition = " + Integer.toString((int)timePosition) +
     * " speedFactor = " + Integer.toString(speedFactor));
     */
    if (motorUpTime == 0) {
      motorUpTime = 1;
      // System.out.println("returning....");
      // return; // ndr
    } else if (motorUpTime == 1000000) {
      motorUpTime--;
    }

    // synchronized (this) {
    /* Factor in speedFactor */
    motorLeftUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out
          .println("MotorContol: setSpeedLeft: output-> " + Integer.toString(motorLeftUpTime));
    }
  }

  /**
   * setSpeedRight - speed control for motor 1.
   * 
   * @param uptime
   *          pulse width (time position)
   */
  public void setSpeedRight( int timePosition) {
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
    // synchronized (this) {
    /* Factor in speedFactor */
    motorRightUpTime = motorUpTime;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setSpeedRight: output-> " + motorRightUpTime);
    }
  }

  public void setUrgentReverse() {
    // synchronized (this) {
    motorLeftUpTime = 1;
    motorRightUpTime = 1;
    // }
    if (DEBUG) {
      System.out.println("MotorControl: setUrgentReverse: motorLeftUpTime-> " + motorLeftUpTime);
      System.out.println("MotorControl: setUrgentReverse: motorRightUpTime-> " + motorRightUpTime);
    }
  }

  public void setUrgentStraight() {
    // synchronized (this) {
    motorLeftUpTime = 99;
    motorRightUpTime = 99;
    if (DEBUG) {
      System.out.println("MotorControl: setUrgentStraight: motorLeftUpTime-> " + motorLeftUpTime);
      System.out.println("MotorControl: setUrgentStraight: motorRightUpTime-> " + motorRightUpTime);
    }
    // }
  }

  public void justSync() {
    // synchronized (this) {
    motorLeftUpTime = motorLeftUpTime;
    motorRightUpTime = motorRightUpTime;
    if (DEBUG) {
      System.out.println("MotorControl: justSync: motorLeftUpTime-> " + motorLeftUpTime);
      System.out.println("MotorControl: justSync: motorRightUpTime-> " + motorRightUpTime);
    }
    // }
  }

  /**
   * Control debug messageing. true - Activate debug messages false - deactivate
   * debug messages
   */
  public void setDebug(boolean debug) {
    DEBUG = debug;
  }

}
