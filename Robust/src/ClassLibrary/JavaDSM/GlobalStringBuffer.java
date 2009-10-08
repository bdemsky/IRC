public class GlobalStringBuffer {
  char value[];
  int count;
  //    private static final int DEFAULTSIZE=16;

  public GlobalStringBuffer(String str) {
		GlobalString gstr;

		atomic {
			gstr = global new GlobalString(str);
		}
		GlobalStringBuffer(gstr);
  }

	public GlobalStringBuffer(GlobalString str) {
		atomic {
			value = global new char[str.count+16];
			count = str.count;
			for (int i = 0; i < count; i++) 
				value[i] = str.value[i+str.offset];
		}
	}

	public GlobalStringBuffer(StringBuffer sb) {
		atomic {
			value = global new char[sb.count];
			for (int i = 0; i < sb.count; i++) 
				value[i] = sb.value[i];
			count = sb.count;
		}
	}

  public GlobalStringBuffer() {
		atomic {
			value = global new char[16];    //16 is DEFAULTSIZE
			count = 0;
		}
  }

  public int length() {
    return count;
  }

  public int capacity() {
    return value.length;
  }

  public char charAt(int x) {
    return value[x];
  }

  public GlobalStringBuffer append(char c) {
    return append(String.valueOf(c));
  }

  public GlobalStringBuffer append(String s) {
		GlobalString str;
		atomic {
			str = global new GlobalString(s);
		}
		return append(str);
  }

  public GlobalStringBuffer append(GlobalString s) {
		atomic {
			if ((s.count+count) > value.length) {
				// Need to allocate
				char newvalue[] = global new char[s.count+count+16];       //16 is DEFAULTSIZE
				for(int i = 0; i < count; i++)
					newvalue[i] = value[i];
				for(int i = 0; i < s.count; i++)
					newvalue[i+count] = s.value[i+s.offset];
				value = newvalue;
				count += s.count;
			} else {
				for(int i = 0; i < s.count; i++) {
					value[i+count] = s.value[i+s.offset];
				}
				count += s.count;
			}
		}
    return this;
  }

  public GlobalStringBuffer append(StringBuffer s) {
		GlobalStringBuffer gsb;
		atomic {
			gsb = global new GlobalStringBuffer(s);
		}
		return append(gsb);
	}


  public GlobalStringBuffer append(GlobalStringBuffer s) {
		atomic {
			if ((s.count+count) > value.length) {
				// Need to allocate
				char newvalue[] = global new char[s.count+count+16];       //16 is DEFAULTSIZE
				for(int i = 0; i < count; i++)
					newvalue[i] = value[i];
				for(int i = 0; i < s.count; i++)
					newvalue[i+count] = s.value[i];
				value = newvalue;
				count += s.count;
			} else {
				for(int i = 0; i < s.count; i++) {
					value[i+count] = s.value[i];
				}
				count += s.count;
			}
		}
    return this;
  }

  public GlobalString toGlobalString() {
    return global new GlobalString(this);
  }

	public String toLocalString() {
		GlobalString gstr = this.toGlobalString();
		return gstr.toLocalString();
	}
	
}
