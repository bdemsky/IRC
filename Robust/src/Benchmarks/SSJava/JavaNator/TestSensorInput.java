public class TestSensorInput {

  private static FileInputStream inputFile;

  @TRUST
  public static void init() {
    inputFile = new FileInputStream("input.dat");
  }

  @TRUST
  public static byte getCommand() {
    String in = inputFile.readLine();
    if (in == null) {
      return (byte) -1;
    }
    return (byte) Integer.parseInt(in);
  }

}
