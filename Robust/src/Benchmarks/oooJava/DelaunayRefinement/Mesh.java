public class Mesh {
  protected HashMap edge_map;

  public Mesh() {
    edge_map = new HashMap();
  }

  public HashSet getBad(EdgeGraph mesh) {
    HashSet ret = new HashSet();
    for (Iterator iterator = mesh.iterator(); iterator.hasNext();) {
      Node node = (Node) iterator.next();
      Element element = (Element) mesh.getNodeData(node);
      if (element.isBad())
        ret.add(node);
    }

    return ret;
  }

  private FileInputStream getScanner(String filename) {
    return new FileInputStream(filename);
  }

  private Tuple[] readNodes(String filename) {
    FileInputStream scanner = getScanner(filename + ".node");
    StringTokenizer st = new StringTokenizer(scanner.readLine());

    int ntups = Integer.parseInt(st.nextToken());
    Tuple tuples[] = new Tuple[ntups];
    for (int i = 0; i < ntups; i++) {
      st = new StringTokenizer(scanner.readLine());
      int index = Integer.parseInt(st.nextToken());
      double x = Double.parseDouble(st.nextToken());
      double y = Double.parseDouble(st.nextToken());
      // we don't parse the z axis
      tuples[index] = new Tuple(x, y, 0.0D);
    }
    return tuples;
  }

  private void readElements(EdgeGraph mesh, String filename, Tuple tuples[]) {
    FileInputStream scanner =getScanner(filename + ".ele");
    StringTokenizer st = new StringTokenizer(scanner.readLine());
    int nels = Integer.parseInt(st.nextToken());
    Element elements[] = new Element[nels];
    for (int i = 0; i < nels; i++) {
      st = new StringTokenizer(scanner.readLine());
      int index = Integer.parseInt(st.nextToken());
      int n1 = Integer.parseInt(st.nextToken());
      int n2 = Integer.parseInt(st.nextToken());
      int n3 = Integer.parseInt(st.nextToken());
      elements[index] = new Element(tuples[n1], tuples[n2], tuples[n3]);
      addElement(mesh, elements[index]);
    }
  }

  private void readPoly(EdgeGraph mesh, String filename, Tuple tuples[]) {
    FileInputStream scanner = getScanner(filename + ".poly");
    StringTokenizer st = new StringTokenizer(scanner.readLine());
    // discard line 1
    st = new StringTokenizer(scanner.readLine());
    int nsegs = Integer.parseInt(st.nextToken());

    Element segments[] = new Element[nsegs];
    for (int i = 0; i < nsegs; i++) {
      st = new StringTokenizer(scanner.readLine());
      int index = Integer.parseInt(st.nextToken());
      int n1 = Integer.parseInt(st.nextToken());
      int n2 = Integer.parseInt(st.nextToken());
      // don't parse z value
      segments[index] = new Element(tuples[n1], tuples[n2]);
      addElement(mesh, segments[index]);
    }

  }

  public void read(EdgeGraph mesh, String basename) {
    Tuple tuples[] = readNodes(basename);
    readElements(mesh, basename, tuples);
    readPoly(mesh, basename, tuples);
  }

  protected Node addElement(EdgeGraph mesh, Element element) {
    Node node = mesh.createNode(element);
    mesh.addNode(node);
    for (int i = 0; i < element.numEdges(); i++) {
      ElementEdge edge = element.getEdge(i);
      if (!edge_map.containsKey(edge)) {
        edge_map.put(edge, node);
      } else {
        Edge_d new_edge = mesh.createEdge(node, (Node) edge_map.get(edge), edge);
        mesh.addEdge(new_edge);
        edge_map.remove(edge);
      }
    }

    return node;
  }

  public boolean verify(EdgeGraph mesh) {
    for (Iterator iterator = mesh.iterator(); iterator.hasNext();) {
      Node node = (Node) iterator.next();
      Element element = (Element) mesh.getNodeData(node);
      if (element.getDim() == 2) {
        if (mesh.getOutNeighborsSize(node) != 1) {
          System.out.println("-> Segment " + element + " has " + mesh.getOutNeighborsSize(node) + " relation(s)");
          return false;
        }
      } else if (element.getDim() == 3) {
        if (mesh.getOutNeighborsSize(node) != 3) {
          System.out.println("-> Triangle " + element + " has " + mesh.getOutNeighborsSize(node) + " relation(s)");
          return false;
        }
      } else {
        System.out.println("-> Figures with " + element.getDim() + " edges");
        return false;
      }
    }

    Node start = mesh.getRandom();
//    Stack remaining = new Stack();
    LinkedList remaining = new LinkedList();
    HashSet found = new HashSet();
    remaining.push(start);
    while (!remaining.isEmpty()) {
      Node node = (Node) remaining.pop();
      if (!found.contains(node)) {
        found.add(node);
        Node neighbor;
        for (Iterator iterator1 = mesh.getOutNeighbors(node); iterator1.hasNext(); remaining
            .push(neighbor))
          neighbor = (Node) iterator1.next();

      }
    }
    if (found.size() != mesh.getNumNodes()) {
      System.out.println("Not all elements are reachable");
      return false;
    } else {
      return true;
    }
  }
}
