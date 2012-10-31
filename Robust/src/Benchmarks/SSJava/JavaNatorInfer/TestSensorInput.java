public class TestSensorInput {

  private static FileInputStream inputFile;
  private static int idx=0;

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
    // DEBUG_OUTPUT();
    return (byte) Integer.parseInt(in);
  }
  
  public static DEBUG_OUTPUT(){
    idx++;
    System.out.println(idx);
  }

}
