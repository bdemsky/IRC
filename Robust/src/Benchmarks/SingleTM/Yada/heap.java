/* =============================================================================
 *
 * heap.c
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
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

#define PARENT(i)       ((i) / 2)
#define LEFT_CHILD(i)   (2*i)
#define RIGHT_CHILD(i)  (2*(i) + 1)

public class heap {
  Object [] elements;
  int size;
  int capacity;

/* =============================================================================
 * heap_alloc
 * -- Returns NULL on failure
 * =============================================================================
 */
  public heap(int initCapacity) {
    int capacity = ((initCapacity > 0) ? (initCapacity) : (1));
    elements = new Object[capacity];
    size=0;
    this.capacity = capacity;
  }

/* =============================================================================
 * siftUp
 * =============================================================================
 */
  public void siftUp(int startIndex) {
    int index = startIndex;
    while ((index > 1)) {
      int parentIndex = PARENT(index);
      Object parentPtr = elements[parentIndex];
      Object thisPtr   = elements[index];
      if (compare(parentPtr, thisPtr) >= 0) {
	break;
      }
      Object tmpPtr = parentPtr;
      elements[parentIndex] = thisPtr;
      elements[index] = tmpPtr;
      index = parentIndex;
    }
  }

/* =============================================================================
 * heap_insert
 * -- Returns FALSE on failure
 * =============================================================================
 */
  public boolean heap_insert(Object dataPtr) {
    if ((size + 1) >= capacity) {
      int newCapacity = capacity * 2;
      Object newElements[] = new Object[newCapacity];
      this.capacity = newCapacity;
      for (int i = 0; i <= size; i++) {
	newElements[i] = elements[i];
      }
      this.elements = newElements;
    }

    size++;
    elements[size] = dataPtr;
    siftUp(size);
    
    return true;
  }


/* =============================================================================
 * heapify
 * =============================================================================
 */
  public void heapify(int startIndex) {
    int index = startIndex;

    while (true) {
      int leftIndex = LEFT_CHILD(index);
      int rightIndex = RIGHT_CHILD(index);
      int maxIndex = -1;

      if ((leftIndex <= size) &&
	  (compare(elements[leftIndex], elements[index]) > 0)) {
	maxIndex = leftIndex;
      } else {
	maxIndex = index;
      }

      if ((rightIndex <= size) &&
	  (compare(elements[rightIndex], elements[maxIndex]) > 0)) {
	maxIndex = rightIndex;
      }
      
      if (maxIndex == index) {
	break;
      } else {
	Object tmpPtr = elements[index];
	elements[index] = elements[maxIndex];
	elements[maxIndex] = tmpPtr;
	index = maxIndex;
      }
    }
  }


/* =============================================================================
 * heap_remove
 * -- Returns NULL if empty
 * =============================================================================
 */
  Object heap_remove() {
    if (size < 1) {
      return null;
    }

    Object dataPtr = elements[1];
    elements[1] = elements[size];
    size--;
    heapify(1);
    
    return dataPtr;
  }

/* =============================================================================
 * heap_isValid
 * =============================================================================
 */
  boolean heap_isValid() {
    for (int i = 1; i < size; i++) {
      if (compare(elements[i+1], elements[PARENT(i+1)]) > 0) {
	return false;
      }
    }
    return true;
  }


  private static int compare(Object aPtr, Object bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
    
    if (aElementPtr.encroachedEdgePtr!=null) {
      if (bElementPtr.encroachedEdgePtr!=null) {
        return 0; /* do not care */
      } else {
        return 1;
      }
    }
    
    if (bElementPtr.encroachedEdgePtr!=null) {
      return -1;
    }
    return 0;
  }

  public void printHeap() {
    System.out.println("[");
    for (int i = 0; i < size; i++) {
      System.out.print(elements[i+1]+" ");
    }
    System.out.println("]");
  }
}
