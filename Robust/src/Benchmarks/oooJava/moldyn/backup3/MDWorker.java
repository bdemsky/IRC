public class MDWorker {

  int interacts;
  double vir;
  double epot;
  double sh_force2[][];
  
  public MDWorker(int partsize) {
    sh_force2=new double[3][partsize];
  }

}
