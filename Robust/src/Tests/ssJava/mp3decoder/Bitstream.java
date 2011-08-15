/*
 * 11/19/04  1.0 moved to LGPL.
 * 
 * 11/17/04     Uncomplete frames discarded. E.B, javalayer@javazoom.net 
 *
 * 12/05/03     ID3v2 tag returned. E.B, javalayer@javazoom.net 
 *
 * 12/12/99     Based on Ibitstream. Exceptions thrown on errors,
 *              Temporary removed seek functionality. mdm@techie.com
 *
 * 02/12/99 : Java Conversion by E.B , javalayer@javazoom.net
 *
 * 04/14/97 : Added function prototypes for new syncing and seeking
 * mechanisms. Also made this file portable. Changes made by Jeff Tsay
 *
 *  @(#) ibitstream.h 1.5, last edit: 6/15/94 16:55:34
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
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
 * The <code>Bistream</code> class is responsible for parsing an MPEG audio
 * bitstream.
 * 
 * <b>REVIEW:</b> much of the parsing currently occurs in the various decoders.
 * This should be moved into this class and associated inner classes.
 */
@LATTICE("FB<F,FF<F,WP<BI,FF*,WP*,BI*")
@METHODDEFAULT("OUT<THIS,THIS<VAR,VAR<IN,VAR*,THISLOC=THIS,GLOBALLOC=IN")
public final class Bitstream implements BitstreamErrors {
  /**
   * Synchronization control constant for the initial synchronization to the
   * start of a frame.
   */
  @LOC("F")
  static byte INITIAL_SYNC = 0;

  /**
   * Synchronization control constant for non-initial frame synchronizations.
   */

  @LOC("F")
  static byte STRICT_SYNC = 1;

  // max. 1730 bytes per frame: 144 * 384kbit/s / 32000 Hz + 2 Bytes CRC
  /**
   * Maximum size of the frame buffer.
   */
  @LOC("F")
  private static final int BUFFER_INT_SIZE = 433;

  /**
   * The frame buffer that holds the data for the current frame.
   */
  @LOC("FB")
  private final int[] framebuffer = new int[BUFFER_INT_SIZE];

  /**
   * Number of valid bytes in the frame buffer.
   */
  @LOC("F")
  private int framesize;

  /**
   * The bytes read from the stream.
   */
  @LOC("FB")
  private byte[] frame_bytes = new byte[BUFFER_INT_SIZE * 4];

  /**
   * Index into <code>framebuffer</code> where the next bits are retrieved.
   */
  @LOC("WP")
  private int wordpointer;

  /**
   * Number (0-31, from MSB to LSB) of next bit for get_bits()
   */
  @LOC("BI")
  private int bitindex;

  /**
   * The current specified syncword
   */
  @LOC("F")
  private int syncword;

  /**
   * Audio header position in stream.
   */
  @LOC("F")
  private int header_pos = 0;

  /**
      *
      */
  @LOC("F")
  private boolean single_ch_mode;
  // private int current_frame_number;
  // private int last_frame_number;

  @LOC("F")
  private final int bitmask[] = {
      0, // dummy
      0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F, 0x0000003F, 0x0000007F,
      0x000000FF, 0x000001FF, 0x000003FF, 0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF,
      0x00007FFF, 0x0000FFFF, 0x0001FFFF };

  @LOC("F")
  private final PushbackInputStream source;

  @LOC("F")
  private final Header header = new Header();

  @LOC("F")
  private final byte syncbuf[] = new byte[4];

  @LOC("F")
  private Crc16[] crc = new Crc16[1];

  @LOC("F")
  private byte[] rawid3v2 = null;

  @LOC("FF")
  private boolean firstframe = true;

  private BitReserve br;
  private int main_data_begin;
  private int frame_start;

  /**
   * Construct a IBitstream that reads data from a given InputStream.
   * 
   * @param in
   *          The InputStream to read from.
   */
  public Bitstream(InputStream in) {
    if (in == null)
      throw new NullPointerException("in");
    in = new BufferedInputStream(in);
    loadID3v2(in);
    firstframe = true;
    // source = new PushbackInputStream(in, 1024);
    source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

    closeFrame();
    // current_frame_number = -1;
    // last_frame_number = -1;

    br = new BitReserve();

  }

  /**
   * Return position of the first audio header.
   * 
   * @return size of ID3v2 tag frames.
   */
  public int header_pos() {
    return header_pos;
  }

  /**
   * Load ID3v2 frames.
   * 
   * @param in
   *          MP3 InputStream.
   * @author JavaZOOM
   */
  private void loadID3v2(InputStream in) {
    int size = -1;
    try {
      // Read ID3v2 header (10 bytes).
      in.mark(10);
      size = readID3v2Header(in);
      header_pos = size;
    } catch (IOException e) {
    } finally {
      try {
        // Unread ID3v2 header (10 bytes).
        in.reset();
      } catch (IOException e) {
      }
    }
    // Load ID3v2 tags.
    try {
      if (size > 0) {
        rawid3v2 = new byte[size];
        in.read(rawid3v2, 0, rawid3v2.length);
      }
    } catch (IOException e) {
    }
  }

  /**
   * Parse ID3v2 tag header to find out size of ID3v2 frames.
   * 
   * @param in
   *          MP3 InputStream
   * @return size of ID3v2 frames + header
   * @throws IOException
   * @author JavaZOOM
   */
  private int readID3v2Header(InputStream in) throws IOException {
    byte[] id3header = new byte[4];
    int size = -10;
    in.read(id3header, 0, 3);
    // Look for ID3v2
    if ((id3header[0] == 'I') && (id3header[1] == 'D') && (id3header[2] == '3')) {
      in.read(id3header, 0, 3);
      int majorVersion = id3header[0];
      int revision = id3header[1];
      in.read(id3header, 0, 4);
      size =
          (int) (id3header[0] << 21) + (id3header[1] << 14) + (id3header[2] << 7) + (id3header[3]);
    }
    return (size + 10);
  }

  /**
   * Return raw ID3v2 frames + header.
   * 
   * @return ID3v2 InputStream or null if ID3v2 frames are not available.
   */
  public InputStream getRawID3v2() {
    if (rawid3v2 == null)
      return null;
    else {
      ByteArrayInputStream bain = new ByteArrayInputStream(rawid3v2);
      return bain;
    }
  }

  /**
   * Close the Bitstream.
   * 
   * @throws BitstreamException
   */
  public void close() throws BitstreamException {
    try {
      source.close();
    } catch (IOException ex) {
      throw newBitstreamException(STREAM_ERROR, ex);
    }
  }

  /**
   * Reads and parses the next frame from the input source.
   * 
   * @return the Header describing details of the frame read, or null if the end
   *         of the stream has been reached.
   */
  public Header readFrame() throws BitstreamException {
    
    System.out.println("ST");
    
    Header result = null;
    try {
      System.out.println("HERE?");
      result = readNextFrame();
      System.out.println("HERE?2");
      // E.B, Parse VBR (if any) first frame.
      if (firstframe == true) {
        result.parseVBR(frame_bytes);
        firstframe = false;
      }
      
      int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
      result.setSideInfoBuf(getSideInfoBuffer(channels));
      result.setBitReserve(getBitReserve(result.slots()));
      
    } catch (BitstreamException ex) {
      if ((ex.getErrorCode() == INVALIDFRAME)) {
        // Try to skip this frame.
        // System.out.println("INVALIDFRAME");
        try {    System.out.println("ET");

          closeFrame();
          result = readNextFrame();
        } catch (BitstreamException e) {
          if ((e.getErrorCode() != STREAM_EOF)) {
            // wrap original exception so stack trace is maintained.
            throw newBitstreamException(e.getErrorCode(), e);
          }
        }
      } else if ((ex.getErrorCode() != STREAM_EOF)) {
        // wrap original exception so stack trace is maintained.
        throw newBitstreamException(ex.getErrorCode(), ex);
      }
    }
    System.out.println("EN");

    return result;
  }

  /**
   * Read next MP3 frame.
   * 
   * @return MP3 frame header.
   * @throws BitstreamException
   */
  private Header readNextFrame() throws BitstreamException {
    System.out.println("framesize="+framesize);
    if (framesize == -1) {
      nextFrame();
    }
    return header;
  }

  /**
   * Read next MP3 frame.
   * 
   * @throws BitstreamException
   */
  private void nextFrame() throws BitstreamException {
    // entire frame is read by the header class.
    System.out.println("nextFrame()");
    header.read_header(this, crc);
  }

  /**
   * Unreads the bytes read from the frame.
   * 
   * @throws BitstreamException
   */
  // REVIEW: add new error codes for this.
  public void unreadFrame() throws BitstreamException {
    if (wordpointer == -1 && bitindex == -1 && (framesize > 0)) {
      try {
        source.unread(frame_bytes, 0, framesize);
      } catch (IOException ex) {
        throw newBitstreamException(STREAM_ERROR);
      }
    }
  }

  /**
   * Close MP3 frame.
   */
  public void closeFrame() {
    framesize = -1;
    wordpointer = -1;
    bitindex = -1;
  }

  /**
   * Determines if the next 4 bytes of the stream represent a frame header.
   */
  public boolean isSyncCurrentPosition(int syncmode) throws BitstreamException {
    int read = readBytes(syncbuf, 0, 4);
    int headerstring =
        ((syncbuf[0] << 24) & 0xFF000000) | ((syncbuf[1] << 16) & 0x00FF0000)
            | ((syncbuf[2] << 8) & 0x0000FF00) | ((syncbuf[3] << 0) & 0x000000FF);

    try {
      source.unread(syncbuf, 0, read);
    } catch (IOException ex) {
    }

    boolean sync = false;
    switch (read) {
    case 0:
      sync = true;
      break;
    case 4:
      sync = isSyncMark(headerstring, syncmode, syncword);
      break;
    }

    return sync;
  }

  // REVIEW: this class should provide inner classes to
  // parse the frame contents. Eventually, readBits will
  // be removed.
  public int readBits(int n) {
    return get_bits(n);
  }

  public int peek_bits(int number_of_bits) {

    int peekbitindex = bitindex;
    int peekPointer = wordpointer;

    int returnvalue = 0;
    int sum = peekbitindex + number_of_bits;

    if (peekPointer < 0) {
      peekPointer = 0;
    }

    if (sum <= 32) {
      // all bits contained in *wordpointer
      returnvalue = (framebuffer[peekPointer] >>> (32 - sum)) & bitmask[number_of_bits];
      // returnvalue = (wordpointer[0] >> (32 - sum)) &
      // bitmask[number_of_bits];
      if ((peekbitindex += number_of_bits) == 32) {
        peekbitindex = 0;
        peekPointer++; // added by me!
      }
      return returnvalue;
    }

    // E.B : Check that ?
    // ((short[])&returnvalue)[0] = ((short[])wordpointer + 1)[0];
    // wordpointer++; // Added by me!
    // ((short[])&returnvalue + 1)[0] = ((short[])wordpointer)[0];
    int Right = (framebuffer[peekPointer] & 0x0000FFFF);
    peekPointer++;
    int Left = (framebuffer[peekPointer] & 0xFFFF0000);
    returnvalue = ((Right << 16) & 0xFFFF0000) | ((Left >>> 16) & 0x0000FFFF);

    returnvalue >>>= 48 - sum; // returnvalue >>= 16 - (number_of_bits - (32
                               // - bitindex))
    returnvalue &= bitmask[number_of_bits];
    peekbitindex = sum - 32;
    return returnvalue;

  }

  public int readCheckedBits(int n) {
    // REVIEW: implement CRC check.
    return get_bits(n);
  }

  protected BitstreamException newBitstreamException(int errorcode) {
    return new BitstreamException(errorcode, null);
  }

  protected BitstreamException newBitstreamException(int errorcode, Throwable throwable) {
    return new BitstreamException(errorcode, throwable);
  }

  /**
   * Get next 32 bits from bitstream. They are stored in the headerstring.
   * syncmod allows Synchro flag ID The returned value is False at the end of
   * stream.
   */

  int syncHeader(byte syncmode) throws BitstreamException {
    boolean sync;
    int headerstring;
    // read additional 2 bytes
    int bytesRead = readBytes(syncbuf, 0, 3);

    if (bytesRead != 3)
      throw newBitstreamException(STREAM_EOF, null);

    headerstring =
        ((syncbuf[0] << 16) & 0x00FF0000) | ((syncbuf[1] << 8) & 0x0000FF00)
            | ((syncbuf[2] << 0) & 0x000000FF);

    do {
      headerstring <<= 8;

      if (readBytes(syncbuf, 3, 1) != 1)
        throw newBitstreamException(STREAM_EOF, null);

      headerstring |= (syncbuf[3] & 0x000000FF);

      sync = isSyncMark(headerstring, syncmode, syncword);
    } while (!sync);

    // current_frame_number++;
    // if (last_frame_number < current_frame_number) last_frame_number =
    // current_frame_number;

    return headerstring;
  }

  public boolean isSyncMark(int headerstring, int syncmode, int word) {
    boolean sync = false;

    if (syncmode == INITIAL_SYNC) {
      // sync = ((headerstring & 0xFFF00000) == 0xFFF00000);
      sync = ((headerstring & 0xFFE00000) == 0xFFE00000); // SZD: MPEG 2.5
    } else {
      sync =
          ((headerstring & 0xFFF80C00) == word)
              && (((headerstring & 0x000000C0) == 0x000000C0) == single_ch_mode);
    }

    // filter out invalid sample rate
    if (sync)
      sync = (((headerstring >>> 10) & 3) != 3);
    // filter out invalid layer
    if (sync)
      sync = (((headerstring >>> 17) & 3) != 0);
    // filter out invalid version
    if (sync)
      sync = (((headerstring >>> 19) & 3) != 1);

    return sync;
  }

  /**
   * Reads the data for the next frame. The frame is not parsed until parse
   * frame is called.
   */
  int read_frame_data(int bytesize) throws BitstreamException {
    int numread = 0;
    numread = readFully(frame_bytes, 0, bytesize);
    framesize = bytesize;
    wordpointer = -1;
    bitindex = -1;
    return numread;
  }

  /**
   * Parses the data previously read with read_frame_data().
   */
  @LATTICE("GLOBAL<B,B<BNUM,BNUM<K,K<BYTE,BYTE<THIS,B*,K*,THISLOC=THIS,GLOBALLOC=GLOBAL")
  void parse_frame() throws BitstreamException {
    // Convert Bytes read to int
    @LOC("B") int b = 0;
    @LOC("BYTE") byte[] byteread = frame_bytes;
    @LOC("BYTE") int bytesize = framesize;

    // Check ID3v1 TAG (True only if last frame).
    // for (int t=0;t<(byteread.length)-2;t++)
    // {
    // if ((byteread[t]=='T') && (byteread[t+1]=='A') && (byteread[t+2]=='G'))
    // {
    // System.out.println("ID3v1 detected at offset "+t);
    // throw newBitstreamException(INVALIDFRAME, null);
    // }
    // }

    for (@LOC("K") int k = 0; k < bytesize; k = k + 4) {
      @LOC("BYTE") int convert = 0;
      @LOC("BNUM") byte b0 = 0;
      @LOC("BNUM") byte b1 = 0;
      @LOC("BNUM") byte b2 = 0;
      @LOC("BNUM") byte b3 = 0;
      b0 = byteread[k];
      if (k + 1 < bytesize)
        b1 = byteread[k + 1];
      if (k + 2 < bytesize)
        b2 = byteread[k + 2];
      if (k + 3 < bytesize)
        b3 = byteread[k + 3];
      framebuffer[b++] =
          ((b0 << 24) & 0xFF000000) | ((b1 << 16) & 0x00FF0000) | ((b2 << 8) & 0x0000FF00)
              | (b3 & 0x000000FF);
    }
    wordpointer = 0;
    bitindex = 0;
  }

  /**
   * Read bits from buffer into the lower bits of an unsigned int. The LSB
   * contains the latest read bit of the stream. (1 <= number_of_bits <= 16)
   */
  @LATTICE("OUT<RL,RL<THIS,THIS<IN,OUT*,THISLOC=THIS,RETURNLOC=OUT")
  public int get_bits(@LOC("IN") int number_of_bits) {

    @LOC("OUT") int returnvalue = 0;
    @LOC("THIS,Bitstream.BI") int sum = bitindex + number_of_bits;

    // E.B
    // There is a problem here, wordpointer could be -1 ?!
    if (wordpointer < 0)
      wordpointer = 0;
    // E.B : End.

    if (sum <= 32) {
      // all bits contained in *wordpointer
      returnvalue = (framebuffer[wordpointer] >>> (32 - sum)) & bitmask[number_of_bits];
      // returnvalue = (wordpointer[0] >> (32 - sum)) & bitmask[number_of_bits];
      if ((bitindex += number_of_bits) == 32) {
        bitindex = 0;
        wordpointer++; // added by me!
      }
      return returnvalue;
    }

    // E.B : Check that ?
    // ((short[])&returnvalue)[0] = ((short[])wordpointer + 1)[0];
    // wordpointer++; // Added by me!
    // ((short[])&returnvalue + 1)[0] = ((short[])wordpointer)[0];
    @LOC("RL") int Right = (framebuffer[wordpointer] & 0x0000FFFF);
    wordpointer++;
    @LOC("RL") int Left = (framebuffer[wordpointer] & 0xFFFF0000);
    returnvalue = ((Right << 16) & 0xFFFF0000) | ((Left >>> 16) & 0x0000FFFF);

    returnvalue >>>= 48 - sum; // returnvalue >>= 16 - (number_of_bits - (32 -
                               // bitindex))
    returnvalue &= bitmask[number_of_bits];
    bitindex = sum - 32;
    return returnvalue;
  }

  /**
   * Set the word we want to sync the header to. In Big-Endian byte order
   */
  void set_syncword(@LOC("IN") int syncword0) {
    syncword = syncword0 & 0xFFFFFF3F;
    single_ch_mode = ((syncword0 & 0x000000C0) == 0x000000C0);
  }

  /**
   * Reads the exact number of bytes from the source input stream into a byte
   * array.
   * 
   * @param b
   *          The byte array to read the specified number of bytes into.
   * @param offs
   *          The index in the array where the first byte read should be stored.
   * @param len
   *          the number of bytes to read.
   * 
   * @exception BitstreamException
   *              is thrown if the specified number of bytes could not be read
   *              from the stream.
   */
  @LATTICE("OUT<VAR,VAR<THIS,THIS<IN,IN*,VAR*,THISLOC=THIS")
  @RETURNLOC("OUT")
  private int readFully(@LOC("OUT") byte[] b, @LOC("IN") int offs, @LOC("IN") int len)
      throws BitstreamException {
    @LOC("VAR") int nRead = 0;
    try {
      while (len > 0) {
        @LOC("IN") int bytesread = source.read(b, offs, len);
        if (bytesread == -1) {
          while (len-- > 0) {
            b[offs++] = 0;
          }
          break;
          // throw newBitstreamException(UNEXPECTED_EOF, new EOFException());
        }
        nRead = nRead + bytesread;
        offs += bytesread;
        len -= bytesread;
      }
    } catch (IOException ex) {
      throw newBitstreamException(STREAM_ERROR, ex);
    }
    return nRead;
  }

  /**
   * Simlar to readFully, but doesn't throw exception when EOF is reached.
   */
  @LATTICE("OUT<VAR,VAR<THIS,THIS<IN,IN*,VAR*,THISLOC=THIS")
  @RETURNLOC("OUT")
  private int readBytes(@LOC("OUT") byte[] b, @LOC("IN") int offs, @LOC("IN") int len)
      throws BitstreamException {
    @LOC("VAR") int totalBytesRead = 0;
    try {
      while (len > 0) {
        @LOC("IN") int bytesread = source.read(b, offs, len);
        if (bytesread == -1) {
          break;
        }
        totalBytesRead += bytesread;
        offs += bytesread;
        len -= bytesread;
      }
    } catch (IOException ex) {
      throw newBitstreamException(STREAM_ERROR, ex);
    }
    return totalBytesRead;
  }

  public SideInfoBuffer getSideInfoBuffer(int channelType) {

    if (wordpointer < 0)
      wordpointer = 0;

    SideInfoBuffer sib = new SideInfoBuffer();

    // first, store main_data_begin from the side inforamtion
    main_data_begin = peek_bits(9);
    System.out.println("main_data_begin=" + main_data_begin);

    int max;
    if (channelType == 1) { // mono
      max = wordpointer + 4;
    } else {
      max = wordpointer + 8;
    }

    try {
      for (; wordpointer < max; wordpointer++) {
        sib.setBuffer(wordpointer, framebuffer[wordpointer]);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.print("wordpointer=" + wordpointer);
      System.out.println("framebuffer length=" + framebuffer.length);
    }

    return sib;
  }

  public BitReserve getBitReserve(int nSlots) {

    int flush_main;
    int bytes_to_discard;
    int i;

    for (i = 0; i < nSlots; i++)
      br.hputbuf(get_bits(8));

    int main_data_end = br.hsstell() >>> 3; // of previous frame

    if ((flush_main = (br.hsstell() & 7)) != 0) {
      br.hgetbits(8 - flush_main);
      main_data_end++;
    }

    bytes_to_discard = frame_start - main_data_end - main_data_begin;

    frame_start += nSlots;

    if (bytes_to_discard < 0) {
      System.out.println("HERE?");
      return null;
    }

    if (main_data_end > 4096) {
      frame_start -= 4096;
      br.rewindNbytes(4096);
    }

    for (; bytes_to_discard > 0; bytes_to_discard--)
      br.hgetbits(8);

    return br;
  }
}
