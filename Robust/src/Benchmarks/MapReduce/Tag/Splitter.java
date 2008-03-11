public class Splitter {
    String filename;
    String content;
    int length;
    int[] splits;
    String[] slices;

    public Splitter(String path, int splitNum, char seperator) {
	//System.printString("Top of Splitter's constructor\n");
	filename = path;
	FileInputStream iStream = new FileInputStream(filename);
	byte[] b = new byte[1024 * 10];
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

	    this.slices = new String[splits.length + 1];
	    for(int i = 0; i < this.slices.length; ++i) {
		this.slices[i] = null;
	    }
	}
    }

    public void split() {
	if(slices.length == 1) {
	    return;
	}
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
    }

    public String getFilename() {
	return filename;
    }

    public String[] getSlices() {
	return this.slices;
    }
}
