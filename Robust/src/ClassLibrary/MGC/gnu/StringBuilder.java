/* StringBuilder.java -- Unsynchronized growable strings
   Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2008
   Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package java.lang;


/**
 * <code>StringBuilder</code> represents a changeable <code>String</code>.
 * It provides the operations required to modify the
 * <code>StringBuilder</code>, including insert, replace, delete, append,
 * and reverse. It like <code>StringBuffer</code>, but is not
 * synchronized.  It is ideal for use when it is known that the
 * object will only be used from a single thread.
 *
 * <p><code>StringBuilder</code>s are variable-length in nature, so even if
 * you initialize them to a certain size, they can still grow larger than
 * that. <em>Capacity</em> indicates the number of characters the
 * <code>StringBuilder</code> can have in it before it has to grow (growing
 * the char array is an expensive operation involving <code>new</code>).
 *
 * <p>Incidentally, compilers often implement the String operator "+"
 * by using a <code>StringBuilder</code> operation:<br>
 * <code>a + b</code><br>
 * is the same as<br>
 * <code>new StringBuilder().append(a).append(b).toString()</code>.
 *
 * <p>Classpath's StringBuilder is capable of sharing memory with Strings for
 * efficiency.  This will help when a StringBuilder is converted to a String
 * and the StringBuilder is not changed after that (quite common when
 * performing string concatenation).
 *
 * @author Paul Fisher
 * @author John Keiser
 * @author Tom Tromey
 * @author Eric Blake (ebb9@email.byu.edu)
 * @see String
 * @see StringBuffer
 *
 * @since 1.5
 */
public final class StringBuilder
{
  // Implementation note: if you change this class, you usually will
  // want to change StringBuffer as well.
  int count;

  /**
   * The buffer.  Note that this has permissions set this way so that String
   * can get the value.
   *
   * @serial the buffer
   */
  char[] value;

  /**
   * The default capacity of a buffer.
   */
  private static final int DEFAULT_CAPACITY = 16;

  /**
   * For compatability with Sun's JDK
   */
  private static final long serialVersionUID = 4383685877147921099L;

  /**
   * Create a new StringBuilder with default capacity 16.
   */
  public StringBuilder()
  {
    value = new char[this.DEFAULT_CAPACITY];
    this.count = 0;
  }

  /**
   * Create an empty <code>StringBuilder</code> with the specified initial
   * capacity.
   *
   * @param capacity the initial capacity
   * @throws NegativeArraySizeException if capacity is negative
   */
  public StringBuilder(int capacity)
  {
    value = new char[capacity];
    this.count = 0;
  }

  /**
   * Create a new <code>StringBuilder</code> with the characters in the
   * specified <code>String</code>. Initial capacity will be the size of the
   * String plus 16.
   *
   * @param str the <code>String</code> to convert
   * @throws NullPointerException if str is null
   */
  public StringBuilder(String str)
  {
    count = str.count;
    value = new char[count + DEFAULT_CAPACITY];
    str.getChars(0, count, value, 0);
  }

  /**
   * Get the length of the <code>String</code> this <code>StringBuilder</code>
   * would create. Not to be confused with the <em>capacity</em> of the
   * <code>StringBuilder</code>.
   *
   * @return the length of this <code>StringBuilder</code>
   * @see #capacity()
   * @see #setLength(int)
   */
  public int length()
  {
    return count;
  }

  /**
   * Get the total number of characters this <code>StringBuilder</code> can
   * support before it must be grown.  Not to be confused with <em>length</em>.
   *
   * @return the capacity of this <code>StringBuilder</code>
   * @see #length()
   * @see #ensureCapacity(int)
   */
  public int capacity()
  {
    return value.length;
  }
  
  void ensureCapacity_unsynchronized(int minimumCapacity)
  {
    if (minimumCapacity > value.length)
      {
        int max = value.length * 2 + 2;
        minimumCapacity = (minimumCapacity < max ? max : minimumCapacity);
        char[] nb = new char[minimumCapacity];
        System.arraycopy(value, 0, nb, 0, count);
        value = nb;
      }
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param obj the <code>Object</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(Object)
   * @see #append(String)
   */
  public StringBuilder append(Object obj)
  {
    append(String.valueOf(obj));
    return this;
  }

  /**
   * Append the <code>String</code> to this <code>StringBuilder</code>. If
   * str is null, the String "null" is appended.
   *
   * @param str the <code>String</code> to append
   * @return this <code>StringBuilder</code>
   */
  public StringBuilder append(String str)
  {
    if (str == null)
      str = "null";
    int len = str.count;
    ensureCapacity_unsynchronized(count + len);
    str.getChars(0, len, value, count);
    count += len;
    return this;
  }

  /**
   * Append the <code>StringBuilder</code> value of the argument to this
   * <code>StringBuilder</code>. This behaves the same as
   * <code>append((Object) stringBuffer)</code>, except it is more efficient.
   *
   * @param stringBuffer the <code>StringBuilder</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see #append(Object)
   */
  public StringBuilder append(StringBuffer stringBuffer)
  {
    if (stringBuffer == null)
      return append("null");
    synchronized (stringBuffer)
    {
      int len = stringBuffer.count;
      ensureCapacity(count + len);
      System.arraycopy(stringBuffer.value, 0, value, count, len);
      count += len;
    }
    return this;
  }
  
  public void ensureCapacity(int minimumCapacity)
  {
    ensureCapacity_unsynchronized(minimumCapacity);
  }

  /**
   * Append the <code>char</code> array to this <code>StringBuilder</code>.
   * This is similar (but more efficient) than
   * <code>append(new String(data))</code>, except in the case of null.
   *
   * @param data the <code>char[]</code> to append
   * @return this <code>StringBuilder</code>
   * @throws NullPointerException if <code>str</code> is <code>null</code>
   * @see #append(char[], int, int)
   */
  public StringBuilder append(char[] data)
  {
    append(data, 0, data.length);
    return this;
  }

  /**
   * Append part of the <code>char</code> array to this
   * <code>StringBuilder</code>. This is similar (but more efficient) than
   * <code>append(new String(data, offset, count))</code>, except in the case
   * of null.
   *
   * @param data the <code>char[]</code> to append
   * @param offset the start location in <code>str</code>
   * @param count the number of characters to get from <code>str</code>
   * @return this <code>StringBuilder</code>
   * @throws NullPointerException if <code>str</code> is <code>null</code>
   * @throws IndexOutOfBoundsException if offset or count is out of range
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public StringBuilder append(char[] data, int offset, int count)
  {
    if (offset < 0 || count < 0 || offset > data.length - count)
      throw new /*StringIndexOutOfBounds*/Exception("StringIndexOutOfBoundsException");
    ensureCapacity_unsynchronized(this.count + count);
    System.arraycopy(data, offset, value, this.count, count);
    this.count += count;
    return this;
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param bool the <code>boolean</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(boolean)
   */
  public StringBuilder append(boolean bool)
  {
    append(bool?"true":"false");
    return this;
  }

  /**
   * Append the <code>char</code> to this <code>StringBuilder</code>.
   *
   * @param ch the <code>char</code> to append
   * @return this <code>StringBuilder</code>
   */
  public StringBuilder append(char ch)
  {
    ensureCapacity_unsynchronized(count + 1);
    value[count++] = ch;
    return this;
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param inum the <code>int</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(int)
   */
  // This is native in libgcj, for efficiency.
  public StringBuilder append(int inum)
  {
    append(String.valueOf(inum));
    return this;
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param lnum the <code>long</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(long)
   */
  public StringBuilder append(long lnum)
  {
    append(String.valueOf(lnum));
    return this;
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param fnum the <code>float</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(float)
   */
  public StringBuilder append(float fnum)
  {
    append(String.valueOf((double)fnum));
    return this;
  }

  /**
   * Append the <code>String</code> value of the argument to this
   * <code>StringBuilder</code>. Uses <code>String.valueOf()</code> to convert
   * to <code>String</code>.
   *
   * @param dnum the <code>double</code> to convert and append
   * @return this <code>StringBuilder</code>
   * @see String#valueOf(double)
   */
  public StringBuilder append(double dnum)
  {
    append(String.valueOf(dnum));
    return this;
  }

  /**
   * Convert this <code>StringBuilder</code> to a <code>String</code>. The
   * String is composed of the characters currently in this StringBuilder. Note
   * that the result is a copy, and that future modifications to this buffer
   * do not affect the String.
   *
   * @return the characters in this StringBuilder
   */
  public String toString()
  {
    return new String(this.value, 0, this.count);
  }
  
  /**
   * Get the character at the specified index.
   *
   * @param index the index of the character to get, starting at 0
   * @return the character at the specified index
   * @throws IndexOutOfBoundsException if index is negative or &gt;= length()
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public char charAt(int index)
  {
    if (index < 0 || index >= count)
      throw new /*StringIndexOutOfBounds*/Exception("StringIndexOutOfBounds " + index);
    return value[index];
  }
  
  /**
   * Set the character at the specified index.
   *
   * @param index the index of the character to set starting at 0
   * @param ch the value to set that character to
   * @throws IndexOutOfBoundsException if index is negative or &gt;= length()
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public void setCharAt(int index, char ch)
  {
    if (index < 0 || index >= count)
      throw new /*StringIndexOutOfBounds*/Exception("StringIndexOutOfBounds " + index);
    // Call ensureCapacity to enforce copy-on-write.
    ensureCapacity_unsynchronized(count);
    value[index] = ch;
  }

  /**
   * Insert the <code>char</code> argument into this <code>StringBuffer</code>.
   *
   * @param offset the place to insert in this buffer
   * @param ch the <code>char</code> to insert
   * @return this <code>StringBuffer</code>
   * @throws StringIndexOutOfBoundsException if offset is out of bounds
   */
  public StringBuffer insert(int offset, char ch)
  {
    if (offset < 0 || offset > count)
      throw new /*StringIndexOutOfBounds*/Exception("StringIndexOutOfBounds " + offset);
    ensureCapacity_unsynchronized(count + 1);
    System.arraycopy(value, offset, value, offset + 1, count - offset);
    value[offset] = ch;
    count++;
    return this;
  }

}
