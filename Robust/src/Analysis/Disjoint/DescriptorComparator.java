package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class DescriptorComparator implements Comparator {

  public int compare( Object o1, Object o2  ) {

    assert o1 instanceof Descriptor;
    assert o2 instanceof Descriptor;

    Descriptor d1 = (Descriptor) o1;
    Descriptor d2 = (Descriptor) o2;

    return d1.getNum() - d2.getNum();
  }
  
}
