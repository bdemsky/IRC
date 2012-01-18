/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

//package java.util.concurrent.atomic;
//import sun.misc.Unsafe;

/**
 * An object reference that may be updated atomically. See the {@link
 * java.util.concurrent.atomic} package specification for description
 * of the properties of atomic variables.
 * @since 1.5
 * @author Doug Lea
 * @param <V> The type of object referred to by this reference
 */
public class AtomicReference/*<V>*/  implements /*java.io.*/Serializable {
    private static final long serialVersionUID = -1848883965231344442L;

    //private static final Unsafe unsafe = Unsafe.getUnsafe();
   // private static final long valueOffset;

    /*static {
      try {
        valueOffset = unsafe.objectFieldOffset
            (AtomicReference.class.getDeclaredField("value"));
      } catch (Exception ex) { throw new Error(ex); }
    }*/

    private volatile Object/*V*/ value;

    /**
     * Creates a new AtomicReference with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicReference(Object/*V*/ initialValue) {
        value = initialValue;
    }

    /**
     * Creates a new AtomicReference with null initial value.
     */
    public AtomicReference() {
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final Object/*V*/ get() {
        return value;
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(Object/*V*/ newValue) {
        value = newValue;
    }

    /**
     * Eventually sets to the given value.
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(Object/*V*/ newValue) {
	synchronized (this) {
            value = newValue;
        }
        //unsafe.putOrderedObject(this, valueOffset, newValue);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(Object/*V*/ expect, Object/*V*/ update) {
	synchronized (this) {
            if(expect == value) {
        	value = update;
        	return true;
            } else {
        	return false;
            }
        }
        //return unsafe.compareAndSwapObject(this, valueOffset, expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously and does not provide ordering guarantees,
     * so is only rarely an appropriate alternative to <tt>compareAndSet</tt>.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public final boolean weakCompareAndSet(Object/*V*/ expect, Object/*V*/ update) {
	synchronized (this) {
            if(expect == value) {
        	value = update;
        	return true;
            } else {
        	return false;
            }
        }
        //return unsafe.compareAndSwapObject(this, valueOffset, expect, update);\
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final Object/*V*/ getAndSet(Object/*V*/ newValue) {
        while (true) {
            Object/*V*/ x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value.
     */
    /*public String toString() {
        return String.valueOf(get());
    }*/

}
