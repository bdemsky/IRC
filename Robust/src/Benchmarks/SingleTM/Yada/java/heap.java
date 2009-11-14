public class heap {
  Object [] elements;
  int size;
  int capacity;
  public heap(int initCapacity) {
    int capacity = ((initCapacity > 0) ? (initCapacity) : (1));
    elements = new Object[capacity];
    size=0;
    this.capacity = capacity;
  }
  public void siftUp(int startIndex) {
    int index = startIndex;
    while ((index > 1)) {
      int parentIndex = ((index) / 2);
      Object parentPtr = elements[parentIndex];
      Object thisPtr = elements[index];
      if (compare(parentPtr, thisPtr) >= 0) {
 break;
      }
      Object tmpPtr = parentPtr;
      elements[parentIndex] = thisPtr;
      elements[index] = tmpPtr;
      index = parentIndex;
    }
  }
  public boolean heap_insert(Object dataPtr) {
    if ((size + 1) >= capacity) {
      int newCapacity = capacity * 2;
      Object newElements[] = new Object[newCapacity];
      this.capacity = newCapacity;
      for (int i = 0; i <= size; i++) {
 newElements[i] = elements[i];
      }
      this.elements = newElements;
    }
    size++;
    elements[size] = dataPtr;
    siftUp(size);
    return true;
  }
  public void heapify(int startIndex) {
    int index = startIndex;
    while (true) {
      int leftIndex = (2*index);
      int rightIndex = (2*(index) + 1);
      int maxIndex = -1;
      if ((leftIndex <= size) &&
   (compare(elements[leftIndex], elements[index]) > 0)) {
 maxIndex = leftIndex;
      } else {
 maxIndex = index;
      }
      if ((rightIndex <= size) &&
   (compare(elements[rightIndex], elements[maxIndex]) > 0)) {
 maxIndex = rightIndex;
      }
      if (maxIndex == index) {
 break;
      } else {
 Object tmpPtr = elements[index];
 elements[index] = elements[maxIndex];
 elements[maxIndex] = tmpPtr;
 index = maxIndex;
      }
    }
  }
  Object heap_remove() {
    if (size < 1) {
      return null;
    }
    Object dataPtr = elements[1];
    elements[1] = elements[size];
    size--;
    heapify(1);
    return dataPtr;
  }
  boolean heap_isValid() {
    for (int i = 1; i < size; i++) {
      if (compare(elements[i+1], elements[((i+1) / 2)]) > 0) {
 return false;
      }
    }
    return true;
  }
  private static int compare(Object aPtr, Object bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
    if (aElementPtr.encroachedEdgePtr!=null) {
      if (bElementPtr.encroachedEdgePtr!=null) {
        return 0;
      } else {
        return 1;
      }
    }
    if (bElementPtr.encroachedEdgePtr!=null) {
      return -1;
    }
    return 0;
  }
  public void printHeap() {
    System.out.println("[");
    for (int i = 0; i < size; i++) {
      System.out.print(elements[i+1]+" ");
    }
    System.out.println("]");
  }
}
