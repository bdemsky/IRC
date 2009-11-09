/* =============================================================================
 *
 * Intlist.java
 * -- Sorted singly linked list
 * -- Options: -DLIST_NO_DUPLICATES (default: allow duplicates)
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * Ported to Java June 2009, Alokika Dash
 * adash@uci.edu
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

public class IntList {
  public IntListNode head;
  public int size;
  public IntList() {

  }
  /* =============================================================================
   * list_iter_reset
   * =============================================================================
   */
  public void
    list_iter_reset (IntListIter itPtr)
    {
      itPtr.ptr = head;
    }

  /* =============================================================================
   * list_iter_hasNext
   * =============================================================================
   */
  public boolean
    list_iter_hasNext (IntListIter itPtr)
    {
      return (itPtr.ptr.nextPtr != null);
    }


  /* =============================================================================
   * list_iter_next
   * =============================================================================
   */
  public int
    list_iter_next (IntListIter itPtr)
    {
      int val=itPtr.ptr.dataPtr;
      itPtr.ptr=itPtr.ptr.nextPtr;
      return val;
    }

  /* =============================================================================
   * allocNode
   * -- Returns null on failure
   * =============================================================================
   */
  public IntListNode
    allocNode (int dataPtr)
    {
      IntListNode nodePtr = new IntListNode();

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
  public static IntList list_alloc ()
    {
      IntList listPtr = new IntList();

      listPtr.head = new IntListNode();
      listPtr.head.dataPtr = 0;
      listPtr.head.nextPtr = null;
      listPtr.size = 0;

      return listPtr;
    }


  /* =============================================================================
   * freeNode
   * =============================================================================
   */
  public void
    freeNode (IntListNode nodePtr)
    {
      nodePtr = null;
    }


  /* =============================================================================
   * freeList
   * =============================================================================
   */
  public void
    freeList (IntListNode nodePtr)
    {
      if(nodePtr != null) {
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
  public IntListNode
    findPrevious (int dataPtr)
    {
      IntListNode prevPtr = head;
      IntListNode nodePtr = prevPtr.nextPtr;

      for (; nodePtr != null; nodePtr = nodePtr.nextPtr) {
        if (compareId(nodePtr.dataPtr, dataPtr) >= 0) {
          return prevPtr;
        }
        prevPtr = nodePtr;
      }

      return prevPtr;
    }

 
  /* =============================================================================
   * list_insert
   * -- Return true on success, else false
   * =============================================================================
   */
  public boolean
    list_insert (int dataPtr)
    {
      IntListNode prevPtr;
      IntListNode nodePtr;
      IntListNode currPtr;

      prevPtr = findPrevious(dataPtr);
      currPtr = prevPtr.nextPtr;

#ifdef LIST_NO_DUPLICATES
      if ((currPtr != null) &&
          compareId(currPtr.dataPtr, dataPtr) == 0) {
        return false;
      }
#endif

      nodePtr = allocNode(dataPtr);

      nodePtr.nextPtr = currPtr;
      prevPtr.nextPtr = nodePtr;
      size++;

      return true;
    }

  /* =================================
   * compareId
   * =================================
   */
  public static int compareId(int a, int b) {
    return (a - b);
  }


  /* =============================================================================
   * list_remove
   * -- Returns TRUE if successful, else FALSE
   * =============================================================================
   */
  public boolean 
    list_remove (int dataPtr)
    {
      IntListNode prevPtr;
      IntListNode nodePtr;

      prevPtr = findPrevious(dataPtr);

      nodePtr = prevPtr.nextPtr;
      if ((nodePtr != null) &&
          (compareId(nodePtr.dataPtr, dataPtr) == 0))
      {
        prevPtr.nextPtr = nodePtr.nextPtr;
        nodePtr.nextPtr = null;
        freeNode(nodePtr);
        size--;

        return true;
      }

      return false;
    }


  /*  ===========================
   *  Test IntList
   *  ==========================
   */
  /*
   public static void main(String[] args) {
     testList mylist = testList.list_alloc();
     mylist.list_insert(1);
     mylist.list_insert(2);
     mylist.list_insert(3);
     mylist.list_insert(4);
     mylist.list_insert(5);
     mylist.list_insert(6);
     mylist.list_insert(7);

     testList testmyList = mylist;
     testListNode it = testmyList.head;
     testmyList.list_iter_reset(it);
     System.out.println("mylist.head= " + mylist.head + " it= " + it);
     while(testmyList.list_iter_hasNext(it)){
       it = it.nextPtr;
       int tmp = testmyList.list_iter_next(it);
       System.out.println("tmp= " + tmp);
     }
     System.out.println("mylist.head= " + mylist.head + " it= " + it);
     System.out.println("testmyList.list_getSize()= " + testmyList.list_getSize());
   }
   */
}

/* =============================================================================
 *
 * End of Intlist.java
 *
 * =============================================================================
 */
