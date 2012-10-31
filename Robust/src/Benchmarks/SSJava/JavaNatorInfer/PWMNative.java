/*
 * PWMNative.java
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

//public class PWMNative extends PWMControl implements Runnable, InterruptEventListener  {
public class PWMNative extends PWMControl {

  private int PULSE_INTERVAL = 2000;
  private int NATIVE_OFFSET = 100;
  private Object obj;
  private Object tc0Obj;
  private Object tc1Obj;
  private int pulseUpTime;

  // private TimerCounter tc0;
  // private TimerCounter tc1;
  // private TimerCounter[] tcSet = new TimerCounter[2];

  PWMNative(PWMManager pwmMan, int motor1bit, int motor2bit) {
    super(pwmMan, motor1bit, motor2bit);

    System.out.println("PWMNative constructor.....Start");
    obj = new Object();
    tc0Obj = new Object();
    tc1Obj = new Object();

    // TimerCounter.setPrescalerClockSource(TimerCounter.INTERNAL_PERIPHERAL_CLOCK);
    // TimerCounter.setPrescalerReloadRegisterValue(375);
    // TimerCounter.setPrescalerEnabled(true);

    // tc0 = tcSet[0] = new TimerCounter(0);
    // tc0.setMode_IO_Line_A(TimerCounter.TIMER_0_OUTPUT);
    // // bring TIMER_0_OUTPUT_DIVIDE_BY_2 out via IO_Line_B
    // tc0.setMode_IO_Line_B(TimerCounter.TIMER_0_OUTPUT_DIVIDE_BY_2);
    // // In JemBuilder, go to Pin Setup 2 and allocate Port E bit 6 to
    // // be Timer IO_Line_B.
    //
    // // Connect signal lead of servo (usually the white one) to IOE6 on
    // // JStamp
    // tc0.set_IO_Line_A_Polarity(TimerCounter.POLARITY_ACTIVE_STATE_HIGH);
    // tc0.set_IO_Line_B_Polarity(TimerCounter.POLARITY_ACTIVE_STATE_HIGH);
    // tc0.setExternalTimerEnableMode(TimerCounter.TIMER_ENABLED_ONLY_VIA_MTEN_AND_TRIGGER);
    // tc0.setReloadRegisterValue(100);
    // tc0.setExternalStartTriggerMode(TimerCounter.NO_EXTERNAL_START_TRIGGER);
    // tc0.setMasterTimerEnabled(true);
    // tc0.setGlobalInterruptEnabled(true);
    // tc0.setTimeOutInterruptEnabled(true);
    // tc0.setTimerTriggerRegister(false);
    // System.out.println("PWMNative: Constructor completed 1....");
    /*
     * tc1 = tcSet[1] = new TimerCounter ( 1 ); tc1.setMode_IO_Line_A(
     * TimerCounter.TIMER_1_OUTPUT ); //bring TIMER_0_OUTPUT_DIVIDE_BY_2 out via
     * IO_Line_B tc1.setMode_IO_Line_B( TimerCounter.TIMER_1_OUTPUT_DIVIDE_BY_2
     * ); //In JemBuilder, go to Pin Setup 2 and allocate Port E bit 6 to //be
     * Timer IO_Line_B.
     * tc1.set_IO_Line_A_Polarity(TimerCounter.POLARITY_ACTIVE_STATE_HIGH);
     * tc1.set_IO_Line_B_Polarity(TimerCounter.POLARITY_ACTIVE_STATE_HIGH);
     * tc1.setExternalTimerEnableMode
     * (TimerCounter.TIMER_ENABLED_ONLY_VIA_MTEN_AND_TRIGGER );
     * tc1.setReloadRegisterValue( 100 );
     * tc1.setExternalStartTriggerMode(TimerCounter.NO_EXTERNAL_START_TRIGGER );
     * tc1.setMasterTimerEnabled( true ); tc1.setGlobalInterruptEnabled( true);
     * tc1.setTimeOutInterruptEnabled( true); tc1.setTimerTriggerRegister( false
     * );
     */
    /*
     * // Add interrupt event listener for GPTC
     * InterruptController.addInterruptListener( this,
     * InterruptController.GPTC_INTERRUPT );
     * 
     * // Turn on interrupts InterruptController.enableInterrupt(
     * InterruptController.GPTC_INTERRUPT );
     * 
     * // start all prescaler based timers TimerCounter.setPrescalerEnabled(
     * true );
     */
    // t2 = new Thread(this);
    // t2.start();
    // t2.setPriority(20);
    System.out.println("PWMNative: Constructor return.....");
  }

  public void setUpTime(int upTime) {
    // synchronized (obj) {
    pulseUpTime = upTime;
    // }
  }

  /*
   * public void interruptEvent() { TimerCounter tc; int tcIntrState;
   * 
   * System.out.println("PWMNative: InterruptEvent"); do { for ( int tcNum=0;
   * tcNum<2; tcNum++ ) { tc = tcSet[ tcNum ];
   * if(tc.readAndClear_TimeOutInterruptStatus()){ switch(tcNum){ case 0:
   * System.out.println("PWMNative: Interrupt case 0"); synchronized(tc0Obj){
   * System.out.println("PWMNative: Interrupt notify 0"); tc0Obj.notify(); }
   * break; case 1: System.out.println("PWMNative: Interrupt case 1");
   * synchronized(tc1Obj){ System.out.println("PWMNative: Interrupt notify 1");
   * tc1Obj.notify(); } break; default:; } } } } while (
   * TimerCounter.isGPTCInterruptPending() ); }
   */
  public void run() {
    //
    // System.out.println("PWMNative: run method........");
    // int upTime0 = 150; // 1.5 milli seconds = 0 speed
    // int upTime1 = 150; // 1.5 milli seconds = 0 speed
    //
    // while (true) {
    // synchronized (obj) {
    // /*
    // * System.out.println("PWMNative: Updating Up Times......Left = " +
    // * Integer.toString(motorLeftUpTime) + " Right = " +
    // * Integer.toString(motorRightUpTime));
    // */
    // upTime0 = motorLeftUpTime;
    // upTime1 = motorRightUpTime;
    // }
    //
    // // Timer number 1
    // tc0.setReloadRegisterValue(upTime0);
    // outToPortMLeft(PULSE_HIGH);
    // tc0.setTimerTriggerRegister(true);
    // do {
    // } while (tc0.didTimeOutInterruptOccur());
    // outToPortMLeft(PULSE_LOW);
    // tc0.setTimerTriggerRegister(false);
    // tc0.resetTimeOutInterruptStatus();
    //
    // // System.out.println("PWMNative: Big loop long suspend1");
    // try {
    // tc0Obj.wait(18, 500000);
    // } catch (Exception e) {
    // }
    // // System.out.println("PWMNative: Big loop long suspend2");
    //
    // tc0.setReloadRegisterValue(upTime1);
    // outToPortMRight(PULSE_HIGH);
    // tc0.setTimerTriggerRegister(true);
    // do {
    // } while (tc0.didTimeOutInterruptOccur());
    // outToPortMRight(PULSE_LOW);
    // tc0.setTimerTriggerRegister(false);
    // tc0.resetTimeOutInterruptStatus();
    //
    // try {
    // tc0Obj.wait(18, 500000);
    // } catch (Exception e) {
    // }

    /*
     * // Timer number 2 tc1.setReloadRegisterValue( upTime1 );
     * tc1.setTimerTriggerRegister( true ); synchronized(tc1Obj){ try{
     * System.out.println("PWMNative: Sleep 3"); tc1Obj.wait();
     * System.out.println("PWMNative: Wake Up 3"); }catch(Exception e){ } }
     * tc1.setTimerTriggerRegister( false ); tc1.resetTimeOutInterruptStatus();
     * tc1.setReloadRegisterValue( PULSE_INTERVAL - upTime1 ); //this sets pulse
     * interval tc1.setTimerTriggerRegister( true ); synchronized(tc1Obj){ try{
     * System.out.println("PWMNative: Sleep 3"); tc1Obj.wait();
     * System.out.println("PWMNative: Wake Up 3"); }catch(Exception e){ } }
     * tc1.setTimerTriggerRegister( false ); tc1.resetTimeOutInterruptStatus();
     */
    // }
  }

  public void setManualMode() {
    if (DEBUG)
      System.out.println("PWMControl: setManualMode... ");
    // synchronized (obj) {
    if (manualMode == false) {
      manualMode = true;
    }
    // }
  }

  public void setAutomatedMode() {
    if (DEBUG)
      System.out.println("PWMControl: setAutomatedMode... ");
    /*
     * synchronized(obj){ if(manualMode == true){
     * System.out.println("PWMControl: wake me up... "); obj.notifyAll();
     * manualMode = false; } }
     */
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
    int timePos = timePosition + NATIVE_OFFSET;
    int motorUpTime = (int) (timePos * agilityFactor * speedFactor) / 10000;

    if (DEBUG) {
      System.out.println("setSpeedSpinLeft: output-> = " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in the speed and agility factors */
    motorLeftUpTime = motorUpTime;
    // }
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
    int timePos = timePosition + NATIVE_OFFSET;
    int motorUpTime = (int) ((timePos) * agilityFactor * speedFactor) / 10000;

    if (DEBUG) {
      System.out.println("setSpeedSpinRight: output-> = " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in the speed and agility factors */
    motorRightUpTime = motorUpTime;
    // }
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
    int timePosLocal = timePosition + NATIVE_OFFSET;
    int motorUpTime =
        ((timePosLocal * 100 + ((100 - timePosLocal) * (100 - agilityFactor))) * speedFactor) / 10000;
    if (DEBUG) {
      System.out.println("setSpeedTurnLeft: output-> = " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in the speed and agility factors */
    motorLeftUpTime = motorUpTime;
    // }
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
    int timePos = timePosition + NATIVE_OFFSET;
    int motorUpTime =
        (((timePos * 100) + ((100 - timePos) * (100 - agilityFactor))) * speedFactor) / 10000;

    if (DEBUG) {
      System.out.println("setSpeedTurnRight: output-> " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in the speed and agility factors */
    motorRightUpTime = motorUpTime;
    // }
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
    int timePos = timePosition + NATIVE_OFFSET;
    int motorUpTime = (int) ((timePos * 100) * speedFactor) / 10000;

    if (DEBUG) {
      System.out.println("setSpeedLeft: output-> " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in speedFactor */
    motorLeftUpTime = motorUpTime;
    // }
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
    int timePos = timePosition + NATIVE_OFFSET;
    int motorUpTime = (int) (((timePos * 100) * speedFactor) / 10000);

    if (DEBUG) {
      System.out.println("setSpeedRight: output-> " + Integer.toString(motorUpTime));
    }
    // synchronized (obj) {
    /* Factor in speedFactor */
    motorRightUpTime = motorUpTime;
    // }
  }

  public void setSpeedAgilityFactors(int speed, int agility) {
    // synchronized (obj) {
    speedFactor = speed;
    agilityFactor = agility;
    // }
  }

  public void setUrgentReverse() {
    // synchronized (obj) {
    motorLeftUpTime = 1;
    motorRightUpTime = 1;
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
