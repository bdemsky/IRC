package Analysis.SSJava;

public class BinaryRelation{
    //suchthat lower<higher
    VarID higher;
    VarID lower;

    public BinaryRelation(VarID h, VarID l){
	higher = h;
	lower = l;
    }
    
    public String toString(){
	return lower.toString()+ "<" + higher.toString();
    }
}