public class BitStreamWrapper {

  private static Bitstream stream;

  public static void init(InputStream in) {
    stream = new Bitstream(in);
  }

  public static Header readFrame() {
    return stream.readFrame();
  }

  public static int get_bits(int number_of_bits) {
    return stream.get_bits(number_of_bits);
  }

}
