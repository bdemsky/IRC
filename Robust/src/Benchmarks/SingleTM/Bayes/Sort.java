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
    swap (char[] base, int a, int b, int width)
    {
      if (a != b ) {
        while (width--) {
          char tmp = base[a];
          base[a++] = base[b];
          base[b++] = tmp;
        }
      }
    }


  /* =============================================================================
   * shortsort
   * =============================================================================
   */

  public static void shortsort(char[] base,
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
  public void
    sort (char[] base,
        int start,
        int num,
        int width,
        int n,
        int offset)
    {
      /**
       * debug
       **/
      /*
      for(int o = 0; o< (width * (num - 1)); o++)
        System.out.println("base["+ o +"]=" + (int)base[o]);
      */
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

      boolean cont = true;

      ptrVal pv = new ptrVal();
      pv.lo = lo;
      pv.hi = hi;
      pv.width = width;
      pv.n = n;

      int typeflag;

      while(cont) {

        size = (pv.hi - pv.lo) / pv.width + 1;
 
        /**
         * debug
         **/
        //System.out.println("DEBUG: lo= "+ pv.lo + " hi= " + pv.hi + " width= " + pv.width+ " offset= " + offset + " n= " + pv.n + " size= " + size);

        if (size <= CUTOFF) {

          shortsort(base, pv.lo, pv.hi, pv.width, pv.n, offset);

        } else {

          pv.mid = pv.lo + (size / 2) * pv.width;
          swap(base, pv.mid, pv.lo, pv.width);

          pv.loguy = pv.lo;
          pv.higuy = pv.hi + pv.width;

          while(true) {
            do {
              pv.loguy += pv.width;
            } while (pv.loguy <= pv.hi && cmp(base, pv.loguy, pv.lo, pv.n, offset) <= 0);
            do {
              pv.higuy -= pv.width;
            } while (pv.higuy > pv.lo && cmp(base, pv.higuy, pv.lo, pv.n, offset) >= 0);
            if (pv.higuy < pv.loguy) {
              break;
            }
            swap(base, pv.loguy, pv.higuy, pv.width);
          }

          swap(base, pv.lo, pv.higuy, pv.width);

          if ((pv.higuy - 1 - pv.lo) >= (pv.hi - pv.loguy)) {
            if (pv.lo + pv.width < pv.higuy) {
              lostk[stkptr] = pv.lo;
              histk[stkptr] = pv.higuy - pv.width;
              ++stkptr;
            }

            if (pv.loguy < pv.hi) {
              pv.lo = pv.loguy;
              continue;
            }
          } else {
            if (pv.loguy < pv.hi) {
              lostk[stkptr] = pv.loguy;
              histk[stkptr] = pv.hi;
              ++stkptr;
            }
            if (pv.lo + pv.width < pv.higuy) {
              pv.hi = pv.higuy - pv.width;
              continue;
            }
          }
        }

        --stkptr;
        if (stkptr >= 0) {
          pv.lo = lostk[stkptr];
          pv.hi = histk[stkptr];
          continue;
        }
        cont = false;
      }
    }

  /* =============================================================================
   * compareRecord
   * =============================================================================
   */

  public static int
    cmp(char[] base, int p1, int  p2, int n, int offset)
    {
      int i = n - offset;
      int s1 = p1 + offset;
      int s2 = p2 + offset;

      while (i-- > 0) {
        char u1 = base[s1];
        char u2 = base[s2];
        if (u1 != u2) {
          return (u1 - u2); 
        }
        s1++;
        s2++;
      }
      return 0;
    }
}

public class ptrVal {
  int lo;
  int hi;
  int width;
  int n;
  int loguy;
  int higuy;
  int max;
  int p;
  int mid;

  public ptrVal() {
    lo = 0;
    hi = 0;
    width = 0;
    n = 0;
    loguy = 0;
    higuy = 0;
    max = 0;
    p = 0;
    mid = 0;
  }
}

/* =============================================================================
 *
 * End of sort.java
 *
 * =============================================================================
 */
