public class Counter {

  static int idx = 0;

  @TRUST
  static boolean inc() {
    idx++;
  }

  @TRUST
  static int idx() {
    return idx;
  }

}
