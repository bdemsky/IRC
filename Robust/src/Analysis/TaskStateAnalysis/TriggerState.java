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

	public TriggerState(ClassDescriptor cd, FlagState fs)
	{
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
}

