public class ArrayListIterator implements Iterator {
  private int pos;
  private int size;
  private int last;
  private ArrayList list;

  public ArrayListIterator(ArrayList list) {
    this.list = list;
    this.pos = 0;
    this.size = this.list.size();
    this.last = -1;
  }

  /**
   * Tests to see if there are any more objects to
   * return.
   *
   * @return True if the end of the list has not yet been
   *         reached.
   */
  public boolean hasNext()
  {
    return pos < size;
  }

  /**
   * Retrieves the next object from the list.
   *
   * @return The next object.
   * @throws NoSuchElementException if there are
   *         no more objects to retrieve.
   * @throws ConcurrentModificationException if the
   *         list has been modified elsewhere.
   */
  public Object next()
  {
    if (pos == size)
      throw new /*NoSuchElement*/Exception("NoSuchElementException");
    last = pos;
    return this.list.get(pos++);
  }

  /**
   * Removes the last object retrieved by <code>next()</code>
   * from the list, if the list supports object removal.
   *
   * @throws ConcurrentModificationException if the list
   *         has been modified elsewhere.
   * @throws IllegalStateException if the iterator is positioned
   *         before the start of the list or the last object has already
   *         been removed.
   * @throws UnsupportedOperationException if the list does
   *         not support removing elements.
   */
  public void remove()
  {
    if (last < 0)
      throw new /*IllegalState*/Exception("IllegalStateException");
    this.list.remove(last);
    pos--;
    size--;
    last = -1;
  }
}
