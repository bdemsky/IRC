public class Test
{
    public Test(){}

    public static void main(String args[]) {

        System.out.println("# it starts");
        Test t = new Test();
        t.doSomeWork();

    }

    public void doSomeWork()
    {

	//hard-coded in board solution: http://www.websudoku.com/?level=4&set_id=1031120945
	int[][] puzzle = new int[9][9];
	puzzle[0][0] = 3;
	puzzle[0][1] = 0;
	puzzle[0][2] = 6;
	puzzle[0][3] = 0;
	puzzle[0][4] = 0;
	puzzle[0][5] = 8;
	puzzle[0][6] = 0;
	puzzle[0][7] = 0;
	puzzle[0][8] = 4;
	puzzle[1][0] = 0;
	puzzle[1][1] = 0;
	puzzle[1][2] = 0;
	puzzle[1][3] = 4;
	puzzle[1][4] = 0;
	puzzle[1][5] = 0;
	puzzle[1][6] = 0;
	puzzle[1][7] = 0;
	puzzle[1][8] = 0;
	puzzle[2][0] = 0;
	puzzle[2][1] = 0;
	puzzle[2][2] = 9;
	puzzle[2][3] = 0;
	puzzle[2][4] = 0;
	puzzle[2][5] = 0;
	puzzle[2][6] = 6;
	puzzle[2][7] = 2;
	puzzle[2][8] = 0;
	puzzle[3][0] = 0;
	puzzle[3][1] = 0;
	puzzle[3][2] = 0;
	puzzle[3][3] = 0;
	puzzle[3][4] = 3;
	puzzle[3][5] = 0;
	puzzle[3][6] = 0;
	puzzle[3][7] = 6;
	puzzle[3][8] = 5;
	puzzle[4][0] = 8;
	puzzle[4][1] = 0;
	puzzle[4][2] = 0;
	puzzle[4][3] = 1;
	puzzle[4][4] = 0;
	puzzle[4][5] = 2;
	puzzle[4][6] = 0;
	puzzle[4][7] = 0;
	puzzle[4][8] = 7;
	puzzle[5][0] = 5;
	puzzle[5][1] = 3;
	puzzle[5][2] = 0;
	puzzle[5][3] = 0;
	puzzle[5][4] = 6;
	puzzle[5][5] = 0;
	puzzle[5][6] = 0;
	puzzle[5][7] = 0;
	puzzle[5][8] = 0;
	puzzle[6][0] = 0;
	puzzle[6][1] = 7;
	puzzle[6][2] = 2;
	puzzle[6][3] = 0;
	puzzle[6][4] = 0;
	puzzle[6][5] = 0;
	puzzle[6][6] = 1;
	puzzle[6][7] = 0;
	puzzle[6][8] = 0;
	puzzle[7][0] = 0;
	puzzle[7][1] = 0;
	puzzle[7][2] = 0;
	puzzle[7][3] = 0;
	puzzle[7][4] = 0;
	puzzle[7][5] = 1;
	puzzle[7][6] = 0;
	puzzle[7][7] = 0;
	puzzle[7][8] = 0;
	puzzle[8][0] = 9;
	puzzle[8][1] = 0;
	puzzle[8][2] = 0;
	puzzle[8][3] = 2;
	puzzle[8][4] = 0;
	puzzle[8][5] = 0;
	puzzle[8][6] = 7;
	puzzle[8][7] = 0;
	puzzle[8][8] = 6;
	Board b = new Board(puzzle);
	Board solved = Solver.go(b);

	System.out.println(solved);
    }

}
