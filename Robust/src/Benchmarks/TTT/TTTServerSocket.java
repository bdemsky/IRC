public class TTTServerSocket extends Socket {
	// TTTServerSocket flags
	flag TTTSInitialize;

	flag MakeMove;
	flag SendError;
	flag SendBoard;
	flag SendDone;
	
	String request;
	int row, col;
		
	//Constructor
	public TTTServerSocket(){
		System.printString("Constructing TTTServerSocket....\n");
	}

	public int receive()
	{
		byte b1[] = new byte[1024];
		read(b1);
		request = new String(b1);
		System.printString("request: ");
		System.printString(request);
		if (parseTransaction() == 1) {
			return 1;
		}
		return 0;
	}

	// Parse request
	public int parseTransaction(){
		int start = request.indexOf('_');
		String s = request.subString(start+1);
//_move:3:3
		if (s.startsWith("move")==true){
			//Get row
			int i1 = s.indexOf(':');
			String rowStr = new String(s.subString(i1+1, i1+2));
			row = Integer.parseInt(rowStr);

			//Get col
			String s2 = new String(s.subString(i1+2));
			int i2 = s2.indexOf(':');
			String colStr = new String(s.subString(i2+1, i2+2));
			col = Integer.parseInt(colStr);
			return 1;
			
		}
		// Error transaction
		return -1;
	}
	
	public int getRow(){
		return row;
	}
	public int getCol(){
		return col;
	}
	
	public void sendBoardDisplay(Board theBoard) {
		StringBuffer line1 = new String ("display_");

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if (theBoard.board[i][j] == 1)
					line1.append("X");
				else if (theBoard.board[i][j] == 2)
					line1.append("O");
				else
					line1.append("-");
			}
			line1.append("_");
		}
		String towrite = new String(line1);
		write(towrite.getBytes());
		return;
	}
	
	public void sendDone(int winner) {
		StringBuffer line1 = new String ("done_");
		if (winner == 0)
			line1.append("tie");
		else if (winner == 1)
			line1.append("player");
		else
			line1.append("computer");
			
		String towrite = new String(line1);
		write(towrite.getBytes());
		return;
	}
	
	public void sendError() {
		StringBuffer line1 = new String ("error_wrongmove");
			
		String towrite = new String(line1);
		write(towrite.getBytes());
		return;
	}
}
