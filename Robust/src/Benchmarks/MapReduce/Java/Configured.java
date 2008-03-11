//package mapreduce;

public class Configured {
    
    Configuration conf;
    
    public Configured() {
	this.conf = null;
    }
    
    public Configured(Configuration conf) {
	this.conf = conf;
    }
    
    public Configuration getConf() {
	return this.conf;
    }
    
    public void setConf(Configuration conf) {
	this.conf = conf;
    }
}
