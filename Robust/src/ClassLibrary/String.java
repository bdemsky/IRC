public class String {
    char string[];

    public String(char str[]) {
	char charstr[]=new char[str.length];
	for(int i=0;i<str.length;i++)
	    charstr[i]=str[i];
	this.string=charstr;
    }

    public String(byte str[]) {
	char charstr[]=new char[str.length];
	for(int i=0;i<str.length;i++)
	    charstr[i]=(char)str[i];
	this.string=charstr;
    }

    char[] toCharArray() {
	char str[]=new char[string.length];
	for(int i=0;i<string.length;i++)
	    str[i]=string[i];
	return str;
    }

    byte[] getBytes() {
	byte str[]=new byte[string.length];
	for(int i=0;i<string.length;i++)
	    str[i]=(byte)string[i];
	return str;
    }

    public int length() {
	return string.length;
    }

    public char charAt(int x) {
	return string[x];
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
	int offset;
	if (x<0) {
	    chararray[0]='-';
	    offset=1;
	    x=-x;
	} else
	    offset=0;
       	
	do {
	    chararray[--length+offset]=(char)(x%10+'0');
	    x=x/10;
	} while (length!=0);
	return new String(chararray);
    }
}
