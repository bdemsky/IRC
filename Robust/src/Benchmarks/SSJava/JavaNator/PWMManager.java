/*
 * PWMManager.java
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
 * PWMManager - Interface between robot strategy code and the various motors PWMControl classes.
 * Agility is a number between 0 to 100 and represent the precentage of max speed to be applied to
 * the turn side wheel. On left turn the left wheel will receive less speed as % of "speed" value
 * represented by agility.
 * 
 * @author Michael Gesundheit
 * @version 1.0
 */

@LATTICE("V")
@METHODDEFAULT("OUT<THIS,THIS<V,V<IN,V*,THISLOC=THIS,RETURNLOC=OUT")
public class PWMManager {

  // private int GPIO_A_OER = 0x09002000;
  // private int GPIO_A_OUT = GPIO_A_OER + 4;
  // private int GPIO_E_OER = 0x09002400;
  // private int GPIO_E_OUT = 0x09002404;
  // private byte MOTOR_PORTID_6 = 6; // Bit 2 (...3,2,1,0)
  // private byte MOTOR_PORTID_7 = 7; // Bit 3 (...3,2,1,0)
  @LOC("V")
  private int currentRegMask;
  @LOC("V")
  private boolean DEBUG = false;
  // private PWMControl pwmControl;
  @LOC("V")
  private RCBridge rcb;
  @LOC("V")
  private int speedFactor;
  @LOC("V")
  private int agilityFactor;
  @LOC("V")
  private int zeroSpeed = 45;

  /**
   * Constractor
   */
  public PWMManager(String pwmSelection) {
    /*
     * Instantiate PWM Makers one for each motor. motorPortId is 1 to 8 and is corresponding to 8
     * bits in a byte.
     */
    // if (pwmSelection.equals("rtsj"))
    // pwmControl = new PWMRtsj(this, MOTOR_PORTID_6, MOTOR_PORTID_7);
    // else {
    // System.out.println("Selection PWMNative is activated");
    // pwmControl = new PWMNative(this, MOTOR_PORTID_6, MOTOR_PORTID_7);
    // System.out.println("Selection PWMNative is activated.... Return");
    // }

    // rcb = new RCBridge(this);
    rcb = new RCBridge();

    /* Enable input bits 0,1 Enable output for the rest */
    // rawJEM.set(GPIO_E_OER, 0x00C0);
  }

  public void setManualMode() {
    if (DEBUG)
      System.out.println("PWMManager: setManualMode....");
    // pwmControl.setManualMode();
    rcb.setManualMode();
  }

  public void setAutomatedMode() {
    if (DEBUG)
      System.out.println("PWMManager: setAutomatedMode....");
    // pwmControl.setAutomatedMode();
    rcb.setAutomatedMode();
  }

  // public PWMControl getPWMControl() {
  // if (DEBUG)
  // System.out.println("PWMManager: getPWMControl....");
  // return pwmControl;
  // }

  public synchronized void writeToPort(int myBit, int myValue) {
    currentRegMask &= ~myBit; // e.g. 0x11110111
    currentRegMask |= myValue;
    /*
     * // if (DEBUG){ // System.out.println("PWMM: writeToPort: myBit = 0x" +
     * Integer.toHexString(myBit) + // " ~myBit = 0x" + Integer.toHexString(~myBit) +
     * " myValue = 0x" + // Integer.toHexString(myValue) + " currentRegMask = 0x" + //
     * Integer.toHexString(currentRegMask)); //}
     */
    // rawJEM.set(GPIO_E_OUT, currentRegMask);
  }

  /*
   * public void stop(){ if(DEBUG) System.out.println("PWMManager: stop....");
   * pwmControl.setSpeedLeft(zeroSpeed); pwmControl.setSpeedRight(zeroSpeed); }
   * 
   * public void search(){ if(DEBUG) System.out.println("PWMManager: search....");
   * pwmControl.setSpeedLeft(70); pwmControl.setSpeedRight(50); }
   * 
   * public void straight(){ if(DEBUG) System.out.println("PWMManager: strait....");
   * pwmControl.setSpeedLeft(100); pwmControl.setSpeedRight(100); }
   * 
   * public void spinRight(){ if(DEBUG) System.out.println("PWMManager: spinRight....");
   * pwmControl.setSpeedSpinLeft(100); pwmControl.setSpeedSpinRight(0); }
   * 
   * public void spinLeft(){ if(DEBUG) System.out.println("PWMManager: spinLeft....");
   * pwmControl.setSpeedSpinLeft(0); pwmControl.setSpeedSpinRight(100); }
   * 
   * public void spin180(){ int mod = (rand.nextInt() % 2);
   * 
   * if(DEBUG) System.out.println("PWMManager: spin180...."); if(mod == 1){
   * pwmControl.setSpeedSpinLeft(0); pwmControl.setSpeedSpinRight(100); }else{
   * pwmControl.setSpeedSpinLeft(100); pwmControl.setSpeedSpinRight(0); } }
   * 
   * public void right(){ if(DEBUG) System.out.println("PWMManager: right....");
   * pwmControl.setSpeedTurnLeft(100); pwmControl.setSpeedRight(zeroSpeed); }
   * 
   * public void left(){ if(DEBUG) System.out.println("PWMManager: left....");
   * pwmControl.setSpeedLeft(zeroSpeed); pwmControl.setSpeedTurnRight(100); }
   * 
   * public void bearRight(){ if(DEBUG) System.out.println("PWMManager: bearRight....");
   * pwmControl.setSpeedTurnLeft(100); pwmControl.setSpeedTurnRight(60); }
   * 
   * public void bearLeft(){ if(DEBUG) System.out.println("PWMManager: bearLeft....");
   * pwmControl.setSpeedTurnLeft(60); pwmControl.setSpeedTurnRight(100); }
   * 
   * public void back(){ if(DEBUG) System.out.println("PWMManager: back....");
   * pwmControl.setSpeedLeft(0); pwmControl.setSpeedRight(0); }
   * 
   * public void backBearLeft(){ if(DEBUG) System.out.println("PWMManager: backBearLeft....");
   * pwmControl.setSpeedLeft(30); pwmControl.setSpeedRight(0); }
   * 
   * public void backBearRight(){ if(DEBUG) System.out.println("PWMManager: backBearRight....");
   * pwmControl.setSpeedLeft(0); pwmControl.setSpeedRight(30); }
   */
  public void resume() {
    if (DEBUG)
      System.out.println("PWMManager: Reasume...........");
  }

  /**
   * setSpeedFactor - set speed factor value
   * 
   * @param speed
   *          factor
   */
  public synchronized void setSpeedFactor(int speedFactor) {
    if (DEBUG)
      System.out.println("PWMManager: setSpeedFactor....");
    this.speedFactor = speedFactor;
    // pwmControl.setSpeedAgilityFactors(speedFactor, agilityFactor);
  }

  /**
   * setAgilityFactor
   * 
   * @param agility
   *          factor
   */
  public synchronized void setAgilityFactor(int agilityFactor) {
    if (DEBUG)
      System.out.println("PWMManager: setAgilityFactor....");
    this.agilityFactor = agilityFactor;
    // pwmControl.setSpeedAgilityFactors(speedFactor, agilityFactor);
  }

  /**
   * setSpeedAgilityFactors - set both speed and agility
   * 
   * @param speed
   * @param agility
   */
  public synchronized void setSpeedAgilityFactors(@LOC("IN") int speed, @LOC("IN") int agility) {
    if (DEBUG)
      System.out.println("PWMManager: setSpeedAgilityFactors....");
    speedFactor = speed;
    agilityFactor = agility;
    // pwmControl.setSpeedAgilityFactors(speedFactor, agilityFactor);
  }

  /**
   * Control debug messaging. true - Activate debug messages false - deactivate debug messages
   */
  public void setDebug(boolean debug) {
    DEBUG = debug;
  }

}