package Analysis.TaskStateAnalysis;

import Analysis.Scheduling.ScheduleEdge;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;
import Util.GraphNode;

/** This class is used to hold the flag states that a class in the Bristlecone
 *  program can exist in, during runtime.
 */
public class FlagState extends GraphNode implements Cloneable {
  public static final int ONETAG=1;
  public static final int NOTAGS=0;
  public static final int MULTITAGS=-1;
  public static final int MAXTIME=10;

  private int uid;
  private static int nodeid=0;

  private final HashSet flagstate;
  private final ClassDescriptor cd;
  private final Hashtable<TagDescriptor,Integer> tags;
  private boolean issourcenode;
  private Vector tasks;
  public static final int KLIMIT=2;

  // jzhou
  // for static scheduling
  private int executeTime;
  private int visited4time;
  private int invokeNum;
  // for building multicore codes
  private int andmask;
  private int checkmask;
  private boolean setmask;
  private int iuid;
  //private boolean isolate;
  //private Vector<ScheduleEdge> allys;

  /** Class constructor
   *  Creates a new flagstate with all flags set to false.
   *	@param cd ClassDescriptor
   */
  public FlagState(ClassDescriptor cd) {
    this.flagstate=new HashSet();
    this.cd=cd;
    this.tags=new Hashtable<TagDescriptor,Integer>();
    this.uid=FlagState.nodeid++;
    this.issourcenode=false;
    this.executeTime = -1;
    this.visited4time = -1;
    this.invokeNum = 0;
    this.andmask = 0;
    this.checkmask = 0;
    this.setmask = false;
    this.iuid = 0;
    //this.isolate = true;
    //this.allys = null;
  }

  /** Class constructor
   *  Creates a new flagstate with flags set according to the HashSet.
   *  If the flag exists in the hashset, it's set to true else set to false.
   *	@param cd ClassDescriptor
   *  @param flagstate a <CODE>HashSet</CODE> containing FlagDescriptors
   */
  private FlagState(HashSet flagstate, ClassDescriptor cd,Hashtable<TagDescriptor,Integer> tags) {
    this.flagstate=flagstate;
    this.cd=cd;
    this.tags=tags;
    this.uid=FlagState.nodeid++;
    this.issourcenode=false;
    this.executeTime = -1;
    this.visited4time = -1;
    this.invokeNum = 0;
  }

  public int getuid() {
    return uid;
  }

  public int getiuid() {
    return iuid++;
  }

  public boolean isSetmask() {
    return setmask;
  }

  public void setSetmask(boolean setmask) {
    this.setmask = setmask;
  }

  /** Accessor method
   *  @param fd FlagDescriptor
   *  @return true if the flagstate contains fd else false.
   */
  public boolean get(FlagDescriptor fd) {
    return flagstate.contains(fd);
  }

  /** Checks if the flagstate is a source node.
   *  @return true if the flagstate is a sourcenode(i.e. Is the product of an allocation site).
   */

  public boolean isSourceNode() {
    return issourcenode;
  }

  /**  Sets the flagstate as a source node.
   */
  public void setAsSourceNode() {
    if(!issourcenode) {
      issourcenode=true;
      this.tasks=new Vector();
    }
  }

  public void addAllocatingTask(TaskDescriptor task) {
    tasks.add(task);
  }

  public Vector getAllocatingTasks() {
    return tasks;
  }


  public String toString() {
    return cd.toString()+getTextLabel();
  }

  /** @return Iterator over the flags in the flagstate.
   */

  public Iterator getFlags() {
    return flagstate.iterator();
  }

  public int numFlags() {
    return flagstate.size();
  }

  public FlagState[] setTag(TagDescriptor tag, boolean set) {
    HashSet newset1=(HashSet)flagstate.clone();
    Hashtable<TagDescriptor,Integer> newtags1=(Hashtable<TagDescriptor,Integer>)tags.clone();

    if (set) {
      int count=0;
      if (tags.containsKey(tag))
	count=tags.get(tag).intValue();
      if (count<KLIMIT)
	count++;
      newtags1.put(tag, new Integer(count));
      return new FlagState[] {new FlagState(newset1, cd, newtags1)};
    } else {
      int count=1;
      if (tags.containsKey(tag))
	count=tags.get(tag).intValue();
      newtags1.put(tag, new Integer(count));
      if ((count+1)==KLIMIT)
	return new FlagState[] {this, new FlagState(newset1, cd, newtags1)};
      else
	return new FlagState[] {new FlagState(newset1, cd, newtags1)};
    }
  }

  public FlagState[] setTag(TagDescriptor tag) {
    HashSet newset1=(HashSet)flagstate.clone();
    Hashtable<TagDescriptor,Integer> newtags1=(Hashtable<TagDescriptor,Integer>)tags.clone();

    if (tags.containsKey(tag)) {
      //Code could try to remove flag that doesn't exist

      switch (tags.get(tag).intValue()) {
      case ONETAG:
	newtags1.put(tag,new Integer(MULTITAGS));
	return new FlagState[] {this, new FlagState(newset1, cd, newtags1)};

      case MULTITAGS:
	return new FlagState[] {this};

      default:
	throw new Error();
      }
    } else {
      newtags1.put(tag,new Integer(ONETAG));
      return new FlagState[] {new FlagState(newset1,cd,newtags1)};
    }
  }

  public int getTagCount(TagDescriptor tag) {
    if (tags.containsKey(tag))
      return tags.get(tag).intValue();
    else return 0;
  }

  public int getTagCount(String tagtype) {
    return getTagCount(new TagDescriptor(tagtype));
  }

  public FlagState[] clearTag(TagDescriptor tag) {
    if (tags.containsKey(tag)) {
      switch(tags.get(tag).intValue()) {
      case ONETAG:
	HashSet newset=(HashSet)flagstate.clone();
	Hashtable<TagDescriptor,Integer> newtags=(Hashtable<TagDescriptor,Integer>)tags.clone();
	newtags.remove(tag);
	return new FlagState[] {new FlagState(newset,cd,newtags)};

      case MULTITAGS:
	//two possibilities - count remains 2 or becomes 1
	//2 case
	HashSet newset1=(HashSet)flagstate.clone();
	Hashtable<TagDescriptor,Integer> newtags1=(Hashtable<TagDescriptor,Integer>)tags.clone();

	//1 case
	HashSet newset2=(HashSet)flagstate.clone();
	Hashtable<TagDescriptor,Integer> newtags2=(Hashtable<TagDescriptor,Integer>)tags.clone();
	newtags1.put(tag,new Integer(ONETAG));
	return new FlagState[] {new FlagState(newset1, cd, newtags2),
		                new FlagState(newset2, cd, newtags2)};

      default:
	throw new Error();
      }
    } else {
      throw new Error("Invalid Operation: Can not clear a tag that doesn't exist.");
    }
  }

  /** Creates a string description of the flagstate
   *  e.g.  a flagstate with five flags could look like 01001
   *  @param flags an array of flagdescriptors.
   *  @return string representation of the flagstate.
   */
  public String toString(FlagDescriptor[] flags) {
    StringBuffer sb = new StringBuffer(flagstate.size());
    for(int i=0; i < flags.length; i++) {
      if (get(flags[i]))
	sb.append(1);
      else
	sb.append(0);
    }

    return new String(sb);
  }

  /** Accessor method
   *  @return returns the classdescriptor of the flagstate.
   */

  public ClassDescriptor getClassDescriptor() {
    return cd;
  }

  /** Sets the status of a specific flag in a flagstate after cloning it.
   *  @param	fd FlagDescriptor of the flag whose status is being set.
   *  @param  status boolean value
   *  @return the new flagstate with <CODE>fd</CODE> set to <CODE>status</CODE>.
   */

  public FlagState setFlag(FlagDescriptor fd, boolean status) {
    HashSet newset=(HashSet) flagstate.clone();
    Hashtable<TagDescriptor,Integer> newtags=(Hashtable<TagDescriptor,Integer>)tags.clone();
    if (status)
      newset.add(fd);
    else if (newset.contains(fd)) {
      newset.remove(fd);
    }

    return new FlagState(newset, cd, newtags);
  }

  /** Tests for equality of two flagstate objects.
   */

  public boolean equals(Object o) {
    if (o instanceof FlagState) {
      FlagState fs=(FlagState)o;
      if (fs.cd!=cd)
	return false;
      return (fs.flagstate.equals(flagstate) & fs.tags.equals(tags));
    }
    return false;
  }

  public int hashCode() {
    return cd.hashCode()^flagstate.hashCode()^tags.hashCode();
  }

  public String getLabel() {
    return "N"+uid;
  }

  public String getTextLabel() {
    String label=null;
    for(Iterator it=getFlags(); it.hasNext();) {
      FlagDescriptor fd=(FlagDescriptor) it.next();
      if (label==null)
	label=fd.toString();
      else
	label+=", "+fd.toString();
    }
    for (Enumeration en_tags=getTags(); en_tags.hasMoreElements();) {
      TagDescriptor td=(TagDescriptor)en_tags.nextElement();
      switch (tags.get(td).intValue()) {
      case ONETAG:
	if (label==null)
	  label=td.toString()+"(1)";
	else
	  label+=", "+td.toString()+"(1)";
	break;

      case MULTITAGS:
	if (label==null)
	  label=td.toString()+"(n)";
	else
	  label+=", "+td.toString()+"(n)";
	break;

      default:
	break;
      }
    }
    if (label==null)
      return " ";
    return label;
  }

  public Enumeration getTags() {
    return tags.keys();
  }

  public int getExeTime() {
    try {
      if(this.executeTime == -1) {
	if(this.visited4time == -1) {
	  this.visited4time = 0;
	  calExeTime();
	} else {
	  // visited, this node is in a loop
	  // TODO
	  // currently set 10 as the largest time
	  this.executeTime = FlagState.MAXTIME;
	}
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
    return this.executeTime;
  }

  public void setExeTime(int exeTime) {
    this.executeTime = exeTime;
  }

  public int getAndmask() {
    return andmask;
  }

  public void setAndmask(int andmask) {
    this.andmask = andmask;
  }

  public int getCheckmask() {
    return checkmask;
  }

  public void setCheckmask(int checkmask) {
    this.checkmask = checkmask;
  }

  public void calExeTime() throws Exception {
    Iterator it = this.edges();
    if(it.hasNext()) {
      FEdge fe = (FEdge)it.next();
      while((fe != null) && (fe.getTarget().equals(this))) {
	if(it.hasNext()) {
	  fe = (FEdge)it.next();
	} else {
	  fe = null;
	}
      }
      if(fe == null) {
	this.executeTime = 0;
      } else {
	if(fe.getExeTime() == -1) {
	  throw new Exception("Error: Uninitiate FEdge!");
	}
	this.executeTime = fe.getExeTime() + ((FlagState)fe.getTarget()).getExeTime();
      }
    } else {
      this.executeTime = 0;
    }
    while(it.hasNext()) {
      FEdge fe = (FEdge)it.next();
      int temp = fe.getExeTime() + ((FlagState)fe.getTarget()).getExeTime();
      if(temp < this.executeTime) {
	this.executeTime = temp;
      }
    }
  }

  public Object clone() {
    FlagState o = null;
    try {
      o = (FlagState) super.clone();
    } catch(CloneNotSupportedException e) {
      e.printStackTrace();
    }
    o.uid = FlagState.nodeid++;
    o.edges = new Vector();
    for(int i = 0; i < this.edges.size(); i++) {
      o.edges.addElement(this.edges.elementAt(i));
    }
    o.inedges = new Vector();
    for(int i = 0; i < this.inedges.size(); i++) {
      o.inedges.addElement(this.inedges.elementAt(i));
    }
    return o;
  }

  public void init4Simulate() {
    this.invokeNum = 0;
  }

  public FEdge process(TaskDescriptor td) {
    FEdge next = null;
    this.invokeNum++;
    // refresh all the expInvokeNum of each edge
    for(int i = 0; i < this.edges.size(); i++) {
      next = (FEdge) this.edges.elementAt(i);
      next.setExpInvokeNum((int)(Math.ceil(this.invokeNum * next.getProbability() / 100)));
    }

    // find the one with the biggest gap between its actual invoke time and the expected invoke time
    // and associated with task td
    int index = 0;
    int gap = 0;
    double prob = 0;
    boolean isbackedge = true;
    for(int i = 0; i < this.edges.size(); i++) {
      next = ((FEdge) this.edges.elementAt(i));
      int temp = next.getInvokeNumGap();
      boolean exchange = false;
      if((temp > gap) && (next.getTask().equals(td))) {
	exchange = true;
      } else if(temp == gap) {
	if(next.getProbability() > prob) {
	  exchange = true;
	} else if(next.getProbability() == prob) {
	  if(!isbackedge && next.isbackedge()) {
	    // backedge has higher priority
	    exchange = true;
	  }
	}
      }
      if(exchange) {
	index = i;
	gap = temp;
	prob = next.getProbability();
	isbackedge = next.isbackedge();
      }
    }
    next = (FEdge) this.edges.elementAt(index);
    next.process();

    return next;
  }

  /*public Vector<ScheduleEdge> getAllys() {
      return allys;
     }

     public void addAlly(ScheduleEdge se) {
      if(this.allys == null) {
          assert(this.isolate == true);
          this.isolate = false;
          this.allys = new Vector<ScheduleEdge>();
      }
      this.allys.addElement(se);
     }

     public boolean isIsolate() {
      return isolate;
     }*/

}
