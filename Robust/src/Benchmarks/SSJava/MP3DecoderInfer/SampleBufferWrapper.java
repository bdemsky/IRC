public class SampleBufferWrapper {

  static SampleBuffer output;

  @TRUST
  static void init(int freq, int channels) {
    output = new SampleBuffer(freq, channels);
  }
  
  @TRUST
  static void clear_buffer(){
    output.clear_buffer();
  }

  @TRUST
  static SampleBuffer getOutput() {
    return output;
  }

  @TRUST
  static short[] getBuffer() {
    return output.getBuffer();
  }

  @TRUST
  static int getBufferLength() {
    return output.getBufferLength();
  }

  @TRUST
  static void appendSamples(int channel, float[] f) {
    output.appendSamples(channel, f);
  }

}
