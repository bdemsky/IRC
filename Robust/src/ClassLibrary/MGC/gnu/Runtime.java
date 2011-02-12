/* Runtime.java -- access to the VM process
   Copyright (C) 1998, 2002, 2003, 2004, 2005 Free Software Foundation

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
 * Runtime represents the Virtual Machine.
 *
 * @author John Keiser
 * @author Eric Blake (ebb9@email.byu.edu)
 * @author Jeroen Frijters
 */
// No idea why this class isn't final, since you can't build a subclass!
public class Runtime
{
  /**
   * The one and only runtime instance.
   */
  private static final Runtime current = new Runtime();

  /**
   * Not instantiable by a user, this should only create one instance.
   */
  private Runtime()
  {
    if (current != null)
      throw new InternalError("Attempt to recreate Runtime");
  }

  /**
   * Get the current Runtime object for this JVM. This is necessary to access
   * the many instance methods of this class.
   *
   * @return the current Runtime object
   */
  public static Runtime getRuntime()
  {
    return current;
  }

  /**
   * Returns the number of available processors currently available to the
   * virtual machine. This number may change over time; so a multi-processor
   * program want to poll this to determine maximal resource usage.
   *
   * @return the number of processors available, at least 1
   */
  public native int availableProcessors();

  /**
   * Find out how much memory is still free for allocating Objects on the heap.
   *
   * @return the number of bytes of free memory for more Objects
   */
  public native long freeMemory();

  /**
   * Find out how much memory total is available on the heap for allocating
   * Objects.
   *
   * @return the total number of bytes of memory for Objects
   */
  public native long totalMemory();

  /**
   * Returns the maximum amount of memory the virtual machine can attempt to
   * use. This may be <code>Long.MAX_VALUE</code> if there is no inherent
   * limit (or if you really do have a 8 exabyte memory!).
   *
   * @return the maximum number of bytes the virtual machine will attempt
   *         to allocate
   */
  public native long maxMemory();
} // class Runtime
