public class ByteArray {
    public byte[] array;
    public int offset;
    
    public ByteArray() {
    }
    
    public ByteArray(byte[] ptr) {
	    array=ptr;
    }
    
    
    public void print() {
      String myStr = new String(array);
      System.out.print("offset: " + offset);
      System.out.println(myStr);
    }

    //Create copy
    public ByteArray newIndex(int newindex) {
    	ByteArray ba=new ByteArray(array);
	    ba.offset=offset+newindex;
    	return ba;
    }
    
    public ByteArray substring(int begindex, int endindex) {
      int newlength = endindex-begindex;
      byte barray[] = new byte[newlength];
      int i = 0;
      for(i = 0; i < (newlength); i++) {
        barray[i] = array[offset+begindex+i];
      }
      ByteArray ba = new ByteArray(barray);
      return ba;
      
    }
    
    //Modify current
    public void changeIndex(int newindex) {   // USEFUL FOR IN MEMORY SUBSTRINGS, JUST CALL CHANGE INDEX TWICE TO RESET OFFSET BACK TO UNEDITED
	    offset = newindex;
    }
    
    public int compareTo(ByteArray targetArray) {
    
      if((array.length-offset) < (targetArray.array.length-targetArray.offset)) {
        return -1;
      } else if ((array.length-offset) > (targetArray.array.length-targetArray.offset)) {
        return 1;
      }
    
      int i = 0;
      for(i = 0; i < array.length; i++) {
        if(array[offset+i] < targetArray.array[targetArray.offset+i]) {
          return -1;
        } else if (array[offset+i] > targetArray.array[targetArray.offset+i]) {
          return 1;
        }
      }
      return 0;
    }
    
    public void concat(ByteArray targetArray) {
      byte newarray[] = new byte[(array.length-offset) + (targetArray.array.length-targetArray.offset)];
      int i = 0;
      for(i = 0; i < (array.length - offset); i++) {
        newarray[i] = array[offset+i];
      }
      int j = 0;
      for(j = 0; j < (targetArray.array.length - targetArray.offset); j++) {
        newarray[i+j] = targetArray.array[targetArray.offset+j];
      }
      array = newarray;
      offset = 0;
    }
}
