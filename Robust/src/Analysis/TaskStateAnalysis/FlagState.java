package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;



public class FlagState
{
	Hashtable flagstate;

	public FlagState(FlagDescriptor[] flags)
	{
		flagstate=new Hashtable(flags.length);
		{
			for (int i=0; i < flags.length; i++)
			{
				flagstate.put(flags[i],new Boolean(false));
			}
		}
	}

	public FlagState(Hashtable flagstate)
	{
		this.flagstate = new Hashtable(flagstate);
	}

	public Hashtable getStateTable()
	{
		return 	flagstate;
	}

	public void put(FlagDescriptor fd, Boolean status)
	{
		flagstate.put(fd,status);
	}
	public boolean get(FlagDescriptor fd)
	{
		if (! flagstate.containsKey(fd))
		{
			return false;
		}
		else
		{
			return ((Boolean)(flagstate.get(fd))).booleanValue();
		}
	}

	
	public String toString()
	{
		StringBuffer sb = new StringBuffer(flagstate.size());
		Enumeration e = flagstate.keys();

		while (e.hasMoreElements())
		{
			if (((Boolean)(flagstate.get((FlagDescriptor)e.nextElement()))).booleanValue())
				sb.append(1);
			else
				sb.append(0);
		}
		return new String(sb);
	}
	
	public String toString(FlagDescriptor[] flags)
	{
		StringBuffer sb = new StringBuffer(flagstate.size());
		
		Enumeration e;
		
		for(int i=0;i < flags.length; i++)
		{
			e = flagstate.keys();
			
			while (e.hasMoreElements())
			{
				FlagDescriptor fdtemp=(FlagDescriptor)e.nextElement();
				if( flags[i] == fdtemp)
				{
					if (((Boolean)(flagstate.get(fdtemp))).booleanValue())
						sb.append(1);
					else
						sb.append(0);
				}
			}
		}
		return new String(sb);
	}

	public Enumeration getFlags()
	{
		return flagstate.keys();
	}
	
	public boolean isEqual(FlagState fs)
	{
		Enumeration en = fs.getFlags();
		while(en.hasMoreElements())
		{
				
			FlagDescriptor flag=(FlagDescriptor)en.nextElement();
			
			//System.out.println(flag.toString()+" "+fs.get(flag)+"   "+this.get(flag));
			if (fs.get(flag) != this.get(flag))
			    return false;
		}
		return true;
	}
}
