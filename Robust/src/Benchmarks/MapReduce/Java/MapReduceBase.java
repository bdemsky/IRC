public class MapReduceBase {
    
    public MapReduceBase() {}
    
    public void map(String key, String value, OutputCollector output);
    
    public void reduce(String key, Vector values, OutputCollector output);
}

