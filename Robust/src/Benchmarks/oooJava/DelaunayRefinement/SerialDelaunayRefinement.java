/*
Lonestar Benchmark Suite for irregular applications that exhibit 
amorphous data-parallelism.

Center for Grid and Distributed Computing
The University of Texas at Austin

Copyright (C) 2007, 2008, 2009 The University of Texas at Austin

Licensed under the Eclipse Public License, Version 1.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.eclipse.org/legal/epl-v10.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

File: UndirectedEdgeGraph.java 

*/

public class SerialDelaunayRefinement {  
  public boolean isFirstRun;
  public SerialDelaunayRefinement() {
    isFirstRun = true;
  }

  public static void main(String args[]) {
    SerialDelaunayRefinement sdr = new SerialDelaunayRefinement();
    sdr.runMain(args);
  }
  
  public void runMain(String args[]) {
    long runtime = 0;
    //Numbers below are Long.Max_Value
    long lasttime = 0x7fffffffffffffffL;
    long mintime = 0x7fffffffffffffffL;
    for (long run = 0; ((run < 3) || Math.abs(lasttime - runtime) * 64 > Math.min(lasttime, runtime)) && run < 7; run++) {
      runtime = run(args);
      if (runtime < mintime) {
        mintime = runtime;
      }
      System.out.println( "Post-run garbage collection started..." );
      System.gc();
      System.out.println( "done gc" );
    }

    System.out.println("minimum runtime: " + mintime + " ms");
    System.out.println("");
  }
  

  public long run(String args[]) {
    if (isFirstRun) {
      System.out.println();
      System.out.println("Lonestar Benchmark Suite v2.1");
      System.out.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
      System.out.println("http://iss.ices.utexas.edu/lonestar/");
      System.out.println();
      System.out.println("application: Delaunay Mesh Refinement (serial version)");
      System.out.println("Refines a Delaunay triangulation mesh such that no angle");
      System.out.println("in the mesh is less than 30 degrees");
      System.out.println("http://iss.ices.utexas.edu/lonestar/delaunayrefinement.html");
      System.out.println();
    }
    if (args.length < 2) {
      System.out.println("Arguments: <input file> <num workers> [verify]");
      System.exit(-1);
    }

    EdgeGraph mesh = new UndirectedEdgeGraph();

    Mesh m = new Mesh();
    m.read(mesh, args[0]);

    int numWorkers = Integer.parseInt( args[1] );

    Stack worklist = new Stack();

    // REPLACE worklist.addAll(Mesh.getBad(mesh)); WITH...
    HashMapIterator it = m.getBad(mesh).iterator();
    while (it.hasNext()) {
      worklist.push(it.next());
    }
    
    if (isFirstRun) {
      System.out.println("configuration: " + 
                         mesh.getNumNodes() + " total triangles, " +
                         worklist.size() + " bad triangles");
      System.out.println();
    }

    System.out.println( "Post-config garbage collection started..." );
    System.gc();
    System.out.println( "done gc" );


    Node  [] nodesForBadTris = new Node  [numWorkers];
    Cavity[] cavities        = new Cavity[numWorkers];
    Cavity lastAppliedCavity = null;


    long startTime = System.currentTimeMillis();
    while (!worklist.isEmpty()) {

      // Phase 1, grab enough work from list for
      // each worker in the parent      
      for(int i=0;i<numWorkers;i++) {
        if(worklist.isEmpty()) {
          nodesForBadTris[i] = null; 
        } else {
          nodesForBadTris[i] = (Node) worklist.pop();
        }
      }
      
      // Phase 2, read mesh and compute cavities in parallel
      for(int i=0;i<numWorkers;i++) {
        
        Node nodeForBadTri = nodesForBadTris[i];        

        if (nodeForBadTri != null &&
            nodeForBadTri.inGraph
            ) {
     
          System.out.println( "computing a cavity..." );
     
          sese computeCavity {
            //takes < 1 sec 
            Cavity cavity = new Cavity(mesh);
          
            //Appears to only call getters (only possible conflict is inherent in Hashmap)
            cavity.initialize(nodeForBadTri);
          
            //Takes up 15% of computation
            //Problem:: Build is recursive upon building neighbor upon neighbor upon neighbor
            //This is could be a problem....
            //TODO check dimensions problem
            cavity.build();
          
            //Takes up a whooping 50% of computation time and changes NOTHING but cavity :D
            cavity.update();                    
          }
  
          sese storeCavity {
            cavities[i] = cavity;
          }

        } else {
          sese storeNoCavity {
            cavities[i] = null;
          }
        }
      }
      
      // Phase 3, apply cavities to mesh, if still applicable
      // this phase can proceed in parallel when a cavity's
      // start nodes are still present
      for(int i=0;i<numWorkers;i++) {

        Cavity  cavity        = cavities[i];
        boolean appliedCavity = false;
        Node    nodeForBadTri = nodesForBadTris[i];
        
        sese applyCavity {

          // go ahead with applying cavity when all of its
          // pre-nodes are still in
          if( cavity != null &&
              cavity.getPre().allNodesStillInCompleteGraph() ) {

            appliedCavity     = true;
            lastAppliedCavity = cavity;

            //remove old data
            Node node;
    
            //Takes up 8.9% of runtime
            for (Iterator iterator = cavity.getPre().getNodes().iterator();
                 iterator.hasNext();) {

              node = (Node) iterator.next();
              mesh.removeNode(node);
            }
 
            //add new data
            //Takes up 1.7% of runtime
            for (Iterator iterator1 = cavity.getPost().getNodes().iterator(); 
                 iterator1.hasNext();) {

              node = (Node) iterator1.next();
              mesh.addNode(node);
            }
 
            //Takes up 7.8% of runtime
            Edge_d edge;
            for (Iterator iterator2 = cavity.getPost().getEdges().iterator();
                 iterator2.hasNext();) {
              
              edge = (Edge_d) iterator2.next();
              mesh.addEdge(edge);
            }
          }
        }
         

        sese scheduleMoreBad {
          
          if( !appliedCavity ) {

            // if we couldn't even apply this cavity, just
            // throw it back on the worklist
            if( nodeForBadTri != null && nodeForBadTri.inGraph ) {
              worklist.push( nodeForBadTri );
            }

          } else {
            // otherwise we did apply the cavity, so repair the all-nodes set of the mesh
            //Iterator itrPreNodes = cavity.getPre().getNodes().iterator();
            //while( itrPreNodes.hasNext() ) {
            //  System.out.println( "yere" );
            //  mesh.removeNodeFromAllNodesSet( (Node)itrPreNodes.next() );
            //  System.out.println( "yeres" );
            //}
            //Iterator itrPostNodes = cavity.getPost().getNodes().iterator();
            //while( itrPostNodes.hasNext() ) {
            //  mesh.addNodeToAllNodesSet( (Node)itrPostNodes.next() );
            //}

            // and we may have introduced new bad triangles
            HashMapIterator it2 = cavity.getPost().newBad(mesh).iterator();
            while (it2.hasNext()) {
              worklist.push((Node)it2.next());
            }
          }
        } // end scheduleMoreBad
      } // end phase 3

    } // end while( !worklist.isEmpty() )

    long time = System.currentTimeMillis() - startTime;
    System.out.println("runtime: " + time + " ms");
    //TODO note how we only verify on first run...
    //TODO verify is outside timing, do it each time
    // since we MIGHT compute something different when
    // we have a bug
    if (//isFirstRun && 
        args.length > 2) {

      Node aNode = (Node)lastAppliedCavity.getPost().getNodes().iterator().next();      
      mesh.discoverAllNodes( aNode );

      verify(mesh);
    }
    isFirstRun = false;
    return time;
  }


  public void verify(EdgeGraph result) {
    //Put in cuz of static issues.
    Mesh m = new Mesh();
    if (!m.verify(result)) {
//      throw new IllegalStateException("refinement failed.");
      System.out.println("Refinement Failed.");
      System.exit(-1);
    }
    
    int size = m.getBad(result).size();
    if (size != 0) {
      System.out.println("refinement failed\nstill have "+size+" bad triangles left.\n");
      System.exit(-1);
    } else {
      System.out.println("OK");
      return;
    }
  }
}
