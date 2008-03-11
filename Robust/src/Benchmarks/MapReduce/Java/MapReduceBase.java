//package mapreduce;

//import java.util.Vector;

public /*abstract*/ class MapReduceBase {
    
    public MapReduceBase() {}
    
    public /*abstract*/ void map(String key, String value, OutputCollector output);
    
    public /*abstract*/ void reduce(String key, Vector values, OutputCollector output);
}

