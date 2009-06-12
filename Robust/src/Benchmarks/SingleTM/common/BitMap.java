/* =============================================================================
 *
 * bitmap.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * Ported to Java:
 * Author:Alokika dash
 * University of California, Irvine
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

#define NUM_BIT_PER_BYTE (8)
#define NUM_BIT_PER_WORD (4 * NUM_BIT_PER_BYTE)

public class BitMap {
  int numBit;
  int numWord;
  int[] bits;

  public BitMap() {

  }

  public int DIVIDE_AND_ROUND_UP(int a, int b) {
    int res1 = a / b;
    int res2 = a % b;
    int val = (res2 > 0) ? (1) : (0);
    return (res1 + val);
  }


  /* =============================================================================
   * bitmap_alloc
   * -- Returns null on failure
   * =============================================================================
   */
  public static BitMap bitmap_alloc(int numBit)
    {
      BitMap bitmapPtr = new BitMap();
      if (bitmapPtr == null) {
        return null;
      }

      bitmapPtr.numBit = numBit;
      int numWord = bitmapPtr.DIVIDE_AND_ROUND_UP(numBit, NUM_BIT_PER_WORD);
      bitmapPtr.numWord = numWord;

      bitmapPtr.bits = new int[numWord];
      if (bitmapPtr.bits == null) {
        return null;
      }
      for(int i = 0; i < numWord; i++)
        bitmapPtr.bits[i] = 0;

      return bitmapPtr;
    }

  /* =============================================================================
   * bitmap_free
   * =============================================================================
   */
  public void
    bitmap_free (BitMap bitmapPtr)
    {
      bitmapPtr.bits = null;
      bitmapPtr = null;
      //free(bitmapPtr.bits);
      //free(bitmapPtr);
    }


  /* =============================================================================
   * bitmap_set
   * -- Sets ith bit to 1
   * -- Returns true on success, else false
   * =============================================================================
   */
  public boolean
    bitmap_set (int i)
    {
      if ((i < 0) || (i >= numBit)) {
        return false;
      }

      bits[i/NUM_BIT_PER_WORD] |= (1 << (i % NUM_BIT_PER_WORD));

      return true;
    }


  /* =============================================================================
   * bitmap_clear
   * -- Clears ith bit to 0
   * -- Returns true on success, else false
   * =============================================================================
   */
  /*
  boolean
    bitmap_clear (bitmap_t* bitmapPtr, int i)
    {
      if ((i < 0) || (i >= bitmapPtr.numBit)) {
        return false;
      }

      bitmapPtr.bits[i/NUM_BIT_PER_WORD] &= ~(1UL << (i % NUM_BIT_PER_WORD));

      return true;
    }
    */


  /* =============================================================================
   * bitmap_clearAll
   * -- Clears all bit to 0
   * =============================================================================
   */
  public void
    bitmap_clearAll ()
    {
      for(int i = 0; i<numWord; i++)
        bits[i] = 0;
      //memset(bitmapPtr.bits, 0, (bitmapPtr.numWord * sizeof(uint_t)));
    }


  /* =============================================================================
   * bitmap_isClear
   * -- Returns true if ith bit is clear, else false
   * =============================================================================
   */
  /*
  boolean
    bitmap_isClear (bitmap_t* bitmapPtr, int i)
    {
      if ((i >= 0) && (i < bitmapPtr.numBit) &&
          !(bitmapPtr.bits[i/NUM_BIT_PER_WORD] & (1UL << (i % NUM_BIT_PER_WORD)))) {
        return true;
      }

      return false;
    }
    */


  /* =============================================================================
   * bitmap_isSet
   * -- Returns true if ith bit is set, else false
   * =============================================================================
   */
  public boolean
    bitmap_isSet (int i)
    {
      if ((i >= 0) && (i < numBit) &&
          (bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD)))) {
        return true;
      }

      return false;
    }


  /* =============================================================================
   * bitmap_findClear
   * -- Returns index of first clear bit
   * -- If start index is negative, will start from beginning
   * -- If all bits are set, returns -1
   * =============================================================================
   */
  public int
    bitmap_findClear (int startIndex)
    {
      int tmp_numBit = numBit;
      int[] tmp_bits = bits;
      //uint_t* bits = bitmapPtr.bits;

      for (int i = MAX(startIndex, 0); i < tmp_numBit; i++) {
        if (!(tmp_bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD)))) {
          return i;
        }
      }

      return -1;
    }


  /* =============================================================================
   * bitmap_findSet
   * -- Returns index of first set bit
   * -- If start index is negative, will start from beginning
   * -- If all bits are clear, returns -1
   * =============================================================================
   */
  /*
  int
    bitmap_findSet (bitmap_t* bitmapPtr, int startIndex)
    {
      int i;
      int numBit = bitmapPtr.numBit;
      uint_t* bits = bitmapPtr.bits;

      for (i = MAX(startIndex, 0); i < numBit; i++) {
        if (bits[i/NUM_BIT_PER_WORD] & (1UL << (i % NUM_BIT_PER_WORD))) {
          return i;
        }
      }

      return -1;
    }
    */

  /* =============================================================================
   * bitmap_getNumClear
   * =============================================================================
   */
  /*
  int
    bitmap_getNumClear (bitmap_t* bitmapPtr)
    {
      int numBit = bitmapPtr.numBit;

      return (numBit - bitmap_getNumSet(bitmapPtr));
    }
    */


  /* =============================================================================
   * bitmap_getNumSet
   * =============================================================================
   */
  /*
  int
    bitmap_getNumSet (bitmap_t* bitmapPtr)
    {
      int i;
      int numBit = bitmapPtr.numBit;
      uint_t* bits = bitmapPtr.bits;
      int count = 0;

      for (i = 0; i < numBit; i++) {
        if (bits[i/NUM_BIT_PER_WORD] & (1UL << (i % NUM_BIT_PER_WORD))) {
          count++;
        }
      }

      return count;
    }
    */


  /* =============================================================================
   * bitmap_copy
   * =============================================================================
   */
  /*
  void
    bitmap_copy (bitmap_t* dstPtr, bitmap_t* srcPtr)
    {
      assert(dstPtr.numBit == srcPtr.numBit);
      memcpy(dstPtr.bits, srcPtr.bits, (dstPtr.numWord * sizeof(uint_t)));
    }
    */


  /* =============================================================================
   * bitmap_toggleAll
   * =============================================================================
   */
  /*
  void
    bitmap_toggleAll (bitmap_t* bitmapPtr)
    {
      uint_t* bits = bitmapPtr.bits;
      int numWord = bitmapPtr.numWord;
      int w;
      for (w = 0; w < numWord; w++) {
        bits[w] ^= (uint_t)(-1L);
      }
    }
    */

  /**
   * ======================================
   * MAX(a.b)
   * ======================================
   **/
  public int MAX(int a, int b) {
    return (a > b) ? a : b; 
  }  
}

/* =============================================================================
 *
 * End of bitmap.java
 *
 * =============================================================================
 */
