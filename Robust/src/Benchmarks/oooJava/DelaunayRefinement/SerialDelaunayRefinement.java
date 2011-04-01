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
      System.gc();
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
    if (args.length < 1) {
      System.out.println("Arguments: <input file> [verify]");
      System.exit(-1);
    }

    EdgeGraph mesh = new UndirectedEdgeGraph();

    Mesh m = new Mesh();
    m.read(mesh, args[0]);

    //treat LinkedList as a stack
    Stack worklist = new Stack();
    //    LinkedList worklist = new LinkedList();

    // worklist.addAll(Mesh.getBad(mesh));
    HashMapIterator it = m.getBad(mesh).iterator();
    while (it.hasNext()) {
      worklist.push(it.next());
    }
    
    if (isFirstRun) {
      System.out.println("configuration: " + mesh.getNumNodes() + " total triangles, " + worklist.size() + " bad triangles");
      System.out.println();
    }

    System.gc();

//    long id = Time.getNewTimeId();
    long startTime = System.currentTimeMillis();
    while (!worklist.isEmpty()) {
      
      Node[] bad_elements = new Node[23];
      for(int i=0;i<23;i++) {
        if(worklist.isEmpty()) {
          bad_elements[i] = null; 
        } else {
          bad_elements[i] = (Node) worklist.pop();
        }
      }
      
  //      Node bad_element = (Node) worklist.pop();
      for(int i=0;i<23;i++) {
        Node bad_element = bad_elements[i];
        if (bad_element != null && mesh.containsNode(bad_element)) {
          
          sese P {
          //takes < 1 sec 
          Cavity cavity = new Cavity(mesh);
          
          //Appears to only call getters (only possible conflict is inherent in Hashmap)
          cavity.initialize(bad_element);
          
          //Takes up 15% of computation
          //Problem:: Build is recursive upon building neighbor upon neighbor upon neighbor
          //This is could be a problem....
          //TODO check dimensions problem
          cavity.build();
          
          //Takes up a whooping 50% of computation time and changes NOTHING but cavity :D
          cavity.update();                    
          }
  
          sese S {
            //28% of runtime #Removes stuff out of our mesh
            middle(mesh, cavity);  
            
            //1.5% of runtime # adds stuff back to the work queue. 
            end(mesh, worklist, bad_element, cavity); 
          }
        }
      
      }
    }
    long time = System.currentTimeMillis() - startTime;
    System.out.println("runtime: " + time + " ms");
    //TODO note how we only verify on first run...
    if (isFirstRun && args.length > 1) {
      verify(mesh);
    }
    isFirstRun = false;
    return time;
  }

  private void end(EdgeGraph mesh, Stack worklist, Node bad_element, Cavity cavity) {
    HashMapIterator it2 = cavity.getPost().newBad(mesh).iterator();
    while (it2.hasNext()) {
      worklist.push((Node)it2.next());
    }
 
    if (mesh.containsNode(bad_element)) {
      worklist.push((Node) bad_element);
    }
  }

  private void middle(EdgeGraph mesh, Cavity cavity) {
    //remove old data
    Node node;
    
    //Takes up 8.9% of runtime
    for (Iterator iterator = cavity.getPre().getNodes().iterator(); iterator.hasNext();) {
      node = (Node) iterator.next();
      mesh.removeNode(node);
    }
 
    //add new data
    //Takes up 1.7% of runtime
    for (Iterator iterator1 = cavity.getPost().getNodes().iterator(); iterator1.hasNext();) {
      node = (Node) iterator1.next();
      mesh.addNode(node);
    }
 
    
    //Takes up 7.8% of runtime
    Edge_d edge;
    for (Iterator iterator2 = cavity.getPost().getEdges().iterator(); iterator2.hasNext();) {
      edge = (Edge_d) iterator2.next();
      mesh.addEdge(edge);
    }
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
