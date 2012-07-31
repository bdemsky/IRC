// dummy audio device
/**
 * The <code>JavaSoundAudioDevice</code> implements an audio device by using the
 * JavaSound API.
 * 
 * @since 0.0.8
 * @author Mat McGowan
 */
public class AudioDevice {

  /**
   * Prepares the AudioDevice for playback of audio samples.
   * 
   * @param decoder
   *          The decoder that will be providing the audio samples.
   * 
   *          If the audio device is already open, this method returns silently.
   * 
   */
  public void open(Decoder decoder) throws JavaLayerException {

  }

  /**
   * Retrieves the open state of this audio device.
   * 
   * @return <code>true</code> if this audio device is open and playing audio
   *         samples, or <code>false</code> otherwise.
   */
  public boolean isOpen() {
    return true;
  }

  /**
   * Writes a number of samples to this <code>AudioDevice</code>.
   * 
   * @param samples
   *          The array of signed 16-bit samples to write to the audio device.
   * @param offs
   *          The offset of the first sample.
   * @param len
   *          The number of samples to write.
   * 
   *          This method may return prior to the samples actually being played
   *          by the audio device.
   */
  public void write(short[] samples, int offs, int len) throws JavaLayerException {

  }

  /**
   * Closes this audio device. Any currently playing audio is stopped as soon as
   * possible. Any previously written audio data that has not been heard is
   * discarded.
   * 
   * The implementation should ensure that any threads currently blocking on the
   * device (e.g. during a <code>write</code> or <code>flush</code> operation
   * should be unblocked by this method.
   */
  public void close() {

  }

  /**
   * Blocks until all audio samples previously written to this audio device have
   * been heard.
   */
  public void flush() {

  }

  /**
   * Retrieves the current playback position in milliseconds.
   */
  public int getPosition() {
    return 0;
  }

}