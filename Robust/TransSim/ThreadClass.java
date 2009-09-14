public class ThreadClass {
  Transaction[] trans;

  public String toString() {
    String s="Thread\n";
    for(int i=0;i<trans.length;i++) {
      s+="Transaction "+i+"\n";
      s+=trans[i].toString();
    }
    return s;
  }
  
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