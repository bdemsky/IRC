/*
 * 11/19/04			1.0 moved to LGPL.
 * 
 * 12/12/99 0.0.7	Implementation stores single bits 
 *					as ints for better performance. mdm@techie.com.
 *
 * 02/28/99 0.0     Java Conversion by E.B, javalayer@javazoom.net
 *
 *                  Adapted from the public c code by Jeff Tsay.
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
 * Implementation of Bit Reservoir for Layer III.
 * <p>
 * The implementation stores single bits as a word in the buffer. If a bit is
 * set, the corresponding word in the buffer will be non-zero. If a bit is
 * clear, the corresponding word is zero. Although this may seem waseful, this
 * can be a factor of two quicker than packing 8 bits to a byte and extracting.
 * <p>
 */

// REVIEW: there is no range checking, so buffer underflow or overflow
// can silently occur.
@LATTICE("BUF<OFF,BUF<BIT,BIT*,OFF*")
@METHODDEFAULT("OUT<THIS,THIS<IN,IN*,THISLOC=THIS,RETURNLOC=OUT")
final class BitReserve {
  /**
   * Size of the internal buffer to store the reserved bits. Must be a power of
   * 2. And x8, as each bit is stored as a single entry.
   */
  private static final int BUFSIZE = 4096 * 8;

  /**
   * Mask that can be used to quickly implement the modulus operation on
   * BUFSIZE.
   */
  private static final int BUFSIZE_MASK = BUFSIZE - 1;

  @LOC("BIT")
  private int offset;

  @LOC("BIT")
  private int totbit;

  @LOC("BIT")
  private int buf_byte_idx;

  @LOC("BIT")
  private final int[] buf;

  BitReserve() {
    offset = 0;
    totbit = 0;
    buf_byte_idx = 0;
    buf = new int[BUFSIZE];
  }

  /**
   * Return totbit Field.
   */
  @RETURNLOC("THIS,BitReserve.BIT")
  public int hsstell() {
    return (totbit);
  }

  /**
   * Read a number bits from the bit stream.
   * 
   * @param N
   *          the number of
   */
  public int hgetbits(@LOC("THIS,BitReserve.BIT") int N) {

    totbit += N;

    @LOC("OUT") int val = 0;

    @LOC("THIS,BitReserve.BIT") int pos = buf_byte_idx;
    if (pos + N < BUFSIZE) {
      TERMINATE:
      while (N-- > 0) {
        val <<= 1;
        val |= ((buf[pos++] != 0) ? 1 : 0);
      }
    } else {
      TERMINATE:
      while (N-- > 0) {
        val <<= 1;
        val |= ((buf[pos] != 0) ? 1 : 0);
        pos = (pos + 1) & BUFSIZE_MASK;
      }
    }

    buf_byte_idx = pos;

    return val;

  }

  /**
   * Returns next bit from reserve.
   * 
   * @returns 0 if next bit is reset, or 1 if next bit is set.
   */
  @RETURNLOC("THIS,BitReserve.BIT")
  public int hget1bit() {
    totbit++;
    @LOC("THIS,BitReserve.BIT") int val = buf[buf_byte_idx];
    buf_byte_idx = (buf_byte_idx + 1) & BUFSIZE_MASK;
    return val;
  }

  /**
   * Write 8 bits into the bit stream.
   */
  @LATTICE("OUT<THIS,THIS<IN,THISLOC=THIS,GLOBALLOC=IN")
  public void hputbuf(@LOC("IN") int val) {
    @LOC("THIS,BitReserve.OFF") int ofs = offset;
    buf[ofs++] = val & 0x80;
    buf[ofs++] = val & 0x40;
    buf[ofs++] = val & 0x20;
    buf[ofs++] = val & 0x10;
    buf[ofs++] = val & 0x08;
    buf[ofs++] = val & 0x04;
    buf[ofs++] = val & 0x02;
    buf[ofs++] = val & 0x01;

    if (ofs == BUFSIZE)
      offset = 0;
    else
      offset = ofs;

  }

  /**
   * Rewind N bits in Stream.
   */
  public void rewindNbits(@LOC("THIS,BitReserve.BIT") int N) {
    totbit -= N;
    buf_byte_idx -= N;
    if (buf_byte_idx < 0)
      buf_byte_idx += BUFSIZE;
  }

  /**
   * Rewind N bytes in Stream.
   */
  @LATTICE("THIS<BIT,BIT<N,THISLOC=THIS,GLOBALLOC=N")
  public void rewindNbytes(@LOC("N") int N) {
    @LOC("BIT") int bits = (N << 3);
    totbit -= bits;
    buf_byte_idx -= bits;
    if (buf_byte_idx < 0)
      buf_byte_idx += BUFSIZE;
  }
}
