/* Number.java =- abstract superclass of numeric objects
   Copyright (C) 1998, 2001, 2002, 2005  Free Software Foundation, Inc.

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

//package java.lang;

//import java.io.Serializable;

/**
 * Number is a generic superclass of all the numeric classes, including the
 * wrapper classes {@link Byte}, {@link Short}, {@link Integer}, {@link Long},
 * {@link Float}, and {@link Double}. Also worth mentioning are the classes in
 * {@link java.math}.
 * 
 * It provides ways to convert numeric objects to any primitive.
 * 
 * @author Paul Fisher
 * @author John Keiser
 * @author Warren Levy
 * @author Eric Blake (ebb9@email.byu.edu)
 * @since 1.0
 * @status updated to 1.4
 */
public/* abstract */class Number // implements Serializable
{
  /**
   * Compatible with JDK 1.1+.
   */
  // private static final long serialVersionUID = -8742448824652078965L;

  /**
   * Table for calculating digits, used in Character, Long, and Integer.
   */
  /* static */final char[] digits;

  /**
   * The basic constructor (often called implicitly).
   */
  public Number() {
    digits = new char[36];
    digits[0] = '0';
    digits[1] = '1';
    digits[2] = '2';
    digits[3] = '3';
    digits[4] = '4';
    digits[5] = '5';
    digits[6] = '6';
    digits[7] = '7';
    digits[8] = '8';
    digits[9] = '9';
    digits[10] = 'a';
    digits[11] = 'b';
    digits[12] = 'c';
    digits[13] = 'd';
    digits[14] = 'e';
    digits[15] = 'f';
    digits[16] = 'g';
    digits[17] = 'h';
    digits[18] = 'i';
    digits[19] = 'j';
    digits[20] = 'k';
    digits[21] = 'l';
    digits[22] = 'm';
    digits[23] = 'n';
    digits[24] = 'o';
    digits[25] = 'p';
    digits[26] = 'q';
    digits[27] = 'r';
    digits[28] = 's';
    digits[29] = 't';
    digits[30] = 'u';
    digits[31] = 'v';
    digits[32] = 'w';
    digits[33] = 'x';
    digits[34] = 'y';
    digits[35] = 'z';
  }

  /**
   * Return the value of this <code>Number</code> as an <code>int</code>.
   * 
   * @return the int value
   */
  public/* abstract */int intValue() {
  }

  /**
   * Return the value of this <code>Number</code> as a <code>long</code>.
   * 
   * @return the long value
   */
  public/* abstract */long longValue() {
  }

  /**
   * Return the value of this <code>Number</code> as a <code>float</code>.
   * 
   * @return the float value
   */
  public/* abstract */float floatValue() {
  }

  /**
   * Return the value of this <code>Number</code> as a <code>float</code>.
   * 
   * @return the double value
   */
  public/* abstract */double doubleValue() {
  }

  /**
   * Return the value of this <code>Number</code> as a <code>byte</code>.
   * 
   * @return the byte value
   * @since 1.1
   */
  public byte byteValue() {
    return (byte) intValue();
  }

  /**
   * Return the value of this <code>Number</code> as a <code>short</code>.
   * 
   * @return the short value
   * @since 1.1
   */
  public short shortValue() {
    return (short) intValue();
  }
}
