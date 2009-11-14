public class Queue_t {
  int pop;
  int push;
  int capacity;
  Object[] elements;
  public Queue_t(int initCapacity) {
    int capacity = ((initCapacity < 2) ? 2 : initCapacity);
    elements = new Object[capacity];
    pop = capacity - 1;
    push = 0;
    this.capacity = capacity;
  }
  public boolean queue_isEmpty () {
    return ((pop + 1) % capacity) == push;
  }
  public void queue_clear () {
    pop = capacity - 1;
    push = 0;
  }
  public void queue_shuffle(Random randomPtr) {
    int numElement;
    if (pop < push) {
      numElement = push - (pop + 1);
    } else {
      numElement = capacity - (pop - push + 1);
    }
    int base = pop + 1;
    for (int i = 0; i < numElement; i++) {
      int r1 = (int) (randomPtr.random_generate() % numElement);
      int r2 = (int) (randomPtr.random_generate() % numElement);
      int i1 = (base + r1) % capacity;
      int i2 = (base + r2) % capacity;
      Object tmp = elements[i1];
      elements[i1] = elements[i2];
      elements[i2] = tmp;
    }
  }
  public boolean queue_push (Object dataPtr) {
    int newPush = (push + 1) % capacity;
    if (newPush == pop) {
      int newCapacity = capacity * 2;
      Object[] newElements = new Object[newCapacity];
      int dst = 0;
      if (pop < push) {
 for (int src = (pop + 1); src < push; src++, dst++) {
   newElements[dst] = elements[src];
 }
      } else {
 for (int src = (pop + 1); src < capacity; src++, dst++) {
   newElements[dst] = elements[src];
 }
 for (int src = 0; src < push; src++, dst++) {
   newElements[dst] = elements[src];
 }
      }
      elements = newElements;
      pop = newCapacity - 1;
      capacity = newCapacity;
      push = dst;
      newPush = push + 1;
    }
    elements[push] = dataPtr;
    push = newPush;
    return true;
  }
  public Object queue_pop() {
    int newPop = (pop + 1) % capacity;
    if (newPop == push) {
      return null;
    }
    Object dataPtr = elements[newPop];
    pop = newPop;
    return dataPtr;
  }
}
