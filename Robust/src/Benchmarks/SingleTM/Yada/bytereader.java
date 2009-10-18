public class bytereader {
  FileInputStream fis;
  byte[] buffer;
  int lastlocation;
  int pos;
  byte[] tmp;

  public bytereader(FileInputStream fis) {
    this.fis=fis;
    this.buffer=new byte[1024];
    this.tmp=new byte[200];
    lastlocation=0;
    pos=0;
  }
  
  public void jumptonextline() {
    while(true) {
      for(;pos<lastlocation;pos++) {
	if (buffer[pos]=='\n') {
	  pos++;
	  return;
	}
      }
      if (pos==lastlocation) {
	readnewdata();
      }
    }
  }

  private void readnewdata() {
    pos=0;
    lastlocation=fis.read(buffer);
  }

  byte[] curbuffer;
  int start;
  int end;

  private void skipline() {
    if (buffer[pos]=='#')
      jumptonextline();
  }

  public int getInt() {
    getBytes();
    String str=new String(curbuffer, start, end-start);
    return Integer.parseInt(str);
  }

  public double getDouble() {
    getBytes();
    String str=new String(curbuffer, start, end-start);
    return Double.parseDouble(str);
  }

  private void getBytes() {
    start=pos;
    for(;pos<lastlocation;pos++) {
      if (buffer[pos]==' '||
	  buffer[pos]=='\n') {
	end=pos;
	pos++;
	skipline();
	curbuffer=buffer;
	return;
      }
    }
    curbuffer=tmp;
    for(int i=start;i<lastlocation;i++) {
      tmp[i-start]=buffer[i];
    }
    readnewdata();
    start=lastlocation-start;
    for(;true;pos++) {
      if (buffer[pos]==' '||
	  buffer[pos]=='\n') {
	pos++;
	skipline();
	return;
      }
      tmp[pos+start]=buffer[pos];
    }
    end=pos+start;
  }

}