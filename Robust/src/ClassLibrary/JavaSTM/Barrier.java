public class Barrier {
  public Barrier() {
  }

  /* Set the number of threads in a Barrier */
  public static native void setBarrier(int nthreads);

  /* Wait for a Barrier */
  public static native void enterBarrier();
}
