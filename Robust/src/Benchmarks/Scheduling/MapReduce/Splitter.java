public class Splitter {
    String filename;
    String content;
    int length;
    String[] slices;

    public Splitter(String inputfile, int splitNum, char seperator) {
	//System.printString("Top of Splitter's constructor\n");
	filename = new String("tmp");
	content = inputfile;
	//System.printString(content + "\n");
	
	this.slices = new String[splitNum];
	this.slices[0] = content;
    }

    public void split() {
	if(slices.length == 1) {
	    return;
	}
	for(int i = 1; i < this.slices.length; ++i) {
	    slices[i] = new String(content.toCharArray());
	}
    }

    public String getFilename() {
	return filename;
    }

    public String[] getSlices() {
	return this.slices;
    }
}
