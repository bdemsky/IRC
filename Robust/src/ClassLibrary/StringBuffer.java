public class StringBuffer {
    char value[];
    int count;
    //    private static final int DEFAULTSIZE=16;

    public StringBuffer(String str) {
	value=new char[str.count+16];//16 is DEFAULTSIZE
	count=str.count;
	for(int i=0;i<count;i++)
	    value[i]=str.value[i+str.offset];
    }

    public StringBuffer() {
	value=new char[16];//16 is DEFAULTSIZE
	count=0;
    }

    public int length() {
	return count;
    }

    public int capacity() {
	return value.length;
    }

    public char charAt(int x) {
	return value[x];
    }

    public void append(String s) {
	if ((s.count+count)>value.length) {
	    // Need to allocate
	    char newvalue[]=new char[s.count+count+16]; //16 is DEFAULTSIZE
	    for(int i=0;i<count;i++)
		newvalue[i]=value[i];
	    for(int i=0;i<s.count;i++)
		newvalue[i+count]=s.value[i+s.offset];
	    value=newvalue;
	    count+=s.count;
	} else {
	    for(int i=0;i<s.count;i++) {
		value[i+count]=s.value[i+s.offset];
	    }
	    count+=s.count;
	}
    }

    public void append(StringBuffer s) {
	if ((s.count+count)>value.length) {
	    // Need to allocate
	    char newvalue[]=new char[s.count+count+16]; //16 is DEFAULTSIZE
	    for(int i=0;i<count;i++)
		newvalue[i]=value[i];
	    for(int i=0;i<s.count;i++)
		newvalue[i+count]=s.value[i];
	    value=newvalue;
	    count+=s.count;
	} else {
	    for(int i=0;i<s.count;i++) {
		value[i+count]=s.value[i];
	    }
	    count+=s.count;
	}
    }

    public String toString() {
	return new String(this);
    }
}
