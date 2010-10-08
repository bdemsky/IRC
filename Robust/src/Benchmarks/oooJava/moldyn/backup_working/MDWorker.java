public class MDWorker {

  int interacts;
  double vir;
  double epot;
  double sh_force2[][];
  
  public MDWorker(int partsize) {
    sh_force2=new double[3][partsize];
//    for(int i=0;i<3;i++){
//      for(int j=0;j<partsize;j++){
//        sh_force2[i][j]=0;
//      }
//    }
  }

}
