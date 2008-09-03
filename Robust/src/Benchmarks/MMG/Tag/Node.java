public class Node {
    
    int m_locX;
    int m_locY;
    int m_index;
    Vector m_neighbours;
    
    public Node(int locX, int locY, int index) {
	this.m_locX = locX;
	this.m_locY = locY;
	this.m_index = index;
	this.m_neighbours = new Vector();
    }
    
    public void addNeighbour(Node n) {
	this.m_neighbours.addElement(n);
    }
    
    public Vector getNeighbours() {
	return this.m_neighbours;
    }
    
    public int getXLoc() {
	return this.m_locX;
    }
    
    public int getYLoc() {
	return this.m_locY;
    }
    
    public int getIndex() {
	return this.m_index;
    }
}