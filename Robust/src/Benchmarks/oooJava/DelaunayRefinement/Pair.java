
public class Pair {
	
    public Pair(Object first, Object second) {
        this.first = first;
        this.second = second;
    }
	
    public Object getFirst() {
        return first;
    }
	
    public Object getSecond() {
        return second;
    }
	
    public void setFirst(Object first) {
        this.first = first;
    }
	
    public void setSecond(Object second) {
        this.second = second;
    }
	
    public String toString() {
        return (new StringBuilder("(")).append(first).append(", ").append(second).append(")").toString();
    }
	
    private static boolean equals(Object x, Object y) {
        return x == null && y == null || x != null && x.equals(y);
    }
	
    public boolean equals(Object other) {
        return (other instanceof Pair) && equals(first, ((Pair)other).first) && equals(second, ((Pair)other).second);
    }
	
    public int hashCode() {
        if(first == null)
            return second != null ? second.hashCode() + 1 : 0;
        if(second == null)
            return first.hashCode() + 2;
        else
            return first.hashCode() * 17 + second.hashCode();
    }
	
    private Object first;
    private Object second;
}
