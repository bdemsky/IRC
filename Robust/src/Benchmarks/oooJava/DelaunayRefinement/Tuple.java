
public class Tuple {
	
    public Tuple(double a, double b, double c) {
        coords = new double[3];
        coords[0] = a;
        coords[1] = b;
        coords[2] = c;
        int tmphashvalue = 17;
        long tmp = Double.doubleToLongBits(coords[0]);
        tmphashvalue = 37 * tmphashvalue + (int)(tmp ^ tmp >>> 32);
        tmp = Double.doubleToLongBits(coords[1]);
        tmphashvalue = 37 * tmphashvalue + (int)(tmp ^ tmp >>> 32);
        tmp = Double.doubleToLongBits(coords[2]);
        tmphashvalue = 37 * tmphashvalue + (int)(tmp ^ tmp >>> 32);
        hashvalue = tmphashvalue;
    }
	
    public Tuple(Tuple rhs) {
        coords = new double[3];
        coords[0] = rhs.coords[0];
        coords[1] = rhs.coords[1];
        coords[2] = rhs.coords[2];
        hashvalue = rhs.hashvalue;
    }
	
    public Tuple() {
        coords = new double[3];
        hashvalue = 1;
    }
	
    public double[] getCoords() {
        return coords;
    }
	
    public boolean equals(Object obj) {
        if(!(obj instanceof Tuple))
            return false;
        Tuple t = (Tuple)obj;
        double rhs_coords[] = t.getCoords();
        return coords[0] == rhs_coords[0] && coords[1] == rhs_coords[1] && coords[2] == rhs_coords[2];
    }
	
    public int hashCode() {
        return hashvalue;
    }
	
    public boolean notEquals(Tuple rhs) {
        return !equals(rhs);
    }
	
    public boolean lessThan(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        if(coords[0] < rhs_coords[0])
            return true;
        if(coords[0] > rhs_coords[0])
            return false;
        if(coords[1] < rhs_coords[1])
            return true;
        if(coords[1] > rhs_coords[1])
            return false;
        return coords[2] < rhs_coords[2];
    }
	
    public boolean greaterThan(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        if(coords[0] > rhs_coords[0])
            return true;
        if(coords[0] < rhs_coords[0])
            return false;
        if(coords[1] > rhs_coords[1])
            return true;
        if(coords[1] < rhs_coords[1])
            return false;
        return coords[2] > rhs_coords[2];
    }
	
    public Tuple add(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        return new Tuple(coords[0] + rhs_coords[0], coords[1] + rhs_coords[1], coords[2] + rhs_coords[2]);
    }
	
    public Tuple subtract(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        return new Tuple(coords[0] - rhs_coords[0], coords[1] - rhs_coords[1], coords[2] - rhs_coords[2]);
    }
	
    public double dotp(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        return coords[0] * rhs_coords[0] + coords[1] * rhs_coords[1] + coords[2] * rhs_coords[2];
    }
	
    public Tuple scale(double s) {
        return new Tuple(s * coords[0], s * coords[1], s * coords[2]);
    }
	
    public int cmp(Tuple x) {
        if(equals(x))
            return 0;
        return !greaterThan(x) ? -1 : 1;
    }
	
    public double distance(Tuple rhs) {
        return Math.sqrt(distance_squared(rhs));
    }
	
    public double distance_squared(Tuple rhs) {
        double rhs_coords[] = rhs.getCoords();
        double x = coords[0] - rhs_coords[0];
        double y = coords[1] - rhs_coords[1];
        double z = coords[2] - rhs_coords[2];
        return x * x + y * y + z * z;
    }
	
    public double angle(Tuple a, Tuple c) {
        Tuple va = a.subtract(this);
        Tuple vc = c.subtract(this);
        double d = va.dotp(vc) / Math.sqrt(distance_squared(a) * distance_squared(c));
        return 57.295779513082323D * Math.acos(d);
    }
	
    public String toString() {
        return new String((new StringBuilder("(")).append(coords[0]).append(", ").append(coords[1]).append(", ").append(coords[2]).append(")").toString());
    }
	
    public static int cmp(Tuple a, Tuple b) {
        return a.cmp(b);
    }
	
    public static double distance(Tuple a, Tuple b) {
        return a.distance(b);
    }
	
    public static double angle(Tuple a, Tuple b, Tuple c) {
        return b.angle(a, c);
    }
	
    public double get(int i) {
        return coords[i];
    }
	
    private final double coords[];
    private final int hashvalue;
}
