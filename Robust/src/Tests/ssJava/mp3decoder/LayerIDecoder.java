/*
 * 09/26/08     throw exception on subbband alloc error: Christopher G. Jennings (cjennings@acm.org)
 * 
 * 11/19/04		1.0 moved to LGPL.
 * 
 * 12/12/99		Initial version. Adapted from javalayer.java
 *				and Subband*.java. mdm@techie.com
 *
 * 02/28/99		Initial version : javalayer.java by E.B
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
 * Implements decoding of MPEG Audio Layer I frames.
 */
@LATTICE("SB<H,H<SH,SH*,SB*")
@METHODDEFAULT("MODE<THIS,THIS<C,C<IN,THISLOC=THIS,C*")
class LayerIDecoder implements FrameDecoder {

  @LOC("SH")
  protected Bitstream stream;
  @LOC("SH")
  protected Header header;
  @LOC("H")
  protected SynthesisFilter filter1;
  @LOC("H")
  protected SynthesisFilter filter2;
  @LOC("SB")
  protected Obuffer buffer;
  @LOC("H")
  protected int which_channels;
  @LOC("SH")
  protected int mode;

  @LOC("SB")
  protected int num_subbands;
  @LOC("SB")
  protected Subband[] subbands;

  @LOC("H")
  protected Crc16 crc = null; // new Crc16[1] to enable CRC checking.

  public LayerIDecoder() {
    crc = new Crc16();
  }

  public void create(@LOC("IN") Bitstream stream0, @LOC("IN") Header header0,
      @LOC("IN") SynthesisFilter filtera, @LOC("IN") SynthesisFilter filterb,
      @LOC("IN") Obuffer buffer0, @LOC("IN") int which_ch0) {
    stream = stream0;
    header = header0;
    filter1 = filtera;
    filter2 = filterb;
    buffer = buffer0;
    which_channels = which_ch0;

  }

  public void decodeFrame() throws DecoderException {

    num_subbands = header.number_of_subbands();
    subbands = new Subband[32];
    mode = header.mode();

    createSubbands();

    readAllocation();
    readScaleFactorSelection();

    if ((crc != null) || header.checksum_ok()) {
      readScaleFactors();

      readSampleData();
    }

  }

  protected void createSubbands() {
    @LOC("THIS,LayerIDecoder.SB") int i;
    if (mode == Header.SINGLE_CHANNEL) {
      for (i = 0; i < num_subbands; ++i) {
        subbands[i] = new SubbandLayer1(i);
      }
    } else if (mode == Header.JOINT_STEREO) {
      for (i = 0; i < header.intensity_stereo_bound(); ++i) {
        subbands[i] = new SubbandLayer1Stereo(i);
      }
      for (; i < num_subbands; ++i) {
        subbands[i] = new SubbandLayer1IntensityStereo(i);
      }
    } else {
      for (i = 0; i < num_subbands; ++i) {
        subbands[i] = new SubbandLayer1Stereo(i);
      }
    }
  }

  protected void readAllocation() throws DecoderException {
    // start to read audio data:
    for (@LOC("THIS,LayerIDecoder.SB") int i = 0; i < num_subbands; ++i)
      subbands[i].read_allocation(stream, header, crc);

  }

  protected void readScaleFactorSelection() {
    // scale factor selection not present for layer I.
  }

  protected void readScaleFactors() {
    for (@LOC("THIS,LayerIDecoder.SB") int i = 0; i < num_subbands; ++i)
      subbands[i].read_scalefactor(stream, header);
  }

  @LATTICE("MODE<THIS,THIS<C,THISLOC=THIS,C*")
  protected void readSampleData() {

    @LOC("THIS,LayerIDecoder.SB") boolean read_ready = false;
    @LOC("THIS,LayerIDecoder.SB") boolean write_ready = false;

    @LOC("MODE") int mode = header.mode(); // header.mode() will return
                                           // DELTA(THIS)

    @LOC("THIS,LayerIDecoder.SB") int i;
    do {

      for (i = 0; i < num_subbands; ++i) {
        read_ready = subbands[i].read_sampledata(stream); // DELTA[Loc[readSampleData.V],Loc[LayerIDecoder.L]]
      }

      do {
        for (i = 0; i < num_subbands; ++i) {
          write_ready = subbands[i].put_next_sample(which_channels, filter1, filter2);
        }

        filter1.calculate_pcm_samples(buffer);
        if ((which_channels == OutputChannels.BOTH_CHANNELS) && (mode != Header.SINGLE_CHANNEL)) {
          filter2.calculate_pcm_samples(buffer);
        }

      } while (!write_ready);

    } while (!read_ready);

  }

  /**
   * Class for layer I subbands in single channel mode. Used for single channel
   * mode and in derived class for intensity stereo mode
   */
  @LATTICE("S<L,L<H,H<SH,SH<SH0,SH*,S*")
  @METHODDEFAULT("OUT<V,V<THIS,THIS<C,C<IN,C*,THISLOC=THIS,RETURNLOC=OUT")
  static class SubbandLayer1 extends Subband {

    // Factors and offsets for sample requantization
    @LOC("H")
    public static final float table_factor[] = { 0.0f, (1.0f / 2.0f) * (4.0f / 3.0f),
        (1.0f / 4.0f) * (8.0f / 7.0f), (1.0f / 8.0f) * (16.0f / 15.0f),
        (1.0f / 16.0f) * (32.0f / 31.0f), (1.0f / 32.0f) * (64.0f / 63.0f),
        (1.0f / 64.0f) * (128.0f / 127.0f), (1.0f / 128.0f) * (256.0f / 255.0f),
        (1.0f / 256.0f) * (512.0f / 511.0f), (1.0f / 512.0f) * (1024.0f / 1023.0f),
        (1.0f / 1024.0f) * (2048.0f / 2047.0f), (1.0f / 2048.0f) * (4096.0f / 4095.0f),
        (1.0f / 4096.0f) * (8192.0f / 8191.0f), (1.0f / 8192.0f) * (16384.0f / 16383.0f),
        (1.0f / 16384.0f) * (32768.0f / 32767.0f) };

    @LOC("H")
    public static final float table_offset[] = { 0.0f, ((1.0f / 2.0f) - 1.0f) * (4.0f / 3.0f),
        ((1.0f / 4.0f) - 1.0f) * (8.0f / 7.0f), ((1.0f / 8.0f) - 1.0f) * (16.0f / 15.0f),
        ((1.0f / 16.0f) - 1.0f) * (32.0f / 31.0f), ((1.0f / 32.0f) - 1.0f) * (64.0f / 63.0f),
        ((1.0f / 64.0f) - 1.0f) * (128.0f / 127.0f), ((1.0f / 128.0f) - 1.0f) * (256.0f / 255.0f),
        ((1.0f / 256.0f) - 1.0f) * (512.0f / 511.0f),
        ((1.0f / 512.0f) - 1.0f) * (1024.0f / 1023.0f),
        ((1.0f / 1024.0f) - 1.0f) * (2048.0f / 2047.0f),
        ((1.0f / 2048.0f) - 1.0f) * (4096.0f / 4095.0f),
        ((1.0f / 4096.0f) - 1.0f) * (8192.0f / 8191.0f),
        ((1.0f / 8192.0f) - 1.0f) * (16384.0f / 16383.0f),
        ((1.0f / 16384.0f) - 1.0f) * (32768.0f / 32767.0f) };

    @LOC("H")
    protected int subbandnumber;
    @LOC("SH")
    protected int samplenumber;
    @LOC("H")
    protected int allocation;
    @LOC("L")
    protected float scalefactor;
    @LOC("L")
    protected int samplelength;
    @LOC("S")
    protected float sample;
    @LOC("L")
    protected float factor;
    @LOC("L")
    protected float offset;

    /**
     * Construtor.
     */
    public SubbandLayer1(@LOC("IN") int subbandnumber) {
      this.subbandnumber = subbandnumber;
      samplenumber = 0;
    }

    /**
	   *
	   */
    // @LATTICE("IN<THIS,THISLOC=THIS")
    @LATTICE("THIS<IN,THISLOC=THIS")
    public void read_allocation(@LOC("THIS,LayerIDecoder$SubbandLayer1.SH") Bitstream stream,
        @LOC("IN") Header header, @LOC("THIS,LayerIDecoder$SubbandLayer1.L") Crc16 crc)
        throws DecoderException {

      if ((allocation = stream.get_bits(4)) == 15) {
        // CGJ: catch this condition and throw appropriate exception
        throw new DecoderException(DecoderErrors.ILLEGAL_SUBBAND_ALLOCATION, null);
        // cerr << "WARNING: stream contains an illegal allocation!\n";
        // MPEG-stream is corrupted!
      }

      if (crc != null) {
        crc.add_bits(allocation, 4); // allocation has [THIS,H]
        // crc has [IN]
      }
      if (allocation != 0) {
        samplelength = allocation + 1;
        factor = table_factor[allocation];
        offset = table_offset[allocation];
      }
    }

    /**
	   *
	   */
    public void read_scalefactor(@LOC("IN") Bitstream stream, @LOC("IN") Header header) {
      if (allocation != 0)
        scalefactor = scalefactors[stream.get_bits(6)];
    }

    // ssjava
    @LATTICE("THIS<IN,THISLOC=THIS")
    @RETURNLOC("THIS,LayerIDecoder$SubbandLayer1.S")
    public boolean read_sampledata(@LOC("THIS,LayerIDecoder$SubbandLayer1.S") Bitstream stream) {
      if (allocation != 0) {
        sample = (float) (stream.get_bits(samplelength));
      }
      if (++samplenumber == 12) {
        samplenumber = 0;
        return true;
      }
      return false;
    }

    public boolean put_next_sample(@LOC("IN") int channels, @LOC("IN") SynthesisFilter filter1,
        @LOC("IN") SynthesisFilter filter2) {
      if ((allocation != 0) && (channels != OutputChannels.RIGHT_CHANNEL)) {
        @LOC("OUT") float scaled_sample = (sample * factor + offset) * scalefactor;
        filter1.input_sample(scaled_sample, subbandnumber);
      }
      return true;
    }
  };

  /**
   * Class for layer I subbands in joint stereo mode.
   */
  @LATTICE("S<L,L<H,H<SH,SH<SH0,SH*")
  @METHODDEFAULT("OUT<V,V<THIS,THIS<C,C<IN,C*,THISLOC=THIS,RETURNLOC=OUT")
  static class SubbandLayer1IntensityStereo extends SubbandLayer1 {
    @LOC("L")
    protected float channel2_scalefactor;

    /**
     * Constructor
     */
    public SubbandLayer1IntensityStereo(@LOC("IN") int subbandnumber) {
      super(subbandnumber);
    }

    /**
	   *
	   */
    @LATTICE("THIS<IN2,IN2<IN1,IN1<IN0,THISLOC=THIS")
    public void read_allocation(@LOC("IN1") Bitstream stream, @LOC("IN0") Header header,
        @LOC("IN2") Crc16 crc) throws DecoderException {
      super.read_allocation(stream, header, crc);
    }

    /**
	   *
	   */
    public void read_scalefactor(@LOC("IN") Bitstream stream, @LOC("IN") Header header) {
      if (allocation != 0) {
        scalefactor = scalefactors[stream.get_bits(6)];
        channel2_scalefactor = scalefactors[stream.get_bits(6)];
      }
    }

    public boolean read_sampledata(@LOC("IN") Bitstream stream) {
      return super.read_sampledata(stream);
    }

    public boolean put_next_sample(@LOC("IN") int channels, @LOC("IN") SynthesisFilter filter1,
        @LOC("IN") SynthesisFilter filter2) {
      if (allocation != 0) {
        sample = sample * factor + offset; // requantization
        if (channels == OutputChannels.BOTH_CHANNELS) {
          @LOC("OUT") float sample1 = sample * scalefactor;
          @LOC("OUT") float sample2 = sample * channel2_scalefactor;
          filter1.input_sample(sample1, subbandnumber);
          filter2.input_sample(sample2, subbandnumber);
        } else if (channels == OutputChannels.LEFT_CHANNEL) {
          @LOC("OUT") float sample1 = sample * scalefactor;
          filter1.input_sample(sample1, subbandnumber);
        } else {
          @LOC("OUT") float sample2 = sample * channel2_scalefactor;
          filter1.input_sample(sample2, subbandnumber);
        }
      }
      return true;
    }
  };

  /**
   * Class for layer I subbands in stereo mode.
   */
  @LATTICE("S<L,L<H,H<SH,SH<SH0,SH*,S*")
  @METHODDEFAULT("OUT<V,V<THIS,THIS<C,C<IN,C*,THISLOC=THIS,RETURNLOC=OUT")
  static class SubbandLayer1Stereo extends SubbandLayer1 {
    @LOC("H")
    protected int channel2_allocation;
    @LOC("L")
    protected float channel2_scalefactor;
    @LOC("L")
    protected int channel2_samplelength;
    @LOC("S")
    protected float channel2_sample;
    @LOC("L")
    protected float channel2_factor;
    @LOC("L")
    protected float channel2_offset;

    /**
     * Constructor
     */
    public SubbandLayer1Stereo(@LOC("IN") int subbandnumber) {
      super(subbandnumber);
    }

    /**
	   *
	   */
    // ssjava
    public void read_allocation(@LOC("THIS,LayerIDecoder$SubbandLayer1Stereo.SH") Bitstream stream,
        @LOC("IN") Header header, @LOC("THIS,LayerIDecoder$SubbandLayer1Stereo.L") Crc16 crc)
        throws DecoderException {
      allocation = stream.get_bits(4);
      channel2_allocation = stream.get_bits(4);
      if (crc != null) {
        crc.add_bits(allocation, 4);
        crc.add_bits(channel2_allocation, 4);
      }
      if (allocation != 0) {
        samplelength = allocation + 1;
        factor = table_factor[allocation];
        offset = table_offset[allocation];
      }
      if (channel2_allocation != 0) {
        channel2_samplelength = channel2_allocation + 1;
        channel2_factor = table_factor[channel2_allocation];
        channel2_offset = table_offset[channel2_allocation];
      }
    }

    /**
	   *
	   */
    public void read_scalefactor(@LOC("IN") Bitstream stream, @LOC("IN") Header header) {
      if (allocation != 0)
        scalefactor = scalefactors[stream.get_bits(6)];
      if (channel2_allocation != 0)
        channel2_scalefactor = scalefactors[stream.get_bits(6)];
    }

    /**
	   *
	   */
    @RETURNLOC("THIS,LayerIDecoder$SubbandLayer1Stereo.S")
    public boolean read_sampledata(@LOC("THIS,LayerIDecoder$SubbandLayer1Stereo.S") Bitstream stream) {
      @LOC("THIS,LayerIDecoder$SubbandLayer1Stereo.S") boolean returnvalue = super.read_sampledata(stream);
      if (channel2_allocation != 0) {
        channel2_sample = (float) (stream.get_bits(channel2_samplelength));
      }
      return returnvalue;
    }

    /**
	   *
	   */
    @RETURNLOC("OUT")
    public boolean put_next_sample(@LOC("IN") int channels, @LOC("IN") SynthesisFilter filter1,
        @LOC("IN") SynthesisFilter filter2) {
      super.put_next_sample(channels, filter1, filter2);
      if ((channel2_allocation != 0) && (channels != OutputChannels.LEFT_CHANNEL)) {
        @LOC("OUT") float sample2 =
            (channel2_sample * channel2_factor + channel2_offset) * channel2_scalefactor;
        if (channels == OutputChannels.BOTH_CHANNELS)
          filter2.input_sample(sample2, subbandnumber);
        else
          filter1.input_sample(sample2, subbandnumber);
      }
      return true;
    }

  };

}
