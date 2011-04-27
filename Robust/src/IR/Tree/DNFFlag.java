package IR.Tree;
import java.util.Vector;
import IR.*;

public class DNFFlag {
  private Vector conjunctions;
  public DNFFlag(FlagNode flag) {
    DNFFlagAtom dfa=new DNFFlagAtom(flag, false);
    conjunctions=new Vector();
    Vector conjunct=new Vector();
    conjunct.add(dfa);
    conjunctions.add(conjunct);
  }
  private DNFFlag() {
    conjunctions=new Vector();
  }

  /** Returns the number of conjunctions in the DNF form. */

  public int size() {
    return conjunctions.size();
  }

  /** Returns a Vector containing the terms in the n'th conjunction. */

  public Vector get(int n) {
    return (Vector) conjunctions.get(n);
  }

  /** This method negates a DNFFlag expression. */

  public DNFFlag not() {
    DNFFlag notflag=null;
    for (int i=0; i<conjunctions.size(); i++) {
      Vector conj=(Vector)conjunctions.get(i);
      DNFFlag newflag=null;
      for (int j=0; j<conj.size(); j++) {
        DNFFlagAtom dfa=(DNFFlagAtom) conj.get(j);
        DNFFlagAtom negdfa=new DNFFlagAtom(dfa.getFlagNode(),!dfa.getNegated());
        DNFFlag tmp=new DNFFlag();
        Vector v=new Vector();
        tmp.conjunctions.add(v);
        v.add(negdfa);

        if (newflag==null)
          newflag=tmp;
        else
          newflag=newflag.or(tmp);
      }
      if (notflag==null)
        notflag=newflag;
      else
        notflag=notflag.and(newflag);
    }
    return notflag;
  }

  /** This method or's two DNFFlag expressions together. */
  public DNFFlag or(DNFFlag dnf2) {
    DNFFlag result=new DNFFlag();
    for(int i=0; i<conjunctions.size(); i++) {
      Vector conjunct=(Vector)conjunctions.get(i);
      Vector newvector=new Vector();
      result.conjunctions.add(newvector);
      for(int j=0; j<conjunct.size(); j++) {
        newvector.add(conjunct.get(j));
      }
    }

    for(int i=0; i<dnf2.conjunctions.size(); i++) {
      Vector conjunct=(Vector)dnf2.conjunctions.get(i);
      Vector newvector=new Vector();
      result.conjunctions.add(newvector);
      for(int j=0; j<conjunct.size(); j++) {
        newvector.add(conjunct.get(j));
      }
    }
    return result;
  }

  /** This method and's two DNFFlag expressions together. */
  public DNFFlag and(DNFFlag dnf2) {
    DNFFlag result=new DNFFlag();
    for(int i=0; i<conjunctions.size(); i++) {
      for(int i2=0; i2<dnf2.conjunctions.size(); i2++) {
        Vector conjunct=(Vector)conjunctions.get(i);
        Vector conjunct2=(Vector)dnf2.conjunctions.get(i2);
        Vector newconjunct=new Vector();
        result.conjunctions.add(newconjunct);
        for(int j=0; j<conjunct.size(); j++) {
          newconjunct.add(conjunct.get(j));
        }
        for(int j2=0; j2<conjunct2.size(); j2++) {
          newconjunct.add(conjunct2.get(j2));
        }
      }
    }
    return result;
  }

  public String toString() {
    String value="";
    for(int i=0; i<conjunctions.size(); i++) {
      if (i!=0)
        value+=" || ";
      Vector conjunct=(Vector)conjunctions.get(i);
      for(int j=0; j<conjunct.size(); j++) {
        if (j!=0)
          value+="&&";
        value+=conjunct.get(j);
      }
    }
    return value;
  }
}
