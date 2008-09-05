public class Splitter {
    String filename;
    String content;
    int length;
    int splitNum;
    char seperator;

    public Splitter(String path, int splitNum, char seperator) {
	//System.printString("Top of Splitter's constructor\n");
	filename = path;
	this.length = -1;
	this.splitNum = splitNum;
	this.seperator = seperator;
    }

    public String[] split() {
	int[] splits;
	String[] slices;
	
	FileInputStream iStream = new FileInputStream(filename);
	byte[] b = new byte[1024 * 1024];
	length = iStream.read(b);
	if(length < 0) {
	    System.printString("Error! Can not read from input file: " + filename + "\n");
	    System.exit(-1);
	}
	content = new String(b, 0, length);
	//System.printString(content + "\n");
	iStream.close();

	if(splitNum == 1) {
	    slices = new String[1];
	    slices[0] = content;
	    this.content = null;
	} else {
	    splits = new int[splitNum - 1];
	    int index = 0;
	    int span = length / splitNum;
	    int temp = 0;
	    for(int i = 0; i < splitNum - 1; ++i) {
		temp += span;
		if(temp > index) {
		    index = temp;
		    while((content.charAt(index) != seperator) && (index != length - 1)) {
			++index;
		    }
		}
		splits[i] = index;
	    }

	    slices = new String[splitNum];
	    int start = 0;
	    int end = 0;
	    for(int i = 0; i < splits.length; ++i) {
		end = splits[i];
		if(end < start) {
		    slices[i] = null;
		} else {
		    slices[i] = content.subString(start, end);
		}
		start = end + 1;
	    }
	    slices[slices.length - 1] = content.subString(start);
	    this.content = null;
	}
	return slices;
    }

    public String getFilename() {
	return filename;
    }
}
