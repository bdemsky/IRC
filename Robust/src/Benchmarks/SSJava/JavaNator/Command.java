@LATTICE("V")
public class Command {

  @LOC("V") public int onOffMode;
  @LOC("V") public int manualAutonomusMode;
  @LOC("V") public byte lineSensorsMask;
  @LOC("V") public byte sonarSensors;
  @LOC("V") byte command;

}
