/* =============================================================================
 *
 * alg_radix_smp.java
 *
 * =============================================================================
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
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

public class Alg_Radix_Smp {
  long[] global_myHisto;
  long[] global_psHisto;
  long[] global_lTemp;
  long[] global_lTemp2;
  int myId;
  int numThread;

  public class Alg_Radix_Smp(int myId, int numThread) {
    global_myHisto = null;
    global_psHisto = null;
    global_lTemp   = null;
    global_lTemp2  = null;
    this.myId = myId;
    this.numThread = numThread;
  }

  public int BITS(x, k, j) {
    int retval = (int) ((x>>k) & ~(~0<<j));
    return retval;
  }

  /* =============================================================================
   * all_countsort_node
   *
   * R (range)      must be a multiple of NODES
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_countsort_node (long q,
        long[] lKey,
        long[] lSorted,
        long R,
        long bitOff,
        long m,
        )
    {
      long[] myHisto = null;
      long[] psHisto = null;

      if (myId == 0) {
        myHisto = new long[numThread*R];
        global_myHisto = myHisto;
        psHisto = new long[numThread*R];
        global_psHisto = psHisto;
      }

      Barrier.enterBarrier();

      myHisto = global_myHisto;
      psHisto = global_psHisto;

      long index = myId * R;

      for (int k = (int) index; k < index+R; k++) {
        myHisto[k] = 0;
      }

      LocalStartStop lss = new LocalStartStop();
      CreatePartition.createPartition(0, (int) q, myId, numThread, lss);

      for (int k = lss.i_start; k < i_stop; k++) {
        myHisto[(int) (myId * R) + BITS(lKey[k],bitOff,m)]++;
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, (int) R, myId, numThread, lss);

      long last;
      for (int k = lss.i_start; k < lss.i_stop; k++) {
        last = psHisto[k] = myHisto[k];
        for (int j = 1; j < numThread; j++) {
          long temp = psHisto[(int)(j*R) + k] = last + myHisto[(int)(j*R) + k];
          last = temp;
        }
      }

      Barrier.enterBarrier();

      long offset = 0;

      for (k = 0; k < R; k++) {
        myHisto[(int)(myId * R) + k] = (psHisto[(int)(myId * R) + k] - myHisto[(int)(myId * R) + k]) + offset;
        offset += psHisto[(int)((numThread - 1) * R) + k];
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, q, myId, numThread, lss);

      for (int k = lss.i_start; k < lss.i_stop; k++) {
        int j = BITS(lKey[k],bitOff,m);
        lSorted[myHisto[(int)(myId * R) + j]] = lKey[k];
        myHisto[(int)(myId * R) + j]++;
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        psHisto = null;
        myHisto = null;
      }
    }


  /* =============================================================================
   * all_countsort_node_aux_seq
   *
   * R (range)      must be a multiple of NODES
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_countsort_node_aux_seq (long q,
        long[] lKey,
        long[] lSorted,
        long[] auxKey,
        long[] auxSorted,
        long R,
        long bitOff,
        long m)
    {
      long[] myHisto = new long[(int) R];
      long[] psHisto = new long[(int) R];
      
      for (int k = 0; k < R; k++) {
        myHisto[k] = 0;
      }

      for (int k = 0; k < q; k++) {
        myHisto[BITS(lKey[k],bitOff,m)]++;
      }

      long last;
      for (int k = 0; k < (int)R; k++) {
        last = psHisto[k] = myHisto[k];
      }

      long offset = 0;

      for (int k = 0; k < (int)R; k++) {
        myHisto[k] = (psHisto[k] - myHisto[k]) + offset;
        offset += psHisto[k];
      }

      for (int k = 0; k < (int) q; k++) {
        int j = BITS(lKey[k], bitOff, m);
        lSorted[myHisto[j]] = lKey[k];
        auxSorted[myHisto[j]] = lKey[k];
        myHisto[j]++;

        /*
        lSorted[mhp[j]] = lKey[k];
        auxSorted[mhp[j]] = auxKey[k];
        mhp[j]++;
        */
      }

      psHisto = null;
      myHisto = null;
    }


  /* =============================================================================
   * all_countsort_node_aux
   *
   * R (range)      must be a multiple of NODES
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_countsort_node_aux (long q,
        long[] lKey,
        long[] lSorted,
        long[] auxKey,
        long[] auxSorted,
        long R,
        long bitOff,
        long m)
    {
      long[] myHisto = null;
      long[] psHisto = null;

      if (myId == 0) {
        myHisto = new long[numThread * R];
        global_myHisto = myHisto;
        psHisto = new long[numThread * R];
        global_psHisto = psHisto;
      }

      Barrier.enterBarrier();

      myHisto = global_myHisto;
      psHisto = global_psHisto;

      for (int k = 0; k < (int) R; k++) {
        myHisto[(int)((myId*R) + k)] = 0;
      }

      LocalStartStop lss = new LocalStartStop();
      CreatePartition.createPartition(0, q, myId, numThread, lss);

      for (int k = lss.i_start; k < lss.i_stop; k++) {
        myHisto[(int)(myId*R) + BITS(lKey[k],bitOff,m)]++;
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, R, myId, numThread, lss);

      long last;
      for (int k = lss.i_start; k < lss.i_stop; k++) {
        last = psHisto[k] = myHisto[k];
        for (int j = 1; j < numThread; j++) {
          long temp = psHisto[(int)(j*R + k)] = last + myHisto[(int) (j*R + k)];
          last = temp;
        }
      }

      Barrier.enterBarrier();

      long offset = 0;

      for (int k = 0; k < (int)R; k++) {
        myHisto[(int)(myId*R) +k] = (psHisto[(int) ((myId*R) + k)] - myHisto[(int) ((myId*R) +k])) + offset;
        offset += psHisto[(int)((numThread -1) * R) + k];
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, q, myId, numThread, lss);

      for (int k = lss.i_start; k < lss.i_stop; k++) {
        int j = BITS(lKey[k], bitOff, m);
        lSorted[myHisto[(int)(myId*R) +j]] = lKey[k];
        auxSorted[myHisto[(int)(myId*R) +j]] = auxKey[k];
        myHisto[(int)(myId*R) +j]++;
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        psHisto = null;
        myHisto = null;
      }
    }


  /* =============================================================================
   * all_radixsort_node_s3
   *
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_radixsort_node_s3 (long q,
        long[] lKeys,
        long[] lSorted)
    {

      long[] lTemp = null;

      if (myId == 0) {
        lTemp = new long[(int) q];
        global_lTemp = lTemp;
      }

      Barrier.enterBarrier();

      lTemp = global_lTemp;

      all_countsort_node(q, lKeys,   lSorted, (1<<11),  0, 11);
      all_countsort_node(q, lSorted, lTemp,   (1<<11), 11, 11);
      all_countsort_node(q, lTemp,   lSorted, (1<<10), 22, 10);

      Barrier.enterBarrier();

      if (myId == 0) {
        lTemp = null
      }
    }


  /* =============================================================================
   * all_radixsort_node_s2
   *
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_radixsort_node_s2 (long q,
        long[] lKeys,
        long[] lSorted)
    {

      long[] lTemp = null;

      if (myId == 0) {
        lTemp = new long[(int) q];
        global_lTemp = lTemp;
      }

      Barrier.enterBarrier();

      lTemp = global_lTemp;

      all_countsort_node(q, lKeys, lTemp,   (1<<16),  0, 16);
      all_countsort_node(q, lTemp, lSorted, (1<<16), 16, 16);

      Barrier.enterBarrier();

      if (myId == 0) {
        lTemp = null;
      }
    }


  /* =============================================================================
   * all_radixsort_node_aux_s3_seq
   *
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  void
    all_radixsort_node_aux_s3_seq (long q,
        long[] lKeys,
        long[] lSorted,
        long[] auxKey,
        long[] auxSorted)
    {
      long[] lTemp  = null;
      long[] lTemp2 = null;

      lTemp = new long[(int) q];
      lTemp2 = new long[(int) q];

      all_countsort_node_aux_seq(q, lKeys, lSorted, auxKey, auxSorted, (1<<11),  0, 11);
      all_countsort_node_aux_seq(q, lSorted, lTemp, auxSorted, lTemp2, (1<<11), 11, 11);
      all_countsort_node_aux_seq(q, lTemp, lSorted, lTemp2, auxSorted, (1<<10), 22, 10);

      lTemp = null;
      lTemp2 = null;
    }


  /* =============================================================================
   * all_radixsort_node_aux_s3
   *
   * q (elems/proc) must be a multiple of NODES
   * =============================================================================
   */
  public static void
    all_radixsort_node_aux_s3 (long q,
        long[] lKeys,
        long[] lSorted,
        long[] auxKey,
        long[] auxSorted)
    {
      long[] lTemp  = null;
      long[] lTemp2 = null;

      if (myId == 0) {
        lTemp = new long[(int) q];
        global_lTemp = lTemp;
        lTemp2 = new long[(int) q];
        global_lTemp2 = lTemp2;
      }

      Barrier.enterBarrier();

      lTemp  = global_lTemp;
      lTemp2 = global_lTemp2;

      all_countsort_node_aux(q, lKeys, lSorted, auxKey, auxSorted, (1<<11),  0, 11);
      all_countsort_node_aux(q, lSorted, lTemp, auxSorted, lTemp2, (1<<11), 11, 11);
      all_countsort_node_aux(q, lTemp, lSorted, lTemp2, auxSorted, (1<<10), 22, 10);

      Barrier.enterBarrier();

      if(myId = 0) {
        lTemp = null;
        lTemp2 = null;
      }
    }
}

/* =============================================================================
 *
 * End of alg_radix_smp.java
 *
 * =============================================================================
 */

