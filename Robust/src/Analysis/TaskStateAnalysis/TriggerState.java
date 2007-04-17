package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TriggerState
{
	ClassDescriptor cd;
	FlagState fs;

    public TriggerState(ClassDescriptor cd, FlagState fs) {
	throw new Error("Just use FlagState...roll classdescriptor into it");
	this.cd = cd;
	this.fs = fs;
    }

	public ClassDescriptor getClassDescriptor()
	{
		return cd;
	}
	
	public FlagState getState()
	{
		return fs;
	}

	public boolean equals(TriggerState ts)
	{
		if ((this.getClassDescriptor().getNum()==ts.getClassDescriptor().getNum()) && (this.getState().isEqual(ts.getState())))
		{
			return true;
		}
		else
		{
			return false;
		}
	}

}

