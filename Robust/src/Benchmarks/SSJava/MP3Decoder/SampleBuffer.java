/* 
 * 11/19/04     1.0 moved to LGPL.
 * 
 * 12/12/99  Initial Version based on FileObuffer.     mdm@techie.com.
 * 
 * FileObuffer:
 * 15/02/99  Java Conversion by E.B ,javalayer@javazoom.net
 *
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
 * The <code>SampleBuffer</code> class implements an output buffer that provides
 * storage for a fixed size block of samples.
 */
@LATTICE("BUF<BUFP,BUFP<IDX,IDX<CONT,BUFP*,IDX*")
@METHODDEFAULT("D<IN,D<C,C*,THISLOC=D")
public class SampleBuffer extends Obuffer {
  @LOC("BUF")
  private short[] buffer;
  @LOC("BUFP")
  private int[] bufferp;
  @LOC("CONT")
  private int channels;
  @LOC("CONT")
  private int frequency;
  @LOC("IDX")
  private int idx;

  static public long sampleNumber = 0;

  /**
   * Constructor
   */
  public SampleBuffer(@LOC("IN") int sample_frequency, @LOC("IN") int number_of_channels) {
    buffer = new short[OBUFFERSIZE];
    bufferp = new int[MAXCHANNELS];
    channels = number_of_channels; // [IN] -> [D]
    frequency = sample_frequency; // [IN] -> [D]

    for (@LOC("C") int i = 0; i < number_of_channels; ++i) {
      bufferp[i] = (short) i; // LOC(bufferp[i]) has indirect flows from the
                              // location C, IN
      // also, it has a direct flow from C
      // anyway, LOC(bufferp[i])=[D,SampleBuffer.BUFP] is lower than all
      // locations that have in-flows
    }

  }

  public int getChannelCount() {
    return this.channels;
  }

  public int getSampleFrequency() {
    return this.frequency;
  }

  public short[] getBuffer() {
    return this.buffer;
  }

  public int getBufferLength() {
    return bufferp[0];
  }

  /**
   * Takes a 16 Bit PCM sample.
   */
  public void append(@LOC("IN") int channel, @LOC("IN") short value) {
    buffer[bufferp[channel]] = value;
    // LOC(bufferp[channel])= [local.D,SampleBuffer.BUF]
    // so, for LHS, LOC(buffer) < LOC(bufferp[channle])
    // also, bet' LHS and RHS, LOC(LHS) < LOC(RHS) since LOC(value)=[IN]

    bufferp[channel] += channels;
    // for lhs, LOC(bufferp[channel]) = [local.D, SampleBuffer.BUFP]
    // for rhs, LOC(channels) = [local.D, SampleBuffer.CON]

  }

  @LATTICE("D<IN,IN<C,THISLOC=D,C*")
  public void appendSamples(@LOC("IN") int channel, @LOC("IN") float[] f) {
    @LOC("D,SampleBuffer.BUFP") int pos = bufferp[channel];
    // LOC(bufferp[channel])=[D,SampleBuffer.BUFP]
    // LOC(pos)=[D,SampleBuffer.BUFP]

    @LOC("D,SampleBuffer.BUFP") short s;
    @LOC("D,SampleBuffer.BUFP") float fs;

    for (@LOC("C") int i = 0; i < 32;) {
      fs = f[i++]; // [IN] -> [D,BUFP]

      if (fs > 32767.0f) {
        fs = 32767.0f;
        // it has an indirect flow from LOC(fs)
        // since LOC(fs) is a shared location, it's okay
      } else {
        if (fs < -32767.0f) {
          fs = -32767.0f;
        }
      }

      /*
       * fs = (fs>32767.0f ? 32767.0f : (fs < -32767.0f ? -32767.0f : fs));
       */

      s = (short) fs; // it's okay since BUFP of [D,BUFP] is a shared location
      buffer[pos] = s;

      // DEBUG_OUTPUT(pos, s);

      // for LHS, LOC(buffer[pos])= GLB( [D,BUF] , [D,BUFP] ) = [D,BUF]
      // for RHS, LOC(s) = [D,BUFP]
      // so it's okay: [D,BUFP] -> [D,BUF]

      pos += channels; // [D,BUFP] -> [D,BUFP]
    }

    bufferp[channel] = pos;
    // for lhs, LOC(bufferp[channel])=[D,BUFP]
    // for rhs, LOC(pos)=[D,BUFP]
    // since BUFP is a shared location, the assignment is okay
  }

  /**
   * Write the samples to the file (Random Acces).
   */
  public void write_buffer(@LOC("IN") int val) {

    // for (int i = 0; i < channels; ++i)
    // bufferp[i] = (short)i;

  }

  public void close() {
  }

  /**
   *
   */

  public void clear_buffer() {
    for (idx = 0; idx < channels; ++idx)
      bufferp[idx] = (short) idx;
  }

  /**
   *
   */
  public void set_stop_flag() {
  }

  @TRUST
  private void DEBUG_OUTPUT(int pos, short s) {
    // there is left and right channel interleaved into the
    // output buffer, so only sample one channel (stride=2)
    if (pos % 2 == 0) {
      System.out.println(sampleNumber + " " + s);
      sampleNumber++;
    }
  }
}
