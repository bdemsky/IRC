package Analysis.TaskStateAnalysis;
import IR.*;
import java.util.*;
import Util.Edge;

/* Edge *****************/

public class FEdge extends Edge {

  private String label;
  private TaskDescriptor td;
  private int parameterindex;

  // jzhou
  private int executeTime;
  private Hashtable<ClassDescriptor, NewObjInfo> newObjInfos;
  private double probability;
  private int invokeNum;
  private int expInvokeNum;
  private boolean m_isbackedge;

  public class NewObjInfo {
    int newRate;
    int probability;
    FlagState root;
    int invokeNum;

    public NewObjInfo() {
      newRate = 0;
      probability = 0;
      root = null;
      invokeNum = 0;
    }

    public NewObjInfo(int newRate, int probability) {
      this.newRate = newRate;
      this.probability = probability;
    }

    public int getNewRate() {
      return this.newRate;
    }

    public void setNewRate(int newRate) {
      this.newRate = newRate;
    }

    public int getProbability() {
      return this.probability;
    }

    public void setProbability(int probability) {
      this.probability = probability;
    }

    public FlagState getRoot() {
      return root;
    }

    public void setRoot(FlagState root) {
      this.root = root;
    }

    public int getInvokeNum() {
      return invokeNum;
    }

    public void incInvokeNum() {
      this.invokeNum++;
    }

    public boolean equals(Object o) {
      if (o instanceof NewObjInfo) {
	NewObjInfo e=(NewObjInfo)o;
	if (e.newRate == this.newRate &&
	    e.probability == this.probability &&
	    e.invokeNum == this.invokeNum &&
	    e.root.equals(this.root)) {
	  return true;
	}
      }
      return false;
    }
  }

  /** Class Constructor
   *
   */
  public FEdge(FlagState target, String label, TaskDescriptor td, int parameterindex) {
    super(target);
    this.label = label;
    this.td=td;
    this.parameterindex=parameterindex;
    this.executeTime = -1;
    this.newObjInfos = null;
    this.probability = -1;
    this.invokeNum = 0;
    this.expInvokeNum = 0;
    this.m_isbackedge = false;
  }

  public double getProbability() {
    return this.probability;
  }

  public void setProbability(double probability) {
    this.probability = probability;
  }

  public boolean isbackedge() {
    return m_isbackedge;
  }

  public void setisbackedge(boolean isbackedge) {
    this.m_isbackedge = isbackedge;
  }

  public String getLabel() {
    return label;
  }

  public int hashCode() {
    int hashcode = label.hashCode()^target.hashCode()^source.hashCode()^parameterindex^executeTime;
    if (td!=null)
      hashcode^=td.hashCode();
    if(newObjInfos != null) {
      hashcode ^= newObjInfos.hashCode();
    }
    return hashcode;
  }

  public TaskDescriptor getTask() {
    return td;
  }

  public int getIndex() {
    return parameterindex;
  }

  public boolean equals(Object o) {
    if (o instanceof FEdge) {
      FEdge e=(FEdge)o;
      if (e.label.equals(label)&&
          e.target.equals(target)&&
          e.source.equals(source) &&
          e.td==td&&
          e.parameterindex==parameterindex &&
          e.executeTime == executeTime) {
	if(this.newObjInfos != null) {
	  if(e.newObjInfos == null) {
	    return false;
	  } else {
	    return e.newObjInfos.equals(this.newObjInfos);
	  }
	}
	return true;
      }
    }
    return false;
  }

  public int getExeTime() {
    return this.executeTime;
  }

  public void setExeTime(int eTime) {
    this.executeTime = eTime;
  }

  public Hashtable<ClassDescriptor, NewObjInfo> getNewObjInfoHashtable() {
    return this.newObjInfos;
  }

  public NewObjInfo getNewObjInfo(ClassDescriptor cd) {
    if(this.newObjInfos == null) {
      return null;
    }
    return this.newObjInfos.get(cd);
  }

  public void addNewObjInfo(ClassDescriptor cd, int newRate, int probability) {
    if(this.newObjInfos == null) {
      this.newObjInfos = new Hashtable<ClassDescriptor, NewObjInfo>();
    }
    this.newObjInfos.put(cd, new NewObjInfo(newRate, probability));
  }

  public void process() {
    this.invokeNum++;
  }

  public int getInvokeNum() {
    return invokeNum;
  }

  public int getInvokeNumGap() {
    return this.expInvokeNum - this.invokeNum;
  }

  public void setExpInvokeNum(int expInvokeNum) {
    this.expInvokeNum = expInvokeNum;
  }

}
