import java.io.PrintStream;
import java.util.*;

public class SerialDelaunayrefinement {
	
    public SerialDelaunayrefinement() {
    }
	
    public static void main(String args[]) {
        long runtime = 0L;
        long lasttime = 0xffffffffL;
        long mintime = 0xffffffffL;
        for(long run = 0L; (run < 3L || Math.abs(lasttime - runtime) * 64L > Math.min(lasttime, runtime)) && run < 7L; run++) {
            System.gc();
            System.gc();
            System.gc();
            System.gc();
            System.gc();
            runtime = run(args);
            if(runtime < mintime)
                mintime = runtime;
        }
		
        System.err.println((new StringBuilder("minimum runtime: ")).append(mintime).append(" ms").toString());
        System.err.println("");
    }
	
    public static long run(String args[]) {
        if(isFirstRun) {
            System.err.println();
            System.err.println("Lonestar Benchmark Suite v2.1");
            System.err.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
            System.err.println("http://iss.ices.utexas.edu/lonestar/");
            System.err.println();
            System.err.println("application: Delaunay Mesh Refinement (serial version)");
            System.err.println("Refines a Delaunay triangulation mesh such that no angle");
            System.err.println("in the mesh is less than 30 degrees");
            System.err.println("http://iss.ices.utexas.edu/lonestar/delaunayrefinement.html");
            System.err.println();
        }
        if(args.length < 1)
            throw new Error("Arguments: <input file> [verify]");
        EdgeGraph mesh = new UndirectedEdgeGraph();
        try {
            (new Mesh()).read(mesh, args[0]);
        }
        catch(Exception e) {
            throw new Error(e);
        }
        Stack worklist = new Stack();
        worklist.addAll(Mesh.getBad(mesh));
        Cavity cavity = new Cavity(mesh);
        if(isFirstRun) {
            System.err.println((new StringBuilder("configuration: ")).append(mesh.getNumNodes()).append(" total triangles, ").append(worklist.size()).append(" bad triangles").toString());
            System.err.println();
        }
        long id = Time.getNewTimeId();
        while(!worklist.isEmpty())  {
            Node bad_element = (Node)worklist.pop();
            if(bad_element != null && mesh.containsNode(bad_element)) {
                cavity.initialize(bad_element);
                cavity.build();
                cavity.update();
                Node node;
                for(Iterator iterator = cavity.getPre().getNodes().iterator(); iterator.hasNext(); mesh.removeNode(node))
                    node = (Node)iterator.next();
				
                for(Iterator iterator1 = cavity.getPost().getNodes().iterator(); iterator1.hasNext(); mesh.addNode(node))
                    node = (Node)iterator1.next();
				
                Edge edge;
                for(Iterator iterator2 = cavity.getPost().getEdges().iterator(); iterator2.hasNext(); mesh.addEdge(edge))
                    edge = (Edge)iterator2.next();
				
                worklist.addAll(cavity.getPost().newBad(mesh));
                if(mesh.containsNode(bad_element))
                    worklist.add(bad_element);
            }
        }
        long time = Time.elapsedTime(id);
        System.err.println((new StringBuilder("runtime: ")).append(time).append(" ms").toString());
        if(isFirstRun && args.length > 1)
            verify(mesh);
        isFirstRun = false;
        return time;
    }
	
    public static void verify(Object res) {
        EdgeGraph result = (EdgeGraph)res;
        if(!Mesh.verify(result))
            throw new IllegalStateException("refinement failed.");
        int size = Mesh.getBad(result).size();
        if(size != 0) {
            throw new IllegalStateException((new StringBuilder("refinement failed\nstill have ")).append(size).append(" bad triangles left.\n").toString());
        } else {
            System.out.println("OK");
            return;
        }
    }
	
    private static boolean isFirstRun = true;
	
}
