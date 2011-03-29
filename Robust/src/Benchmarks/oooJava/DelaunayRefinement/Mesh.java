import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Mesh {
	
    public Mesh() {
    }
	
    public static HashSet getBad(EdgeGraph mesh) {
        HashSet ret = new HashSet();
        for(Iterator iterator = mesh.iterator(); iterator.hasNext();) {
            Node node = (Node)iterator.next();
            Element element = (Element)mesh.getNodeData(node);
            if(element.isBad())
                ret.add(node);
        }
		
        return ret;
    }
	
    private static Scanner getScanner(String filename)
	throws Exception {
        try {
            return new Scanner(new GZIPInputStream(new FileInputStream((new StringBuilder(String.valueOf(filename))).append(".gz").toString())));
        }
        catch(FileNotFoundException _) {
            return new Scanner(new FileInputStream(filename));
        }
    }
	
    private Tuple[] readNodes(String filename)
	throws Exception {
        Scanner scanner = getScanner((new StringBuilder(String.valueOf(filename))).append(".node").toString());
        int ntups = scanner.nextInt();
        scanner.nextInt();
        scanner.nextInt();
        scanner.nextInt();
        Tuple tuples[] = new Tuple[ntups];
        for(int i = 0; i < ntups; i++) {
            int index = scanner.nextInt();
            double x = scanner.nextDouble();
            double y = scanner.nextDouble();
            scanner.nextDouble();
            tuples[index] = new Tuple(x, y, 0.0D);
        }
		
        return tuples;
    }
	
    private void readElements(EdgeGraph mesh, String filename, Tuple tuples[])
	throws Exception {
        Scanner scanner = getScanner((new StringBuilder(String.valueOf(filename))).append(".ele").toString());
        int nels = scanner.nextInt();
        scanner.nextInt();
        scanner.nextInt();
        Element elements[] = new Element[nels];
        for(int i = 0; i < nels; i++) {
            int index = scanner.nextInt();
            int n1 = scanner.nextInt();
            int n2 = scanner.nextInt();
            int n3 = scanner.nextInt();
            elements[index] = new Element(tuples[n1], tuples[n2], tuples[n3]);
            addElement(mesh, elements[index]);
        }
		
    }
	
    private void readPoly(EdgeGraph mesh, String filename, Tuple tuples[])
	throws Exception {
        Scanner scanner = getScanner((new StringBuilder(String.valueOf(filename))).append(".poly").toString());
        scanner.nextInt();
        scanner.nextInt();
        scanner.nextInt();
        scanner.nextInt();
        int nsegs = scanner.nextInt();
        scanner.nextInt();
        Element segments[] = new Element[nsegs];
        for(int i = 0; i < nsegs; i++) {
            int index = scanner.nextInt();
            int n1 = scanner.nextInt();
            int n2 = scanner.nextInt();
            scanner.nextInt();
            segments[index] = new Element(tuples[n1], tuples[n2]);
            addElement(mesh, segments[index]);
        }
		
    }
	
    public void read(EdgeGraph mesh, String basename)
	throws Exception {
        Tuple tuples[] = readNodes(basename);
        readElements(mesh, basename, tuples);
        readPoly(mesh, basename, tuples);
    }
	
    protected Node addElement(EdgeGraph mesh, Element element) {
        Node node = mesh.createNode(element);
        mesh.addNode(node);
        for(int i = 0; i < element.numEdges(); i++) {
            Element.Edge edge = element.getEdge(i);
            if(!edge_map.containsKey(edge)) {
                edge_map.put(edge, node);
            } else {
                Edge new_edge = mesh.createEdge(node, (Node)edge_map.get(edge), edge);
                mesh.addEdge(new_edge);
                edge_map.remove(edge);
            }
        }
		
        return node;
    }
	
    public static boolean verify(EdgeGraph mesh) {
        for(Iterator iterator = mesh.iterator(); iterator.hasNext();) {
            Node node = (Node)iterator.next();
            Element element = (Element)mesh.getNodeData(node);
            if(element.getDim() == 2) {
                if(mesh.getOutNeighbors(node).size() != 1) {
                    System.out.println((new StringBuilder("-> Segment ")).append(element).append(" has ").append(mesh.getOutNeighbors(node).size()).append(" relation(s)").toString());
                    return false;
                }
            } else
				if(element.getDim() == 3) {
					if(mesh.getOutNeighbors(node).size() != 3) {
						System.out.println((new StringBuilder("-> Triangle ")).append(element).append(" has ").append(mesh.getOutNeighbors(node).size()).append(" relation(s)").toString());
						return false;
					}
				} else {
					System.out.println((new StringBuilder("-> Figures with ")).append(element.getDim()).append(" edges").toString());
					return false;
				}
        }
		
        Node start = mesh.getRandom();
        Stack remaining = new Stack();
        HashSet found = new HashSet();
        remaining.push(start);
        while(!remaining.isEmpty())  {
            Node node = (Node)remaining.pop();
            if(!found.contains(node)) {
                found.add(node);
                Node neighbor;
                for(Iterator iterator1 = mesh.getOutNeighbors(node).iterator(); iterator1.hasNext(); remaining.push(neighbor))
                    neighbor = (Node)iterator1.next();
				
            }
        }
        if(found.size() != mesh.getNumNodes()) {
            System.out.println("Not all elements are reachable");
            return false;
        } else {
            return true;
        }
    }
	
    protected static final HashMap edge_map = new HashMap();
	
}
