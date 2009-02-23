package Analysis.Scheduling;

import java.util.Vector;

public class CombinationUtil {
  static CombinationUtil cu;

  public CombinationUtil() {
  }

  public static CombinationUtil allocateCombinationUtil() {
    if(cu == null) {
      cu = new CombinationUtil();
    }
    return cu;
  }

  public static RootsGenerator allocateRootsGenerator(Vector<Vector<ScheduleNode>> snodevecs, 
	                                              int rootNum) {
    return CombinationUtil.allocateCombinationUtil().new RootsGenerator(snodevecs, rootNum);
  }

  public static CombineGenerator allocateCombineGenerator(Vector<Vector<ScheduleNode>> rootnodes, 
	                                                  Vector<Vector<ScheduleNode>> node2combine) {
    return CombinationUtil.allocateCombinationUtil().new CombineGenerator(rootnodes, node2combine);
  }

  public class RootsGenerator {
    Vector<Vector<ScheduleNode>> sNodeVecs;
    Vector<Vector<ScheduleNode>> node2Combine;
    Vector<Vector<ScheduleNode>> rootNodes;
    int rootNum;

    public RootsGenerator(Vector<Vector<ScheduleNode>> snodevecs, int rootNum) {
      this.sNodeVecs = snodevecs;
      this.rootNum = rootNum;
      this.node2Combine = null;
      this.rootNodes = null;
    }

    public boolean nextGen() {
      boolean trial = false;
      if(this.rootNodes == null) {
	int num2choose = this.rootNum;
	this.rootNodes = new Vector<Vector<ScheduleNode>>();
	this.rootNodes.add(new Vector<ScheduleNode>());
	Vector<ScheduleNode> toadd = this.sNodeVecs.elementAt(0);
	for(int i = 0; i < toadd.size(); i++) {
	  // should be only one element: startup object
	  this.rootNodes.elementAt(0).add(toadd.elementAt(i));
	  num2choose--;
	}
	int next = 1;
	trial = trial(num2choose, next);
	toadd = null;
      } else {
	if(this.rootNodes.size() == 1) {
	  return false;
	}
	int next = this.rootNodes.lastElement().elementAt(0).getCid() + 1;
	int num2choose = 0;
	while(next == this.sNodeVecs.size()) {
	  // backtrack
	  num2choose = this.rootNodes.lastElement().size();
	  this.rootNodes.removeElementAt(this.rootNodes.size() - 1);
	  if(this.rootNodes.size() == 1) {
	    // only startup object left, no more choices
	    return false;
	  }
	  next = this.rootNodes.lastElement().elementAt(0).getCid() + 1;
	}
	num2choose++;
	// reduce one from the last one
	this.rootNodes.lastElement().removeElementAt(this.rootNodes.lastElement().size() - 1);
	if(this.rootNodes.lastElement().size() == 0) {
	  this.rootNodes.removeElementAt(this.rootNodes.size() - 1);
	}

	trial = trial(num2choose, next);
      }
      if(trial) {
	// left nodes are all to be combined
	this.node2Combine = new Vector<Vector<ScheduleNode>>();
	int next = 1;
	int index = 0;
	for(int i = 1; i < this.rootNodes.size(); i++) {
	  int tmp = this.rootNodes.elementAt(i).elementAt(0).getCid();
	  while(next < tmp) {
	    Vector<ScheduleNode> toadd = this.sNodeVecs.elementAt(next);
	    if(toadd != null) {
	      this.node2Combine.add(new Vector<ScheduleNode>());
	      for(index = 0; index < toadd.size(); index++) {
		this.node2Combine.lastElement().add(toadd.elementAt(index));
	      }
	      toadd = null;
	    } else {
	      this.node2Combine.add(null);
	    }
	    next++;
	  }
	  Vector<ScheduleNode> toadd = this.sNodeVecs.elementAt(tmp);
	  if(toadd.size() > this.rootNodes.elementAt(i).size()) {
	    this.node2Combine.add(new Vector<ScheduleNode>());
	    for(index = this.rootNodes.elementAt(i).size(); index < toadd.size(); index++) {
	      this.node2Combine.lastElement().add(toadd.elementAt(index));
	    }
	  }
	  toadd = null;
	  next++;
	}
	while(next < this.sNodeVecs.size()) {
	  Vector<ScheduleNode> toadd = this.sNodeVecs.elementAt(next);
	  if(toadd != null) {
	    this.node2Combine.add(new Vector<ScheduleNode>());
	    for(index = 0; index < toadd.size(); index++) {
	      this.node2Combine.lastElement().add(toadd.elementAt(index));
	    }
	    toadd = null;
	  } else {
	    this.node2Combine.add(null);
	  }
	  next++;
	}
      }
      return trial;
    }

    private boolean trial(int num2choose, int next) {
      int index = 0;
      boolean first = true;
      while(num2choose > 0) {
	if(first) {
	  if(next == this.sNodeVecs.size()) {
	    // no more nodes available to add
	    return false;
	  }
	}
	if(this.sNodeVecs.elementAt(next) != null) {
	  if(first) {
	    this.rootNodes.add(new Vector<ScheduleNode>());
	    first = false;
	  }
	  this.rootNodes.lastElement().add(this.sNodeVecs.elementAt(next).elementAt(index));
	  num2choose--;
	  index++;
	  if(index == this.sNodeVecs.elementAt(next).size()) {
	    index = 0;
	    next++;
	    first = true;
	  }
	} else {
	  next++;
	  first = true;
	}
      }
      return true;
    }

    public Vector<Vector<ScheduleNode>> getNode2Combine() {
      return node2Combine;
    }

    public Vector<Vector<ScheduleNode>> getRootNodes() {
      return rootNodes;
    }
  }

  public class Combine {
    public ScheduleNode node;
    public int root;
    public int index;

    public Combine(ScheduleNode n) {
      this.node = n;
      this.root = -1;
      this.index = -1;
    }
  }

  public class CombineGenerator {
    Vector<Vector<ScheduleNode>> rootNodes;
    Vector<Vector<int[]>> rootNStates;
    Vector<Vector<ScheduleNode>> node2Combine;
    Vector<Vector<Combine>> combine;
    int[] lastchoices;
    boolean first4choice;

    public CombineGenerator(Vector<Vector<ScheduleNode>> rootnodes, Vector<Vector<ScheduleNode>> node2combine) {
      this.rootNodes = rootnodes;
      this.node2Combine = node2combine;
      this.rootNStates = new Vector<Vector<int[]>>();
      for(int i = 0; i < this.rootNodes.size(); i++) {
	this.rootNStates.add(new Vector<int[]>());
	for(int j = 0; j < this.rootNodes.elementAt(i).size(); j++) {
	  this.rootNStates.elementAt(i).add(new int[this.node2Combine.size()]);
	  for(int k = 0; k < this.node2Combine.size(); k++) {
	    this.rootNStates.elementAt(i).elementAt(j)[k] = 0;
	  }
	}
      }
      this.combine = new Vector<Vector<Combine>>();
      for(int i = 0; i < this.node2Combine.size(); i++) {
	if(this.node2Combine.elementAt(i) == null) {
	  this.combine.add(null);
	} else {
	  this.combine.add(new Vector<Combine>());
	  for(int j = 0; j < this.node2Combine.elementAt(i).size(); j++) {
	    this.combine.elementAt(i).add(new Combine(this.node2Combine.elementAt(i).elementAt(j)));
	  }
	}
      }
      this.lastchoices = null;
      this.first4choice = false;
    }

    public Vector<Vector<Combine>> getCombine() {
      return combine;
    }

    public boolean nextGen() {
      boolean trial = false;
      if(this.lastchoices == null) {
	// first time
	this.lastchoices = new int[this.node2Combine.size()];
	for(int i = 0; i < this.lastchoices.length; i++) {
	  this.lastchoices[i] = 0;
	}
	this.first4choice = true;
	trial = trial();
      } else {
	trial = trial();
	while(!trial) {
	  // no more available combination under this choice
	  // choose another choice
	  int next = this.node2Combine.size() - 1;
	  boolean iter = false;
	  do {
	    if(this.node2Combine.elementAt(next) != null) {
	      this.lastchoices[next]++;
	      if((this.lastchoices[next] == this.rootNodes.size() ||
	          (this.rootNodes.elementAt(this.lastchoices[next]).elementAt(0).getCid() >
	           this.node2Combine.elementAt(next).elementAt(0).getCid()))) {
		// break the rule that a node can only be combined to nodes with smaller colorid.
		// or no more buckets
		// backtrack
		next--;
		iter = true;
	      } else {
		iter = false;
	      }
	    } else {
	      next--;
	      iter = true;
	    }
	  } while(iter && !(next < 0));
	  if(next < 0) {
	    return false;
	  }
	  for(next += 1; next < this.node2Combine.size(); next++) {
	    this.lastchoices[next] = 0;
	  }
	  this.first4choice = true;
	  trial = trial();
	}
      }
      return trial;
    }

    private boolean trial() {
      boolean suc = false;
      if(this.first4choice) {
	// first time for each choice
	// put all the objects of one color into the first bucket indicated by the choice
	int next = 0;
	suc = firstexpand(next, this.first4choice);
	this.first4choice = false;
      } else {
	int next = this.node2Combine.size() - 1;
	int layer = 0;
	suc = innertrial(next, layer);
      }
      return suc;
    }

    private boolean firstexpand(int next, boolean first) {
      for(int i = next; i < this.node2Combine.size(); i++) {
	if(this.node2Combine.elementAt(i) != null) {
	  int choice = this.lastchoices[i];
	  for(int j = 0; j < this.node2Combine.elementAt(i).size(); j++) {
	    Combine tmp = this.combine.elementAt(i).elementAt(j);
	    if(!first) {
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	    }
	    tmp.root = choice;
	    tmp.index = 0;
	    if(!first) {
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	    }
	  }
	  if(first) {
	    this.rootNStates.elementAt(choice).elementAt(0)[i] = this.node2Combine.elementAt(i).size();
	    for(int j = 1; j < this.rootNodes.elementAt(choice).size(); j++) {
	      this.rootNStates.elementAt(choice).elementAt(j)[i] = 0;
	    }
	  }
	}
      }
      return true;
    }

    private boolean innertrial(int next, int layer) {
      if((this.combine.elementAt(next) == null) ||
         (this.combine.elementAt(next).size() < 2)) {
	// skip over empty buckets and bucket with only one obj ( make sure
	// at least one obj is existing in the chosen bucket)
	if(next - 1 < 0) {
	  return false;
	} else {
	  return innertrial(next - 1, ++layer);
	}
      }
      Combine tmp = this.combine.elementAt(next).lastElement();
      // try to move it backward
      int root = tmp.root;
      int index = tmp.index;
      index++;
      if(index == this.rootNodes.elementAt(root).size()) {
	// no more place in this color bucket
	index = 0;
	root++;
      } else if(this.rootNStates.elementAt(root).elementAt(index - 1)[next] <
                this.rootNStates.elementAt(root).elementAt(index)[next] + 2) {
	// break the law of non-increase order inside one color bucket
	// try next bucket of another color
	index = 0;
	root++;
      }
      if(root == this.rootNodes.size()) {
	// no more bucket
	// backtrack
	root = tmp.root;
	index = tmp.index;
	int t = this.combine.elementAt(next).size() - 2;
	while(true) {
	  while(!(t < 0)) {
	    tmp = this.combine.elementAt(next).elementAt(t);
	    if ((tmp.root != root) || (tmp.index != index)) {
	      break;
	    }
	    t--;
	  }
	  if(t < 0) {
	    // try the bucket in node2combine before
	    if(next - 1 < 0) {
	      return false;
	    } else {
	      return innertrial(next - 1, ++layer);
	    }
	  } else if(tmp.root != root) {
	    if((tmp.root == this.lastchoices[next]) &&
	       (this.rootNStates.elementAt(this.lastchoices[next]).elementAt(0)[next] == 1)) {
	      // only 1 obj left in the chosen bucket
	      // can not move this one
	      // try the bucket in node2combine before
	      if(next - 1 < 0) {
		return false;
	      } else {
		return innertrial(next - 1, ++layer);
	      }
	    }
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	    //tmp.root = root;
	    int newroot = tmp.root + 1;
	    tmp.root = newroot;
	    tmp.index = 0;
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	    // make all left things in this color bucket reset
	    for(t++; t < this.combine.elementAt(next).size(); t++) {
	      tmp = this.combine.elementAt(next).elementAt(t);
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	      tmp.root = newroot;
	      tmp.index = 0;
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	    }
	    if(layer != 0) {
	      return firstexpand(next+1, this.first4choice);
	    }
	    return true;
	  } else if(tmp.index != index) {
	    if(this.rootNStates.elementAt(root).elementAt(tmp.index)[next] ==
	       this.rootNStates.elementAt(root).elementAt(index)[next]) {
	      // break the law of non-increase order inside one color bucket
	      // go on search forward
	      index = tmp.index;
	    } else if(this.rootNStates.elementAt(root).elementAt(tmp.index)[next] <
	              this.rootNStates.elementAt(root).elementAt(index)[next] + 2) {
	      // break the law of non-increase order inside one color bucket
	      // and now they only differ by 1
	      // propagate this difference to see if it can fix
	      boolean suc = propagateOne(next, root, index, t, tmp);
	      if(suc) {
		return suc;
	      } else {
		// go on search forward
		index = tmp.index;
	      }
	    } else {
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	      int tmproot = tmp.root;
	      int tmpindex = tmp.index;
	      tmp.root = root;
	      tmp.index = index;
	      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	      int desroot = tmp.root;
	      int desindex = tmp.index;
	      // make all left things in this color bucket reset
	      t++;
	      boolean first = true;
	      while(t < this.combine.elementAt(next).size()) {
		int k = 0;
		if(first) {
		  k = 1;
		  first = false;
		}
		for(; (k < this.rootNStates.elementAt(tmproot).elementAt(tmpindex)[next]) && (t < this.combine.elementAt(next).size()); t++) {
		  tmp = this.combine.elementAt(next).elementAt(t);
		  this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
		  tmp.root = desroot;
		  tmp.index = desindex;
		  this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
		}
		if(k == this.rootNStates.elementAt(tmproot).elementAt(tmpindex)[next]) {
		  desindex++;
		}
	      }
	      if(layer != 0) {
		return firstexpand(next+1, this.first4choice);
	      }
	      return true;
	    }
	  }
	}
      } else {
	if((tmp.root != this.lastchoices[next]) ||
	   (this.rootNStates.elementAt(this.lastchoices[next]).elementAt(0)[next] > 1)) {
	  this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	  tmp.root = root;
	  tmp.index = index;
	  this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	  if(layer != 0) {
	    return firstexpand(next+1, this.first4choice);
	  }
	  return true;
	} else {
	  // only 1 obj with the color next exist on the chosen bucket this time,
	  // can not move it, try objs in forward color bucket
	  if(next - 1 < 0) {
	    return false;
	  } else {
	    return innertrial(next - 1, ++layer);
	  }
	}
      }
    }

    private boolean propagateOne(int next, int rooti, int indexi, int ti, Combine tmp) {
      int root = rooti;
      int index = indexi;
      int t = ti;
      int rootbk = tmp.root;
      int indexbk = tmp.index;
      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
      tmp.root = root;
      tmp.index = index;
      this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
      t += 2;
      Combine tmpt = null;
      if(this.rootNStates.elementAt(root).elementAt(index - 1)[next] <
         this.rootNStates.elementAt(root).elementAt(index)[next]) {
	// need to continue propagate
	while(t < this.combine.elementAt(next).size()) {
	  tmpt = this.combine.elementAt(next).elementAt(t);
	  if ((tmpt.root != root) || (tmpt.index != index)) {
	    break;
	  }
	  t++;
	}
	if(t == this.combine.elementAt(next).size()) {
	  // last element of this color bucket
	  if(index + 1 < this.rootNodes.elementAt(root).size()) {
	    // there is available place inside the same color bucket
	    Combine tmpbk = this.combine.elementAt(next).elementAt(t - 1);
	    boolean suc = propagateOne(next, root, index + 1, t - 1, tmpbk);
	    /*if(!suc) {
	        // fail, roll back
	        this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	        tmp.root = rootbk;
	        tmp.index = indexbk;
	        this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	       }*/
	    return suc;
	  } else if(root+1 < this.rootNodes.size()) {           // check if there are another bucket
	    // yes
	    this.rootNStates.elementAt(tmpt.root).elementAt(tmpt.index)[next]--;
	    tmpt.root = root + 1;
	    tmpt.index = 0;
	    this.rootNStates.elementAt(tmpt.root).elementAt(tmpt.index)[next]++;
	    return firstexpand(next+1, this.first4choice);
	  } else {
	    // no, roll back
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	    tmp.root = rootbk;
	    tmp.index = indexbk;
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	    return false;
	  }
	} else if(tmpt.root != root) {
	  Combine tmpbk = this.combine.elementAt(next).elementAt(t - 1);
	  this.rootNStates.elementAt(tmpbk.root).elementAt(tmpbk.index)[next]--;
	  tmpbk.root = tmpt.root;
	  tmpbk.index = 0;
	  this.rootNStates.elementAt(tmpbk.root).elementAt(tmpbk.index)[next]++;
	  root = tmpt.root;
	  index = tmpt.index;
	  // make all left things in this color bucket reset
	  for(t += 1; t < this.combine.elementAt(next).size(); t++) {
	    tmpt = this.combine.elementAt(next).elementAt(t);
	    this.rootNStates.elementAt(tmpt.root).elementAt(tmpt.index)[next]--;
	    tmpt.root = root;
	    tmpt.index = 0;
	    this.rootNStates.elementAt(tmpt.root).elementAt(tmpt.index)[next]++;
	  }
	  return firstexpand(next+1, this.first4choice);
	} else if(tmpt.index != index) {
	  Combine tmpbk = this.combine.elementAt(next).elementAt(t - 1);
	  boolean suc = propagateOne(next, root, tmpt.index, t - 1, tmpbk);
	  if(!suc) {
	    // fail, roll back
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]--;
	    tmp.root = rootbk;
	    tmp.index = indexbk;
	    this.rootNStates.elementAt(tmp.root).elementAt(tmp.index)[next]++;
	  }
	  return suc;
	}
	// won't reach here, only to avoid compiler's complain
	return true;
      } else {
	return true;
      }
    }
  }
}