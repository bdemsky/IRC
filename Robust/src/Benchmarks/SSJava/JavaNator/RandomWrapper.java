public class RandomWrapper {
  private static Random rand;

  @TRUST
  public static void init() {
    rand = new Random();
  }

  @TRUST
  public static int nextInt() {
    return rand.nextInt();
  }

}
