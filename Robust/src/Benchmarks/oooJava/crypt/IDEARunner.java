/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                  Original version of this code by                       *
 *                 Gabriel Zachmann (zach@igd.fhg.de)                      *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/


public class IDEARunner {

  int id, key[];
  byte text1[], text2[];
  int nthreads;
  int local_size;

  public IDEARunner(int id, byte[] text1, byte[] text2, int local_size, int[] key, int nthreads) {
    this.id = id;
    this.text1 = text1;
    this.text2 = text2;
    this.key = key;
    this.nthreads = nthreads;
    this.local_size = local_size;
  }

  //
  // run()
  // 
  // IDEA encryption/decryption algorithm. It processes plaintext in
  // 64-bit blocks, one at a time, breaking the block into four 16-bit
  // unsigned subblocks. It goes through eight rounds of processing
  // using 6 new subkeys each time, plus four for last step. The source
  // text is in array text1, the destination text goes into array text2
  // The routine represents 16-bit subblocks and subkeys as type int so
  // that they can be treated more easily as unsigned. Multiplication
  // modulo 0x10001 interprets a zero sub-block as 0x10000; it must to
  // fit in 16 bits.
  //

  public void run() {
    int ilow, iupper, slice, tslice, ttslice;

    tslice = text1.length / 8;
    ttslice = (tslice + nthreads - 1) / nthreads;
    slice = ttslice * 8;

    ilow = id * slice;
    iupper = (id + 1) * slice;
    if (iupper > text1.length)
      iupper = text1.length;

    int i1 = ilow; // Index into first text array.
    // int i2 = ilow; // Index into second text array.
    int i2 = 0;

    int ik; // Index into key array.
    int x1, x2, x3, x4, t1, t2; // Four "16-bit" blocks, two temps.
    int r; // Eight rounds of processing.

    for (int i = ilow; i < iupper; i += 8) {

      ik = 0; // Restart key index.
      r = 8; // Eight rounds of processing.

      // Load eight plain1 bytes as four 16-bit "unsigned" integers.
      // Masking with 0xff prevents sign extension with cast to int.

      x1 = text1[i1++] & 0xff; // Build 16-bit x1 from 2 bytes,
      x1 |= (text1[i1++] & 0xff) << 8; // assuming low-order byte first.
      x2 = text1[i1++] & 0xff;
      x2 |= (text1[i1++] & 0xff) << 8;
      x3 = text1[i1++] & 0xff;
      x3 |= (text1[i1++] & 0xff) << 8;
      x4 = text1[i1++] & 0xff;
      x4 |= (text1[i1++] & 0xff) << 8;

      do {
        // 1) Multiply (modulo 0x10001), 1st text sub-block
        // with 1st key sub-block.

        x1 = (int) ((long) x1 * key[ik++] % 0x10001L & 0xffff);

        // 2) Add (modulo 0x10000), 2nd text sub-block
        // with 2nd key sub-block.

        x2 = x2 + key[ik++] & 0xffff;

        // 3) Add (modulo 0x10000), 3rd text sub-block
        // with 3rd key sub-block.

        x3 = x3 + key[ik++] & 0xffff;

        // 4) Multiply (modulo 0x10001), 4th text sub-block
        // with 4th key sub-block.

        x4 = (int) ((long) x4 * key[ik++] % 0x10001L & 0xffff);

        // 5) XOR results from steps 1 and 3.

        t2 = x1 ^ x3;

        // 6) XOR results from steps 2 and 4.
        // Included in step 8.

        // 7) Multiply (modulo 0x10001), result of step 5
        // with 5th key sub-block.

        t2 = (int) ((long) t2 * key[ik++] % 0x10001L & 0xffff);

        // 8) Add (modulo 0x10000), results of steps 6 and 7.

        t1 = t2 + (x2 ^ x4) & 0xffff;

        // 9) Multiply (modulo 0x10001), result of step 8
        // with 6th key sub-block.

        t1 = (int) ((long) t1 * key[ik++] % 0x10001L & 0xffff);

        // 10) Add (modulo 0x10000), results of steps 7 and 9.

        t2 = t1 + t2 & 0xffff;

        // 11) XOR results from steps 1 and 9.

        x1 ^= t1;

        // 14) XOR results from steps 4 and 10. (Out of order).

        x4 ^= t2;

        // 13) XOR results from steps 2 and 10. (Out of order).

        t2 ^= x2;

        // 12) XOR results from steps 3 and 9. (Out of order).

        x2 = x3 ^ t1;

        x3 = t2; // Results of x2 and x3 now swapped.

      } while (--r != 0); // Repeats seven more rounds.

      // Final output transform (4 steps).

      // 1) Multiply (modulo 0x10001), 1st text-block
      // with 1st key sub-block.

      x1 = (int) ((long) x1 * key[ik++] % 0x10001L & 0xffff);

      // 2) Add (modulo 0x10000), 2nd text sub-block
      // with 2nd key sub-block. It says x3, but that is to undo swap
      // of subblocks 2 and 3 in 8th processing round.

      x3 = x3 + key[ik++] & 0xffff;

      // 3) Add (modulo 0x10000), 3rd text sub-block
      // with 3rd key sub-block. It says x2, but that is to undo swap
      // of subblocks 2 and 3 in 8th processing round.

      x2 = x2 + key[ik++] & 0xffff;

      // 4) Multiply (modulo 0x10001), 4th text-block
      // with 4th key sub-block.

      x4 = (int) ((long) x4 * key[ik++] % 0x10001L & 0xffff);

      // Repackage from 16-bit sub-blocks to 8-bit byte array text2.

      text2[i2++] = (byte) x1;
      text2[i2++] = (byte) (x1 >>> 8);
      text2[i2++] = (byte) x3; // x3 and x2 are switched
      text2[i2++] = (byte) (x3 >>> 8); // only in name.
      text2[i2++] = (byte) x2;
      text2[i2++] = (byte) (x2 >>> 8);
      text2[i2++] = (byte) x4;
      text2[i2++] = (byte) (x4 >>> 8);
    } // End for loop.

  } // End routine.

} // End of class
