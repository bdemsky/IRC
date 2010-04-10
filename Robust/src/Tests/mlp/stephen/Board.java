
public class Board 
{
	private PossibleNumbers[][] board;
	private boolean changesWereMade;
	private boolean Error; //added to replace throwing Exceptions
	
	//attempts to solve the thing
	// if is incomplete at the end of solving routine, will return false
	public boolean solve()
	{
		do
		{
			//resets the flag to begin with
			reset();
				
			singlePass();
			if(Error) return false;
		}
		while(!isDone());
		
		//if it's done, it's done...
		return isComplete();
		
	}
	
	/**
	 * This function eliminates used possibilities already and was originally 
	 * intended for the "solve()" function. However, when used only once by itself,
	 * it can be used to check for duplicates without additional code.
	 */
	public void singlePass()
	{
		//Didn't use to be this messy, but this is how I fixed errors being thrown
		checkRows();
		if(Error) return;
		
		checkColumns();
		if(Error) return;
		
		checkBoxes();	
		if(Error) return;
	}

	//used for cloning
	private Board(PossibleNumbers[][] array)
	{
		changesWereMade = false;
		board = array;
		Error = false;
	}
	
	public Board(int[][] input)
	{
		board = new PossibleNumbers[9][9];
		
		for(int row = 0; row < 9; row++)
		{
			for(int column = 0; column < 9; column++)
			{	
				if(input[row][column] != 0)
				{
					board[row][column] = new PossibleNumbers(input[row][column]);
				}
				else
				{
					board[row][column] = new PossibleNumbers();
				}
			}
		}
		
		Error = false;
		changesWereMade = false;
		
	}
	
	public void checkRows()
	{
		for(int row = 0; row < 9; row ++)
		{
			//stores the numbers that we know are true already...
			ArrayList numbersDone = new ArrayList();
			ArrayList pointData = new ArrayList();
			
			//this for grabs the elements done
			for(int element = 0; element < 9; element++)
			{			
				if(board[row][element].considerable())
				{
					numbersDone.add(board[row][element].getNum());
					pointData.add(new BoxLocation(row, element));
					changesWereMade = true;
				}
			}
			
			checkDuplicates(numbersDone, pointData);
			if(hasError()) return;
			
			//these for loops eliminates them...
			for(int index = 0; index < numbersDone.size(); index++)
			{
				Integer i = (Integer) numbersDone.get(index);
				for(int element = 0; element < 9; element++)
				{
					if(!board[row][element].remove(i))
					{
						this.setError();
						return;
					}
				}
			}
			
		}
	}
	
	public void checkColumns()
	{
		for(int column = 0; column < 9; column ++)
		{
			//stores the numbers that we know are true already...
			ArrayList numbersDone = new ArrayList();
			ArrayList pointData = new ArrayList();
			
			//this for grabs the elements done
			for(int element = 0; element < 9; element++)
			{			
				if(board[element][column].considerable())
				{
					numbersDone.add(board[element][column].getNum());
					pointData.add(new BoxLocation(element, column));
					changesWereMade = true;
				}
			}
			
			checkDuplicates(numbersDone, pointData);
			if(hasError()) return;
			
			//these for loops eliminates them...
			for(int index = 0; index < numbersDone.size(); index++)
			{
				Integer i = (Integer) numbersDone.get(index);
				for(int element = 0; element < 9; element++)
				{
					if(!board[element][column].remove(i))
					{
						setError();
						return;
					}
				}
			}
			
		}
	}
	
	public void checkBoxes() 
	{
		for(int boxRow = 0; boxRow <= 6; boxRow += 3)
		{
			for(int boxColumn = 0; boxColumn <= 6; boxColumn += 3)
			{
				checkBoxesHelper(boxRow, boxColumn);
				if(hasError()) return;
			}
		}
	}
	
	public void checkBoxesHelper(int startingRow, int startingColumn) 
	{
		//stores elements to be removed...
		ArrayList numbersDone = new ArrayList();
		ArrayList pointData = new ArrayList();
		
		//checks for elements to be checked for
		for(int row = startingRow; row < (3 + startingRow); row++)
		{
			for(int column = startingColumn; column < (3 + startingColumn); column++)
			{
				if(board[row][column].considerable())
				{
					numbersDone.add(board[row][column].getNum());
					pointData.add(new BoxLocation(row, column));
					changesWereMade = true;
				}
			}
		}
		
		
		//checks for duplicates
		checkDuplicates(numbersDone, pointData);
		if(hasError()) return;
		
		//removes those elements
		for(int index = 0; index < numbersDone.size(); index++)
		{
			Integer i = (Integer) numbersDone.get(index);
			for(int row = startingRow; row < (3 + startingRow); row++)
			{
				for(int column = startingColumn; column < (3 + startingColumn); column++)
				{
					if(!board[row][column].remove(i))
					{
						setError();
						return;
					}
				}
			}
		}
	}
	
	public void reset()
	{
		changesWereMade = false;
	}
	
	public boolean isDone()
	{
		return !changesWereMade;
	}
	
	public boolean isComplete()
	{
		//searches through all elements to make sure they're done
		for(int row = 0; row < 9; row++)
		{
			for(int column = 0; column < 9; column++)
			{
				if(!(board[row][column].isDone()))
				{
					return false;
				}
			}
		}
		
		return true;
	}

	public int[][] getArray()
	{
		int[][] result = new int[9][9];
		
		for(int row = 0; row < 9; row++)
			for(int column = 0; column < 9; column++)
				result[row][column] = board[row][column].getNumNoTouch();
				
		return result;
	}
	
	public String toString()
	{
		String finalString = "";
		int[][] array = getArray();
		
		
		for(int row = 0; row < 9; row++)
		{
			for(int column = 0; column < 9; column++)
			{
				char outputChar = (char)(array[row][column] + 48) ;
				
				//replaces 0 with ?
				if(outputChar == 48)
					outputChar = '?';
				
				finalString += " " + outputChar;
				
				//inserts pipes to separate them.
				if(column == 2 || column == 5)
				{
					finalString += " |";
				}
			}
			
			//inserts a row of awesomeness
			if(row == 2 || row == 5)
			{
				finalString += "\n ---------------------";
			}
			
			//new line
			finalString += "\n";
		}
		
		return finalString;
	}

	public Board clone()
	{
		PossibleNumbers[][] newBoard = new PossibleNumbers[9][9];
		
		//clones all the elements in the old board...
		for(int row = 0; row < 9; row++)
		{
			for(int column = 0; column < 9; column++)
			{
				newBoard[row][column] = board[row][column].clone();
			}
		}
		
		return new Board(newBoard);
	}

	public void checkDuplicates(ArrayList array, ArrayList point)
	{
		//there's no need to check if there's only 1 element in here...
		if(array.size() > 1)
		{
			//the last object doesn't need to checked against anything else
			for(int element = 0; element < (array.size() - 1); element++)
			{
				//+ 1 because the element should not check against itself
				for(int x = element + 1; x < array.size(); x++)
				{
					//Here's a bug that took an hour to find because of not auto-casting. 
					if(((Integer)array.get(element)).intValue() == ((Integer) array.get(x)).intValue())
					{
						//System.out.println("FATAL ERROR WOULD HAVE BEEN THROWN: Duplicate item at " + point.get(element));
						this.setError();
						return;
					}
				}
			}
		}
	}
	
	public ArrayList getEmptyBoxes()
	{
		//stores remaining empty boxes
		ArrayList list = new ArrayList();
		
		for(int row = 0; row < 9; row++)
			for(int column = 0; column < 9; column++)
				if(!board[row][column].isDone())
					list.add(board[row][column]);
					
		
		return list;
	}
	
	private void setError()
	{
		Error = true;
	}
	
	public boolean hasError()
	{
		return Error;
	}
}

