//package mapreduce;

//import java.util.Vector;

public class OutputCollector {

    Vector keys;
    Vector values;

    public OutputCollector() {
	this.keys = new Vector();
	this.values = new Vector();
    }

    public void emit(String key, String value) {
	this.keys.addElement(key);
	this.values.addElement(value);
    }

    public int size() {
	return this.keys.size();
    }

    public String getKey(int i) {
	return (String)this.keys.elementAt(i);
    }

    public String getValue(int i) {
	return (String)this.values.elementAt(i);
    }
}
