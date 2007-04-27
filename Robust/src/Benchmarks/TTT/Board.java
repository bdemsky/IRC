public class Board {
	// TicTacToe Board flags
	flag init;
	
	int[][] board;
	
	int winningplayer;
	
	public Board()	{
		winningplayer = -1;
		board = new int[3][3];
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				board[i][j] = 0;
	}
	
	public int makeMove(int row, int col) {
		if (boardFull() == 1) {
			winningplayer = 0;
			return 2;
		}
		if (board[row][col] != 0) { // Space taken
			return -1;
		}
		else {
			board[row][col] = 1;
			if (checkForWin(1) == 1) { // Check if player won
				winningplayer = 1;
				return 2;
			}
			// Computer makes move
			if (computerMakeMove() == 1) { // If made move successful
				if (checkForWin(2) == 1) { // Check if computer won
					winningplayer = 2;
					return 2;
				}
			}
			else { // Board full, no winner
				winningplayer = 0;
				return 2;
			}
		}
		return 1;
	}

	public int boardFull() {
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				if (board[i][j] == 0)
					return 0;
		return 1;
	}
	
	public int computerMakeMove() {
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				if (board[i][j] == 0) {
					board[i][j] = 2;
					return 1;
				}
		return 0;	
	}
	
	public int checkForWin(int p) {
		// Add logic for checking if player p wins
		// Horiz

		if ((board[0][0] == p) && (board[0][1] == p) && (board[0][2] == p) ||
			(board[1][0] == p) && (board[1][1] == p) && (board[1][2] == p) ||
			(board[2][0] == p) && (board[2][1] == p) && (board[2][2] == p)) {
				return 1;
		}
		
		// Vert
		if ((board[0][0] == p) && (board[1][0] == p) && (board[2][0] == p) ||
			(board[0][1] == p) && (board[1][1] == p) && (board[2][1] == p) ||
			(board[0][2] == p) && (board[1][2] == p) && (board[2][2] == p)) {
				return 1;
		}
		
		//Diag
		if ((board[0][0] == p) && (board[1][1] == p) && (board[2][2] == p) ||
			(board[0][2] == p) && (board[1][1] == p) && (board[2][0] == p)) {
			return 1;
		}
				
		return 0;
	}
	
	public int winner() {
		return winningplayer;
	}
}
