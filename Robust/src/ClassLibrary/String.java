public class String {
    char value[];
    int count;
    int offset;
    private int cachedHashcode;

    private String() {
    }

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

    public String subString(int beginIndex, int endIndex) {
	String str=new String();
	if (beginIndex>this.count||endIndex>this.count||beginIndex>endIndex) {
	    // FIXME
	}
	str.value=this.value;
	str.count=endIndex-beginIndex;
	str.offset=this.offset+beginIndex;
	return str;
    }

    public String subString(int beginIndex) {
	return this.subString(beginIndex, this.count);
    }

    public int indexOf(int ch) {
	return this.indexOf(ch, 0);
    }

    public int indexOf(int ch, int fromIndex) {
	for(int i=fromIndex;i<count;i++)
	    if (this.charAt(i)==ch)
		return i;
	return -1;
    }

    public int indexOf(String str) {
	return this.indexOf(str, 0);
    }

    public int indexOf(String str, int fromIndex) {
	if (fromIndex<0)
	    fromIndex=0;
	for(int i=fromIndex;i<=(count-str.count);i++)
	    if (regionMatches(i, str, 0, str.count))
		return i;
	return -1;
    }

    public boolean startsWith(String str) {
	return regionMatches(0, str, 0, str.count);
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
	if (toffset<0 || ooffset <0 || (toffset+len)>count || (ooffset+len)>other.count)
	    return false;
	for(int i=0;i<len;i++)
	    if (other.value[i+other.offset+ooffset]!=
		this.value[i+this.offset+toffset])
		return false;
	return true;
    }

    public char[] toCharArray() {
	char str[]=new char[count];
	for(int i=0;i<count;i++)
	    str[i]=value[i+offset];
	return str;
    }

    public byte[] getBytes() {
	byte str[]=new byte[count];
	for(int i=0;i<count;i++)
	    str[i]=(byte)value[i+offset];
	return str;
    }

    public int length() {
	return count;
    }

    public char charAt(int i) {
	return value[i+offset];
    }

    public String toString() {
	return this;
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

    public int hashCode() {
	if (cachedHashcode!=0)
	    return cachedHashcode;
	int hashcode=0;
	for(int i=0;i<count;i++)
	    hashcode=hashcode*31+value[i+offset];
	cachedHashcode=hashcode;
	return hashcode;
    }

    public boolean equals(Object o) {
	if (o.getType()!=getType())
	    return false;
	String s=(String)o;
	if (s.count!=count)
	    return false;
	for(int i=0;i<count;i++) {
	    if (s.value[i+s.offset]!=value[i+offset])
		return false;
	}
	return true;
    }
}
