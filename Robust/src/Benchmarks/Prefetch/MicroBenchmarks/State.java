/*************************************************************************
 *  Compilation:  javac State.java
 *  Execution:    java State N T
 *  Dependencies: StdDraw.java
 *  
 *  Create an N-by-N grid of sites.
 *
 *  % java 50 2
 *
 *  % java 50 2.1
 * 
 *  % java 50 2.2
 *
 *************************************************************************/

public class State {
  private int N;                  // N-by-N grid
  private Cell[][] lattice;       // N-by-N lattice of cells

  // N-by-N grid, p = prob of up spin
  public State(int N, double p) {
    this.N  = N;
    this.lattice = new Cell[N][N];

    // initialize at random
    for (int i = 0; i < N; i++)
      for (int j = 0; j < N; j++)
        lattice[i][j] = new Cell(p);
  }


  // total magnetization
  public double magnetization() {
    double M = 0.0;
    for (int i = 0; i < N; i++)
      for (int j = 0; j < N; j++)
        M += lattice[i][j].magnetization();
    return M;
  }

  // total energy of site (i, j), using periodic boundary conditions
  // assumes 0 <= i, j < N
  private double energy(int i, int j) {
    double E = 0.0;
    E += lattice[(i+1)%N][j].magnetization();
    E += lattice[i][(j+1)%N].magnetization();
    E += lattice[(i-1+N)%N][j].magnetization();
    E += lattice[i][(j-1+N)%N].magnetization();
    E *= lattice[i][j].magnetization();
    return -E;
  }
  // total energy, using periodic boundary conditions
  public double energy() {
    double E = 0.0;
    for (int i = 0; i < N; i++)
      for (int j = 0; j < N; j++)
        E += energy(i, j); 
    return 0.5 * E;   // divide by two to mitigate double-counting
  }

  // flip spin of cell i-j if energy decreases or Metropolis increase
  public void metropolis(int i, int j, double kT) {
    double deltaE = -2 * energy(i, j);
    if ((deltaE <= 0) || (Math.random() <= Math.exp(-deltaE  / kT)))
      lattice[i][j].flip();
  }

  // one Metropolis phase - N^2 random steps
  // kT = temperature (say between 1 and 4)
  public void phase(double kT) {
    for (int steps = 0; steps < N*N; steps++) {
      int i = (int) (Math.random() * N);
      int j = (int) (Math.random() * N);
      metropolis(i, j, kT);
    }
  }

  // plot Ising state
  public void draw() {
    /*
    for (int i = 0; i < N; i++)
      for (int j = 0; j < N; j++)
        lattice[i][j].draw(i + 0.5, j + 0.5);

    // draw grid lines
    StdDraw.setPenColor(StdDraw.BLACK);
    for (int i = 0; i < N; i++) {
      StdDraw.line(i, 0, i, N);
      StdDraw.line(0, i, N, i);
    }
    */
  }

  // string representation
  public String toString() {
    String NEWLINE = System.getProperty("line.separator");
    String s = "";
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        s += lattice[i][j] + " ";
      }
      s += NEWLINE;
    }
    return s;
  }


  public static void main(String[] args) {
    int N = Integer.parseInt(args[0]);          // N-by-N lattice
    double kT = Double.parseDouble(args[1]);    // temperature
    State state = new State(N, 1.0);

    //StdDraw.setXscale(0, N);
    //StdDraw.setYscale(0, N);

    System.out.println("warming up");
    for (int t = 0; t < 10000; t++) state.phase(kT);
    System.out.println("done warming up");

    double sumM = 0.0;
    for (int t = 1; true; t++) {
      state.phase(kT);
      sumM += state.magnetization();
      if (t % 10000 == 0) { 
        System.out.println("kT = " + kT +  " (" + t + "): " + sumM / t / (N*N));
      }
    }
  }

}

