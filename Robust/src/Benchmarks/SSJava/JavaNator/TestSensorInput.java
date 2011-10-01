public class TestSensorInput {

  private static FileInputStream inputFile;

  public static void init() {
    inputFile = new FileInputStream("input.dat");
  }

  public static byte getCommand() {
    String in = inputFile.readLine();
    if (in == null) {
      return (byte) -1;
    }
    return (byte) Integer.parseInt(in);
  }

}
