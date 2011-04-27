package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;
import Util.GraphNode;
import Util.GraphNode.SCC;

import java.io.FileInputStream;
import java.util.*;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleAnalysis {

  State state;
  TaskAnalysis taskanalysis;

  Vector<ScheduleNode> scheduleNodes;
  Vector<ClassNode> classNodes;
  Vector<ScheduleEdge> scheduleEdges;
  Hashtable<ClassDescriptor, ClassNode> cd2ClassNode;
  boolean sorted = false;

  int transThreshold;

  int scheduleThreshold;
  int coreNum;
  Vector<Vector<ScheduleNode>> scheduleGraphs;

  // Main CD table for multi-param tasks
  Hashtable<TaskDescriptor, ClassDescriptor> td2maincd;

  public ScheduleAnalysis(State state,
                          TaskAnalysis taskanalysis) {
    this.state = state;
    this.taskanalysis = taskanalysis;
    this.scheduleNodes = new Vector<ScheduleNode>();
    this.classNodes = new Vector<ClassNode>();
    this.scheduleEdges = new Vector<ScheduleEdge>();
    this.cd2ClassNode = new Hashtable<ClassDescriptor, ClassNode>();
    this.transThreshold = 45; // defaultly 45
    this.scheduleThreshold = 1000; // defaultly 1000
    this.coreNum = -1;
    this.scheduleGraphs = null;
    this.td2maincd = null;
  }

  public void setTransThreshold(int tt) {
    this.transThreshold = tt;
  }

  public void setScheduleThreshold(int stt) {
    this.scheduleThreshold = stt;
  }

  public int getCoreNum() {
    return coreNum;
  }

  public void setCoreNum(int coreNum) {
    this.coreNum = coreNum;
  }

  public Vector<Vector<ScheduleNode>> getScheduleGraphs() {
    return this.scheduleGraphs;
  }

  // for test
  public Vector<ScheduleEdge> getSEdges4Test() {
    return scheduleEdges;
  }

  public Hashtable<TaskDescriptor, ClassDescriptor> getTd2maincd() {
    // TODO, for test
    /*Iterator<TaskDescriptor> key = td2maincd.keySet().iterator();
       while(key.hasNext()) {
       TaskDescriptor td = key.next();
       System.err.println(td.getSymbol() + ", maincd: "
     + this.td2maincd.get(td).getSymbol());
       }*/

    return td2maincd;
  }

  public boolean schedule(int generateThreshold,
                          int skipThreshold,
                          Vector<TaskDescriptor> multiparamtds) {
    boolean tooptimize = true;
    try {
      Vector<ScheduleEdge> toBreakDown = new Vector<ScheduleEdge>();
      ScheduleNode startupNode = null;

      if((multiparamtds != null) || (multiparamtds.size() > 0)) {
        this.td2maincd = new Hashtable<TaskDescriptor, ClassDescriptor>();
      }

      // necessary preparation such as read profile info etc.
      preSchedule();
      // build the CFSTG
      startupNode = buildCFSTG(toBreakDown, multiparamtds);
      // do Tree transform
      treeTransform(toBreakDown, startupNode);
      // do CFSTG transform to explore the potential parallelism as much
      // as possible
      CFSTGTransform();
      // mappint to real multi-core processor
      tooptimize = coreMapping(generateThreshold, skipThreshold);
      toBreakDown = null;
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    return tooptimize;
  }

  private void preSchedule() {
    this.checkBackEdge();

    // set up profiling data
    if(state.USEPROFILE) {
      java.util.Hashtable<String, TaskInfo> taskinfos =
        new java.util.Hashtable<String, TaskInfo>();
      this.readProfileInfo(taskinfos);

      long tint = 0;
      Iterator it_classes = state.getClassSymbolTable().getDescriptorsIterator();
      while(it_classes.hasNext()) {
        ClassDescriptor cd = (ClassDescriptor) it_classes.next();
        if(cd.hasFlags()) {
          Vector rootnodes = this.taskanalysis.getRootNodes(cd);
          if(rootnodes!=null) {
            Iterator it_rootnodes = rootnodes.iterator();
            while(it_rootnodes.hasNext()) {
              FlagState root = (FlagState)it_rootnodes.next();
              Vector allocatingTasks = root.getAllocatingTasks();
              if(allocatingTasks != null) {
                for(int k = 0; k < allocatingTasks.size(); k++) {
                  TaskDescriptor td =
                    (TaskDescriptor)allocatingTasks.elementAt(k);
                  Vector<FEdge> fev = this.taskanalysis.getFEdgesFromTD(td);
                  int numEdges = fev.size();
                  for(int j = 0; j < numEdges; j++) {
                    FEdge pfe = fev.elementAt(j);
                    TaskInfo taskinfo = taskinfos.get(td.getSymbol());
                    tint = taskinfo.m_exetime[pfe.getTaskExitIndex()];
                    pfe.setExeTime(tint);
                    double idouble =
                      taskinfo.m_probability[pfe.getTaskExitIndex()];
                    pfe.setProbability(idouble);
                    int newRate = 0;
                    int tindex = pfe.getTaskExitIndex();
                    if((taskinfo.m_newobjinfo.elementAt(tindex) != null)
                       && (taskinfo.m_newobjinfo.elementAt(tindex).containsKey(
                             cd.getSymbol()))) {
                      newRate = taskinfo.m_newobjinfo.elementAt(tindex).get(
                        cd.getSymbol());
                    }
                    pfe.addNewObjInfo(cd, newRate, idouble);
                    if(taskinfo.m_byObj != -1) {
                      ((FlagState)pfe.getSource()).setByObj(taskinfo.m_byObj);
                    }
                    // TODO for test
                    /*System.err.println("task " + td.getSymbol() + " exit# " +
                        pfe.getTaskExitIndex() + " exetime: " + pfe.getExeTime()
                     + " prob: " + pfe.getProbability() + "% newobj: "
                     + pfe.getNewObjInfoHashtable().size());*/
                  }
                  fev = null;
                }
              }
            }
            it_rootnodes = null;
          }
          Iterator it_flags = this.taskanalysis.getFlagStates(cd).iterator();
          while(it_flags.hasNext()) {
            FlagState fs = (FlagState)it_flags.next();
            Iterator it_edges = fs.edges();
            while(it_edges.hasNext()) {
              FEdge edge = (FEdge)it_edges.next();
              TaskInfo taskinfo = taskinfos.get(edge.getTask().getSymbol());
              double idouble = 0.0;
              if(edge.getTaskExitIndex() >= taskinfo.m_exetime.length) {
                tint = 0;
              } else {
                tint = taskinfo.m_exetime[edge.getTaskExitIndex()];
                idouble = taskinfo.m_probability[edge.getTaskExitIndex()];
              }
              edge.setExeTime(tint);
              edge.setProbability(idouble);
              if(taskinfo.m_byObj != -1) {
                ((FlagState)edge.getSource()).setByObj(taskinfo.m_byObj);
              }
              // TODO for test
              /*System.err.println("task " + edge.getTask().getSymbol() + " exit# " +
                  edge.getTaskExitIndex() + " exetime: " + edge.getExeTime()
               + " prob: " + edge.getProbability());*/
            }
            it_edges = null;
          }
          it_flags = null;
        }
      }
      taskinfos = null;
      it_classes = null;
    } else {
      randomProfileSetting();
    }
  }

  private void checkBackEdge() {
    // Indentify backedges
    Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();
    while(it_classes.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor) it_classes.next();
      if(cd.hasFlags()) {
        Set<FlagState> fss = this.taskanalysis.getFlagStates(cd);
        SCC scc=GraphNode.DFS.computeSCC(fss);
        if (scc.hasCycles()) {
          for(int i=0; i<scc.numSCC(); i++) {
            if (scc.hasCycle(i)) {
              Set cycleset = scc.getSCC(i);
              Iterator it_fs = cycleset.iterator();
              while(it_fs.hasNext()) {
                FlagState fs = (FlagState)it_fs.next();
                Iterator it_edges = fs.edges();
                while(it_edges.hasNext()) {
                  FEdge edge = (FEdge)it_edges.next();
                  if(cycleset.contains(edge.getTarget())) {
                    // a backedge
                    edge.setisbackedge(true);
                  }
                }
                it_edges = null;
              }
              it_fs = null;
            }
          }
        }
        fss = null;
      }
    }
    it_classes = null;
  }

  private void readProfileInfo(java.util.Hashtable<String, TaskInfo> taskinfos) {
    try {
      // read in profile data and set
      //FileInputStream inStream = new FileInputStream("/scratch/profile.rst");
      FileInputStream inStream =
        new FileInputStream(/*"/scratch/" + */ this.state.profilename);
      byte[] b = new byte[1024 * 100];
      int length = inStream.read(b);
      if(length < 0) {
        System.out.print("No content in input file: /scratch/"
                         + this.state.profilename + "\n");
        System.exit(-1);
      }
      String profiledata = new String(b, 0, length);

      // profile data format:
      //   taskname, numoftaskexits(; exetime, probability, numofnewobjtypes(,
      //   newobj type, num of objs)+)+
      int inindex = profiledata.indexOf('\n');
      while((inindex != -1) ) {
        String inline = profiledata.substring(0, inindex);
        profiledata = profiledata.substring(inindex + 1);
        //System.printString(inline + "\n");
        int tmpinindex = inline.indexOf(',');
        if(tmpinindex == -1) {
          break;
        }
        String inname = inline.substring(0, tmpinindex);
        String inint = inline.substring(tmpinindex + 1);
        while(inint.startsWith(" ")) {
          inint = inint.substring(1);
        }
        tmpinindex = inint.indexOf(',');
        if(tmpinindex == -1) {
          break;
        }
        int numofexits = Integer.parseInt(inint.substring(0, tmpinindex));
        TaskInfo tinfo = new TaskInfo(numofexits);
        inint = inint.substring(tmpinindex + 1);
        while(inint.startsWith(" ")) {
          inint = inint.substring(1);
        }
        tmpinindex = inint.indexOf(';');
        int byObj = Integer.parseInt(inint.substring(0, tmpinindex));
        if(byObj != -1) {
          tinfo.m_byObj = byObj;
        }
        inint = inint.substring(tmpinindex + 1);
        while(inint.startsWith(" ")) {
          inint = inint.substring(1);
        }
        for(int i = 0; i < numofexits; i++) {
          String tmpinfo = null;
          if(i < numofexits - 1) {
            tmpinindex = inint.indexOf(';');
            tmpinfo = inint.substring(0, tmpinindex);
            inint = inint.substring(tmpinindex + 1);
            while(inint.startsWith(" ")) {
              inint = inint.substring(1);
            }
          } else {
            tmpinfo = inint;
          }

          tmpinindex = tmpinfo.indexOf(',');
          tinfo.m_exetime[i] = Long.parseLong(tmpinfo.substring(0, tmpinindex));
          tmpinfo = tmpinfo.substring(tmpinindex + 1);
          while(tmpinfo.startsWith(" ")) {
            tmpinfo = tmpinfo.substring(1);
          }
          tmpinindex = tmpinfo.indexOf(',');
          tinfo.m_probability[i] = Double.parseDouble(
            tmpinfo.substring(0,tmpinindex));
          tmpinfo = tmpinfo.substring(tmpinindex + 1);
          while(tmpinfo.startsWith(" ")) {
            tmpinfo = tmpinfo.substring(1);
          }
          tmpinindex = tmpinfo.indexOf(',');
          int numofnobjs = 0;
          if(tmpinindex == -1) {
            numofnobjs = Integer.parseInt(tmpinfo);
            if(numofnobjs != 0) {
              System.err.println("Error profile data format!");
              System.exit(-1);
            }
          } else {
            tinfo.m_newobjinfo.setElementAt(new Hashtable<String,Integer>(), i);
            numofnobjs = Integer.parseInt(tmpinfo.substring(0, tmpinindex));
            tmpinfo = tmpinfo.substring(tmpinindex + 1);
            while(tmpinfo.startsWith(" ")) {
              tmpinfo = tmpinfo.substring(1);
            }
            for(int j = 0; j < numofnobjs; j++) {
              tmpinindex = tmpinfo.indexOf(',');
              String nobjtype = tmpinfo.substring(0, tmpinindex);
              tmpinfo = tmpinfo.substring(tmpinindex + 1);
              while(tmpinfo.startsWith(" ")) {
                tmpinfo = tmpinfo.substring(1);
              }
              int objnum = 0;
              if(j < numofnobjs - 1) {
                tmpinindex = tmpinfo.indexOf(',');
                objnum  = Integer.parseInt(tmpinfo.substring(0, tmpinindex));
                tmpinfo = tmpinfo.substring(tmpinindex + 1);
                while(tmpinfo.startsWith(" ")) {
                  tmpinfo = tmpinfo.substring(1);
                }
              } else {
                objnum = Integer.parseInt(tmpinfo);
              }
              tinfo.m_newobjinfo.elementAt(i).put(nobjtype, objnum);
            }
          }
        }
        taskinfos.put(inname, tinfo);
        inindex = profiledata.indexOf('\n');
      }
      inStream.close();
      inStream = null;
      b = null;
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  // for test
  private void randomProfileSetting() {
    // Randomly set the newRate and probability of FEdges
    java.util.Random r=new java.util.Random();
    int tint = 0;
    Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();
    for(; it_classes.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor) it_classes.next();
      if(cd.hasFlags()) {
        Vector rootnodes=this.taskanalysis.getRootNodes(cd);
        if(rootnodes!=null) {
          Iterator it_rootnodes=rootnodes.iterator();
          for(; it_rootnodes.hasNext(); ) {
            FlagState root=(FlagState)it_rootnodes.next();
            Vector allocatingTasks = root.getAllocatingTasks();
            if(allocatingTasks != null) {
              for(int k = 0; k < allocatingTasks.size(); k++) {
                TaskDescriptor td = (TaskDescriptor)allocatingTasks.elementAt(k);
                Vector<FEdge> fev =
                  (Vector<FEdge>) this.taskanalysis.getFEdgesFromTD(td);
                int numEdges = fev.size();
                int total = 100;
                for(int j = 0; j < numEdges; j++) {
                  FEdge pfe = fev.elementAt(j);
                  if(numEdges - j == 1) {
                    pfe.setProbability(total);
                  } else {
                    if((total != 0) && (total != 1)) {
                      do {
                        tint = r.nextInt()%total;
                      } while(tint <= 0);
                    }
                    pfe.setProbability(tint);
                    total -= tint;
                  }
                  //do {
                  //   tint = r.nextInt()%10;
                  //  } while(tint <= 0);
                  //int newRate = tint;
                  //int newRate = (j+1)%2+1;
                  int newRate = 1;
                  String cdname = cd.getSymbol();
                  if((cdname.equals("SeriesRunner")) ||
                     (cdname.equals("MDRunner")) ||
                     (cdname.equals("Stage")) ||
                     (cdname.equals("AppDemoRunner")) ||
                     (cdname.equals("FilterBankAtom")) ||
                     (cdname.equals("Grid")) ||
                     (cdname.equals("Fractal")) ||
                     (cdname.equals("KMeans")) ||
                     (cdname.equals("ZTransform")) ||
                     (cdname.equals("TestRunner")) ||
                     (cdname.equals("TestRunner2")) ||
                     (cdname.equals("LinkList")) ||
                     (cdname.equals("BHRunner"))) {
                    newRate = this.coreNum;
                  } else if(cdname.equals("SentenceParser")) {
                    newRate = 4;
                  } else if(cdname.equals("BlurPiece")) {
                    newRate = 4;
                  } else if(cdname.equals("ImageX")) {
                    newRate = 2 * 2;
                  } else if(cdname.equals("ImageY")) {
                    newRate = 1 * 4;
                  }
                  //do {
                  //    tint = r.nextInt()%100;
                  //   } while(tint <= 0);
                  //   int probability = tint;
                  int probability = 100;
                  pfe.addNewObjInfo(cd, newRate, probability);
                }
                fev = null;
              }
            }
          }
          it_rootnodes = null;
        }

        Iterator it_flags = this.taskanalysis.getFlagStates(cd).iterator();
        while(it_flags.hasNext()) {
          FlagState fs = (FlagState)it_flags.next();
          Iterator it_edges = fs.edges();
          int total = 100;
          while(it_edges.hasNext()) {
            //do {
            //    tint = r.nextInt()%10;
            //   } while(tint <= 0);
            tint = 3;
            FEdge edge = (FEdge)it_edges.next();
            edge.setExeTime(tint);
            if((fs.getClassDescriptor().getSymbol().equals("MD"))
               && (edge.getTask().getSymbol().equals("t6"))) {
              if(edge.isbackedge()) {
                if(edge.getTarget().equals(edge.getSource())) {
                  edge.setProbability(93.75);
                } else {
                  edge.setProbability(3.125);
                }
              } else {
                edge.setProbability(3.125);
              }
              continue;
            }
            if(!it_edges.hasNext()) {
              edge.setProbability(total);
            } else {
              if((total != 0) && (total != 1)) {
                do {
                  tint = r.nextInt()%total;
                } while(tint <= 0);
              }
              edge.setProbability(tint);
              total -= tint;
            }
          }
          it_edges = null;
        }
        it_flags = null;
      }
    }
    it_classes = null;
  }

  private ScheduleNode buildCFSTG(Vector<ScheduleEdge> toBreakDown,
                                  Vector<TaskDescriptor> multiparamtds) {
    Hashtable<ClassDescriptor, ClassNode> cdToCNodes =
      new Hashtable<ClassDescriptor, ClassNode>();
    // Build the combined flag transition diagram
    // First, for each class create a ClassNode
    Iterator it_classes = state.getClassSymbolTable().getDescriptorsIterator();
    while(it_classes.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it_classes.next();
      Set<FlagState> fStates = taskanalysis.getFlagStates(cd);

      //Sort flagState nodes inside this ClassNode
      Vector<FlagState> sFStates = FlagState.DFS.topology(fStates, null);

      Vector rootnodes  = taskanalysis.getRootNodes(cd);
      if(((rootnodes != null) && (rootnodes.size() > 0))
         || (cd.getSymbol().equals(TypeUtil.StartupClass))) {
        ClassNode cNode = new ClassNode(cd, sFStates);
        cNode.setSorted(true);
        classNodes.add(cNode);
        cd2ClassNode.put(cd, cNode);
        cdToCNodes.put(cd, cNode);
        cNode.calExeTime();
      }
      rootnodes = null;
      fStates = null;
      sFStates = null;
    }
    it_classes = null;

    ScheduleNode startupNode = null;
    // For each ClassNode create a ScheduleNode containing it
    int i = 0;
    for(i = 0; i < classNodes.size(); i++) {
      ClassNode cn = classNodes.elementAt(i);
      ScheduleNode sn = new ScheduleNode(cn, 0);
      if(cn.getClassDescriptor().getSymbol().equals(TypeUtil.StartupClass)) {
        startupNode = sn;
      }
      cn.setScheduleNode(sn);
      scheduleNodes.add(sn);
      try {
        sn.calExeTime();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // Create 'new' edges between the ScheduleNodes.
    for(i = 0; i < classNodes.size(); i++) {
      ClassNode cNode = classNodes.elementAt(i);
      ClassDescriptor cd = cNode.getClassDescriptor();
      Vector rootnodes  = taskanalysis.getRootNodes(cd);
      if(rootnodes != null) {
        for(int h = 0; h < rootnodes.size(); h++) {
          FlagState root=(FlagState)rootnodes.elementAt(h);
          Vector allocatingTasks = root.getAllocatingTasks();
          if(allocatingTasks != null) {
            for(int k = 0; k < allocatingTasks.size(); k++) {
              TaskDescriptor td = (TaskDescriptor)allocatingTasks.elementAt(k);
              Vector<FEdge> fev =
                (Vector<FEdge>)taskanalysis.getFEdgesFromTD(td);
              int numEdges = fev.size();
              ScheduleNode sNode = cNode.getScheduleNode();
              for(int j = 0; j < numEdges; j++) {
                FEdge pfe = fev.elementAt(j);
                FEdge.NewObjInfo noi = pfe.getNewObjInfo(cd);
                if ((noi == null) || (noi.getNewRate() == 0)
                    || (noi.getProbability() == 0)) {
                  // fake creating edge, do not need to create corresponding
                  // 'new' edge
                  continue;
                }
                if(noi.getRoot() == null) {
                  // set root FlagState
                  noi.setRoot(root);
                }
                FlagState pfs = (FlagState)pfe.getTarget();
                ClassDescriptor pcd = pfs.getClassDescriptor();
                ClassNode pcNode = cdToCNodes.get(pcd);

                ScheduleEdge sEdge = new ScheduleEdge(sNode,
                                                      "new",
                                                      root,
                                                      ScheduleEdge.NEWEDGE,
                                                      0);
                sEdge.setFEdge(pfe);
                sEdge.setSourceCNode(pcNode);
                sEdge.setTargetCNode(cNode);
                sEdge.setTargetFState(root);
                sEdge.setNewRate(noi.getNewRate());
                sEdge.setProbability(noi.getProbability());
                pcNode.getScheduleNode().addEdge(sEdge);
                scheduleEdges.add(sEdge);
                if((j !=0 ) || (k != 0) || (h != 0)) {
                  toBreakDown.add(sEdge);
                }
              }
              fev = null;
            }
            allocatingTasks = null;
          }
        }
        rootnodes = null;
      }
    }
    cdToCNodes = null;

    for(i = 0; i < multiparamtds.size(); i++) {
      TaskDescriptor td = multiparamtds.elementAt(i);
      ClassDescriptor cd = td.getParamType(0).getClassDesc();
      // set the first parameter as main cd
      // NOTE: programmer should write in such a style that
      //       for all multi-param tasks, the main class should be
      //       the first parameter
      // TODO: may have bug when cd has multiple new flag states
      this.td2maincd.put(td, cd);
    }

    return startupNode;
  }

  private void treeTransform(Vector<ScheduleEdge> toBreakDown,
                             ScheduleNode startupNode) {
    int i = 0;

    // Break down the 'cycle's
    try {
      for(i = 0; i < toBreakDown.size(); i++ ) {
        cloneSNodeList(toBreakDown.elementAt(i), false);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    // Remove fake 'new' edges
    for(i = 0; i < scheduleEdges.size(); i++) {
      ScheduleEdge se = (ScheduleEdge)scheduleEdges.elementAt(i);
      if((0 == se.getNewRate()) || (0 == se.getProbability())) {
        scheduleEdges.removeElement(se);
        scheduleNodes.removeElement(se.getTarget());
      }
    }

    // Do topology sort of the ClassNodes and ScheduleEdges.
    Vector<ScheduleEdge> ssev = new Vector<ScheduleEdge>();
    Vector<ScheduleNode> tempSNodes =
      ClassNode.DFS.topology(scheduleNodes, ssev);
    scheduleNodes.removeAllElements();
    scheduleNodes = tempSNodes;
    tempSNodes = null;
    scheduleEdges.removeAllElements();
    scheduleEdges = ssev;
    ssev = null;
    sorted = true;

    // Set the cid of these ScheduleNode
    Queue<ScheduleNode> toVisit = new LinkedList<ScheduleNode>();
    toVisit.add(startupNode);
    while(!toVisit.isEmpty()) {
      ScheduleNode sn = toVisit.poll();
      if(sn.getCid() == -1) {
        // not visited before
        sn.setCid(ScheduleNode.colorID++);
        Iterator it_edge = sn.edges();
        while(it_edge.hasNext()) {
          toVisit.add((ScheduleNode)((ScheduleEdge)it_edge.next()).getTarget());
        }
        it_edge = null;
      }
    }
    toVisit = null;

    if(this.state.PRINTSCHEDULING) {
      SchedulingUtil.printScheduleGraph(
        this.state.outputdir + "scheduling_ori.dot", this.scheduleNodes);
    }
  }

  private void handleDescenSEs(Vector<ScheduleEdge> ses,
                               boolean isflag) {
    if(isflag) {
      ScheduleEdge tempse = ses.elementAt(0);
      long temptime = tempse.getListExeTime();
      // find out the ScheduleEdge with least exeTime
      for(int k = 1; k < ses.size(); k++) {
        long ttemp = ses.elementAt(k).getListExeTime();
        if(ttemp < temptime) {
          tempse = ses.elementAt(k);
          temptime = ttemp;
        } // if(ttemp < temptime)
      } // for(int k = 1; k < ses.size(); k++)
        // handle the tempse
      handleScheduleEdge(tempse, true);
      ses.removeElement(tempse);
    }
    // handle other ScheduleEdges
    for(int k = 0; k < ses.size(); k++) {
      handleScheduleEdge(ses.elementAt(k), false);
    } // for(int k = 0; k < ses.size(); k++)
  }

  private void CFSTGTransform() {
    // First iteration
    int i = 0;

    // table of all schedule edges associated to one fedge
    Hashtable<FEdge, Vector<ScheduleEdge>> fe2ses =
      new Hashtable<FEdge, Vector<ScheduleEdge>>();
    // table of all fedges associated to one schedule node
    Hashtable<ScheduleNode, Vector<FEdge>> sn2fes =
      new Hashtable<ScheduleNode, Vector<FEdge>>();
    ScheduleNode preSNode = null;
    // Access the ScheduleEdges in reverse topology order
    for(i = scheduleEdges.size(); i > 0; i--) {
      ScheduleEdge se = (ScheduleEdge)scheduleEdges.elementAt(i-1);
      if(ScheduleEdge.NEWEDGE == se.getType()) {
        if(preSNode == null) {
          preSNode = (ScheduleNode)se.getSource();
        }

        boolean split = false;
        FEdge fe = se.getFEdge();
        if(fe.getSource() == fe.getTarget()) {
          // the associated start fe is a back edge
          try {
            // check the number of newly created objs
            int repeat = (int)Math.ceil(se.getNewRate()*se.getProbability()/100);
            int rate = 0;
            /*if(repeat > 1) {
               // more than one new objs, expand the new edge
               for(int j = 1; j< repeat; j++ ) {
                cloneSNodeList(se, true);
               } // for(int j = 1; j< repeat; j++ )
               se.setNewRate(1);
               se.setProbability(100);
               } // if(repeat > 1)*/
            try {
              // match the rates of obj creation and new obj consumption
              rate = (int)Math.ceil(
                se.getListExeTime()/calInExeTime(se.getSourceFState()));
            } catch (Exception e) {
              e.printStackTrace();
            } // try-catch {}
            repeat = (rate > repeat)?rate:repeat;
            // expand the new edge
            for(int j = 1; j< repeat; j++ ) {
              cloneSNodeList(se, true);
            } // for(int j = 1; j< repeat; j++ )
            se.setNewRate(1);
            se.setProbability(100);
            /*for(int j = rate - 1; j > 0; j--) {
               for(int k = repeat; k > 0; k--) {
                cloneSNodeList(se, true);
               } // for(int k = repeat; k > 0; k--)
               } // for(int j = rate - 1; j > 0; j--)*/
          } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
          } // try-catch{}
        } else { // if(fe.getSource() == fe.getTarget())
          // the associated start fe is not a back edge
          // Note: if preSNode is not the same as se's source ScheduleNode
          // handle any ScheduleEdges previously put into fe2ses whose source
          // ScheduleNode is preSNode
          boolean same = (preSNode == se.getSource());
          if(!same) {
            // check the topology sort, only process those after se.getSource()
            if(preSNode.getFinishingTime() < se.getSource().getFinishingTime()) {
              if(sn2fes.containsKey(preSNode)) {
                Vector<FEdge> fes = sn2fes.remove(preSNode);
                for(int j = 0; j < fes.size(); j++) {
                  FEdge tempfe = fes.elementAt(j);
                  Vector<ScheduleEdge> ses = fe2ses.get(tempfe);
                  boolean isflag = !(preSNode.edges().hasNext());
                  this.handleDescenSEs(ses, isflag);
                  ses = null;
                  fe2ses.remove(tempfe);
                } // for(int j = 0; j < fes.size(); j++)
                fes = null;
              }
            }
            preSNode = (ScheduleNode)se.getSource();
          } // if(!same)

          if(fe.getTarget().edges().hasNext()) {
            // not associated with the last task, check if to split the snode
            if((!(se.getTransTime() < this.transThreshold))
               && (se.getSourceCNode().getTransTime() < se.getTransTime())) {
              // it's better to transfer the other obj with preSnode
              split = true;
              splitSNode(se, true);
            }
          } // if(!fe.getTarget().edges().hasNext())

          if(!split) {
            // delay the expanding and merging until we find all such 'new'
            // edges associated with a last task inside this ClassNode
            if(fe2ses.get(fe) == null) {
              fe2ses.put(fe, new Vector<ScheduleEdge>());
            }
            if(sn2fes.get((ScheduleNode)se.getSource()) == null) {
              sn2fes.put((ScheduleNode)se.getSource(), new Vector<FEdge>());
            }
            if(!fe2ses.get(fe).contains(se)) {
              fe2ses.get(fe).add(se);
            }
            if(!sn2fes.get((ScheduleNode)se.getSource()).contains(fe)) {
              sn2fes.get((ScheduleNode)se.getSource()).add(fe);
            }
          } // if(!split)
        } // if(fe.getSource() == fe.getTarget())
      } // if(ScheduleEdge.NEWEDGE == se.getType())
    } // for(i = scheduleEdges.size(); i > 0; i--)
    if(!fe2ses.isEmpty()) {
      Set<FEdge> keys = fe2ses.keySet();
      Iterator it_keys = keys.iterator();
      while(it_keys.hasNext()) {
        FEdge tempfe = (FEdge)it_keys.next();
        Vector<ScheduleEdge> ses = fe2ses.get(tempfe);
        boolean isflag = !(tempfe.getTarget().edges().hasNext());
        this.handleDescenSEs(ses, isflag);
        ses = null;
      }
      keys = null;
      it_keys = null;
    }
    fe2ses.clear();
    sn2fes.clear();
    fe2ses = null;
    sn2fes = null;

    if(this.state.PRINTSCHEDULING) {
      SchedulingUtil.printScheduleGraph(
        this.state.outputdir + "scheduling_extend.dot", this.scheduleNodes);
    }
  }

  private void handleScheduleEdge(ScheduleEdge se,
                                  boolean merge) {
    try {
      int rate = 0;
      int repeat = (int)Math.ceil(se.getNewRate() * se.getProbability() / 100);
      if(merge) {
        try {
          if(se.getListExeTime() == 0) {
            rate = repeat;
          } else {
            rate = (int)Math.ceil(
              (se.getTransTime()-calInExeTime(se.getSourceFState()))
              /se.getListExeTime());
          }
          if(rate < 0 ) {
            rate = 0;
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        if(0 == rate) {
          // clone the whole ScheduleNode lists starting with se's target
          for(int j = 1; j < repeat; j++ ) {
            cloneSNodeList(se, true);
          }
          se.setNewRate(1);
          se.setProbability(100);
        } else {
          repeat -= rate;
          if(repeat > 0) {
            // clone the whole ScheduleNode lists starting with se's target
            for(int j = 0; j < repeat; j++ ) {
              cloneSNodeList(se, true);
            }
            se.setNewRate(rate);
            se.setProbability(100);
          }
        }
        // merge the original ScheduleNode to the source ScheduleNode
        ((ScheduleNode)se.getSource()).mergeSEdge(se);
        scheduleNodes.remove(se.getTarget());
        scheduleEdges.remove(se);
        // As se has been changed into an internal edge inside a ScheduleNode,
        // change the source and target of se from original ScheduleNodes
        // into ClassNodes.
        if(se.getType() == ScheduleEdge.NEWEDGE) {
          se.setTarget(se.getTargetCNode());
          //se.setSource(se.getSourceCNode());
          //se.getTargetCNode().addEdge(se);
          se.getSourceCNode().addEdge(se);
        }
      } else {
        // clone the whole ScheduleNode lists starting with se's target
        for(int j = 1; j < repeat; j++ ) {
          cloneSNodeList(se, true);
        }
        se.setNewRate(1);
        se.setProbability(100);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private void cloneSNodeList(ScheduleEdge sEdge,
                              boolean copyIE) throws Exception {
    Hashtable<ClassNode, ClassNode> cn2cn =
      new Hashtable<ClassNode, ClassNode>(); // hashtable from classnode in
                                             // orignal se's targe to cloned one
    ScheduleNode csNode =
      (ScheduleNode)((ScheduleNode)sEdge.getTarget()).clone(cn2cn, 0);
    scheduleNodes.add(csNode);

    // Clone all the external in ScheduleEdges
    int i;
    if(copyIE) {
      Vector inedges = sEdge.getTarget().getInedgeVector();
      for(i = 0; i < inedges.size(); i++) {
        ScheduleEdge tse = (ScheduleEdge)inedges.elementAt(i);
        ScheduleEdge se;
        switch(tse.getType()) {
        case ScheduleEdge.NEWEDGE: {
          se = new ScheduleEdge(csNode,"new",tse.getFstate(),tse.getType(),0);
          se.setProbability(100);
          se.setNewRate(1);
          break;
        }

        case ScheduleEdge.TRANSEDGE: {
          se = new ScheduleEdge(csNode,"transmit",tse.getFstate(),tse.getType(),0);
          se.setProbability(tse.getProbability());
          se.setNewRate(tse.getNewRate());
          break;
        }

        default: {
          throw new Exception("Error: not valid ScheduleEdge here");
        }
        }
        se.setSourceCNode(tse.getSourceCNode());
        se.setTargetCNode(cn2cn.get(tse.getTargetCNode()));
        se.setFEdge(tse.getFEdge());
        se.setTargetFState(tse.getTargetFState());
        se.setIsclone(true);
        tse.getSource().addEdge(se);
        scheduleEdges.add(se);
      }
      inedges = null;
    } else {
      sEdge.getTarget().removeInedge(sEdge);
      sEdge.setTarget(csNode);
      csNode.getInedgeVector().add(sEdge);
      sEdge.setTargetCNode(cn2cn.get(sEdge.getTargetCNode()));
      sEdge.setIsclone(true);
    }

    Queue<ScheduleNode> toClone = new LinkedList<ScheduleNode>(); // all nodes to be cloned
    Queue<ScheduleNode> clone = new LinkedList<ScheduleNode>();  //clone nodes
    Queue<Hashtable> qcn2cn = new LinkedList<Hashtable>(); // queue of the mappings of classnodes inside cloned ScheduleNode
    Vector<ScheduleNode> origins = new Vector<ScheduleNode>();  // queue of source ScheduleNode cloned
    Hashtable<ScheduleNode, ScheduleNode> sn2sn =
      new Hashtable<ScheduleNode, ScheduleNode>(); // mapping from cloned ScheduleNode to clone ScheduleNode
    clone.add(csNode);
    toClone.add((ScheduleNode)sEdge.getTarget());
    origins.addElement((ScheduleNode)sEdge.getTarget());
    sn2sn.put((ScheduleNode)sEdge.getTarget(), csNode);
    qcn2cn.add(cn2cn);
    while(!toClone.isEmpty()) {
      Hashtable<ClassNode, ClassNode> tocn2cn =
        new Hashtable<ClassNode, ClassNode>();
      csNode = clone.poll();
      ScheduleNode osNode = toClone.poll();
      cn2cn = qcn2cn.poll();
      // Clone all the external ScheduleEdges and the following ScheduleNodes
      Vector edges = osNode.getEdgeVector();
      for(i = 0; i < edges.size(); i++) {
        ScheduleEdge tse = (ScheduleEdge)edges.elementAt(i);
        ScheduleNode tSNode =
          (ScheduleNode)((ScheduleNode)tse.getTarget()).clone(tocn2cn, 0);
        scheduleNodes.add(tSNode);
        clone.add(tSNode);
        toClone.add((ScheduleNode)tse.getTarget());
        origins.addElement((ScheduleNode)tse.getTarget());
        sn2sn.put((ScheduleNode)tse.getTarget(), tSNode);
        qcn2cn.add(tocn2cn);
        ScheduleEdge se = null;
        switch(tse.getType()) {
        case ScheduleEdge.NEWEDGE: {
          se = new ScheduleEdge(tSNode,"new",tse.getFstate(),tse.getType(),0);
          break;
        }

        case ScheduleEdge.TRANSEDGE: {
          se = new ScheduleEdge(tSNode,"transmit",tse.getFstate(),tse.getType(),0);
          break;
        }

        default: {
          throw new Exception("Error: not valid ScheduleEdge here");
        }
        }
        se.setSourceCNode(cn2cn.get(tse.getSourceCNode()));
        se.setTargetCNode(tocn2cn.get(tse.getTargetCNode()));
        se.setFEdge(tse.getFEdge());
        se.setTargetFState(tse.getTargetFState());
        se.setProbability(tse.getProbability());
        se.setNewRate(tse.getNewRate());
        se.setIsclone(true);
        csNode.addEdge(se);
        scheduleEdges.add(se);
      }
      tocn2cn = null;
      edges = null;
    }

    toClone = null;
    clone = null;
    qcn2cn = null;
    cn2cn.clear();
    cn2cn = null;
    origins = null;
    sn2sn = null;
  }

  private long calInExeTime(FlagState fs) throws Exception {
    long exeTime = 0;
    ClassDescriptor cd = fs.getClassDescriptor();
    ClassNode cNode = cd2ClassNode.get(cd);
    exeTime = cNode.getFlagStates().elementAt(0).getExeTime() - fs.getExeTime();
    while(true) {
      Vector inedges = cNode.getInedgeVector();
      // Now that there are associate ScheduleEdges, there may be
      // multiple inedges of a ClassNode
      if(inedges.size() > 1) {
        throw new Exception("Error: ClassNode's inedges more than one!");
      }
      if(inedges.size() > 0) {
        ScheduleEdge sEdge = (ScheduleEdge)inedges.elementAt(0);
        cNode = (ClassNode)sEdge.getSource();
        exeTime += cNode.getFlagStates().elementAt(0).getExeTime();
      } else {
        break;
      }
      inedges = null;
    }
    exeTime = cNode.getScheduleNode().getExeTime() - exeTime;
    return exeTime;
  }

  private ScheduleNode splitSNode(ScheduleEdge se,
                                  boolean copy) {
    assert(ScheduleEdge.NEWEDGE == se.getType());

    FEdge fe = se.getFEdge();
    FlagState fs = (FlagState)fe.getTarget();
    FlagState nfs = (FlagState)fs.clone();
    fs.getEdgeVector().removeAllElements();
    nfs.getInedgeVector().removeAllElements();
    ClassNode sCNode = se.getSourceCNode();

    // split the subtree whose root is nfs from the whole flag transition tree
    Vector<FlagState> sfss = sCNode.getFlagStates();
    Vector<FlagState> fStates = new Vector<FlagState>();
    Queue<FlagState> toiterate = new LinkedList<FlagState>();
    toiterate.add(nfs);
    fStates.add(nfs);
    while(!toiterate.isEmpty()) {
      FlagState tfs = toiterate.poll();
      Iterator it_edges = tfs.edges();
      while(it_edges.hasNext()) {
        FlagState temp = (FlagState)((FEdge)it_edges.next()).getTarget();
        if(!fStates.contains(temp)) {
          fStates.add(temp);
          toiterate.add(temp);
          sfss.removeElement(temp);
        }
      }
      it_edges = null;
    }
    sfss = null;
    Vector<FlagState> sFStates = FlagState.DFS.topology(fStates, null);
    fStates = null;
    // create a ClassNode and ScheduleNode for this subtree
    ClassNode cNode = new ClassNode(sCNode.getClassDescriptor(), sFStates);
    ScheduleNode sNode = new ScheduleNode(cNode, 0);
    cNode.setScheduleNode(sNode);
    cNode.setSorted(true);
    cNode.setTransTime(sCNode.getTransTime());
    classNodes.add(cNode);
    scheduleNodes.add(sNode);
    try {
      sNode.calExeTime();
    } catch (Exception e) {
      e.printStackTrace();
    }
    // flush the exeTime of fs and its ancestors
    fs.setExeTime(0);
    toiterate.add(fs);
    while(!toiterate.isEmpty()) {
      FlagState tfs = toiterate.poll();
      long ttime = tfs.getExeTime();
      Iterator it_inedges = tfs.inedges();
      while(it_inedges.hasNext()) {
        FEdge fEdge = (FEdge)it_inedges.next();
        FlagState temp = (FlagState)fEdge.getSource();
        long time = fEdge.getExeTime() + ttime;
        if(temp.getExeTime() > time) {
          temp.setExeTime(time);
          toiterate.add(temp);
        }
      }
      it_inedges = null;
    }
    toiterate = null;

    // create a 'trans' ScheudleEdge between this new ScheduleNode and se's
    // source ScheduleNode
    ScheduleEdge sEdge =
      new ScheduleEdge(sNode, "transmit", fs, ScheduleEdge.TRANSEDGE, 0);
    sEdge.setFEdge(fe);
    sEdge.setSourceCNode(sCNode);
    sEdge.setTargetCNode(cNode);
    sEdge.setTargetFState(nfs);
    // TODO
    // Add calculation codes for calculating transmit time of an object
    sEdge.setTransTime(cNode.getTransTime());
    se.getSource().addEdge(sEdge);
    scheduleEdges.add(sEdge);
    // remove the ClassNodes and internal ScheduleEdges out of this subtree
    // to the new ScheduleNode
    ScheduleNode oldSNode = (ScheduleNode)se.getSource();
    Iterator it_isEdges = oldSNode.getScheduleEdgesIterator();
    Vector<ScheduleEdge> toremove = new Vector<ScheduleEdge>();
    Vector<ClassNode> rCNodes = new Vector<ClassNode>();
    rCNodes.addElement(sCNode);
    if(it_isEdges != null) {
      while(it_isEdges.hasNext()) {
        ScheduleEdge tse = (ScheduleEdge)it_isEdges.next();
        if(rCNodes.contains(tse.getSourceCNode())) {
          if(sCNode.equals(tse.getSourceCNode())) {
            if (!(tse.getSourceFState().equals(fs))
                && (sFStates.contains(tse.getSourceFState()))) {
              tse.setSource(cNode);
              tse.setSourceCNode(cNode);
            } else {
              continue;
            }
          }
          sNode.getScheduleEdges().addElement(tse);
          sNode.getClassNodes().addElement(tse.getTargetCNode());
          rCNodes.addElement(tse.getTargetCNode());
          oldSNode.getClassNodes().removeElement(tse.getTargetCNode());
          toremove.addElement(tse);
        }
      }
    }
    it_isEdges = null;
    oldSNode.getScheduleEdges().removeAll(toremove);
    toremove.clear();
    // redirect ScheudleEdges out of this subtree to the new ScheduleNode
    Iterator it_sEdges = se.getSource().edges();
    while(it_sEdges.hasNext()) {
      ScheduleEdge tse = (ScheduleEdge)it_sEdges.next();
      if(!(tse.equals(se)) && !(tse.equals(sEdge))
         && (tse.getSourceCNode().equals(sCNode))) {
        if(!(tse.getSourceFState().equals(fs))
           && (sFStates.contains(tse.getSourceFState()))) {
          tse.setSource(sNode);
          tse.setSourceCNode(cNode);
          sNode.getEdgeVector().addElement(tse);
          toremove.add(tse);
        }
      }
    }
    it_sEdges = null;
    se.getSource().getEdgeVector().removeAll(toremove);
    toremove = null;
    rCNodes = null;
    sFStates = null;

    try {
      if(!copy) {
        //merge se into its source ScheduleNode
        sNode.setCid(((ScheduleNode)se.getSource()).getCid());
        ((ScheduleNode)se.getSource()).mergeSEdge(se);
        scheduleNodes.remove(se.getTarget());
        scheduleEdges.removeElement(se);
        // As se has been changed into an internal edge inside a ScheduleNode,
        // change the source and target of se from original ScheduleNodes
        // into ClassNodes.
        if(se.getType() == ScheduleEdge.NEWEDGE) {
          se.setTarget(se.getTargetCNode());
          //se.setSource(se.getSourceCNode());
          //se.getTargetCNode().addEdge(se);
          se.getSourceCNode().addEdge(se);
        }
      } else {
        sNode.setCid(ScheduleNode.colorID++);
        handleScheduleEdge(se, true);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    return sNode;
  }

  // TODO: restrict the number of generated scheduling according to the setted
  // scheduleThreshold
  private boolean coreMapping(int generateThreshold,
                              int skipThreshold) throws Exception {
    if(this.coreNum == -1) {
      throw new Exception("Error: un-initialized coreNum when doing scheduling.");
    }

    if(this.scheduleGraphs == null) {
      this.scheduleGraphs = new Vector<Vector<ScheduleNode>>();
    }

    int reduceNum = this.scheduleNodes.size() - this.coreNum;

    // Combine some ScheduleNode if necessary
    // May generate multiple graphs suggesting candidate schedulings
    if(!(reduceNum > 0)) {
      // Enough cores, no need to combine any ScheduleNode
      this.scheduleGraphs.addElement(this.scheduleNodes);
      int gid = 1;
      if(this.state.PRINTSCHEDULING) {
        String path = this.state.outputdir + "scheduling_" + gid + ".dot";
        SchedulingUtil.printScheduleGraph(path, this.scheduleNodes);
      }
      return false;
    } else {
      SchedulingUtil.assignCids(this.scheduleNodes);

      // Go through all the Schedule Nodes, organize them in order of their cid
      Vector<Vector<ScheduleNode>> sNodeVecs =
        SchedulingUtil.rangeScheduleNodes(this.scheduleNodes);

      int gid = 1;
      boolean isBig = Math.pow(this.coreNum, reduceNum) > 1000;
      Random rand = new Random();
      if(isBig && state.BAMBOOCOMPILETIME) {
        CombinationUtil.RootsGenerator rGen =
          CombinationUtil.allocateRootsGenerator(sNodeVecs,
                                                 this.coreNum);
        while((gid <= this.scheduleThreshold) && (rGen.nextGen())) {
          // first get the chosen rootNodes
          Vector<Vector<ScheduleNode>> rootNodes = rGen.getRootNodes();
          Vector<Vector<ScheduleNode>> nodes2combine = rGen.getNode2Combine();

          CombinationUtil.CombineGenerator cGen =
            CombinationUtil.allocateCombineGenerator(rootNodes,
                                                     nodes2combine);
          while((gid <= this.scheduleThreshold) && (cGen.randomGenE())) {
            boolean implement = true;
            /*if(isBig) {
                implement = Math.abs(rand.nextInt()) % 100 > generateThreshold;
               }*/
            if(implement) {
              Vector<Vector<CombinationUtil.Combine>> combine = cGen.getCombine();
              Vector<ScheduleNode> sNodes =
                SchedulingUtil.generateScheduleGraph(this.state,
                                                     this.scheduleNodes,
                                                     this.scheduleEdges,
                                                     rootNodes,
                                                     combine,
                                                     gid++);
              this.scheduleGraphs.add(sNodes);
              sNodes = null;
              combine = null;
            } else if(Math.abs(rand.nextInt()) % 100 > skipThreshold) {
              break;
            }
          }
          cGen.clear();
          rootNodes = null;
          nodes2combine = null;
        }
        rGen.clear();
        sNodeVecs = null;
      } else if (false) {
        CombinationUtil.RandomGenerator rGen =
          CombinationUtil.allocateRandomGenerator(sNodeVecs,
                                                  this.coreNum);
        // random genenration
        while((gid <= this.scheduleThreshold) && (rGen.nextGen())) {
          Vector<Vector<ScheduleNode>> mapping = rGen.getMapping();
          boolean implement = true;
          if(isBig) {
            implement = Math.abs(rand.nextInt()) % 100 > generateThreshold;
          }
          if(implement) {
            Vector<ScheduleNode> sNodes =
              SchedulingUtil.generateScheduleGraph(this.state,
                                                   this.scheduleNodes,
                                                   this.scheduleEdges,
                                                   mapping,
                                                   gid++);
            this.scheduleGraphs.add(sNodes);
            sNodes = null;
          }
          mapping = null;
        }
        rGen.clear();
        sNodeVecs = null;
      } else {
        CombinationUtil.RootsGenerator rGen =
          CombinationUtil.allocateRootsGenerator(sNodeVecs,
                                                 this.coreNum);
        while((!isBig || (gid <= this.scheduleThreshold)) && (rGen.nextGen())) {
          // first get the chosen rootNodes
          Vector<Vector<ScheduleNode>> rootNodes = rGen.getRootNodes();
          Vector<Vector<ScheduleNode>> nodes2combine = rGen.getNode2Combine();

          CombinationUtil.CombineGenerator cGen =
            CombinationUtil.allocateCombineGenerator(rootNodes,
                                                     nodes2combine);
          while((!isBig || (gid <= this.scheduleThreshold)) && (cGen.nextGen())) {
            boolean implement = true;
            if(isBig) {
              implement = Math.abs(rand.nextInt()) % 100 > generateThreshold;
            }
            if(implement) {
              Vector<Vector<CombinationUtil.Combine>> combine = cGen.getCombine();
              Vector<ScheduleNode> sNodes =
                SchedulingUtil.generateScheduleGraph(this.state,
                                                     this.scheduleNodes,
                                                     this.scheduleEdges,
                                                     rootNodes,
                                                     combine,
                                                     gid++);
              this.scheduleGraphs.add(sNodes);
              sNodes = null;
              combine = null;
            } else if(Math.abs(rand.nextInt()) % 100 > skipThreshold) {
              break;
            }
          }
          cGen.clear();
          rootNodes = null;
          nodes2combine = null;
        }
        rGen.clear();
        sNodeVecs = null;
      }
      return isBig;
    }
  }

  static class TaskInfo {
    public int m_numofexits;
    public long[] m_exetime;
    public double[] m_probability;
    public Vector<Hashtable<String, Integer>> m_newobjinfo;
    public int m_byObj;

    public TaskInfo(int numofexits) {
      this.m_numofexits = numofexits;
      this.m_exetime = new long[this.m_numofexits];
      this.m_probability = new double[this.m_numofexits];
      this.m_newobjinfo = new Vector<Hashtable<String, Integer>>();
      for(int i = 0; i < this.m_numofexits; i++) {
        this.m_newobjinfo.add(null);
      }
      this.m_byObj = -1;
    }
  }
}
