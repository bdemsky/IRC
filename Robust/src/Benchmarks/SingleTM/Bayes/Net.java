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

  public static NetNode allocNode (int id) {
    NetNode nodePtr = new NetNode();

    nodePtr.parentIdListPtr = IntList.list_alloc(); 
    nodePtr.childIdListPtr = IntList.list_alloc();
    nodePtr.id = id;
    
    return nodePtr;
  }


  /* =============================================================================
   * net_alloc
   * =============================================================================
   */
  public Net(int numNode) {
    Vector_t nodeVectorPtr = new Vector_t(numNode);

    for (int i = 0; i < numNode; i++) {
      NetNode nodePtr = allocNode(i);
      boolean status = nodeVectorPtr.vector_pushBack(nodePtr);
    }
    this.nodeVectorPtr = nodeVectorPtr;
  }


  /* =============================================================================
   * net_free
   * =============================================================================
   */
  public void net_free () {
    nn=null;
    nodeVectorPtr=null;
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

      status = parentIdListPtr.list_insert(fromId);

      NetNode parentNodePtr = (NetNode)(nodeVectorPtr.vector_at(fromId));
      IntList childIdListPtr = parentNodePtr.childIdListPtr;

      status = childIdListPtr.list_insert(toId);
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

      NetNode parentNodePtr = (NetNode)(nodeVectorPtr.vector_at(fromId));
      IntList childIdListPtr = parentNodePtr.childIdListPtr;
      status = childIdListPtr.list_remove(toId);
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

      while (it.nextPtr!=null) {
        it = it.nextPtr;
        int parentId = it.dataPtr;
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

      while (it.nextPtr!=null) {
        it = it.nextPtr;
        int parentId = it.dataPtr;
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

      visitedBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      status = workQueuePtr.queue_push(fromId);

      while (!workQueuePtr.queue_isEmpty()) {
        int id = workQueuePtr.queue_pop();
        if (id == toId) {
          workQueuePtr.queue_clear();
          return true;
        }

	status = visitedBitmapPtr.bitmap_set(id);

        NetNode nodePtr = (NetNode) (nodeVectorPtr.vector_at(id));
        IntList childIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = childIdListPtr.head;

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int childId = it.dataPtr;
          if (!visitedBitmapPtr.bitmap_isSet(childId)) {
            status = workQueuePtr.queue_push(childId);
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

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int childId = it.dataPtr;
          NetNode childNodePtr = (NetNode)(nodeVectorPtr.vector_at(childId));
          if (isCycle(nodeVectorPtr, childNodePtr)) {
            return true;
          }
        }

      } else if(nodePtr.mark == NET_NODE_MARK_TEST) {
        return true;
      } else if(nodePtr.mark == NET_NODE_MARK_DONE) {
        return false;
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

      ancestorBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(id));
        IntList parentIdListPtr = nodePtr.parentIdListPtr;
        IntListNode it = parentIdListPtr.head;

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int parentId = it.dataPtr;
          status = ancestorBitmapPtr.bitmap_set(parentId);
	  status = workQueuePtr.queue_push(parentId);
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

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int grandParentId = it.dataPtr;
          if (!ancestorBitmapPtr.bitmap_isSet(grandParentId)) {
	    status = ancestorBitmapPtr.bitmap_set(grandParentId);

	    status = workQueuePtr.queue_push(grandParentId);
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

      descendantBitmapPtr.bitmap_clearAll();
      workQueuePtr.queue_clear();

      {
        NetNode nodePtr = (NetNode)(nodeVectorPtr.vector_at(id));
        IntList childIdListPtr = nodePtr.childIdListPtr;
        IntListNode it = childIdListPtr.head;

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int childId = it.dataPtr;
	  status = descendantBitmapPtr.bitmap_set(childId);

	  status = workQueuePtr.queue_push(childId);

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

        while (it.nextPtr!=null) {
          it = it.nextPtr;
          int grandChildId = it.dataPtr;
          if (!descendantBitmapPtr.bitmap_isSet(grandChildId)) {
	    status = descendantBitmapPtr.bitmap_set(grandChildId);
	    status = workQueuePtr.queue_push(grandChildId);
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
