public class Lookup extends Socket {
    flag query;
    flag initialstate;
    flag done;

    boolean exclusive;
    String url;
    String hostname;
    String data;
    String start;
    String end;

    public void doLookup() {
	String query="GET /"+url+" HTTP/1.1\r\nConnection: close\r\nHost:"+hostname+"\r\n\r\n";
	connect(hostname, 80);
	write(query.getBytes());
    }

    public boolean Receive() {
	byte[] buffer=new byte[1024];
	int numchars=read(buffer);
	if (numchars<=0) {
	    fix();
	    close();
	    return true;
	}
	String str=new String(buffer, 0, numchars);
	if (data==null) {
	    data=str;
	} else
	    data=data+str;
	return false;
    }
		
    public void fix() {
	int istart=data.indexOf(start);
        int iend=data.indexOf(end);
        if (exclusive)
            data=data.substring(istart+start.length(), iend);
        else
            data=data.substring(istart, iend+end.length());
	String m1="src=\"/";
	String m2="src=\'/";
	String m3="href=\"/";
	boolean cnt=true;
	while(cnt) {
	    if (data.indexOf(m1)!=-1) {
		int index=data.indexOf(m1)-1;
		data=data.substring(0,index+m1.length())+"http://"+hostname+data.substring(index+m1.length(),data.length());
	    } else if (data.indexOf(m2)!=-1) {
		int index=data.indexOf(m2)-1;
		data=data.substring(0,index+m2.length())+"http://"+hostname+data.substring(index+m2.length(),data.length());
	    } else if (data.indexOf(m3)!=-1) {
		int index=data.indexOf(m3)-1;
		data=data.substring(0,index+m3.length())+"http://"+hostname+data.substring(index+m3.length(),data.length());
	    } else cnt=false;
	}
	
    }	
	

}
