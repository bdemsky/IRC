public class BitstreamWrapper {

  private static Bitstream stream;

  @TRUST
  public static void init(String filename) {
    FileInputStream fin = new FileInputStream(filename);
    BufferedInputStream bin = new BufferedInputStream(fin);
    stream = new Bitstream(bin);
  }

  @TRUST
  public static Header readFrame() {
    return stream.readFrame();
  }

}
