
public class Parser 
{
	private File file;
	private int[][] preBoard;
	
	public Parser(String filename)
	{
		file = new File(filename);
		preBoard = new int[9][9];
	}
	
	public int[][] go() 
	{
		FileInputStream in = new FileInputStream(file.getPath());
		

		for(int row = 0; row < 9; row++)
		{
			//grabs the row we're on
			String temp = in.readLine();
			if(temp == null)
			{
				System.out.println("Malformed file (not enough lines)");
				return null;
			}

			//builds new scanner for the line
			StringTokenizer scan = new StringTokenizer(temp);
			if(scan.countTokens() < 8)
			{
				System.out.println("Malformed file (not enough columns");
				return null;
			}
			
			for(int column = 0; column < 9; column++)
			{
				int num = Integer.parseInt(scan.nextToken());
				
				
				//we may remove this later so that we can have everything instead of just this....
				if(num > 9 || num < 0)
				{
					System.out.println("File is malformed");
					return null;
//							throw new FatalError("File is malformed");
				}
				else
				{
					preBoard[row][column] = num;
				}
				
			}
		}
		
		System.out.println("Parsing complete, returning result");
		return preBoard;
	}
}
