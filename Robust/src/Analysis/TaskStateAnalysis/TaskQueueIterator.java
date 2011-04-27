package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TaskQueueIterator {
  TaskQueue tq;
  int index;
  FlagTagState fts;
  FlagTagState ftsarray[];
  Hashtable<TempDescriptor, TagState> tsarray;
  Hashtable<TempDescriptor, Integer> tsindexarray;
  Iterator<FlagTagState> itarray[];
  boolean needit;
  boolean needinit;

  public TaskQueueIterator(TaskQueue tq, int index, FlagTagState fts) {
    this.tq=tq;
    this.index=index;
    this.fts=fts;
    this.ftsarray=new FlagTagState[tq.numParameters()];
    this.ftsarray[index]=fts;
    this.tsarray=new Hashtable<TempDescriptor, TagState>();
    this.tsindexarray=new Hashtable<TempDescriptor, Integer>();
    this.itarray=(Iterator<FlagTagState>[]) new Iterator[tq.numParameters()];
    needit=false;
    needinit=true;
    init();
  }

  private void init() {
    for(int i=ftsarray.length-1; i>=0; i--) {
      if (i!=index)
        itarray[i]=tq.parameterset[i].iterator();
      VarDescriptor vd=tq.task.getParameter(i);
      TagExpressionList tel=tq.task.getTag(vd);
      if (tel!=null)
        for(int j=0; j<tel.numTags(); j++) {
          TempDescriptor tmp=tel.getTemp(j);
          if (!tsindexarray.containsKey(tmp)) {
            tsindexarray.put(tmp, new Integer(i));
          }
        }
    }
  }

  public boolean hasNext() {
    TaskDescriptor td=tq.task;
    int i;
    if (needinit) {
      i=ftsarray.length-1;
      if (i!=index)
        ftsarray[i]=itarray[i].next();
    } else {
      i=0;
    }

    if (i==0&&index==0&&ftsarray[0]!=null&&!needit) {
      needinit=false;
      return true;
    }

objloop:
    for(; i<ftsarray.length; i++) {
      FlagState currfs=ftsarray[i].fs;
      VarDescriptor vd=td.getParameter(i);
      TagExpressionList tel=td.getTag(vd);
      int j;
      if (needinit) {
        j=(tel!=null)&&tel.numTags()>0?tel.numTags()-1:0;
        needinit=false;
      } else
        j=0;
tagloop:
      for(; tel!=null&&j<tel.numTags(); j++) {
        TempDescriptor tmp=tel.getTemp(j);
        TagState currtag=tsarray.get(tmp);
        String type=tel.getType(j);

        if (tsindexarray.get(tmp).intValue()==i) {
          //doing the assignment right here!!!
          Vector<FlagTagState> possts=tq.map.get(currfs);
          int index=0;
          if (currtag!=null)
            index=possts.indexOf(new FlagTagState(currtag,currfs));
          if (needit) {
            index++;
            needit=false;
          }
          for(int k=index; k<possts.size(); k++) {
            FlagTagState posstag=possts.get(k);
            if (posstag.ts.getTag().getSymbol().equals(type)) {
              tsarray.put(tmp, posstag.ts);
              if (j==0) {
                if (i==0) {
                  //We are done!
                  return true;
                } else {
                  //Backtrack on objects
                  i-=2;
                  continue objloop;
                }
              } else {
                //Backtrack on tags
                j-=2;
                continue tagloop;
              }
            }
          }
          //couldn't find a tag
          tsarray.put(tmp, null);
          needit=true;
        } else {
          //check tag compatibility
          if (!currtag.containsFS(currfs)) {
            //incompatible tag set by previous level
            //need to increment object state
            needit=true;
            break;
          }
        }
      }
      if (index==i) {
        continue;
      }
      if (needit) {
        if (itarray[i].hasNext()) {
          ftsarray[i]=itarray[i].next();
          needit=false;
          i-=1;
          continue objloop;           //backtrack and fix up everything
        } else {
          itarray[i]=tq.parameterset[i].iterator();          //keep going backwards
        }
      } else {
        throw new Error();
      }
    }
    return false;
  }

  public FlagTagState getFTS(int index) {
    return ftsarray[index];
  }

  public TagState getTS(TempDescriptor tmp) {
    return tsarray.get(tmp);
  }

  public void next() {
    needit=true;
  }
}
