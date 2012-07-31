public class BitstreamWrapper {

  private static Bitstream stream;
  private static int idx=0;

  @TRUST
  public static void init(String filename) {
    FileInputStream fin = new FileInputStream(filename);
    BufferedInputStream bin = new BufferedInputStream(fin);
    stream = new Bitstream(bin);
  }

  @TRUST
  public static Header readFrame() {
    Header h=stream.readFrame();
    h.idx=idx++;
    return h;
  }

}
