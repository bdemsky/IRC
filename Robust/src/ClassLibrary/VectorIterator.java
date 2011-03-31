public class VectorIterator extends Iterator {
  private int pos;
  private int size;
  private Vector list;

  public VectorIterator(Vector v) {
    this.list = v;
    this.pos = 0;
    this.size = this.list.size();
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
   */
  public Object next()
  {
    if (pos == size) {
      return null;  //since we can't throw anything...
    }
    return this.list.get(pos++);
  }
}