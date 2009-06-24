/* =============================================================================
 *
 * net.java
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
 * Ported to Java June 2009 by Alokika Dash
 * -- adash@uci.edu
 * University of California, Irvine
 *
 *  Copyright (c) 2009, University of California, Irvine
 * ============================================================================
 */

#define NET_NODE_MARK_INIT    0
#define NET_NODE_MARK_DONE    1
#define NET_NODE_MARK_TEST    2
#define OPERATION_INSERT      0
#define OPERATION_REMOVE      1
#define OPERATION_REVERSE     2

public class Net {
  NetNode nn;
  Vector_t nodeVectorPtr;

  public Net() {
  }

  /* =============================================================================
   * allocNode
   * =============================================================================
   */
  public static NetNode allocNode (int id)
  {
    NetNode nodePtr = new NetNode();

    if (nodePtr != null) {
      nodePtr.parentIdListPtr = IntList.list_alloc(); 
      if (nodePtr.parentIdListPtr == null) {
        nodePtr = null;
        return null;
      }
      nodePtr.childIdListPtr = IntList.list_alloc();
      if (nodePtr.childIdListPtr == null) {
        nodePtr.parentIdListPtr.list_free();
        nodePtr = null;
        return null;
      }
      nodePtr.id = id;
    }

    return nodePtr;
  }


  /* =============================================================================
   * net_alloc
   * =============================================================================
   */
  public static Net net_alloc (int numNode)
  {
    Net netPtr = new Net();
    if (netPtr != null) {
      Vector_t nodeVectorPtr = Vector_t.vector_alloc(numNode);
      if (nodeVectorPtr == null) {
        netPtr = null;
        return null;
      }

      for (int i = 0; i < numNode; i++) {
        NetNode nodePtr = allocNode(i);
        if (nodePtr == null) {
          for (int j = 0; j < i; j++) {
            nodePtr = (NetNode)(nodeVectorPtr.vector_at(j));
            nodePtr.freeNode();
          }
          nodeVectorPtr.vector_free();
          netPtr = null;
          return null;
        }

        boolean status = nodeVectorPtr.vector_pushBack(nodePtr);
      }
      netPtr.nodeVectorPtr = nodeVectorPtr;
    }

    return netPtr;
  }


  /* =============================================================================
   * net_free
   * =============================================================================
   */
  public void
    net_free ()
    {
      int numNode = nodeVectorPtr.vector_getSize();
      for (int i = 0; i < numNode; i++) {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(i));
        nodePtr.freeNode();
      }
      nodeVectorPtr.vector_free();
    }


  /* =============================================================================
   * insertEdge
   * =============================================================================
   */
  public void
    insertEdge (int fromId, int toId)
    {
      boolean status;

      NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(toId));
      IntList parentIdListPtr = childNodePtr.parentIdListPtr;

      if((status = parentIdListPtr.list_insert(fromId)) != true) {
        System.out.println("Assert failed for parentIdListPtr.list_insert in insertEdge()");
        System.exit(0);
      }

      NetNode parentNodePtr = (NetNode)(nodeVectorPtr.vector_at(fromId));
      IntList childIdListPtr = parentNodePtr.childIdListPtr;

      if((status = childIdListPtr.list_insert(toId)) != true) {
        System.out.println("Assert failed for childIdListPtr.list_insert in insertEdge()");
        System.exit(0);
      }
    }


  /* =============================================================================
   * removeEdge
   * =============================================================================
   */
  public void
    removeEdge (int fromId, int toId)
    {
      boolean status;

      NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(toId));
      IntList parentIdListPtr = childNodePtr.parentIdListPtr;
      status = parentIdListPtr.list_remove(fromId);
      if(status == false) {
        System.out.println("Assert failed: when removing from list");
        System.exit(0);
      }

      NetNode parentNodePtr = (NetNode)(nodeVectorPtr.vector_at(fromId));
      IntList childIdListPtr = parentNodePtr.childIdListPtr;
      status = childIdListPtr.list_remove(toId);
      if(status == false) {
        System.out.println("Assert failed: when removing from list");
        System.exit(0);
      }
    }

  /* =============================================================================
   * reverseEdge
   * =============================================================================
   */
  public void
    reverseEdge (int fromId, int toId)
    {
      removeEdge(fromId, toId);
      insertEdge(toId, fromId);
    }


  /* =============================================================================
   * net_applyOperation
   * =============================================================================
   */
  public void
    net_applyOperation (int op, int fromId, int toId)
    {
      if(op == OPERATION_INSERT) {
        insertEdge(fromId, toId);
      } else if(op == OPERATION_REMOVE) {
        removeEdge(fromId, toId);
      } else if(op == OPERATION_REVERSE) {
        reverseEdge(fromId, toId);
      } else {
        System.out.println("Assert failed: We shouldn't get here in net_applyOperation()");
        System.exit(0);
      }
    }


  /* =============================================================================
   * net_hasEdge
   * =============================================================================
   */
   public boolean
    net_hasEdge (int fromId, int toId)
    {
      NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(toId));

      IntList parentIdListPtr = childNodePtr.parentIdListPtr;
      IntListNode it = parentIdListPtr.head; //intialize iterator
      parentIdListPtr.list_iter_reset(it);

      while (parentIdListPtr.list_iter_hasNext(it)) {
        it = it.nextPtr;
        int parentId = parentIdListPtr.list_iter_next(it);
        if (parentId == fromId) {
          return true;
        }
      }

      return false;
    }


  /* =============================================================================
   * TMnet_hasEdge
   * =============================================================================
   */
  public boolean
    TMnet_hasEdge (int fromId, int toId)
    {
      NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(toId));

      IntList parentIdListPtr = childNodePtr.parentIdListPtr;
      IntListNode it = parentIdListPtr.head;//initialize iterator
      parentIdListPtr.list_iter_reset(it);

      while (parentIdListPtr.list_iter_hasNext(it)) {
        it = it.nextPtr;
        int parentId = parentIdListPtr.list_iter_next(it);
        if (parentId == fromId) {
          return true;
        }
      }

      return false;
    }


  /* =============================================================================
   * net_isPath
   * =============================================================================
   */
  public boolean
    net_isPath (int fromId,
        int toId,
        BitMap visitedBitmapPtr,
        Queue workQueuePtr)
    {
      boolean status;

      if(visitedBitmapPtr.numBit != nodeVectorPtr.vector_getSize()) {
        System.out.println("Assert failed for numbit == vector size in net_isPath()");
        System.exit(0);
      }

      visitedBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      if((status = workQueuePtr.queue_push(fromId)) != true) {
        System.out.println("Assert failed while inserting into Queue in net_isPath()");
        System.exit(0);
      }

      while (!workQueuePtr.queue_isEmpty()) {
        int id = workQueuePtr.queue_pop();
        if (id == toId) {
          workQueuePtr.queue_clear();
          return true;
        }

        if((status = visitedBitmapPtr.bitmap_set(id)) != true) {
          System.out.println("Assert failed while checking bitmap_set in net_isPath()");
          System.exit(0);
        }

        NetNode nodePtr = (NetNode) (nodeVectorPtr.vector_at(id));
        IntList childIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = childIdListPtr.head;
        childIdListPtr.list_iter_reset(it);

        while (childIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int childId = childIdListPtr.list_iter_next(it);
          if (!visitedBitmapPtr.bitmap_isSet(childId)) {
            status = workQueuePtr.queue_push(childId);
            if(status == false) {
              System.out.println("Assert failed: queue_push failed in net_isPath()");
              System.exit(0);
            }
          }
        }
      }

      return false;
    }


  /* =============================================================================
   * isCycle
   * =============================================================================
   */
  public boolean
    isCycle (Vector_t nodeVectorPtr, NetNode nodePtr)
    {
      if(nodePtr.mark == NET_NODE_MARK_INIT ) { 
        nodePtr.mark = NET_NODE_MARK_TEST;
        IntList childIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = childIdListPtr.head;
        childIdListPtr.list_iter_reset(it);

        while (childIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int childId = childIdListPtr.list_iter_next(it);
          NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(childId));
          if (isCycle(nodeVectorPtr, childNodePtr)) {
            return true;
          }
        }

      } else if(nodePtr.mark == NET_NODE_MARK_TEST) {
        return true;
      } else if(nodePtr.mark == NET_NODE_MARK_DONE) {
        return false;
      } else {
        System.out.println("We should have never come here in isCycle()");
        System.exit(0);
      }

      nodePtr.mark = NET_NODE_MARK_DONE;
      return false;
    }


  /* =============================================================================
   * net_isCycle
   * =============================================================================
   */
  public boolean
    net_isCycle ()
    {
      int numNode = nodeVectorPtr.vector_getSize();
      for (int n = 0; n < numNode; n++) {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(n));
        nodePtr.mark = NET_NODE_MARK_INIT;
      }

      for (int n = 0; n < numNode; n++) {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(n));
        if(nodePtr.mark == NET_NODE_MARK_INIT) {
          if(isCycle(nodeVectorPtr, nodePtr))
            return true;
        } else if(nodePtr.mark == NET_NODE_MARK_DONE) {
          /* do nothing */
          ;
        } else if(nodePtr.mark == NET_NODE_MARK_TEST) {
          /* Assert 0 */
          System.out.println("We should have never come here in net_isCycle()");
          System.exit(0);
          break;
        } else {
          /* Assert 0 */
          System.out.println("We should have never come here in net_isCycle()");
          System.exit(0);
          break;
        }
      }

      return false;
    }


  /* =============================================================================
   * net_getParentIdListPtr
   * =============================================================================
   */
  public IntList
    net_getParentIdListPtr (int id)
    {
      NetNode nodePtr = (NetNode) (nodeVectorPtr.vector_at(id));
      if(nodePtr == null) {
        System.out.println("Assert failed for nodePtr");
        System.exit(0);
      }

      return nodePtr.parentIdListPtr;
    }


  /* =============================================================================
   * net_getChildIdListPtr
   * =============================================================================
   */
  public IntList 
    net_getChildIdListPtr (int id)
    {
      NetNode nodePtr = (NetNode) (nodeVectorPtr.vector_at(id));
      if(nodePtr == null) {
        System.out.println("Assert failed for nodePtr");
        System.exit(0);
      }

      return nodePtr.childIdListPtr;
    }


  /* =============================================================================
   * net_findAncestors
   * -- Contents of bitmapPtr set to 1 if ancestor, else 0
   * -- Returns false if id is not root node (i.e., has cycle back id)
   * =============================================================================
   */
  public boolean
    net_findAncestors (int id,
        BitMap ancestorBitmapPtr,
        Queue workQueuePtr)
    {
      boolean status;

      if(ancestorBitmapPtr.numBit != nodeVectorPtr.vector_getSize()) {
        System.out.println("Assert failed for numbit == vector size in net_findAncestors()");
        System.exit(0);
      }

      ancestorBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(id));
        IntList parentIdListPtr = nodePtr.parentIdListPtr;
        IntListNode it = parentIdListPtr.head;
        parentIdListPtr.list_iter_reset(it);

        while (parentIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int parentId = parentIdListPtr.list_iter_next(it);
          status = ancestorBitmapPtr.bitmap_set(parentId);
          if(status == false) {
            System.out.println("Assert failed: for bitmap_set in net_findAncestors()");
            System.exit(0);
          }
          if((status = workQueuePtr.queue_push(parentId)) == false) {
            System.out.println("Assert failed: for workQueuePtr.queue_push in net_findAncestors()");
            System.exit(0);
          }
        }

      }

      while (!workQueuePtr.queue_isEmpty()) {
        int parentId = workQueuePtr.queue_pop();
        if (parentId == id) {
          workQueuePtr.queue_clear();
          return false;
        }
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(parentId));
        IntList grandParentIdListPtr = nodePtr.parentIdListPtr;
        IntListNode it = grandParentIdListPtr.head;
        grandParentIdListPtr.list_iter_reset(it);

        while (grandParentIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int grandParentId = grandParentIdListPtr.list_iter_next(it);
          if (!ancestorBitmapPtr.bitmap_isSet(grandParentId)) {
            if((status = ancestorBitmapPtr.bitmap_set(grandParentId)) == false) {
              System.out.println("Assert failed: for ancestorBitmapPtr bitmap_set in net_findAncestors()");
              System.exit(0);
            }

            if((status = workQueuePtr.queue_push(grandParentId)) == false) {
              System.out.println("Assert failed: for workQueuePtr.queue_push in net_findAncestors()");
              System.exit(0);
            }
          }
        }
      }

      return true;
    }


  /* =============================================================================
   * net_findDescendants
   * -- Contents of bitmapPtr set to 1 if descendants, else 0
   * -- Returns false if id is not root node (i.e., has cycle back id)
   * =============================================================================
   */
  public boolean
    net_findDescendants (int id,
        BitMap descendantBitmapPtr,
        Queue workQueuePtr)
    {
      boolean status;

      if(descendantBitmapPtr.numBit != nodeVectorPtr.vector_getSize()) {
        System.out.println("Assert failed: for descendantBitmapPtr.numbit in net_findDescendants()");
        System.exit(0);
      }

      descendantBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(id));
        IntList childIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = childIdListPtr.head;
        childIdListPtr.list_iter_reset(it);

        while (childIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int childId = childIdListPtr.list_iter_next(it);
          if((status = descendantBitmapPtr.bitmap_set(childId)) == false) {
            System.out.println("Assert failed: for descendantBitmapPtr.bitmap_set in net_findDescendants()");
            System.exit(0);
          }

          if((status = workQueuePtr.queue_push(childId)) == false) {
            System.out.println("Assert failed: for workQueuePtr.queue_push in net_findDescendants()");
            System.exit(0);
          }

        }
      }

      while (!workQueuePtr.queue_isEmpty()) {
        int childId = workQueuePtr.queue_pop();
        if (childId == id) {
          workQueuePtr.queue_clear();
          return false;
        }

        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(childId));
        IntList grandChildIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = grandChildIdListPtr.head;
        grandChildIdListPtr.list_iter_reset(it);

        while (grandChildIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int grandChildId = grandChildIdListPtr.list_iter_next(it);
          if (!descendantBitmapPtr.bitmap_isSet(grandChildId)) {
            if((status = descendantBitmapPtr.bitmap_set(grandChildId)) == false) {
              System.out.println("Assert failed: for descendantBitmapPtr.bitmap_set in net_findDescendants()");
              System.exit(0);
            }

            if((status = workQueuePtr.queue_push(grandChildId)) == false) {
              System.out.println("Assert failed: for workQueuePtr.queue_push in net_findDescendants()");
              System.exit(0);
            }
          }
        }
      }

      return true;
    }

  /* =============================================================================
   * net_generateRandomEdges
   * =============================================================================
   */
  public void
    net_generateRandomEdges (
        int maxNumParent,
        int percentParent,
        Random randomPtr)
    {
      int numNode = nodeVectorPtr.vector_getSize();
      BitMap visitedBitmapPtr = BitMap.bitmap_alloc(numNode);
      if(visitedBitmapPtr == null) {
        System.out.println("Assert failed: during bitmap_alloc in net_generateRandomEdges()");
        System.exit(0);
      }

      Queue workQueuePtr = Queue.queue_alloc(-1);

      for (int n = 0; n < numNode; n++) {
        for (int p = 0; p < maxNumParent; p++) {
          int value = (int) (randomPtr.random_generate() % 100);
          if (value < percentParent) {
            int parent = (int) (randomPtr.random_generate() % numNode);
            if ((parent != n) &&
                !net_hasEdge(parent, n) &&
                !net_isPath(n, parent, visitedBitmapPtr, workQueuePtr))
            {
              insertEdge(parent, n);
            }
          }
        }
      }

      if(net_isCycle()) {
        System.out.println("Assert failed: Cycle detected in net_generateRandomEdges()");
        System.exit(0);
      }

      visitedBitmapPtr.bitmap_free();
      workQueuePtr.queue_free();
    }
}

/* =============================================================================
 *
 * End of net.java
 *
 * =============================================================================
 */
