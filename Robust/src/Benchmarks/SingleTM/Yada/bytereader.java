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
    if (pos>=lastlocation)
      readnewdata();
    if (pos<lastlocation&&buffer[pos]=='#')
      jumptonextline();
  }

  public int getInt() {
    getBytes();
    int value=0;
    boolean negative=false;
    for(;start<end;start++) {
      if (curbuffer[start]>='0'&&curbuffer[start]<='9')
	value=value*10+(curbuffer[start]-'0');
      else if (curbuffer[start]=='-')
	negative=true;
    }
    if (negative)
      value=-value;
    return value;
  }

  public double getDouble() {
    getBytes();
    boolean negative=false;
    double value=0;
    for(;start<end;start++) {
      if (curbuffer[start]>='0'&&curbuffer[start]<='9')
	value=value*((double)10.0)+((double)(curbuffer[start]-'0'));
      else if (curbuffer[start]=='-')
	negative=true;
    }
    if (negative)
      value=-value;
    return value;
  }

  private void getBytes() {
    boolean searching=true;
    while(searching) {
      for(;pos<lastlocation;pos++) {
	if (buffer[pos]=='#') {
	  jumptonextline();
	} else if (buffer[pos]!=' '&&buffer[pos]!='\n'&&buffer[pos]!='\t') {
	  searching=false;
	  break;
	}
      }
      if (searching) {
	readnewdata();
      }
    }
    start=pos;
    for(;pos<lastlocation;pos++) {
      if (buffer[pos]==' '||
	  buffer[pos]=='\n') {
	end=pos;
	pos++;
	curbuffer=buffer;
	return;
      }
    }
    curbuffer=tmp;
    for(int i=start;i<lastlocation;i++) {
      tmp[i-start]=buffer[i];
    }
    start=lastlocation-start;
    readnewdata();
    for(;pos<lastlocation;pos++) {
      if (buffer[pos]==' '||
	  buffer[pos]=='\n') {
	end=pos+start;
	start=0;
	pos++;
	return;
      }
      tmp[pos+start]=buffer[pos];
    }
  }
}