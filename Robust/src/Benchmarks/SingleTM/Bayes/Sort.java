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
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
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

//#include "sort.h"

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
      if (a != b) {
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
  public static void
    shortsort (char[] base,
        int lo,
        int hi,
        int width,
        int n,
        int offset)
    {
      while (hi > lo) {
        int max = lo;
        int p;
        for (p = (lo + width); p <= hi; p += width) {
          if (cmp(base, p, max, n, offset) > 0) {
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
      if (num < 2 || width == 0) {
        return;
      }

      int lo = start;
      int hi = start + (width * (num - 1));

      //System.out.println("start= " + start + " base.length= " + base.length + " hi= " + hi);

      recurse(base, lo, hi, width, n, offset);

    }

  public void recurse(char[] base, int lo, int hi, int width, int n, int offset) 
  {
      char[] lostk= new char[30];
      char[] histk= new char[30];

      int stkptr = 0;
      int size;
      //recurse:

      size = (hi - lo) / width + 1;

      if (size <= CUTOFF) {

        shortsort(base, lo, hi, width, n, offset);

      } else {

        int mid = lo + (size / 2) * width;
        swap(base, mid, lo, width);

        int loguy = lo;
        int higuy = hi + width;

        boolean status = true;
        while(true) {
          do {
            loguy += width;
          } while (loguy <= hi && cmp(base, loguy, lo, n, offset) <= 0);
          do {
            higuy -= width;
          } while (higuy > lo && cmp(base, higuy, lo, n, offset) >= 0);
          if (higuy < loguy) {
            break;
          }
          swap(base, loguy, higuy, width);
        }

        swap(base, lo, higuy, width);

        if ((higuy - 1 - lo) >= (hi - loguy)) {
          if (lo + width < higuy) {
            lostk[stkptr] = base[lo];
            histk[stkptr] = base[higuy - width];
            ++stkptr;
          }

          if (loguy < hi) {
            lo = loguy;
            recurse(base, lo, hi, width, n, offset);
          }
        } else {
          if (loguy < hi) {
            lostk[stkptr] = base[loguy];
            histk[stkptr] = base[hi];
            ++stkptr;
          }
          if (lo + width < higuy) {
            hi = higuy - width;
            recurse(base, lo, hi, width, n, offset);
          }
        }
      }

      --stkptr;
      if (stkptr >= 0) {
        base[lo] = lostk[stkptr];
        base[hi] = histk[stkptr];
        recurse(base, lo, hi, width, n, offset);
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

      //const char* s1 = (const char*)p1 + offset;
      //const char* s2 = (const char*)p2 + offset;

      while (i-- > 0) {
        char u1 = base[s1];
        char u2 = base[s2];
        //unsigned char u1 = (unsigned char)*s1++;
        //unsigned char u2 = (unsigned char)*s2++;
        if (u1 != u2) {
          return (u1 - u2); //CAN YOU DO THIS
        }
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
