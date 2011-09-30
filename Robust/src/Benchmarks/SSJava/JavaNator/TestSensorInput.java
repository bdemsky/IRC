public class TestSensorInput {

  FileInputStream inputFile;

  public void init() {
    inputFile = new FileInputStream("input.dat");
  }

  public static byte getCommand() {
    // return Byte.parseInt(inputFile.readLine());
    return 0;
  }

}
