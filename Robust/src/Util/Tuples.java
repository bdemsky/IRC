import java.util.ArrayList;

public interface Tuples {
    int size();
    int hashCode();
    void remove(int i);
    ArrayList getList();
	Object get(int i);
	String toString();
}