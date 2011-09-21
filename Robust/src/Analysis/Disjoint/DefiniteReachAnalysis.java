package Analysis.Disjoint;

import java.util.*;

import IR.*;
import IR.Flat.*;
import Util.*;


public class DefiniteReachAnalysis {

  // R
  //
  // Maps variables and an edge (x, y, e) to an unused value when the
  // object of x is already reachable from the object of y, and the
  // set of edges conservatively gives the path.
  // NOTE: Use EdgeKey instead of edges because this analysis's
  // scope is beyond the scope of a single reach graph.

  // Fs
  // Just a hashmap of variable to enum (unknown, new).

  // Fu

  // Fd

  public DefiniteReachAnalysis() {
  }

  // what are the transfer functions that are relevant for this analyis?

  public void methodEntry(Set<TempDescriptor> parameters) {
    // R' := {}
    // R.clear();

    // Rs' := P x {unknown}
  }

  public void copy(TempDescriptor x,
                   TempDescriptor y) {
    // R' := (R - <x,*> - <*,x>)        U
    //       {<x,z>->e | <y,z>->e in R} U
    //       {<z,x>->e | <z,y>->e in R}
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);
    // setYs = R.get(view0, y);
    // for each <y,z>->e: R'.put(<x,z>, e);
    // setYs = R.get(view1, y);
    // for each <z,y>->e: R'.put(<z,x>, e);

    // Rs' := (Rs - <x,*>) U {<x,v> | <y,v> in Rs}
  }

  public void load(TempDescriptor x,
                   TempDescriptor y,
                   FieldDescriptor f) {
    // R' := (R - <x,*> - <*,x>) U
    //       ({<x,y>} x Eo(y,f)) U
    //       U        {<x,z>} x (Eo(y,f)U{e})
    //   <y,z>->e in R
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);
    // R'.put(<x,y>, eee!);
    // setYs = R.get(view0, y);
    // for each <y,z>->e: R'.put(<x,z>, eee!Ue);

    // Rs' := (Rs - <x,*>) U {<x, unknown>}
  }

  public void store(TempDescriptor x,
                   FieldDescriptor f,
                   TempDescriptor y) {
    // I think this should be if there is ANY <w,z>->e' IN Eremove, then kill all <w,z>
    // R' := (R - {<w,z>->e | <w,z>->e in R, A<w,z>->e' in R, e' notin Eremove}) U
    //       {<y,x>->e | e in E(x) x {f} x E(y)}
    // R' = new Map(R)
    // R'.remove(?); some e's...
    // R'.put(<y,x>, E(x) x {f} x E(y));

    // Rs' := Rs
  }

  public void newObject(TempDescriptor x) {
    // R' := (R - <x,*> - <*,x>)
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);

    // Rs' := (Rs - <x,*>) U {<x, new>}
  }

  public void methodCall(TempDescriptor x) {
    // R' := (R - <x,*> - <*,x>)
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);

    // Rs' := (Rs - <x,*>) U {<x, unknown>}
  }

  public void merge() {
    // R' := <x,y>->e iff its in all incoming edges

    // Rs' := <x, new> iff in all incoming edges, otherwie <x, unknown>
  }
}
