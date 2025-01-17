public class Element {
  private final boolean bObtuse;
  private final boolean bBad;
  private final Tuple obtuse;
  private final Tuple coords[];
  private final ElementEdge edges[];
  private final int dim;
  private final Tuple center;
  private final double radius_squared;
  private final double MINANGLE;
  
  public Element(Tuple a, Tuple b, Tuple c) {
    MINANGLE = 30D;
    dim = 3;
    coords = new Tuple[3];
    coords[0] = a;
    coords[1] = b;
    coords[2] = c;
    if (b.lessThan(a) || c.lessThan(a))
      if (b.lessThan(c)) {
        coords[0] = b;
        coords[1] = c;
        coords[2] = a;
      } else {
        coords[0] = c;
        coords[1] = a;
        coords[2] = b;
      }
    edges = new ElementEdge[3];
    edges[0] = new ElementEdge(coords[0], coords[1]);
    edges[1] = new ElementEdge(coords[1], coords[2]);
    edges[2] = new ElementEdge(coords[2], coords[0]);
    boolean l_bObtuse = false;
    boolean l_bBad = false;
    Tuple l_obtuse = null;
    for (int i = 0; i < 3; i++) {
      double angle = getAngle(i);
      if (angle > 90.099999999999994D) {
        l_bObtuse = true;
        l_obtuse = new Tuple(coords[i]);
      } else if (angle < 30D)
        l_bBad = true;
    }

    bBad = l_bBad;
    bObtuse = l_bObtuse;
    obtuse = l_obtuse;
    Tuple x = b.subtract(a);
    Tuple y = c.subtract(a);
    double xlen = a.distance(b);
    double ylen = a.distance(c);
    double cosine = x.dotp(y) / (xlen * ylen);
    double sine_sq = 1.0D - cosine * cosine;
    double plen = ylen / xlen;
    double s = plen * cosine;
    double t = plen * sine_sq;
    double wp = (plen - cosine) / (2D * t);
    double wb = 0.5D - wp * s;
    Tuple tmpval = a.scale(1.0D - wb - wp);
    tmpval = tmpval.add(b.scale(wb));
    center = tmpval.add(c.scale(wp));
    radius_squared = center.distance_squared(a);
  }

  public Element(Tuple a, Tuple b) {
    dim = 2;
    coords = new Tuple[2];
    coords[0] = a;
    coords[1] = b;
    if (b.lessThan(a)) {
      coords[0] = b;
      coords[1] = a;
    }
    edges = new ElementEdge[2];
    edges[0] = new ElementEdge(coords[0], coords[1]);
    edges[1] = new ElementEdge(coords[1], coords[0]);
    bBad = false;
    bObtuse = false;
    obtuse = null;
    center = a.add(b).scale(0.5D);
    radius_squared = center.distance_squared(a);
  }
  


  public Tuple center() {
    return center;
  }

  public boolean inCircle(Tuple p) {
    double ds = center.distance_squared(p);
    return ds <= radius_squared;
  }

  public double getAngle(int i) {
    int j = i + 1;
    if (j == dim)
      j = 0;
    int k = j + 1;
    if (k == dim)
      k = 0;
    Tuple a = coords[i];
    Tuple b = coords[j];
    Tuple c = coords[k];
    return Tuple.angle(b, a, c);
  }

  public ElementEdge getEdge(int i) {
    return edges[i];
  }

  public Tuple getPoint(int i) {
    return coords[i];
  }

  public Tuple getObtuse() {
    return obtuse;
  }

  public boolean isBad() {
    return bBad;
  }

  public int getDim() {
    return dim;
  }

  public int numEdges() {
    return (dim + dim) - 3;
  }

  public boolean isObtuse() {
    return bObtuse;
  }

  public ElementEdge getRelatedEdge(Element e) {
    int edim = e.getDim();
    ElementEdge e_edge2 = null;
    ElementEdge my_edge = edges[0];
    ElementEdge e_edge0 = e.edges[0];
    if (my_edge.equals(e_edge0))
      return my_edge;
    ElementEdge e_edge1 = e.edges[1];
    if (my_edge.equals(e_edge1))
      return my_edge;
    if (edim == 3) {
      e_edge2 = e.edges[2];
      if (my_edge.equals(e_edge2))
        return my_edge;
    }
    my_edge = edges[1];
    if (my_edge.equals(e_edge0))
      return my_edge;
    if (my_edge.equals(e_edge1))
      return my_edge;
    if (edim == 3 && my_edge.equals(e_edge2))
      return my_edge;
    if (dim == 3) {
      my_edge = edges[2];
      if (my_edge.equals(e_edge0))
        return my_edge;
      if (my_edge.equals(e_edge1))
        return my_edge;
      if (edim == 3 && my_edge.equals(e_edge2))
        return my_edge;
    }
    return null;
  }

  public Element getCopy() {
    if (dim == 3)
      return new Element(coords[0], coords[1], coords[2]);
    else
      return new Element(coords[0], coords[1]);
  }
  
  public boolean lessThan(Element e) {
    if (dim < e.getDim())
      return false;
    if (dim > e.getDim())
      return true;
    for (int i = 0; i < dim; i++) {
      if (coords[i].lessThan(e.coords[i]))
        return true;
      if (coords[i].greaterThan(e.coords[i]))
        return false;
    }

    return false;
  }

  public boolean isRelated(Element e) {
    int edim = e.getDim();
    ElementEdge e_edge2 = null;
    ElementEdge my_edge = edges[0];
    ElementEdge e_edge0 = e.edges[0];
    if (my_edge.equals(e_edge0))
      return true;
    ElementEdge e_edge1 = e.edges[1];
    if (my_edge.equals(e_edge1))
      return true;
    if (edim == 3) {
      e_edge2 = e.edges[2];
      if (my_edge.equals(e_edge2))
        return true;
    }
    my_edge = edges[1];
    if (my_edge.equals(e_edge0))
      return true;
    if (my_edge.equals(e_edge1))
      return true;
    if (edim == 3 && my_edge.equals(e_edge2))
      return true;
    if (dim == 3) {
      my_edge = edges[2];
      if (my_edge.equals(e_edge0))
        return true;
      if (my_edge.equals(e_edge1))
        return true;
      if (edim == 3 && my_edge.equals(e_edge2))
        return true;
    }
    return false;
  }

  public String toString() {
    String ret = "[";
    for (int i = 0; i < dim; i++) {
      ret += coords[i].toString();
      if (i != (dim - 1)) {
        ret += ", ";
      }
    }
    ret += "]";
    return ret;
  }
}
