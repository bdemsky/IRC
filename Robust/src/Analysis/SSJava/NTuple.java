package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NTuple<T> {

  private List<T> elements;

  public NTuple(List<T> l) {
    this.elements = new ArrayList<T>();
    this.elements.addAll(l);
  }

  public NTuple() {
    this.elements = new ArrayList<T>();
  }

  public String toString() {
    return elements.toString();
  }

  public T get(int index) {
    return elements.get(index);
  }

  public int size() {
    return elements.size();
  }

  public void add(T newElement) {
    this.elements.add(newElement);
  }

  public void addAll(Collection<T> all) {
    this.elements.addAll(all);
  }

  public void addAll(NTuple<T> tuple) {
    for (int i = 0; i < tuple.size(); i++) {
      elements.add(tuple.get(i));
    }
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

  public void removeAt(int i) {
    elements.remove(i);
  }

  public List<T> getList() {
    return elements;
  }

  public boolean startsWith(T prefix) {
    return get(0).equals(prefix);
  }

  public boolean startsWith(NTuple<T> prefix) {

    if (prefix.size() > size()) {
      return false;
    }

    for (int i = 0; i < prefix.size(); i++) {
      if (prefix.get(i).equals(get(i))) {
        return false;
      }
    }
    return true;

  }

}
