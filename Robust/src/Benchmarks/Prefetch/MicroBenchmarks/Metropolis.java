public class Metropolis {
  public static void main(String[] args) {
    int N = Integer.parseInt(args[0]);
    double kT = Double.parseDouble(args[1]);
    StdDraw.setXscale(0, N);
    StdDraw.setYscale(0, N);
    State state = new State(N, 0.5);
    while (true) {
      state.phase(kT);
      state.draw();
      StdDraw.show(50);
    }
  }
}
