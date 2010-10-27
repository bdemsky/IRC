package IR;

import java.util.*;

public class SymbolTable {

  private Hashtable table;
  private SymbolTable parent;
  private HashSet valueset;
  
  private Vector<SymbolTable> parentIFs;

  public SymbolTable() {
    table = new Hashtable();
    valueset = new HashSet();
    this.parent = null;
    this.parentIFs = null;
  }

  public SymbolTable(SymbolTable parent) {
    table = new Hashtable();
    this.parent = parent;
  }

  public void add(Descriptor d) {
    add(d.getSymbol(), d);
  }

  public void add(String name, Descriptor d) {
    if (!table.containsKey(name))
      table.put(name, new HashSet());
    HashSet hs=(HashSet)table.get(name);
    hs.add(d);
    valueset.add(d);
  }

  public Set getSet(String name) {
    return getPSet(name);
  }

  private HashSet getPSet(String name) {
    HashSet hs=null;
    if (parent!=null)
      hs=parent.getPSet(name);
    else
      hs=new HashSet();
    if (table.containsKey(name)) {
      hs.addAll((HashSet)table.get(name));
    }
    return hs;
  }

  public Set getSetFromSameScope(String name) {
    return getPSetFromSameScope(name);
  }

  private HashSet getPSetFromSameScope(String name) {
    if (table.containsKey(name)) {
      HashSet hs=(HashSet)table.get(name);
      return hs;
    } else
      return new HashSet();
  }

  public Descriptor get(String name) {
    Descriptor d = getFromSameScope(name);
    if (d == null && parent != null) {
      return parent.get(name);
    } else {
      return d;
    }
  }

  public Descriptor getFromSameScope(String name) {
    if (table.containsKey(name)) {
      HashSet hs=(HashSet) table.get(name);
      return (Descriptor) hs.iterator().next();
    } else
      return null;

  }

  public Enumeration getNames() {
    return table.keys();
  }

  public Iterator getNamesIterator() {
    return table.keySet().iterator();
  }

  public Set getValueSet() {
    return valueset;
  }

  public Iterator getDescriptorsIterator() {
    return getValueSet().iterator();
  }

  public Set getAllValueSet() {
    HashSet hs=null;
    if (parent!=null)
      hs=(HashSet) parent.getAllValueSet();
    else
      hs=new HashSet();
    if (this.parentIFs != null) {
      for(int i = 0; i < this.parentIFs.size(); i++) {
        hs.addAll(this.parentIFs.elementAt(i).getAllValueSet());
      }
    }
    hs.addAll(valueset);
    return hs;
  }

  public Iterator getAllDescriptorsIterator() {
    return getAllValueSet().iterator();
  }

  public boolean contains(String name) {
    return (get(name) != null);
  }

  public SymbolTable getParent() {
    return parent;
  }

  public void setParent(SymbolTable parent) {
    this.parent = parent;
  }
  
  public Vector<SymbolTable> getParentIFs() {
    return this.parentIFs;
  }

  public void addParentIF(SymbolTable parentif) {
    if(this.parentIFs == null) {
      this.parentIFs = new Vector<SymbolTable>();
    }
    this.parentIFs.addElement(parentif);
  }


  public String toString() {
    return "ST: " + table.toString();
  }
}
