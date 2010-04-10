
public class Solver 
{
	public static Board go(Board boardIn) 
//	throws FatalError	//thrown when the first board fails
	{
		//makes sure we dont mess with the recursive boardness...
		Board board = boardIn.clone();
		
		//Try to solve and if there are are errors than abort the current try
		if(!board.solve() && !board.hasError())
		{
			//clones the board
			Board clone = board.clone();
			//gets the possible numbers from cloned board
			ArrayList possible = clone.getEmptyBoxes();
			
			//for every element in possible numbers.
			for(int j = 0; j < possible.size(); j++)
			{
				PossibleNumbers pn = (PossibleNumbers) possible.get(j);
				
				//saves the current edition of possibleNumbers for after testing it out.
				PossibleNumbers revert = pn.clone();
				//get their cloned ArrayList of Numbers
				ArrayList tryNumbers = pn.getArray();
				
				//for all the elements in the ArrayList of numbers
				for(int k = 0; k < tryNumbers.size(); k ++)
				{
					Integer i = (Integer) tryNumbers.get(k);
					pn.setPossibility(i);
					
//					try
					{
						Board boardStacked = go(clone);
						
						//if they're not equal, that means it worked!
						if(boardStacked.isComplete() && !boardStacked.hasError())
							return boardStacked;
					}
//					catch (Exception e)
//					
//					{
//						//I don't care if a step in fails...
//						//this just means we tried an invalid number
//					}
				}
				
				//reverts it back to the previous PossibleNumber before the changes...
				pn = revert;
				
			}
			//return empty if it fails...
			return board;
		}
		else
			return board;
	}
	
}
