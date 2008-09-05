public class Configuration {
    
    MapReduceBase mapreducer;
    
    int m;
    int r;
    char seperator;
    String inputfile;
    
    public Configuration() {
	this.mapreducer = null;
    }

    public MapReduceBase getMapReduce() {
	return this.mapreducer;
    }
    
    public void setMapReduceClass(MapReduceBase mapreducer) {
	this.mapreducer = mapreducer;
    }

    public int getM() {
        return m;
    }

    public void setM(int m) {
        this.m = m;
    }

    public int getR() {
        return r;
    }

    public void setR(int r) {
        this.r = r;
    }

    public char getSeperator() {
        return seperator;
    }

    public void setSeperator(char seperator) {
        this.seperator = seperator;
    }

    public String getInputfile() {
        return inputfile;
    }

    public void setInputfile(String inputfile) {
        this.inputfile = inputfile;
    }
}
