import java.util.*;

public final class Sets {
	
    public Sets() {
    }
	
    public static Object getAny(Collection c)
	throws NoSuchElementException {
        return c.iterator().next();
    }
}
