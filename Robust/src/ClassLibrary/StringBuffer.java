public class StringBuffer {
    char value[];
    int count;
    int offset;
    //    private static final int DEFAULTSIZE=16;

    public StringBuffer(String str) {
	value=new char[str.value+16];//16 is DEFAULTSIZE
	count=str.count;
	offset=0;
	for(int i=0;i<count;i++)
	    value[i]=str.value[i+str.offset];
    }

    public int length() {
	return count;
    }

    public int capacity() {
	return value.length;
    }

    public char charAt(int x) {
	return value[x+offset];
    }

    public void append(String s) {
	if ((s.count+count+offset)>value.length) {
	    // Need to allocate
	    char newvalue[]=new char[s.count+count+16]; //16 is DEFAULTSIZE
	    for(int i=0;i<count;i++)
		newvalue[i]=value[i+offset];
	    for(int i=0;i<s.count;i++)
		newvalue[i+count]=s.value[i+s.offset];
	    value=newvalue;
	    count+=s.count;
	    offset=0;
	} else {
	    for(int i=0;i<s.count;i++) {
		value[i+count+offset]=s.value[i+s.offset];
	    }
	    count+=s.count;
	}
    }

    public String toString() {
	return new String(this);
    }
}
