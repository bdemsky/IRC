/* =============================================================================
 *
 * list.java
 * -- Sorted singly linked list
 * -- Options: -DLIST_NO_DUPLICATES (default: allow duplicates)
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * Ported to Java June 2009 Alokika Dash
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

public class List {
  public ListNode head;
  public int size;
  public List() {

  }

  /* =============================================================================
   * compareTask
   * -- Want greatest score first
   * -- For list
   * =============================================================================
   */
  public static int
    compareTask (LearnerTask aPtr, LearnerTask bPtr)
    {
      LearnerTask aTaskPtr = (LearnerTask) aPtr;
      LearnerTask bTaskPtr = (LearnerTask) bPtr;
      float aScore = aTaskPtr.score;
      float bScore = bTaskPtr.score;

      if (aScore < bScore) {
        return 1;
      } else if (aScore > bScore) {
        return -1;
      } else {
        return (aTaskPtr.toId - bTaskPtr.toId);
      }
    }
  
  /* =============================================================================
   * list_iter_reset
   * =============================================================================
   */
  public void
    list_iter_reset (ListIter itPtr)
    {
      itPtr.ptr = head;
    }

  /* =============================================================================
   * list_iter_hasNext
   * =============================================================================
   */
  public boolean
    list_iter_hasNext (ListIter itPtr)
    {
      return itPtr.ptr.nextPtr != null;
    }

  /* =============================================================================
   * list_iter_next
   * =============================================================================
   */
  public LearnerTask
    list_iter_next (ListIter itPtr)
    {
      LearnerTask lt=itPtr.ptr.dataPtr;
      itPtr.ptr=itPtr.ptr.nextPtr;
      return lt;
    }

  /* =============================================================================
   * allocNode
   * -- Returns null on failure
   * =============================================================================
   */
  public ListNode
    allocNode (LearnerTask dataPtr)
    {
      ListNode nodePtr = new ListNode();

      nodePtr.dataPtr = dataPtr;
      nodePtr.nextPtr = null;

      return nodePtr;
    }

  /* =============================================================================
   * list_alloc
   * -- If 'compare' function return null, compare data pointer addresses
   * -- Returns null on failure
   * =============================================================================
   */
  public static List list_alloc ()
    {
      List listPtr = new List();

      listPtr.head = new ListNode();
      listPtr.head.dataPtr = null;
      listPtr.head.nextPtr = null;
      listPtr.size = 0;

      return listPtr;
    }

  /* =============================================================================
   * freeNode
   * =============================================================================
   */
  public void
    freeNode (ListNode nodePtr)
    {
      nodePtr = null;
    }


  /* =============================================================================
   * freeList
   * =============================================================================
   */
  public void
    freeList (ListNode nodePtr)
    {
      if (nodePtr != null) {
        freeList(nodePtr.nextPtr);
        freeNode(nodePtr);
      }
    }


  /* =============================================================================
   * list_free
   * =============================================================================
   */
  public void
    list_free ()
    {
      freeList(head.nextPtr);
    }

  /* =============================================================================
   * list_isEmpty
   * -- Return true if list is empty, else false
   * =============================================================================
   */
  public boolean
    list_isEmpty ()
    {
      return (head.nextPtr == null);
    }

  /* =============================================================================
   * list_getSize
   * -- Returns the size of the list
   * =============================================================================
   */
  public int
    list_getSize ()
    {
      return size;
    }


  /* =============================================================================
   * findPrevious
   * =============================================================================
   */
  public ListNode
    findPrevious (LearnerTask dataPtr)
    {
      ListNode prevPtr = head;
      ListNode nodePtr = prevPtr.nextPtr;

      for (; nodePtr != null; nodePtr = nodePtr.nextPtr) {
        if (compareTask(nodePtr.dataPtr, dataPtr) >= 0) {
          return prevPtr;
        }
        prevPtr = nodePtr;
      }

      return prevPtr;
    }

  /* =============================================================================
   * list_find
   * -- Returns null if not found, else returns pointer to data
   * =============================================================================
   */
  public LearnerTask
    list_find (LearnerTask dataPtr)
    {
      ListNode nodePtr;
      ListNode prevPtr = findPrevious(dataPtr);

      nodePtr = prevPtr.nextPtr;

      if ((nodePtr == null) ||
          (compareTask(nodePtr.dataPtr, dataPtr) != 0)) {
        return null;
      }

      return (nodePtr.dataPtr);
    }

  /* =============================================================================
   * list_insert
   * -- Return true on success, else false
   * =============================================================================
   */
  public boolean
    list_insert (LearnerTask dataPtr)
    {
      ListNode prevPtr;
      ListNode nodePtr;
      ListNode currPtr;

      prevPtr = findPrevious(dataPtr);
      currPtr = prevPtr.nextPtr;

#ifdef LIST_NO_DUPLICATES
      if ((currPtr != null) &&
          compareTask(currPtr.dataPtr, dataPtr) == 0) {
        return false;
      }
#endif

      nodePtr = allocNode(dataPtr);

      nodePtr.nextPtr = currPtr;
      prevPtr.nextPtr = nodePtr;
      size++;

      return true;
    }

  /* =============================================================================
   * list_remove
   * -- Returns true if successful, else false
   * =============================================================================
   */
  public boolean
    list_remove (LearnerTask dataPtr)
    {
      ListNode prevPtr;
      ListNode nodePtr;

      prevPtr = findPrevious(dataPtr);

      nodePtr = prevPtr.nextPtr;
      if ((nodePtr != null) &&
          (compareTask(nodePtr.dataPtr, dataPtr) == 0))
      {
        prevPtr.nextPtr = nodePtr.nextPtr;
        nodePtr.nextPtr = null;
        freeNode(nodePtr);
        size--;

        return true;
      }

      return false;
    }

  /* =============================================================================
   * list_clear
   * -- Removes all elements
   * =============================================================================
   */
  public void
    list_clear ()
    {
      freeList(head.nextPtr);
      head.nextPtr = null;
      size = 0;
    }
}

/* =============================================================================
 *
 * End of list.java
 *
 * =============================================================================
 */
