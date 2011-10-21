package Analysis.SSJava;

import java.util.ArrayList;
import IR.Descriptor;

public class VarID{
    
    //contains field and var descriptors
    //given the case a.b.f it contains descriptors for a,b, and f
    private ArrayList<Descriptor> var;
    //properties of ID
    private boolean isThis;
    private boolean isGlobal;
    private boolean isTop;
    private boolean isReturn;

    public VarID(){
	this.var = new ArrayList<Descriptor>();
	isThis = false;
	isGlobal = false;
	isTop = false;
	isReturn = false;
    }
    public VarID(Descriptor var){
	this.var = new ArrayList<Descriptor>();
	this.var.add(var);
	isThis = false;
	isGlobal = false;
	isTop = false;
	isReturn = false;
    }
    
    public void addAccess(Descriptor var){
	this.var.add(var);
    }
    
    public ArrayList<Descriptor> getDesc(){
	return var;
    }

    public void setThis(){
	isThis = true;
    }
    
    public boolean isThis(){
	return isThis;
    }

    public void setGlobal(){
	isGlobal = true;
    }

    public boolean isGlobal(){
	return isGlobal;
    }

    public void setTop(){
	isTop = true;
    }
    
    public boolean isTop(){
	return isTop;
    }
    
    public void setReturn(){
	isReturn = true;
    }

    public boolean isReturn(){
	return isReturn;
    }

    public boolean equals(VarID id){
	return var.equals(id.getDesc());
    }

    public String toString(){
	if(isReturn)
	    return "RETURN";
	String toReturn = "";
	for(Descriptor d: var)
	    toReturn += d.toString() + " ";
	return toReturn;
    }
}