public class ThreadClass {
  Transaction[] trans;
  
  public ThreadClass(int numTrans) {
    trans=new Transaction[numTrans];
  }

  public int numTransactions() {
    return trans.length;
  }

  public Transaction getTransaction(int i) {
    return trans[i];
  }

  public void setTransaction(int i, Transaction t) {
    trans[i]=t;
  }
}