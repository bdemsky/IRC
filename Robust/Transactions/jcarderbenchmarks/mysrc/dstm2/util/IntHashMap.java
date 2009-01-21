package dstm2.util;

import java.io.IOException;
import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dstm2.Thread;
import dstm2.atomic;
import dstm2.factory.Factory;

public class IntHashMap implements Iterable<Integer>{
	
    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     **/
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    //transient TEntry<V>[] table;
    dstm2.AtomicArray<TEntry> table;
    final private Factory<TEntry> factory;
	
    //transient Entry<K, V>[] table;

    /**
     * The number of key-value mappings contained in this identity hash map.
     */
    //transient int size;
    
    java.util.concurrent.atomic.AtomicInteger size;
    
    /*
     * The capacity of the table
     */
    transient int capacity;
  
    /**
     * The next size value at which to resize (capacity * load factor).
     * @serial
     */
    int threshold;
  
    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    final float loadFactor;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient volatile int modCount;

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and load factor.
     *
     * @param  initialCapacity The initial capacity.
     * @param  loadFactor      The load factor.
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive.
     */
    public IntHashMap(int initialCapacity, float loadFactor) {
    	size = new AtomicInteger(0);
    	factory = Thread.makeFactory(TEntry.class);
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);

        // Find a power of 2 >= initialCapacity
        int capacity = 1;
        while (capacity < initialCapacity) 
            capacity <<= 1;
    
        this.capacity = capacity;
        this.loadFactor = loadFactor;
        threshold = (int)(capacity * loadFactor);
        table = new dstm2.AtomicArray<TEntry>(TEntry.class, capacity);
//        for(int i = 0; i < capacity; i++) {
//        	table[i] = factory.create();
//        }
        //table = new Entry[capacity];
        init();
    }
  
    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public IntHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    public IntHashMap() {
    	this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new <tt>HashMap</tt> with the same mappings as the
     * specified <tt>Map</tt>.  The <tt>HashMap</tt> is created with
     * default load factor (0.75) and an initial capacity sufficient to
     * hold the mappings in the specified <tt>Map</tt>.
     *
     * @param   m the map whose mappings are to be placed in this map.
     * @throws  NullPointerException if the specified map is null.
     */
    public IntHashMap(IntHashMap m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        putAllForCreate(m);
    }

    // internal utilities

    /**
     * Initialization hook for subclasses. This method is called
     * in all constructors and pseudo-constructors (clone, readObject)
     * after HashMap has been initialized but before any entries have
     * been inserted.  (In the absence of this method, readObject would
     * require explicit knowledge of subclasses.)
     */
    void init() {
    }
    public dstm2.AtomicArray<TEntry> getBuckets() {
    	return table;
    }
    /**
     * Value representing null keys inside tables.
     */
    static final Object NULL_KEY = new Object();

    /**
     * Returns internal representation for key. Use NULL_KEY if key is null.
     */
    static <T> T maskNull(T key) {
        return key == null ? (T)NULL_KEY : key;
    }

    /**
     * Returns key represented by specified internal representation.
     */
    static <T> T unmaskNull(T key) {
        return (key == NULL_KEY ? null : key);
    }

    /**
     * Whether to prefer the old supplemental hash function, for
     * compatibility with broken applications that rely on the
     * internal hashing order.
     *
     * Set to true only by hotspot when invoked via
     * -XX:+UseNewHashFunction or -XX:+AggressiveOpts
     */
    private static final boolean useNewHash;
    static { useNewHash = false; }

    private static int oldHash(int h) {
        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);
        return h;
    }

    private static int newHash(int h) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because HashMap uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits.
     */
    public static int hash(int h) {
	return useNewHash ? newHash(h) : oldHash(h);
    }

    public static int hash(Object key) {
	return hash(key.hashCode());
    }

    /** 
     * Check for equality of non-null reference x and possibly-null y. 
     */
    static boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /**
     * Returns index for hash code h. 
     */
    public static int indexFor(int h, int length) {
        return h & (length-1);
    }
 
    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        return size.get();
    }
  
    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return size.get() == 0;
    }

    /**
     * Returns the value to which the specified key is mapped in this identity
     * hash map, or <tt>null</tt> if the map contains no mapping for this key.
     * A return value of <tt>null</tt> does not <i>necessarily</i> indicate
     * that the map contains no mapping for the key; it is also possible that
     * the map explicitly maps the key to <tt>null</tt>. The
     * <tt>containsKey</tt> method may be used to distinguish these two cases.
     *
     * @param   key the key whose associated value is to be returned.
     * @return  the value to which this map maps the specified key, or
     *          <tt>null</tt> if the map contains no mapping for this key.
     * @see #put(Object, Object)
     */
    public Integer get(int key) {
//		if (key == null)
//		    return getForNullKey();
        int hash = hash(key);
        for (TEntry e = table.get(indexFor(hash, capacity));
             e != null;
             e = e.getNext()) {
            Object k;
            if (e.getHash() == hash)
                return e.getValue();
        }
        return null;
    }

//    private V getForNullKey() {
//        int hash = hash(NULL_KEY.hashCode());
//        int i = indexFor(hash, capacity);
//        TEntry<K,V> e = table[i];
//        //Entry<K,V> e = table[i];
//        while (true) {
//            if (e == null)
//                return null;
//            if (e.getKey() == NULL_KEY)
//                return e.getValue();
//            e = e.getNext();
//        }
//    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the
     * specified key.
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(int key) {
        Object k = maskNull(key);
        int hash = hash(k);
        int i = indexFor(hash, capacity);
        TEntry e = table.get(i); 
        while (e != null) {
            if (e.getHash() == hash) 
                return true;
            e = e.getNext();
        }
        return false;
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     */
    TEntry getEntry(int key) {
        int hash = hash(key);
        int i = indexFor(hash, capacity);
        TEntry e = table.get(i); 
        while (e != null && !(e.getHash() == hash))
            e = e.getNext();
        return e;
    }
  
    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or <tt>null</tt>
     *	       if there was no mapping for key.  A <tt>null</tt> return can
     *	       also indicate that the HashMap previously associated
     *	       <tt>null</tt> with the specified key.
     */
    public Integer put(int key, Integer value) {
        int hash = hash(key);
        int i = indexFor(hash, capacity);
        for (TEntry e = table.get(i); e != null; e = e.getNext()) {
            if (e.getHash() == hash) {
                Integer oldValue = e.getValue();
                e.setValue(value);
//                e.recordAccess(this);
                return oldValue;
            }
        }
        modCount++;
        addEntry(hash, value, i);
        return null;
    }



    /**
     * This method is used instead of put by constructors and
     * pseudoconstructors (clone, readObject).  It does not resize the table,
     * check for comodification, etc.  It calls createEntry rather than
     * addEntry.
     */
    private void putForCreate(int key, Integer value) {
        int hash = hash(key);
        int i = indexFor(hash, capacity);

        /**
         * Look for preexisting entry for key.  This will never happen for
         * clone or deserialize.  It will only happen for construction if the
         * input Map is a sorted map whose ordering is inconsistent w/ equals.
         */
        for (TEntry e = table.get(i); e != null; e = e.getNext()) {
            if (e.getHash() == hash) {
                e.setValue(value);
                return;
            }
        }

        createEntry(hash, value, i);
    }

    void putAllForCreate(IntHashMap m) {
        for (Iterator<? extends IntHashMap.TEntry> i = m.entrySet().iterator(); i.hasNext(); ) {
            IntHashMap.TEntry e = i.next();
            putForCreate(e.getHash(), e.getValue());
        }
    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *        must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value
     *        is irrelevant).
     */
    void resize(int newCapacity) {
        dstm2.AtomicArray<TEntry> oldTable = table;
        int oldCapacity = capacity;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        dstm2.AtomicArray<TEntry> newTable = new dstm2.AtomicArray<TEntry>(TEntry.class, newCapacity);
        transfer(newTable, newCapacity);
        table = newTable;
        threshold = (int)(newCapacity * loadFactor);
        capacity = newCapacity;
    }

    /** 
     * Transfer all entries from current table to newTable.
     */
    void transfer(dstm2.AtomicArray<TEntry> newTable, int nc) {
    	dstm2.AtomicArray<TEntry> src = table;
        int newCapacity = nc;
        for (int j = 0; j < capacity; j++) {
            TEntry e = src.get(j);
            if (e != null) {
                src.set(j, null);
                do {
                    TEntry next = e.getNext();
                    int i = indexFor(e.getHash(), newCapacity);  
                    e.setNext(newTable.get(i));
                    newTable.set(i, e);
                    e = next;
                } while (e != null);
            }
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map
     * These mappings will replace any mappings that
     * this map had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @throws NullPointerException if the specified map is null.
     */
    public void putAll(IntHashMap m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself
         * to at most one extra resize.
         */
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = capacity;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > capacity)
                resize(newCapacity);
        }

        for (Iterator<? extends IntHashMap.TEntry> i = m.entrySet().iterator(); i.hasNext(); ) {
            IntHashMap.TEntry e = i.next();
            put(e.getHash(), e.getValue());
        }
    }
  
    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or <tt>null</tt>
     *	       if there was no mapping for key.  A <tt>null</tt> return can
     *	       also indicate that the map previously associated <tt>null</tt>
     *	       with the specified key.
     */
    public Integer remove(int key) {
        TEntry e = removeEntryForKey(key);
        return (e == null ? null : e.getValue());
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     */
    TEntry removeEntryForKey(int key) {
        int hash = hash(key);
        int i = indexFor(hash, capacity);
        TEntry prev = table.get(i);
        TEntry e = prev;

        while (e != null) {
            TEntry next = e.getNext();
            if (e.getHash() == hash) {
                modCount++;
                size.decrementAndGet();
                if (prev == e) 
                    table.set(i, next);
                else
                    prev.setNext(next);
//                e.recordRemoval(this);
                return e;
            }
            prev = e;
            e = next;
        }
   
        return e;
    }

    /**
     * Special version of remove for EntrySet.
     */
    TEntry removeMapping(TEntry o) {

        TEntry entry = o;
        int hash = hash(o.getHash());
        int i = indexFor(hash, capacity);
        TEntry prev = table.get(i);
        TEntry e = prev;

        while (e != null) {
            TEntry next = e.getNext();
            if (e.getHash() == hash) {
                modCount++;
                size.decrementAndGet();
                if (prev == e) 
                	table.set(i,  next);
                else
                    prev.setNext(next);
//                e.recordRemoval(this);
                return e;
            }
            prev = e;
            e = next;
        }
   
        return e;
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        modCount++;
        dstm2.AtomicArray<TEntry> tab = table;
        for (int i = 0; i < capacity; i++) 
        	table.set(i, null);
        size.set(0);
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested.
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value.
     */
    public boolean containsValue(Object value) {
	if (value == null) 
            return containsNullValue();

	dstm2.AtomicArray<TEntry> tab = table;
        for (int i = 0; i < capacity; i++)
            for (TEntry e = tab.get(i); e != null ; e = e.getNext())
                if (value.equals(e.getValue()))
                    return true;
	return false;
    }

    /**
     * Special-case code for containsValue with null argument
     **/
    private boolean containsNullValue() {
    	dstm2.AtomicArray<TEntry> tab = table;
        for (int i = 0; i < capacity ; i++)
            for (TEntry e = tab.get(i) ; e != null ; e = e.getNext())
                if (e.getValue() == null)
                    return true;
	return false;
    }

    @atomic public interface TEntry {
    	int getHash();
	Integer getValue();
    	TEntry getNext();
    	void setHash(int h);
    	void setValue(Integer v);
    	void setNext(TEntry n);
    	
    }
    

    /**
     * Add a new entry with the specified key, value and hash code to
     * the specified bucket.  It is the responsibility of this 
     * method to resize the table if appropriate.
     *
     * Subclass overrides this to alter the behavior of put method.
     */
    void addEntry(int hash, Integer value, int bucketIndex) {
    	TEntry e = table.get(bucketIndex);
    	TEntry n = factory.create();
        n.setHash(hash);
        n.setValue(value);
        n.setNext(e);
    	table.set(bucketIndex, n);
        if (size.incrementAndGet() >= threshold) {
        	synchronized(this) {
        		if(size.get() >= threshold)
        			resize(2 * capacity);
        	}
        }
    }

    /**
     * Like addEntry except that this version is used when creating entries
     * as part of Map construction or "pseudo-construction" (cloning,
     * deserialization).  This version needn't worry about resizing the table.
     *
     * Subclass overrides this to alter the behavior of HashMap(Map),
     * clone, and readObject.
     */
    void createEntry(int hash, Integer value, int bucketIndex) {
    	TEntry e = table.get(bucketIndex);
    	TEntry n = factory.create();
        n.setHash(hash);
        n.setValue(value);
        n.setNext(e);
    	table.set(bucketIndex, n);
        size.incrementAndGet();
    }

    private abstract class HashIterator<E> implements Iterator<E> {
        TEntry next;	// next entry to return
        int expectedModCount;	// For fast-fail 
        int index;		// current slot 
        TEntry current;	// current entry

        HashIterator() {
            expectedModCount = modCount;
            dstm2.AtomicArray<TEntry> t = table;
            int i = capacity;
            TEntry n = null;
            if (size.get() != 0) { // advance to first entry
                while (i > 0 && (n = t.get(--i)) == null)
                    ;
            }
            next = n;
            index = i;
        }

        public boolean hasNext() {
            return next != null;
        }

        TEntry nextEntry() { 
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            TEntry e = next;
            if (e == null) 
                throw new NoSuchElementException();
                
            TEntry n = e.getNext();
            dstm2.AtomicArray<TEntry> t = table;
            int i = index;
            while (n == null && i > 0)
                n = t.get(--i);
            index = i;
            next = n;
            return current = e;
        }

        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            int k = current.getHash();
            current = null;
            IntHashMap.this.removeEntryForKey(k);
            expectedModCount = modCount;
        }

    }

    private class ValueIterator extends HashIterator<Integer> {
        public Integer next() {
            return nextEntry().getValue();
        }
    }

    private class KeyIterator extends HashIterator<Integer> {
        public Integer next() {
            return nextEntry().getHash();
        }
    }

//    private class EntryIterator extends HashIterator<Map.Entry<K,V>> {
//        public Map.Entry<K,V> next() {
//            return nextEntry();
//        }
//    }

    // Subclass overrides these to alter behavior of views' iterator() method
    public Iterator<Integer> newKeyIterator()   {
        return new KeyIterator();
    }
    public Iterator<Integer> newValueIterator()   {
        return new ValueIterator();
    }
//    Iterator<Map.Entry<K,V>> newEntryIterator()   {
//        return new EntryIterator();
//    }


    // Views

    private transient Set<IntHashMap.TEntry> entrySet = null;



    private class KeySet extends AbstractSet<Integer> {
        public Iterator<Integer> iterator() {
            return newKeyIterator();
        }
        public int size() {
            return size.get();
        }
        public boolean contains(Integer o) {
            return containsKey(o);
        }
        public boolean remove(Integer o) {
            return IntHashMap.this.removeEntryForKey(o) != null;
        }
        public void clear() {
            IntHashMap.this.clear();
        }
    }



    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is a <tt>Map.Entry</tt>.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the mappings contained in this map.
     * @see Map.Entry
     */
    public Set<IntHashMap.TEntry> entrySet() {
        Set<IntHashMap.TEntry> es = entrySet;
        return (es != null ? es : (entrySet = (Set<IntHashMap.TEntry>) (Set) new EntrySet()));
    }

    private class EntrySet {//extends AbstractSet/*<Map.Entry<K,V>>*/ {
//        public Iterator/*<Map.Entry<K,V>>*/ iterator() {
//            return newEntryIterator();
//        }
        public boolean contains(IntHashMap.TEntry o) {
            IntHashMap.TEntry e = (IntHashMap.TEntry) o;
            TEntry candidate = getEntry(e.getHash());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(IntHashMap.TEntry o) {
            return removeMapping(o) != null;
        }
        public int size() {
            return size.get();
        }
        public void clear() {
            IntHashMap.this.clear();
        }
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *		   bucket array) is emitted (int), followed  by the
     *		   <i>size</i> of the HashMap (the number of key-value
     *		   mappings), followed by the key (Object) and value (Object)
     *		   for each key-value mapping represented by the HashMap
     *             The key-value mappings are emitted in the order that they
     *             are returned by <tt>entrySet().iterator()</tt>.
     * 
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
	Iterator<IntHashMap.TEntry> i = entrySet().iterator();

	// Write out the threshold, loadfactor, and any hidden stuff
	s.defaultWriteObject();

	// Write out number of buckets
	s.writeInt(capacity);

	// Write out size (number of Mappings)
	s.writeInt(size.get());

        // Write out keys and values (alternating)
	while (i.hasNext()) { 
            IntHashMap.TEntry e = i.next();
            s.writeObject(e.getHash());
            s.writeObject(e.getValue());
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    /**
     * Reconstitute the <tt>HashMap</tt> instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
	// Read in the threshold, loadfactor, and any hidden stuff
	s.defaultReadObject();

	// Read in number of buckets and allocate the bucket array;
	int numBuckets = s.readInt();
	table = new dstm2.AtomicArray(TEntry.class, numBuckets);

        init();  // Give subclass a chance to do its thing.

	// Read in size (number of Mappings)
	int size = s.readInt();

	// Read the keys and values, and put the mappings in the HashMap
	for (int i=0; i<size; i++) {
	    int key = (Integer) s.readObject();
	    Integer value = (Integer) s.readObject();
	    putForCreate(key, value);
	}
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return capacity; }
    float loadFactor()   { return loadFactor;   }

	public int getCapacity() {
		return capacity;
	}

	
	  public Iterator<Integer> iterator() {
	    return new Iterator<Integer>() {
	      int tableIndex = 0;
	      public TEntry cursor = table.get(tableIndex);
	      public boolean hasNext() {
	        return cursor != null;
	      }
	      public Integer next() {
	        TEntry node = cursor;
	        cursor = cursor.getNext();
	        while(cursor==null) {
	        	tableIndex++;
	        	if(tableIndex < capacity) {
	        		cursor = table.get(tableIndex);
	        	}
	        }
	        return node.getValue();
	      }
	      public void remove() {
	        throw new UnsupportedOperationException();
	      }
	      
	    };
	  }
}
