public class HWSimulator {

  public static int onOffMode = RobotMain.ON_MODE;
  public static int manualAutonomusMode = RobotMain.AUTONOMUS_MODE;
  public static byte lineSensorsMask;
  public static byte sonarSensors;

  // public byte currentSonars;

  @TRUST
  public static Command getCommand() {

    byte currentCommand = TestSensorInput.getCommand();

    int data = (int) (currentCommand & RobotMain.ALL_DATA);
    int opCode = (int) (currentCommand & 0xe0); // ALL_COMMANDS);

    switch ((int) opCode) {
    case RobotMain.ON_OFF:
      if ((data & 0x1) == 0x1) {
        HWSimulator.onOffMode = RobotMain.ON_MODE;
      } else {
        HWSimulator.onOffMode = RobotMain.OFF_MODE;
      }
      break;
    case RobotMain.MANUAL_AUTONOMUS:
      if ((data & 0x1) == 0x1) {
        HWSimulator.manualAutonomusMode = RobotMain.MANUAL_MODE;
      } else {
        HWSimulator.manualAutonomusMode = RobotMain.AUTONOMUS_MODE;
      }
      break;
    case RobotMain.LINE_SENSORS:
      HWSimulator.lineSensorsMask = (byte) (data & RobotMain.LS_ALL);
      break;
    case RobotMain.SONAR_SENSORS:
      HWSimulator.sonarSensors = (byte) (data & RobotMain.ALL_SONARS);
      break;
    }

    Command com = new Command();

    com.command = currentCommand;

    com.onOffMode = HWSimulator.onOffMode;
    com.manualAutonomusMode = HWSimulator.manualAutonomusMode;
    com.lineSensorsMask = HWSimulator.lineSensorsMask;
    com.sonarSensors = HWSimulator.sonarSensors;

    return com;

  }

}
