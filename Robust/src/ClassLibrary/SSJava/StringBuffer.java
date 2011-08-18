@LATTICE("V<C, V<O")
@METHODDEFAULT("O<V,V<C,C<IN,C*,THISLOC=O,RETURNLOC=O")
public class StringBuffer {
  @LOC("V")
  char value[];
  @LOC("C")
  int count;

  // private static final int DEFAULTSIZE=16;

  public StringBuffer(String str) {
    value = new char[str.count + 16]; // 16 is DEFAULTSIZE
    count = str.count;
    for (int i = 0; i < count; i++)
      value[i] = str.value[i + str.offset];
  }

  public StringBuffer() {
    value = new char[16]; // 16 is DEFAULTSIZE
    count = 0;
  }

  public StringBuffer(int i) {
    value = new char[i];
    count = 0;
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

  public StringBuffer append(@LOC("IN") char c) {
    return append(String.valueOf(c));
  }

  public StringBuffer append(@LOC("IN") String s) {
    if ((s.count + count) > value.length) {
      // Need to allocate
      @LOC("C") char newvalue[] = new char[s.count + count + 16]; // 16 is
                                                                  // DEFAULTSIZE
      for (@LOC("C") int i = 0; i < count; i++)
        newvalue[i] = value[i];
      for (@LOC("C") int i = 0; i < s.count; i++)
        newvalue[i + count] = s.value[i + s.offset];
      value = newvalue;
      count += s.count;
    } else {
      for (@LOC("C") int i = 0; i < s.count; i++) {
        value[i + count] = s.value[i + s.offset];
      }
      count += s.count;
    }
    return this;
  }

  public void ensureCapacity(int i) {
    int size = 2 * count;
    if (i > size)
      size = i;
    if (i > value.length) {
      char newvalue[] = new char[i];
      for (int ii = 0; ii < count; ii++)
        newvalue[ii] = value[ii];
      value = newvalue;
    }
  }

  public StringBuffer append(StringBuffer s) {
    if ((s.count + count) > value.length) {
      // Need to allocate
      char newvalue[] = new char[s.count + count + 16]; // 16 is DEFAULTSIZE
      for (int i = 0; i < count; i++)
        newvalue[i] = value[i];
      for (int i = 0; i < s.count; i++)
        newvalue[i + count] = s.value[i];
      value = newvalue;
      count += s.count;
    } else {
      for (int i = 0; i < s.count; i++) {
        value[i + count] = s.value[i];
      }
      count += s.count;
    }
    return this;
  }

  public int indexOf(String str) {
    return indexOf(str, 0);
  }

  public synchronized int indexOf(String str, int fromIndex) {
    String vstr = new String(value, 0, count);
    return vstr.indexOf(str, fromIndex);
  }

  public String toString() {
    return new String(this);
  }

  public synchronized StringBuffer replace(int start, int end, String str) {
    if (start < 0) {
      // FIXME
      System.printString("StringIndexOutOfBoundsException: " + start + "\n");
    }
    if (start > count) {
      // FIXME
      System.printString("StringIndexOutOfBoundsException: start > length()\n");
    }
    if (start > end) {
      // FIXME
      System.printString("StringIndexOutOfBoundsException: start > end\n");
    }
    if (end > count)
      end = count;

    if (end > count)
      end = count;
    int len = str.length();
    int newCount = count + len - (end - start);
    if (newCount > value.length)
      expandCapacity(newCount);

    System.arraycopy(value, end, value, start + len, count - end);
    str.getChars(value, start);
    count = newCount;
    return this;
  }

  void expandCapacity(int minimumCapacity) {
    int newCapacity = (value.length + 1) * 2;
    if (newCapacity < 0) {
      newCapacity = 0x7fffffff /* Integer.MAX_VALUE */;
    } else if (minimumCapacity > newCapacity) {
      newCapacity = minimumCapacity;
    }
    char newValue[] = new char[newCapacity];
    System.arraycopy(value, 0, newValue, 0, count);
    value = newValue;
  }
}
