public class Signal {
  public Signal() {
  }
  public native void nativeSigAction(); 
  public void sigAction() {
    nativeSigAction();
  }
}
