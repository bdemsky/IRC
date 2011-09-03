package Analysis.SSJava;

import java.util.Set;
import java.util.HashSet;
//contains all binary relations
public class RelationSet{
    Set<BinaryRelation> relations;
    
    public RelationSet(){
	relations = new HashSet<BinaryRelation>();
    }

    public void addRelation(BinaryRelation br){
	if(!relations.contains(br))
	    relations.add(br);
    }

    public String toString(){
	String toReturn = "";
	for(BinaryRelation br: relations)
	    toReturn += br.toString() + "\n";
	return toReturn;
    }
}