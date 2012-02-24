import java.io.*;

public class TreeDotGen{

    public int idx;
    
    public static void main(String args[]){
	TreeDotGen t=new TreeDotGen();
	t.generate();
    }

    public void generate(){
	
	try{
	    FileWriter fstream=new FileWriter("out.dot");
	    BufferedWriter out=new BufferedWriter(fstream);
   
	    out.write("digraph btree{\n");
	    idx=0;
	    generateTree(0,out);

	    out.write("}\n");
	    
	    out.close();
	}catch(Exception e){

	}

    }

    public void generateTree(int parentIdx,BufferedWriter out) throws IOException{

	
	//left

	int leftIdx= ++idx;
	out.write("N"+parentIdx+"->"+"N"+leftIdx+"\n");

	//right
	int rightIdx= ++idx;
	out.write("N"+parentIdx+"->"+"N"+rightIdx+"\n");

	if(idx<100){
	    generateTree(leftIdx,out);	
	    generateTree(rightIdx,out);	
	}
    }

}