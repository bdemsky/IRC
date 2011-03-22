package Analysis.Pointer;

import Analysis.Disjoint.Alloc;
import java.util.*;
import IR.*;
import IR.Flat.*;

public class AllocFactory {
  public static AllocSiteNode dummySite=new AllocSiteNode(-1, null);
  public static AllocNode dummyNode=new AllocNode(-1, null, false, dummySite);

  public static class AllocNode {
    int allocsite;
    boolean summary;
    FlatNew node;
    AllocSiteNode as;
    
    public AllocNode(int allocsite, FlatNew node, boolean summary) {
      this.allocsite=allocsite;
      this.summary=summary;
      this.node=node;
    }

    public AllocNode(int allocsite, FlatNew node, boolean summary, AllocSiteNode as) {
      this.allocsite=allocsite;
      this.summary=summary;
      this.node=node;
      this.as=as;
    }

    public AllocSiteNode getAllocSite() {
      return as;
    }

    public TypeDescriptor getType() {
      return node.getType();
    }

    public FlatNew getFlatNew() {
      return node;
    }

    public int getUniqueAllocSiteID() {
      return allocsite;
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

  public static class AllocSiteNode implements Alloc {
    int allocsite;
    FlatNew node;
    
    public AllocSiteNode(int allocsite, FlatNew node) {
      this.allocsite=allocsite;
      this.node=node;
    }

    public TypeDescriptor getType() {
      return node.getType();
    }

    public FlatNew getFlatNew() {
      return node;
    }

    public int getUniqueAllocSiteID() {
      return allocsite;
    }

    public int hashCode() {
      return allocsite;
    }
    
    public boolean equals(Object o) {
      if (o instanceof AllocSiteNode) {
	AllocSiteNode an=(AllocSiteNode)o;
	return (allocsite==an.allocsite);
      }
      return false;
    }

    public String toStringBrief() {
      return getID();
    }
    
    public String toString() {
      return getID();
    }

    public String getID() {
      return "N"+allocsite;
    }
  }

  public AllocFactory(State state, TypeUtil typeUtil) {
    allocMap=new HashMap<FlatNew, Integer>();
    allocNodeMap=new HashMap<AllocNode, AllocNode>();
    allocSiteMap=new HashMap<AllocSiteNode, AllocSiteNode>();
    this.typeUtil=typeUtil;
    ClassDescriptor stringcd=typeUtil.getClass(TypeUtil.StringClass);
    TypeDescriptor stringtd=new TypeDescriptor(stringcd);
    TypeDescriptor stringarraytd=stringtd.makeArray(state);
    StringArray=new AllocNode(0, new FlatNew(stringarraytd, null, false), false);
    Strings=new AllocNode(1, new FlatNew(stringtd, null, false), true);
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
    AllocNode key=new AllocNode(site, node, isSummary);
    if (!allocNodeMap.containsKey(key)) {
      allocNodeMap.put(key, key);
      key.as=getAllocSite(key);
      return key;
    } else
      return allocNodeMap.get(key);
  }

  public AllocNode getAllocNode(AllocNode node, boolean isSummary) {
    int site=node.allocsite;
    AllocNode key=new AllocNode(site, node.node, isSummary);
    if (!allocNodeMap.containsKey(key)) {
      allocNodeMap.put(key, key);
      key.as=getAllocSite(key);
      return key;
    } else
      return allocNodeMap.get(key);
  }

  public AllocSiteNode getAllocSite(AllocNode node) {
    AllocSiteNode as=new AllocSiteNode(node.allocsite, node.node);
    if (!allocSiteMap.containsKey(as)) {
      allocSiteMap.put(as, as);
      return as;
    } else
      return allocSiteMap.get(as);
  }

  HashMap<AllocNode, AllocNode> allocNodeMap;
  HashMap<AllocSiteNode, AllocSiteNode> allocSiteMap;
  HashMap<FlatNew, Integer> allocMap;
  TypeUtil typeUtil;
  int siteCounter=2;

  public AllocNode StringArray;
  public AllocNode Strings;
}