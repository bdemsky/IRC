
public class PossibleNumbers
{
	//would usually be set to 3 and that is done in the constructor
	private int maxLookups;
	
	private ArrayList nums;
	//this makes it so we don't have to reconsider numbers we've already searched...
	private int timesTouched;
	
	public PossibleNumbers()
	{
		maxLookups = 3;
		
		nums = new ArrayList();
		for(int x = 1; x <= 9; x++)
			nums.add(new Integer(x));
		
		timesTouched = 0;
	}
	
	public PossibleNumbers(int num)
	{
		maxLookups = 3;
		
		nums = new ArrayList();
		nums.add(new Integer(num));	
		
		timesTouched = 0;
	}
	
	//private cloner
	private PossibleNumbers(ArrayList array, int touched)
	{
		maxLookups = 3;
		
		nums = (ArrayList) array.clone();
		//reset counter to have everything in again...
		//potentially triples the solving time but it will fix all
		//known errors
		timesTouched = 0; 
	}
	
	public boolean isDone()
	{	
		return (nums.size() == 1);
	}
	
	//this will be called before every search...
	public boolean considerable()
	{
		return (isDone() && timesTouched <= maxLookups);
	}
	
	
	public Integer getNum()
	{
		timesTouched++; 
		return (Integer) nums.get(0);
	}
	
	public int getNumNoTouch()
	{
		if(isDone())
		{
			return ((Integer) nums.get(0)).intValue();
		}
		
		return 0;
	}
	
	//Changed from void to boolean to account for board failures 
	public boolean remove(Integer num) 
	{
		if(!isDone())
		{
			nums.remove(num);
		}
		
		if(nums.size() < 1)
		{
			//System.out.println("AN ERROR WOULD NORMALLY BE THROWN HERE; invalid remove preformed in PossibleNumbers.remove");
			return false;
		}
		return true;
	}
	
	//this is used to clone the object
	public PossibleNumbers clone()
	{
		return new PossibleNumbers(nums, timesTouched);
	}
	
	public ArrayList getArray()
	{
		return nums;
	}
	
	public void setPossibility(Integer i)
	{
		nums = new ArrayList();
		nums.add(i);
		//that way it can be reconsidered...
		timesTouched = 0;
	}
}
