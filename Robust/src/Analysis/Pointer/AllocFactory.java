package Analysis.Pointer;

import java.util.*;
import IR.*;
import IR.Flat.*;

public class AllocFactory {
  public static class AllocNode {
    int allocsite;
    boolean summary;
    TypeDescriptor type;
    
    public AllocNode(int allocsite, TypeDescriptor type, boolean summary) {
      this.allocsite=allocsite;
      this.summary=summary;
      this.type=type;
    }

    public TypeDescriptor getType() {
      return type;
    }

    public boolean isSummary() {
      return summary;
    }
    
    public int hashCode() {
      return allocsite<<1^(summary?0:1);
    }
    
    public boolean equals(Object o) {
      if (o instanceof AllocNode) {
	AllocNode an=(AllocNode)o;
	return (allocsite==an.allocsite)&&(summary==an.summary);
      }
      return false;
    }

    public String toString() {
      return getID();
    }

    public String getID() {
      if (summary)
	return "SUM"+allocsite;
      else
	return "SING"+allocsite;
    }
  }

  public AllocFactory(State state, TypeUtil typeUtil) {
    allocMap=new HashMap<FlatNew, Integer>();
    allocNodeMap=new HashMap<AllocNode, AllocNode>();
    this.typeUtil=typeUtil;
    ClassDescriptor stringcd=typeUtil.getClass(TypeUtil.StringClass);
    TypeDescriptor stringtd=new TypeDescriptor(stringcd);
    TypeDescriptor stringarraytd=stringtd.makeArray(state);
    StringArray=new AllocNode(0, stringarraytd, false);
    Strings=new AllocNode(1, stringtd, true);
  }

  public int getSiteNumber(FlatNew node) {
    if (allocMap.containsKey(node))
      return allocMap.get(node);
    int index=siteCounter++;
    allocMap.put(node, index);
    return index;
  }

  public AllocNode getAllocNode(FlatNew node, boolean isSummary) {
    int site=getSiteNumber(node);
    AllocNode key=new AllocNode(site, node.getType(), isSummary);
    if (!allocNodeMap.containsKey(key)) {
      allocNodeMap.put(key, key);
      return key;
    } else
      return allocNodeMap.get(key);
  }

  public AllocNode getAllocNode(AllocNode node, boolean isSummary) {
    int site=node.allocsite;
    AllocNode key=new AllocNode(site, node.getType(), isSummary);
    if (!allocNodeMap.containsKey(key)) {
      allocNodeMap.put(key, key);
      return key;
    } else
      return allocNodeMap.get(key);
  }

  HashMap<AllocNode, AllocNode> allocNodeMap;
  HashMap<FlatNew, Integer> allocMap;
  TypeUtil typeUtil;
  int siteCounter=2;

  public AllocNode StringArray;
  public AllocNode Strings;
}