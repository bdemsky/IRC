package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NTuple<T> {

  private List<T> elements;

  public NTuple(T... elements) {
    this.elements = Arrays.asList(elements);
  }

  public NTuple() {
    this.elements = new ArrayList<T>();
  }

  public String toString() {
    return elements.toString();
  }

  public T at(int index) {
    return elements.get(index);
  }

  public int size() {
    return elements.size();
  }

  public void addElement(T newElement) {
    this.elements.add(newElement);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || o.getClass() != this.getClass()) {
      return false;
    }
    return (((NTuple) o).elements).equals(elements);
  }

  public int hashCode() {
    return elements.hashCode();
  }

}
