public class Lookup {
    boolean exclusive;
    String url;
    String hostname;
    String data;
    String start;
    String end;
    Socket s;
    
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
