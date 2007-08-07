package Analysis.TaskStateAnalysis;
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
public class FlagState extends GraphNode {
    public static final int ONETAG=1;
    public static final int NOTAGS=0;
    public static final int MULTITAGS=-1;
    
    private int uid;
    private static int nodeid=0;

    private final HashSet flagstate;
    private final ClassDescriptor cd;
    private final Hashtable<TagDescriptor,Integer> tags;
    private boolean issourcenode;
    private Vector tasks;

    private boolean marked=false;
    

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
	
    }
   
    public int getuid() {
	return uid;
    }

    public boolean isMarked() {
	return marked;
    }

    public void doUnmarking() {
	marked = false;
    }

    public void doMarking() {
	marked = true;
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
      
    public boolean isSourceNode(){
	    return issourcenode;
   	}
   	
   	/**  Sets the flagstate as a source node. 
     */
   	public void setAsSourceNode(){
	   	if(!issourcenode){
	   		issourcenode=true;
	   		this.tasks=new Vector();
   		}
   	}
   	
   	public void addAllocatingTask(TaskDescriptor task){
	   	tasks.add(task);
   	}

   	public Vector getAllocatingTasks(){
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

    public int numFlags(){
	return flagstate.size();
    }
    
    public FlagState[] setTag(TagDescriptor tag){
	HashSet newset1=(HashSet)flagstate.clone();
	Hashtable<TagDescriptor,Integer> newtags1=(Hashtable<TagDescriptor,Integer>)tags.clone();
	    
	if (tags.containsKey(tag)){
	    //Code could try to remove flag that doesn't exist
	    
	    switch (tags.get(tag).intValue()){
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

    public int getTagCount(String tagtype){
	for (Enumeration en=getTags();en.hasMoreElements();){
	    TagDescriptor td=(TagDescriptor)en.nextElement();
	    if (tagtype.equals(td.getSymbol()))
		return tags.get(td).intValue();   //returns either ONETAG or MULTITAG
	}
	return NOTAGS;
    }
    
    public FlagState[] clearTag(TagDescriptor tag){
	if (tags.containsKey(tag)){
	    switch(tags.get(tag).intValue()){
	    case ONETAG:
		HashSet newset=(HashSet)flagstate.clone();
		Hashtable<TagDescriptor,Integer> newtags=(Hashtable<TagDescriptor,Integer>)tags.clone();
		newtags.remove(tag);
		return new FlagState[]{new FlagState(newset,cd,newtags)};
		
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
	public String toString(FlagDescriptor[] flags)
	{
		StringBuffer sb = new StringBuffer(flagstate.size());
		for(int i=0;i < flags.length; i++)
		{
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
	 
    public ClassDescriptor getClassDescriptor(){
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
	else if (newset.contains(fd)){
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
	for(Iterator it=getFlags();it.hasNext();) {
	    FlagDescriptor fd=(FlagDescriptor) it.next();
	    if (label==null)
		label=fd.toString();
	    else
		label+=", "+fd.toString();
	}
	for (Enumeration en_tags=getTags();en_tags.hasMoreElements();){
		TagDescriptor td=(TagDescriptor)en_tags.nextElement();
		switch (tags.get(td).intValue()){
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
    
    public Enumeration getTags(){
	    return tags.keys();
    }
}
