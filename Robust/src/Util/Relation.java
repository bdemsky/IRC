package Util;
import java.util.*;


public class Relation {
    private Hashtable table;
    int size;

    public Relation() {
	table=new Hashtable();
	size=0;
    }

    public int size() {
	return size;
    }

    public boolean containsKey(Object o) {
	return table.containsKey(o);
    }

    public Set keySet() {
	return table.keySet();
    }

    public synchronized boolean put(Object key, Object value) {
	HashSet s;
	if (table.containsKey(key)) {
	    s=(HashSet) table.get(key);
	} else {
	    s=new HashSet();
	    table.put(key,s);
	}
	if (!s.contains(value)) {
	    size++;
	    s.add(value);
	    return true;
	} else
	    return false;
    }
    
    public Set get(Object key) {
	return (Set)table.get(key);
    }
}
