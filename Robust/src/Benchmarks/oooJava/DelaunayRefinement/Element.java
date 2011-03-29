
public class Element {
    public static class Edge {
		
        public boolean equals(Object obj) {
            if(!(obj instanceof Edge))
                return false;
            Edge edge = (Edge)obj;
            return p1.equals(edge.p1) && p2.equals(edge.p2);
        }
		
        public int hashCode() {
            return hashvalue;
        }
		
        public boolean notEqual(Edge rhs) {
            return !equals(rhs);
        }
		
        public boolean lessThan(Edge rhs) {
            return p1.lessThan(rhs.p1) || p1.equals(rhs.p1) && p2.lessThan(rhs.p2);
        }
		
        public boolean greaterThan(Edge rhs) {
            return p1.greaterThan(rhs.p1) || p1.equals(rhs.p1) && p2.greaterThan(rhs.p2);
        }
		
        public Tuple getPoint(int i) {
            if(i == 0)
                return p1;
            if(i == 1) {
                return p2;
            } else {
                System.exit(-1);
                return null;
            }
        }
		
        public String toString() {
            return (new StringBuilder("<")).append(p1.toString()).append(", ").append(p2.toString()).append(">").toString();
        }
		
        private final Tuple p1;
        private final Tuple p2;
        private final int hashvalue;
		
        public Edge() {
            p1 = null;
            p2 = null;
            hashvalue = 1;
        }
		
        public Edge(Tuple a, Tuple b) {
            if(a.lessThan(b)) {
                p1 = a;
                p2 = b;
            } else {
                p1 = b;
                p2 = a;
            }
            int tmphashval = 17;
            tmphashval = 37 * tmphashval + p1.hashCode();
            tmphashval = 37 * tmphashval + p2.hashCode();
            hashvalue = tmphashval;
        }
		
        public Edge(Edge rhs) {
            p1 = rhs.p1;
            p2 = rhs.p2;
            hashvalue = rhs.hashvalue;
        }
    }
	
	
    public Element(Tuple a, Tuple b, Tuple c) {
        dim = 3;
        coords = new Tuple[3];
        coords[0] = a;
        coords[1] = b;
        coords[2] = c;
        if(b.lessThan(a) || c.lessThan(a))
            if(b.lessThan(c)) {
                coords[0] = b;
                coords[1] = c;
                coords[2] = a;
            } else {
                coords[0] = c;
                coords[1] = a;
                coords[2] = b;
            }
        edges = new Edge[3];
        edges[0] = new Edge(coords[0], coords[1]);
        edges[1] = new Edge(coords[1], coords[2]);
        edges[2] = new Edge(coords[2], coords[0]);
        boolean l_bObtuse = false;
        boolean l_bBad = false;
        Tuple l_obtuse = null;
        for(int i = 0; i < 3; i++) {
            double angle = getAngle(i);
            if(angle > 90.099999999999994D) {
                l_bObtuse = true;
                l_obtuse = new Tuple(coords[i]);
            } else
				if(angle < 30D)
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
        if(b.lessThan(a)) {
            coords[0] = b;
            coords[1] = a;
        }
        edges = new Edge[2];
        edges[0] = new Edge(coords[0], coords[1]);
        edges[1] = new Edge(coords[1], coords[0]);
        bBad = false;
        bObtuse = false;
        obtuse = null;
        center = a.add(b).scale(0.5D);
        radius_squared = center.distance_squared(a);
    }
	
    public Element getCopy() {
        if(dim == 3)
            return new Element(coords[0], coords[1], coords[2]);
        else
            return new Element(coords[0], coords[1]);
    }
	
    public boolean lessThan(Element e) {
        if(dim < e.getDim())
            return false;
        if(dim > e.getDim())
            return true;
        for(int i = 0; i < dim; i++) {
            if(coords[i].lessThan(e.coords[i]))
                return true;
            if(coords[i].greaterThan(e.coords[i]))
                return false;
        }
		
        return false;
    }
	
    public boolean isRelated(Element e) {
        int edim = e.getDim();
        Edge e_edge2 = null;
        Edge my_edge = edges[0];
        Edge e_edge0 = e.edges[0];
        if(my_edge.equals(e_edge0))
            return true;
        Edge e_edge1 = e.edges[1];
        if(my_edge.equals(e_edge1))
            return true;
        if(edim == 3) {
            e_edge2 = e.edges[2];
            if(my_edge.equals(e_edge2))
                return true;
        }
        my_edge = edges[1];
        if(my_edge.equals(e_edge0))
            return true;
        if(my_edge.equals(e_edge1))
            return true;
        if(edim == 3 && my_edge.equals(e_edge2))
            return true;
        if(dim == 3) {
            my_edge = edges[2];
            if(my_edge.equals(e_edge0))
                return true;
            if(my_edge.equals(e_edge1))
                return true;
            if(edim == 3 && my_edge.equals(e_edge2))
                return true;
        }
        return false;
    }
	
    public String toString() {
        String ret = "[";
        for(int i = 0; i < dim; i++) {
            ret = (new StringBuilder(String.valueOf(ret))).append(coords[i].toString()).toString();
            if(i != dim - 1)
                ret = (new StringBuilder(String.valueOf(ret))).append(", ").toString();
        }
		
        ret = (new StringBuilder(String.valueOf(ret))).append("]").toString();
        return ret;
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
        if(j == dim)
            j = 0;
        int k = j + 1;
        if(k == dim)
            k = 0;
        Tuple a = coords[i];
        Tuple b = coords[j];
        Tuple c = coords[k];
        return Tuple.angle(b, a, c);
    }
	
    public Edge getEdge(int i) {
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
	
    public Edge getRelatedEdge(Element e) {
        int edim = e.getDim();
        Edge e_edge2 = null;
        Edge my_edge = edges[0];
        Edge e_edge0 = e.edges[0];
        if(my_edge.equals(e_edge0))
            return my_edge;
        Edge e_edge1 = e.edges[1];
        if(my_edge.equals(e_edge1))
            return my_edge;
        if(edim == 3) {
            e_edge2 = e.edges[2];
            if(my_edge.equals(e_edge2))
                return my_edge;
        }
        my_edge = edges[1];
        if(my_edge.equals(e_edge0))
            return my_edge;
        if(my_edge.equals(e_edge1))
            return my_edge;
        if(edim == 3 && my_edge.equals(e_edge2))
            return my_edge;
        if(dim == 3) {
            my_edge = edges[2];
            if(my_edge.equals(e_edge0))
                return my_edge;
            if(my_edge.equals(e_edge1))
                return my_edge;
            if(edim == 3 && my_edge.equals(e_edge2))
                return my_edge;
        }
        return null;
    }
	
    private final boolean bObtuse;
    private final boolean bBad;
    private final Tuple obtuse;
    private final Tuple coords[];
    private final Edge edges[];
    private final int dim;
    private final Tuple center;
    private final double radius_squared;
    private static final double MINANGLE = 30D;
}
