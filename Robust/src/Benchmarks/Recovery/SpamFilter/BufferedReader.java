public class BufferedReader {
    FileInputStream fr;
    byte[] buffer;
    int offset;
    int end;

    public BufferedReader(FileInputStream fr) {
	this.fr=fr;
	this.buffer=new byte[2048];
    }

    public int read() {
	if (offset<end) {
	    return buffer[offset++];
	} else {
	    readBuffer();
	    if (end<=0)
		return -1;
	    return buffer[offset++];
	}
    }

    public int read(byte[] array) {
	int off=0;
	int arraylen=array.length;
	do {
	    for(;offset<end;offset++) {
		if (off>=arraylen)
		    return off;
		array[off++]=buffer[offset];
	    }
	    readBuffer();
	    if (end==0)
		return off;
	    if (end<0)
		return end;
	} while(true);
    }

    public void readBuffer() {
	offset=0;
	end=fr.read(buffer);
    }

    public String readLine() {
	String str=null;
	do {
	    boolean foundcr=false;
	    int index=offset;
	    for(;index<end;index++) {
		if (buffer[index]=='\n'||buffer[index]==13) {
		    foundcr=true;
		    break;
		}
	    }
	    String buf=new String(buffer, offset, index-offset);
	    if (str==null)
		str=buf;
	    else
		str=str.concat(buf);
	    if (foundcr) {
		offset=index++;
		do {
		    for(;offset<end;offset++) {
			if (buffer[offset]!='\n'&&buffer[offset]!=13) {
			    return str;
			}
		    }
		    readBuffer();
		    if (end<=0)
			return str;
		} while(true);
	    } else {
		readBuffer();
		if (end<=0)
		    return null;
	    }
	} while(true);

    }

    public void close() {
	fr.close();
    }

}