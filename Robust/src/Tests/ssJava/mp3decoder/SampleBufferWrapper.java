public class SampleBufferWrapper {

  static SampleBuffer output;

  @TRUST
  static void init(int freq, int channels) {
    output = new SampleBuffer(freq, channels);
  }

  @TRUST
  static SampleBuffer getOutput() {
    return output;
  }

}
