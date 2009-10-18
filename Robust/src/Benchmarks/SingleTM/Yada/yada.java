/* 
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

public class yada extends Thread {
  String global_inputPrefix;
  int global_numThread;
  double global_angleConstraint;
  mesh  global_meshPtr;
  heap  global_workHeapPtr;
  int global_totalNumAdded;
  int global_numProcess;
  global_arg globalvar;

  public yada() {
    global_inputPrefix     = "";
    global_numThread       = 1;
    global_angleConstraint = 20.0;
    global_totalNumAdded = 0;
    global_numProcess    = 0;
  }

  public yada(mesh meshptr, heap heapptr, double angle, global_arg g) {
    global_meshPtr=meshptr;
    global_workHeapPtr=heapptr;
    global_angleConstraint=angle;
    globalvar=g;
  }


/* =============================================================================
 * displayUsage
 * =============================================================================
 */
  public static void displayUsage () {
    System.out.println("Usage: Yada [options]");
    System.out.println("Options:                              (defaults)");
    System.out.println("    a <FLT>   Min [a]ngle constraint  (20.0)");
    System.out.println("    i <STR>   [i]nput name prefix     (\"\")");
    System.out.println("    t <UINT>  Number of [t]hreads     (1L)");
    System.exit(1);
  }


/* =============================================================================
 * parseArgs
 * =============================================================================
 */
  public void parseArgs (String argv[]) {
    for(int index=0;index>argv.length;index++) {
      if (argv[index].equals("-a")) {
	index++;
	global_angleConstraint=Double.parseDouble(argv[index]);
      } else if (argv[index].equals("-i")) {
	index++;
	global_inputPrefix=argv[index];
      } else if (argv[index].equals("-t")) {
	index++;
	global_numThread=Integer.parseInt(argv[index]);
      } else {
	displayUsage();
	System.exit(-1);
      }
    }
}


/* =============================================================================
 * initializeWork
 * =============================================================================
 */
  public static int initializeWork (heap workHeapPtr, mesh meshPtr) {
    Random randomPtr = new Random();
    randomPtr.random_seed(0);
    meshPtr.mesh_shuffleBad(randomPtr);

    int numBad = 0;
    while (true) {
      element elementPtr = meshPtr.mesh_getBad();
      if (elementPtr==null) {
	break;
      }
      numBad++;
      boolean status = workHeapPtr.heap_insert(elementPtr);
      yada.Assert(status);
      elementPtr.element_setIsReferenced(true);
    }
    
    return numBad;
  }
  
  public static void Assert(boolean status) {
  }
/* =============================================================================
 * process
 * =============================================================================
 */
  public void process() {
    heap workHeapPtr = global_workHeapPtr;
    mesh meshPtr = global_meshPtr;
    int totalNumAdded = 0;
    int numProcess = 0;
    region regionPtr = new region();

    while (true) {
        element elementPtr;
        atomic {
	  elementPtr = (element) workHeapPtr.heap_remove();
        }
        if (elementPtr == null) {
	  break;
        }
        boolean isGarbage;
        atomic {
	  isGarbage = elementPtr.element_isGarbage();
        }

        if (isGarbage) {
            /*
             * Handle delayed deallocation
             */
            continue;
        }

        int numAdded;
        atomic {
	  regionPtr.region_clearBad();
	  numAdded = regionPtr.TMregion_refine(elementPtr, meshPtr, global_angleConstraint);
        }
        atomic {
	  elementPtr.element_setIsReferenced(false);
	  isGarbage = elementPtr.element_isGarbage();
        }

        totalNumAdded += numAdded;
	
        atomic {
	  regionPtr.region_transferBad(workHeapPtr);
        }
        numProcess++;
    }

    atomic {
      globalvar.global_totalNumAdded=globalvar.global_totalNumAdded + totalNumAdded;
      globalvar.global_numProcess=globalvar.global_numProcess + numProcess;
    }
  }

  public void run() {
    Barrier.enterBarrier();
    process();
    Barrier.enterBarrier();
  }
  
/* =============================================================================
 * main
 * =============================================================================
 */
  public static void main(String[] argv) {
    /*
     * Initialization
     */
    yada y=new yada();
    global_arg g=new global_arg();
    y.globalvar=g;
    y.parseArgs(argv);
    Barrier.setBarrier(y.global_numThread);
    y.global_meshPtr = new mesh(y.global_angleConstraint);
    System.out.println("Angle constraint = "+ y.global_angleConstraint);
    System.out.println("Reading input... ");
    int initNumElement = y.global_meshPtr.mesh_read(y.global_inputPrefix);
    System.out.println("done.");
    y.global_workHeapPtr = new heap(1);

    for(int i=1;i<y.global_numThread;i++) {
      yada ychild=new yada(y.global_meshPtr, y.global_workHeapPtr, y.global_angleConstraint, g);
      ychild.start();
    }

    int initNumBadElement = initializeWork(y.global_workHeapPtr,y.global_meshPtr);

    System.out.println("Initial number of mesh elements = "+ initNumElement);
    System.out.println("Initial number of bad elements  = "+ initNumBadElement);
    System.out.println("Starting triangulation...");

    /*
     * Run benchmark
     */

    long start=System.currentTimeMillis();
    y.run();
    long stop=System.currentTimeMillis();

    System.out.println(" done.");
    System.out.println("Elapsed time                    = "+(stop-start));

    int finalNumElement = initNumElement + y.globalvar.global_totalNumAdded;
    System.out.println("Final mesh size                 = "+ finalNumElement);
    System.out.println("Number of elements processed    = "+ y.globalvar.global_numProcess);
  }
}

/* =============================================================================
 *
 * End of ruppert.c
 *
 * =============================================================================
 */
