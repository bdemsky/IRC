public class String {
    char value[];
    int count;
    int offset;

    public String(char str[]) {
	char charstr[]=new char[str.length];
	for(int i=0;i<str.length;i++)
	    charstr[i]=str[i];
	this.value=charstr;
	this.count=str.length;
	this.offset=0;
    }

    public String(byte str[]) {
	char charstr[]=new char[str.length];
	for(int i=0;i<str.length;i++)
	    charstr[i]=(char)str[i];
	this.value=charstr;
	this.count=str.length;
	this.offset=0;
    }

    public String(String str) {
	this.value=str.value;
	this.count=str.count;
	this.offset=str.offset;
    }

    public String(StringBuffer strbuf) {
	value=new char[strbuf.length()];
	count=strbuf.length();
	offset=0;
	for(int i=0;i<count;i++)
	    value[i]=strbuf.value[i];
    }

    char[] toCharArray() {
	char str[]=new char[count];
	for(int i=0;i<count;i++)
	    str[i]=value[i+offset];
	return str;
    }

    byte[] getBytes() {
	byte str[]=new byte[count];
	for(int i=0;i<value.length;i++)
	    str[i]=(byte)value[i+offset];
	return str;
    }

    public int length() {
	return count;
    }

    public char charAt(int i) {
	return value[i+offset];
    }

    public static String valueOf(Object o) {
	return o.toString();
    }

    public static String valueOf(int x) {
	int length=0;
	int tmp;
	if (x<0)
	    tmp=-x;
	else
	    tmp=x;
	do {
	    tmp=tmp/10;
	    length=length+1;
	} while(tmp!=0);
	
	char chararray[];
	if (x<0)
	    chararray=new char[length+1];
	else
	    chararray=new char[length];
	int voffset;
	if (x<0) {
	    chararray[0]='-';
	    voffset=1;
	    x=-x;
	} else
	    voffset=0;
       	
	do {
	    chararray[--length+voffset]=(char)(x%10+'0');
	    x=x/10;
	} while (length!=0);
	return new String(chararray);
    }
}
