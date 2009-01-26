/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

/**
 *
 * @author navid
 */
public class INode implements Comparable{
   
    private String filepath;
    private long number;


    public INode(long number) {
        this.number = number;
    }
    
    public INode(long number, String filepath) {
        this(number);
        this.filepath = filepath;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }
    
    
    
    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }

    public int compareTo(Object arg0) {
        INode other = (INode) arg0;
        if (this.getNumber() < other.getNumber())
            return -1;
        else if (this.getNumber() > other.getNumber())
            return 1;
        else{ 
            System.out.println("Logical Error Two Inodes cannot have the same number" + this.filepath + " " + other.filepath);
            return 0;
        }
       
    }
    
    
    

}
