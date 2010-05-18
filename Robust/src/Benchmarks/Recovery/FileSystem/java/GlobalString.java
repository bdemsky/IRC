public class GlobalString {
  char value[];
  int count;
  int offset;
	private int cachedHashcode;

  public GlobalString() {
  }

  public GlobalString(String str) {
		 {
			value =  new char[str.count];
			for(int i =0; i< str.count;i++) {
				value[i] = str.value[i+str.offset];
			}
		  count = str.count;
			offset = 0;
		}
  }

	public GlobalString(GlobalString gstr) {
		 {
			this.value = gstr.value;
			this.count = gstr.count;
			this.offset = gstr.offset;
		}
	}

	public GlobalString(GlobalStringBuffer gsb) {
		 {
			value =  new char[gsb.length()];
			count = gsb.length();
			offset = 0;
			for (int i = 0; i < count; i++) 
				value[i] = gsb.value[i];
		}
	}

  public int hashCode() {
    if (cachedHashcode!=0)
      return cachedHashcode;
    int hashcode=0;
    for(int i=0; i<count; i++)
      hashcode=hashcode*31+value[i+offset];
    cachedHashcode=hashcode;
    return hashcode;
  }


	public static char[] toLocalCharArray(GlobalString str) {
		char[] c;
		int length;

		 { 
			length = str.length();
		}

		c = new char[length];

		 {
			for (int i = 0; i < length; i++) {
				c[i] = str.value[i+str.offset];
			}
		}
		return c;
	}

	public String toLocalString() {
		return new String(toLocalCharArray(this));
	}

	public int length() {
		return count;
	}

	public int indexOf(int ch, int fromIndex) {
		for (int i = fromIndex; i < count; i++)
			if (this.charAt(i) == ch) 
				return i;
		return -1;
	}

	public int lastindexOf(int ch) {
		return this.lastindexOf(ch, count - 1);
	}

	public int lastindexOf(int ch, int fromIndex) {
		for (int i = fromIndex; i > 0; i--) 
			if (this.charAt(i) == ch) 
				return i;
		return -1;
	}

	public char charAt(int i) {
		return value[i+offset];
	}

	public int indexOf(GlobalString str) {
		return this.indexOf(str, 0);
	}

	public int indexOf(GlobalString str, int fromIndex) {
		if (fromIndex < 0) 
			fromIndex = 0;
		for (int i = fromIndex; i <= (count-str.count); i++)
			if (regionMatches(i, str, 0, str.count)) 
				return i;
		return -1;
	}	

	public boolean regionMatches(int toffset, GlobalString other, int ooffset, int len) {
		if (toffset < 0 || ooffset < 0 || (toffset+len) > count || (ooffset+len) > other.count)
			return false;

		for (int i = 0; i < len; i++) {
			if (other.value[i+other.offset+ooffset] != this.value[i+this.offset+toffset])
				return false;
		}
		return true;
	}

	public GlobalString substring(int beginIndex) {
		return substring(beginIndex, this.count);
	}

  public GlobalString subString(int beginIndex) {
    return subString(beginIndex, this.count);
  }

	public GlobalString subString(int beginIndex, int endIndex) {
		return substring(beginIndex, endIndex);
	}

	public GlobalString substring(int beginIndex, int endIndex) {
		GlobalString str;
		 {
			str =  new GlobalString();
		}
		if (beginIndex > this.count || endIndex > this.count || beginIndex > endIndex) {
			System.printString("Index error\n");
		}
		 {
			str.value = this.value;
			str.count = endIndex-beginIndex;
			str.offset = this.offset + beginIndex;
		}
		return str;	
	}

	public boolean equals(String str) {
		GlobalString gstr;

		 {
			gstr =  new GlobalString(str);
		}
		return equals(gstr);
	}

	public boolean equals(Object o) {
		if (o.getType()!= getType())
			return false;
		
		GlobalString gs = (GlobalString)o;
		if (gs.count!= count)
			return false;

		for(int i = 0; i < count; i++) {
			if (gs.value[i+gs.offset] != value[i+offset])
				return false;
		}
		return true;
	}
}
