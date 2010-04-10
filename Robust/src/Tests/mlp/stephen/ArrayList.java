

public class ArrayList
{
    /**
     * The array buffer into which the elements of the ArrayList are stored.
     * The capacity of the ArrayList is the length of this array buffer.
     */
    private Object[] elementData;
    private int size;

    public ArrayList(int initialCapacity) {
		this.elementData = new Object[initialCapacity];
    }

    public ArrayList() {
		this.elementData = new Object[10];
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
	return size;
    }

    /**
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
	return size == 0;
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    public boolean contains(Object o) {
	return indexOf(o) >= 0;
    }

    /**
     * Returns the index of the first occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     */
    public int indexOf(Object o) {
	if (o == null) {
	    for (int i = 0; i < size; i++)
		if (elementData[i]==null)
		    return i;
	} else {
	    for (int i = 0; i < size; i++)
		if (o.equals(elementData[i]))
		    return i;
	}
	return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     */
    public int lastIndexOf(Object o) {
	if (o == null) {
	    for (int i = size-1; i >= 0; i--)
		if (elementData[i]==null)
		    return i;
	} else {
	    for (int i = size-1; i >= 0; i--)
		if (o.equals(elementData[i]))
		    return i;
	}
	return -1;
    }

    /**
     * Returns the element at the specified position in this list.
     *
     * @param  index index of the element to return
     * @return the element at the specified position in this list
     * @throws NOTHING
     */
    public Object get(int index) {
    	RangeCheck(index);

	return elementData[index];
    }

    /**
     * Replaces the element at the specified position in this list with
     * the specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws NOTHING
     */
    public Object set(int index, Object element) {
    	RangeCheck(index);

    	Object oldValue = elementData[index];
    	elementData[index] = element;
		return oldValue;
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * @param e element to be appended to this list
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     */
    public boolean add(Object e) {
	ensureCapacity(size + 1);  // Increments modCount!!
	elementData[size++] = e;
	return true;
    }
    
    public void ensureCapacity(int minCapacity) 
    {
    	int oldCapacity = elementData.length;
    	if (minCapacity > oldCapacity) 
    	{
    	    int newCapacity = (oldCapacity * 3)/2 + 1;
        	    if (newCapacity < minCapacity)
        	    	newCapacity = minCapacity;
               
        	    // minCapacity is usually close to size, so this is a win:
                elementData = copyOf(elementData, newCapacity);
    	}
     }
    
    public Object[] copyOf(Object[] data, int newCap)
    {
    	Object[] array = new Object[newCap];
    	
    	for(int i = 0; i < size; i++)
    		array[i] = data[i];
    	
    	return array;
    }

    /**
     * Inserts the specified element at the specified position in this
     * list. Shifts the element currently at that position (if any) and
     * any subsequent elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws NOTHING
     */
    public void add(int index, Object element) {

	ensureCapacity(size+1);  // Increments modCount!!
	systemArrayCopy(elementData, index, elementData, index + 1,
			 size - index);
	elementData[index] = element;
	size++;
    }

    //Mimics System.arraycopy 
    public void systemArrayCopy(Object[] a, int orgStart, Object[] b, int newStart, int length)
    {
    	Object[] original;
    	
    	if(a != b)
    		original = a;
    	else
    	{
	    	original = new Object[a.length];
	    	
	    	for(int i = 0; i < a.length; i++)
	    		original[i] = a[i];
    	}
    	
    	for(int i = 0; i < length && i < (a.length - orgStart - 1) && i < (b.length - newStart - 1); i++)
    	{
    		b[i + newStart] = original[i + orgStart];
    	}
    }
    
    /**
     * Returns a shallow copy of this <tt>ArrayList</tt> instance.  (The
     * elements themselves are not copied.)
     *
     * @return a clone of this <tt>ArrayList</tt> instance
     */
    public ArrayList clone() 
    {
    	ArrayList clone = new ArrayList(this.size + 1);
    	systemArrayCopy(this.elementData, 0, clone.elementData, 0, this.size);
    	
    	clone.size = this.size;
    	
    	return clone;
    }
    
    /**
     * Removes the element at the specified position in this list.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).
     *
     * @param index the index of the element to be removed
     * @return the element that was removed from the list
     * @throws NOTHING
     */
    public Object remove(int index) {
    	RangeCheck(index);

	
	Object oldValue = elementData[index];

	int numMoved = size - index - 1;
	if (numMoved > 0)
		systemArrayCopy(elementData, index+1, elementData, index,
			     numMoved);
	elementData[--size] = null; // Let gc do its work

	return oldValue;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If the list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean remove(Object o) {
	if (o == null) {
            for (int index = 0; index < size; index++)
		if (elementData[index] == null) {
		    fastRemove(index);
		    return true;
		}
	} else {
	    for (int index = 0; index < size; index++)
		if (o.equals(elementData[index])) {
		    fastRemove(index);
		    return true;
		}
        }
	return false;
    }

    /*
     * Private remove method that skips bounds checking and does not
     * return the value removed.
     */
    private void fastRemove(int index) 
    {
        int numMoved = size - index - 1;
        if (numMoved > 0)
        	systemArrayCopy(elementData, index+1, elementData, index,
                             numMoved);
        elementData[--size] = null; // Let gc do its work
    }

    /**
     * Removes all of the elements from this list.  The list will
     * be empty after this call returns.
     */
    public void clear() {

	// Let gc do its work
	for (int i = 0; i < size; i++)
	    elementData[i] = null;

	size = 0;
    }

   

    
    /**
     * Removes from this list all of the elements whose index is between
     * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.
     * Shifts any succeeding elements to the left (reduces their index).
     * This call shortens the list by <tt>(toIndex - fromIndex)</tt> elements.
     * (If <tt>toIndex==fromIndex</tt>, this operation has no effect.)
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     * @throws NOTHING
     */
    protected void removeRange(int fromIndex, int toIndex) {
	int numMoved = size - toIndex;
		systemArrayCopy(elementData, toIndex, elementData, fromIndex,
                         numMoved);

	// Let gc do its work
	int newSize = size - (toIndex-fromIndex);
	while (size != newSize)
	    elementData[--size] = null;
    }

    /**
     * Checks if the given index is in range.  If not, throws an appropriate
     * runtime exception.  This method does *not* check if the index is
     * negative: It is always used immediately prior to an array access,
     * which throws an ArrayIndexOutOfBoundsException if index is negative.
     */
    private void RangeCheck(int index) {
	if (index >= size)
	    System.out.println("Array index out of bounds Exception Occured but cannot be thrown (not yet implemented)");
    }
}
