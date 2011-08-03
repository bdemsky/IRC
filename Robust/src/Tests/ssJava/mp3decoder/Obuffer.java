/* 
 * 11/19/04  1.0 moved to LGPL.
 * 12/12/99  Added appendSamples() method for efficiency. MDM.
 * 15/02/99 ,Java Conversion by E.B ,ebsp@iname.com, JavaLayer
 *
 *   Declarations for output buffer, includes operating system
 *   implementation of the virtual Obuffer. Optional routines
 *   enabling seeks and stops added by Jeff Tsay. 
 *
 *  @(#) obuffer.h 1.8, last edit: 6/15/94 16:51:56
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
 *
 *  Idea and first implementation for u-law output with fast downsampling by
 *  Jim Boucher (jboucher@flash.bu.edu)
 *
 *  LinuxObuffer class written by
 *  Louis P. Kruger (lpkruger@phoenix.princeton.edu)
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
 * Base Class for audio output.
 */
@LATTICE("")
@METHODDEFAULT("D<IN,D<C,C*,THISLOC=D")
public abstract class Obuffer {
  public static final int OBUFFERSIZE = 2 * 1152; // max. 2 * 1152 samples per
                                                  // frame
  public static final int MAXCHANNELS = 2; // max. number of channels

  /**
   * Takes a 16 Bit PCM sample.
   */
  public abstract void append(@LOC("IN") int channel, @LOC("IN") short value);

  /**
   * Accepts 32 new PCM samples.
   */
  @LATTICE("THIS<C,C<IN,C*,THISLOC=THIS")
  public void appendSamples(@LOC("IN") int channel, @LOC("IN") float[] f) {
    @LOC("C") short s;
    for (@LOC("C") int i = 0; i < 32;) {
      s = clip(f[i++]);
      append(channel, s);
    }
  }

  /**
   * Clip Sample to 16 Bits
   */
  @LATTICE("THIS,OUT<IN,RETURNLOC=OUT,THISLOC=THIS")
  private final short clip(@LOC("IN") float sample) {

    @LOC("OUT") short s = (short) sample;

    if (sample > 32767.0f) {
      s = (short) 32767;
    } else if (sample < -32768.0f) {
      s = (short) -32768;
    }

    return s;

  }

  /**
   * Write the samples to the file or directly to the audio hardware.
   */
  public abstract void write_buffer(@LOC("IN") int val);

  public abstract void close();

  /**
   * Clears all data in the buffer (for seeking).
   */
  public abstract void clear_buffer();

  /**
   * Notify the buffer that the user has stopped the stream.
   */
  public abstract void set_stop_flag();
}
