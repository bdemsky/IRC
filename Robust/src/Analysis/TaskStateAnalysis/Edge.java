package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class Edge
{
	FlagState fs;
	String name;

	public Edge(FlagState fs, String name)
	{
		this.fs=fs;
		this.name=name;
	}

	public FlagState getState()
	{
		return fs;
	}

	public String getName()
	{
		return name;
	}

}

