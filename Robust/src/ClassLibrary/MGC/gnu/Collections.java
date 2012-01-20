/* Collections.java -- Utility class with methods to operate on collections
   Copyright (C) 1998, 1999, 2000, 2001, 2002, 2004, 2005, 2006
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

/**
 * Utility class consisting of static methods that operate on, or return
 * Collections. Contains methods to sort, search, reverse, fill and shuffle
 * Collections, methods to facilitate interoperability with legacy APIs that
 * are unaware of collections, a method to return a list which consists of
 * multiple copies of one element, and methods which "wrap" collections to give
 * them extra properties, such as thread-safety and unmodifiability.
 * <p>
 *
 * All methods which take a collection throw a {@link NullPointerException} if
 * that collection is null. Algorithms which can change a collection may, but
 * are not required, to throw the {@link UnsupportedOperationException} that
 * the underlying collection would throw during an attempt at modification.
 * For example,
 * <code>Collections.singleton("").addAll(Collections.EMPTY_SET)</code>
 * does not throw a exception, even though addAll is an unsupported operation
 * on a singleton; the reason for this is that addAll did not attempt to
 * modify the set.
 *
 * @author Original author unknown
 * @author Eric Blake (ebb9@email.byu.edu)
 * @author Tom Tromey (tromey@redhat.com)
 * @author Andrew John Hughes (gnu_andrew@member.fsf.org)
 * @see Collection
 * @see Set
 * @see List
 * @see Map
 * @see Arrays
 * @since 1.2
 * @status updated to 1.5
 */
public class Collections
{
  /**
   * Sort a list according to the natural ordering of its elements. The list
   * must be modifiable, but can be of fixed size. The sort algorithm is
   * precisely that used by Arrays.sort(Object[]), which offers guaranteed
   * nlog(n) performance. This implementation dumps the list into an array,
   * sorts the array, and then iterates over the list setting each element from
   * the array.
   *
   * @param l the List to sort (<code>null</code> not permitted)
   * @throws ClassCastException if some items are not mutually comparable
   * @throws UnsupportedOperationException if the List is not modifiable
   * @throws NullPointerException if the list is <code>null</code>, or contains
   *     some element that is <code>null</code>.
   * @see Arrays#sort(Object[])
   */
  public static void sort(Vector l)
  {
    /*T[] a = (T[]) l.toArray();
    Arrays.sort(a, c);
    ListIterator<T> i = l.listIterator();
    for (int pos = 0, alen = a.length;  pos < alen;  pos++)
      {
	i.next();
	i.set(a[pos]);
      }*/
    //TODO 
    System.println("Unimplemented Collections.sort() invoked");
  }
  
  public static  void sort(List l, Comparator c)
  {
    /*Object[] a =  l.toArray();
    Arrays.sort(a, c);
    ListIterator i = l.listIterator();
    for (int pos = 0, alen = a.length;  pos < alen;  pos++)
      {
	i.next();
	i.set(a[pos]);
      }*/
//    TODO 
      System.println("Unimplemented Collections.sort(l, c) invoked");
  }

  
  static final /*<T>*/ int compare(Object/*T*/ o1, Object/*T*/ o2, Comparator/*<? super T>*/ c)
  {
    return c == null ? ((Comparable) o1).compareTo(o2) : c.compare(o1, o2);
  }
  
  public static /*<T extends Object & Comparable<? super T>>*/
  Object/*T*/ min(Collection/*<? extends T>*/ c)
  {
    return min(c, null);
  }

  /**
   * Find the minimum element in a Collection, according to a specified
   * Comparator. This implementation iterates over the Collection, so it
   * works in linear time.
   *
   * @param c the Collection to find the minimum element of
   * @param order the Comparator to order the elements by, or null for natural
   *        ordering
   * @return the minimum element of c
   * @throws NoSuchElementException if c is empty
   * @throws ClassCastException if elements in c are not mutually comparable
   * @throws NullPointerException if null is compared by natural ordering
   *        (only possible when order is null)
   */
  public static /*<T> T*/Object min(Collection/*<? extends T>*/ c,
        Comparator/*<? super T>*/ order)
  {
    Iterator/*<? extends T>*/ itr = c.iterator();
    Object/*T*/ min = itr.next(); // throws NoSuchElementExcception
    int csize = c.size();
    for (int i = 1; i < csize; i++)
      {
  Object/*T*/ o = itr.next();
  if (compare(min, o, order) > 0)
    min = o;
      }
    return min;
  }
  
  public static /*<T extends Object & Comparable<? super T>>
  T*/Object max(Collection/*<? extends T>*/ c)
  {
    return max(c, null);
  }

  /**
   * Find the maximum element in a Collection, according to a specified
   * Comparator. This implementation iterates over the Collection, so it
   * works in linear time.
   *
   * @param c the Collection to find the maximum element of
   * @param order the Comparator to order the elements by, or null for natural
   *        ordering
   * @return the maximum element of c
   * @throws NoSuchElementException if c is empty
   * @throws ClassCastException if elements in c are not mutually comparable
   * @throws NullPointerException if null is compared by natural ordering
   *        (only possible when order is null)
   */
  public static /*<T> T*/Object max(Collection/*<? extends T>*/ c,
        Comparator/*<? super T>*/ order)
  {
    Iterator/*<? extends T>*/ itr = c.iterator();
    Object/*T*/ max = itr.next(); // throws NoSuchElementException
    int csize = c.size();
    for (int i = 1; i < csize; i++)
      {
  Object/*T*/ o = itr.next();
  if (compare(max, o, order) < 0)
    max = o;
      }
    return max;
  }
} // class Collections
