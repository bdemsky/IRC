/* =============================================================================
 *
 * sort.java
 *
 * =============================================================================
 *
 * Quick sort
 *
 * Copyright (C) 2002 Michael Ringgaard. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF  SUCH DAMAGE
 *
 * =============================================================================
 *
 * Modifed October 2007 by Chi Cao Minh
 * -- Changed signature of comparison function
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

#define CUTOFF 8

public class Sort {

  public Sort() {

  }

  /* =============================================================================
   * swap
   * =============================================================================
   */
  public static void
    swap (byte[] base, int a, int b, int width)
    {
      if (a != b ) {
        while (width--) {
          byte tmp = base[a];
          base[a++] = base[b];
          base[b++] = tmp;
        }
      }
    }


  /* =============================================================================
   * shortsort
   * =============================================================================
   */

  public static void shortsort(byte[] base,
      int lo,
      int hi,
      int width,
      int n,
      int offset)
  {
    while(hi > lo) {
      int max = lo;
      for(int p = (lo + width); p <= hi; p += width) {
        if(cmp(base, p, max, n, offset) > 0) {
          max = p;
        }
      }
      swap(base, max, hi, width);
      hi -= width;
    }
  }

  /* =============================================================================
   * sort
   * =============================================================================
   */
  public static void
    sort (byte[] base,
        int start,
        int num,
        int width,
        int n,
        int offset)
    {
      if (num < 2 || width == 0) {
        return;
      }

      /**
       * Pointers that keep track of
       * where to start looking in 
       * the base array
       **/
      int[] lostk= new int[30];
      int[] histk= new int[30];

      int stkptr = 0;

      int lo = start;
      int hi = start + (width * (num - 1));

      int size = 0;

      int pvlo = lo;
      int pvhi = hi;
      int pvwidth = width;
      int pvn = n;
      int pvmid;
      int pvloguy;
      int pvhiguy;
      int typeflag;

      while(true) {

        size = (pvhi - pvlo) / pvwidth + 1;
 
        if (size <= CUTOFF) {

          shortsort(base, pvlo, pvhi, pvwidth, pvn, offset);

        } else {

          pvmid = pvlo + (size / 2) * pvwidth;
          swap(base, pvmid, pvlo, pvwidth);

          pvloguy = pvlo;
          pvhiguy = pvhi + pvwidth;

          while(true) {
            do {
              pvloguy += pvwidth;
            } while (pvloguy <= pvhi && cmp(base, pvloguy, pvlo, pvn, offset) <= 0);
            do {
              pvhiguy -= pvwidth;
            } while (pvhiguy > pvlo && cmp(base, pvhiguy, pvlo, pvn, offset) >= 0);
            if (pvhiguy < pvloguy) {
              break;
            }
            swap(base, pvloguy, pvhiguy, pvwidth);
          }

          swap(base, pvlo, pvhiguy, pvwidth);

          if ((pvhiguy - 1 - pvlo) >= (pvhi - pvloguy)) {
            if (pvlo + pvwidth < pvhiguy) {
              lostk[stkptr] = pvlo;
              histk[stkptr] = pvhiguy - pvwidth;
              ++stkptr;
            }

            if (pvloguy < pvhi) {
              pvlo = pvloguy;
              continue;
            }
          } else {
            if (pvloguy < pvhi) {
              lostk[stkptr] = pvloguy;
              histk[stkptr] = pvhi;
              ++stkptr;
            }
            if (pvlo + pvwidth < pvhiguy) {
              pvhi = pvhiguy - pvwidth;
              continue;
            }
          }
        }

        --stkptr;
        if (stkptr >= 0) {
          pvlo = lostk[stkptr];
          pvhi = histk[stkptr];
          continue;
        }
	break;
      }
    }

  /* =============================================================================
   * compareRecord
   * =============================================================================
   */

  public static int
    cmp(byte[] base, int p1, int  p2, int n, int offset)
    {
      int i = n - offset;
      int s1 = p1 + offset;
      int s2 = p2 + offset;

      while (i-- > 0) {
        byte u1 = base[s1];
        byte u2 = base[s2];
        if (u1 != u2) {
          return (u1 - u2); 
        }
        s1++;
        s2++;
      }
      return 0;
    }
}


/* =============================================================================
 *
 * End of sort.java
 *
 * =============================================================================
 */
