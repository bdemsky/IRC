/*
 * 11/19/04		1.0 moved to LGPL.
 * 01/12/99		Initial version.	mdm@techie.com
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
 * The <code>Decoder</code> class encapsulates the details of decoding an MPEG
 * audio frame.
 * 
 * @author MDM
 * @version 0.0.7 12/12/99
 * @since 0.0.5
 */
@LATTICE("OUT<DE,DE<FILTER,FILTER<FACTORS,FACTORS<EQ,EQ<PARAM,PARAM<H,H<INIT,PARAM*,INIT*")
@METHODDEFAULT("THIS,THISLOC=THIS,RETURNLOC=THIS")
public class Decoder implements DecoderErrors {

  static private final Params DEFAULT_PARAMS = new Params();

  /**
   * The Bistream from which the MPEG audio frames are read.
   */
  // @LOC("ST")
  // private Bitstream stream;

  /**
   * The Obuffer instance that will receive the decoded PCM samples.
   */
  // @LOC("OUT")
  // private Obuffer output;

  /**
   * Synthesis filter for the left channel.
   */
  // @LOC("FIL")
  // private SynthesisFilter filter1;

  /**
   * Sythesis filter for the right channel.
   */
  // @LOC("FIL")
  // private SynthesisFilter filter2;

  /**
   * The decoder used to decode layer III frames.
   */
  @LOC("DE")
  private LayerIIIDecoder l3decoder;
  // @LOC("DE")
  // private LayerIIDecoder l2decoder;
  // @LOC("DE")
  // private LayerIDecoder l1decoder;

  @LOC("OUT")
  private int outputFrequency;
  @LOC("OUT")
  private int outputChannels;

  @LOC("EQ")
  private Equalizer equalizer = new Equalizer();

  @LOC("PARAM")
  private Params params;

  @LOC("INIT")
  private boolean initialized;

  /**
   * Creates a new <code>Decoder</code> instance with default parameters.
   */

  public Decoder() {
    this(null);
  }

  /**
   * Creates a new <code>Decoder</code> instance with default parameters.
   * 
   * @param params
   *          The <code>Params</code> instance that describes the customizable
   *          aspects of the decoder.
   */
  public Decoder(@DELEGATE Params params0) {

    if (params0 == null) {
      params0 = getDefaultParams();
    }

    params = params0;

    Equalizer eq = params.getInitialEqualizerSettings();
    if (eq != null) {
      equalizer.setFrom(eq);
    }
  }

  static public Params getDefaultParams() {
    return (Params) DEFAULT_PARAMS.clone();
  }

  // public void setEqualizer(Equalizer eq) {
  // if (eq == null)
  // eq = Equalizer.PASS_THRU_EQ;
  //
  // equalizer.setFrom(eq);
  //
  // float[] factors = equalizer.getBandFactors();
  //
  // if (filter1 != null)
  // filter1.setEQ(factors);
  //
  // if (filter2 != null)
  // filter2.setEQ(factors);
  // }
  @LATTICE("THIS<VAR,THISLOC=THIS,VAR*")
  public void init( @LOC("THIS,Decoder.H") Header header) {
    @LOC("VAR") float scalefactor = 32700.0f;

    @LOC("THIS,Decoder.PARAM") int mode = header.mode();
    @LOC("THIS,Decoder.PARAM") int layer = header.layer();
    @LOC("THIS,Decoder.PARAM") int channels = mode == Header.SINGLE_CHANNEL ? 1 : 2;

    // set up output buffer if not set up by client.
    // if (output == null)
    // output = new SampleBuffer(header.frequency(), channels);
    SampleBufferWrapper.init(header.frequency(), channels);

    @LOC("THIS,Decoder.FACTORS") float[] factors = equalizer.getBandFactors();
    @LOC("THIS,Decoder.FILTER") SynthesisFilter filter1 =
        new SynthesisFilter(0, scalefactor, factors);

    // REVIEW: allow mono output for stereo
    @LOC("THIS,Decoder.FILTER") SynthesisFilter filter2 = null;
    if (channels == 2) {
      filter2 = new SynthesisFilter(1, scalefactor, factors);
    }

    outputChannels = channels;
    outputFrequency = header.frequency();

    l3decoder = new LayerIIIDecoder(header,filter1, filter2, OutputChannels.BOTH_CHANNELS);

  }

  /**
   * Decodes one frame from an MPEG audio bitstream.
   * 
   * @param header
   *          The header describing the frame to decode.
   * @param bitstream
   *          The bistream that provides the bits for te body of the frame.
   * 
   * @return A SampleBuffer containing the decoded samples.
   */
  @LATTICE("THIS<VAR,THISLOC=THIS,VAR*")
  public void decodeFrame(@LOC("THIS,Decoder.H") Header header) throws DecoderException {

    SampleBufferWrapper.clear_buffer();
    l3decoder.decode(header);
    // SampleBufferWrapper.getOutput().write_buffer(1);

  }

  /**
   * Changes the output buffer. This will take effect the next time
   * decodeFrame() is called.
   */
  // public void setOutputBuffer(Obuffer out) {
  // output = out;
  // }

  /**
   * Retrieves the sample frequency of the PCM samples output by this decoder.
   * This typically corresponds to the sample rate encoded in the MPEG audio
   * stream.
   * 
   * @param the
   *          sample rate (in Hz) of the samples written to the output buffer
   *          when decoding.
   */
  public int getOutputFrequency() {
    return outputFrequency;
  }

  /**
   * Retrieves the number of channels of PCM samples output by this decoder.
   * This usually corresponds to the number of channels in the MPEG audio
   * stream, although it may differ.
   * 
   * @return The number of output channels in the decoded samples: 1 for mono,
   *         or 2 for stereo.
   * 
   */
  public int getOutputChannels() {
    return outputChannels;
  }

  /**
   * Retrieves the maximum number of samples that will be written to the output
   * buffer when one frame is decoded. This can be used to help calculate the
   * size of other buffers whose size is based upon the number of samples
   * written to the output buffer. NB: this is an upper bound and fewer samples
   * may actually be written, depending upon the sample rate and number of
   * channels.
   * 
   * @return The maximum number of samples that are written to the output buffer
   *         when decoding a single frame of MPEG audio.
   */
  public int getOutputBlockSize() {
    return Obuffer.OBUFFERSIZE;
  }

  protected DecoderException newDecoderException(int errorcode) {
    return new DecoderException(errorcode, null);
  }

  protected DecoderException newDecoderException(int errorcode, Throwable throwable) {
    return new DecoderException(errorcode, throwable);
  }

  /**
   * The <code>Params</code> class presents the customizable aspects of the
   * decoder.
   * <p>
   * Instances of this class are not thread safe.
   */
  public static class Params implements Cloneable {

    // private OutputChannels outputChannels = OutputChannels.BOTH;
    private OutputChannels outputChannels = new OutputChannels(0);

    private Equalizer equalizer = new Equalizer();

    public Params() {
    }

    public Object clone() {
      // TODO: need to have better clone method
      Params clone = new Params();
      clone.outputChannels = new OutputChannels(outputChannels.getChannelsOutputCode());
      clone.equalizer = new Equalizer();
      return clone;
      // try
      // {
      // return super.clone();
      // }
      // catch (CloneNotSupportedException ex)
      // {
      // throw new InternalError(this+": "+ex);
      // }
    }

    public void setOutputChannels(OutputChannels out) {
      if (out == null)
        throw new NullPointerException("out");

      outputChannels = out;
    }

    public OutputChannels getOutputChannels() {
      return outputChannels;
    }

    /**
     * Retrieves the equalizer settings that the decoder's equalizer will be
     * initialized from.
     * <p>
     * The <code>Equalizer</code> instance returned cannot be changed in real
     * time to affect the decoder output as it is used only to initialize the
     * decoders EQ settings. To affect the decoder's output in realtime, use the
     * Equalizer returned from the getEqualizer() method on the decoder.
     * 
     * @return The <code>Equalizer</code> used to initialize the EQ settings of
     *         the decoder.
     */
    public Equalizer getInitialEqualizerSettings() {
      return equalizer;
    }

  };
}
