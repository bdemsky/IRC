
public class BoxLocation 
{
	int row;
	int column;
	ArrayList array;
	
	public BoxLocation(int row, int column)
	{
		this.row = row;
		this.column = column;
	}
	
	private BoxLocation(int row, int column, ArrayList array)
	{
		this.row = row;
		this.column = column;
		this.array = array;
	}
	public String toString()
	{
		return "row " + (row + 1) + " column " + (column + 1);
	}
	
	public int getRow()
	{
		return row;
	}
	
	public int getColumn()
	{
		return column;
	}
	
	public BoxLocation clone()
	{
		return new BoxLocation(row, column, (ArrayList) array.clone());
	}
	
}
