public class DFile {
  char [] data;
  int size;
  public DFile() {
    data=global new char[4096];
    size=data.length;
  }
  public void write(int offset, char[] towrite) {
    int length=offset+towrite.length;
    if (length>size) {
      if (length>data.length) {
	char [] ptr=global new char[length];
	for(int i=0;i<size;i++) {
	  ptr[i]=data[i];
	}
	this.data=ptr;
      }
      size=length;
    }
    int j=0;
    for(int i=offset;i<length;i++)
      data[i]=towrite[j++];
  }
  public char[] read() {
    char[] ptr=new char[size];
    for(int i=0;i<size;i++)
      ptr[i]=data[i];
    return ptr;
  }
}
