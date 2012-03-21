import java.awt.image.SampleModel;

import FileOutputStream;

/*
 * 11/19/04		1.0 moved to LGPL.
 * 29/01/00		Initial version. mdm@techie.com
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

/**
 * The <code>Player</code> class implements a simple player for playback of an
 * MPEG audio stream.
 * 
 * @author Mat McGowan
 * @since 0.0.8
 */

// REVIEW: the audio device should not be opened until the
// first MPEG audio frame has been decoded.
@LATTICE("B<DE,DE<ST,DE<HE,HE<ST,ST<FR")
public class Player {
  /**
   * The current frame number.
   */
  @LOC("FR")
  private int frame = 0;

  /**
   * The MPEG audio bitstream.
   */
  // private Bitstream bitstream;

  /**
   * The MPEG audio decoder.
   */
  /* final */@LOC("DE")
  private Decoder decoder;

  /**
   * The AudioDevice the audio samples are written to.
   */
  // private AudioDevice audio;

  /**
   * Has the player been closed?
   */
  @LOC("B")
  private boolean closed = false;

  /**
   * Has the player played back all frames from the stream?
   */
  @LOC("B")
  private boolean complete = false;

  @LOC("B")
  private int lastPosition = 0;

  @LOC("B")
  private long sampleNumber;

  /**
   * Creates a new <code>Player</code> instance.
   */
  public Player() throws JavaLayerException {
    this(null);
  }

  public Player(AudioDevice device) throws JavaLayerException {
    // bitstream = new Bitstream(stream);
    decoder = new Decoder();

    // if (device!=null)
    // {
    // audio = device;
    // }
    // else
    // {
    // FactoryRegistry r = FactoryRegistry.systemRegistry();
    // audio = r.createAudioDevice();
    // }

    device.open(decoder);
  }

  public void play() throws JavaLayerException {
    play(Integer.MAX_VALUE);
  }

  /**
   * Plays a number of MPEG audio frames.
   * 
   * @param frames
   *          The number of frames to play.
   * @return true if the last frame was played, or false if there are more
   *         frames.
   */
  @LATTICE("OUT<THIS,THIS<IN,IN*,THISLOC=THIS")
  @RETURNLOC("OUT")
  public boolean play(@LOC("IN") int frames) throws JavaLayerException {
    @LOC("OUT") boolean ret = true;

    // initialization before ssjava loop
    @LOC("THIS,Player.FR") boolean init = true;
    @LOC("THIS,Player.ST") Header h = BitstreamWrapper.readFrame();
    decoder.init(h);

    sampleNumber = 1;
    System.out.println("Gobble sentinel: +++");

    // @LOC("IN") int count = 0;
    SSJAVA: while (true) {
      if (h == null) {
        break;
      }
      ret = decodeFrame(init, h);
      if (!ret) {
        break;
      }
      h = BitstreamWrapper.readFrame();
    }

    /*
     * if (!ret) { // last frame, ensure all data flushed to the audio device.
     * AudioDevice out = audio; if (out!=null) { out.flush(); synchronized
     * (this) { complete = (!closed); close(); } } }
     */
    return ret;
  }

  /**
   * Cloases this player. Any audio currently playing is stopped immediately.
   */

  public synchronized void close() {
    /*
     * AudioDevice out = audio; if (out!=null) { closed = true; audio = null; //
     * this may fail, so ensure object state is set up before // calling this
     * method. out.close(); lastPosition = out.getPosition(); try {
     * bitstream.close(); } catch (BitstreamException ex) { } }
     */
  }

  /**
   * Returns the completed status of this player.
   * 
   * @return true if all available MPEG audio frames have been decoded, or false
   *         otherwise.
   */
  public synchronized boolean isComplete() {
    return complete;
  }

  /**
   * Retrieves the position in milliseconds of the current audio sample being
   * played. This method delegates to the <code>
   * AudioDevice</code> that is used by this player to sound the decoded audio
   * samples.
   */
  public int getPosition() {
    // int position = lastPosition;

    // AudioDevice out = audio;
    // if (out!=null)
    // {
    // position = out.getPosition();
    // }
    // return position;
    return 0;
  }

  /**
   * Decodes a single frame.
   * 
   * @return true if there are no more frames to decode, false otherwise.
   */
  @LATTICE("C<THIS,O<THIS,THIS<IN,C*,THISLOC=THIS,RETURNLOC=O,GLOBALLOC=THIS")
  protected boolean decodeFrame(@LOC("THIS,Player.FR") boolean init, @LOC("THIS,Player.ST") Header h)
      throws JavaLayerException {
    try {
      // AudioDevice out = audio;
      // if (out==null)
      // return false;

      // Header h = bitstream.readFrame();

      // if (h == null){
      // return false;
      // }
      
      // @LOC("O") SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h);
      decoder.decodeFrame(h);

//      DEBUG_OUTPUT_CHECKSUM();
//       DEBUG_OUTPUT();
      // synchronized (this)
      // {
      // out = audio;
      // if (out!=null)
      // {
      // out.write(output.getBuffer(), 0, output.getBufferLength());
      // }
      // }

      // bitstream.closeFrame();
    } catch (RuntimeException ex) {
      throw new JavaLayerException("Exception decoding audio frame", ex);
    }
    /*
     * catch (IOException ex) {
     * System.out.println("exception decoding audio frame: "+ex); return false;
     * } catch (BitstreamException bitex) {
     * System.out.println("exception decoding audio frame: "+bitex); return
     * false; } catch (DecoderException decex) {
     * System.out.println("exception decoding audio frame: "+decex); return
     * false; }
     */
    return true;
  }

  @TRUST
  public void DEBUG_OUTPUT() {
    // it looks like there is left and right channel interleaved into the
    // output buffer, so only sample one channel (stride=2)
    short[] outbuf = SampleBufferWrapper.getBuffer();
    for (int i = 0; i < SampleBufferWrapper.getBufferLength(); i = i + 2) {
      System.out.println(sampleNumber + " " + outbuf[i]);
      sampleNumber++;
    }
  }

  @TRUST
  public void DEBUG_OUTPUT_CHECKSUM() {
    // eom debug
    @LOC("C") int sum = 0;
    @LOC("C") short[] outbuf = SampleBufferWrapper.getBuffer();
    // short[] outbuf = output.getBuffer();
    TERMINATE: for (@LOC("C") int i = 0; i < SampleBufferWrapper.getBufferLength(); i++) {
      // System.out.println(outbuf[i]);
      sum += outbuf[i];
    }
    System.out.println(sum);
    //
  }

}
