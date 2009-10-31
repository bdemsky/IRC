public class HashStat {
  int[] userid;
  FilterStatistic[] userstat; 
  public HashStat() {
    userid = new int[8]; //max users for our system=8
    userstat = new FilterStatistic[8];
    for(int i=0; i<8; i++) {
      userstat[i] = new FilterStatistic();
    }
  }

  public void setuser(int id, int spam, int ham, int unknown) {
    userstat[id].setSpam(spam);
    userstat[id].setHam(ham);
    userstat[id].setUnknown(unknown);
  }

  public int getuser(int id) {
    return userid[id];
  }

  public int getspamcount(int userid) {
    return userstat[userid].getSpam();
  }

  public int gethamcount(int userid) {
    return userstat[userid].getham();
  }

  public int getunknowncount(int userid) {
    return userstat[userid].getUnknown();
  }
}
