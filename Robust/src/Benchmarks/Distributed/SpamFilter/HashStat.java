public class HashStat {
  int[] userid;
  FilterStatistic[] userstat; 
  int[] listofusers;
  public HashStat() {
    userid = new int[8]; //max users for our system=8
    userstat = new FilterStatistic[8];
    for(int i=0; i<8; i++) {
      userstat[i] = new FilterStatistic();
    }
  }

  public void setuser(int id, int spam, int ham, int unknown) {
    userid[id] = 1;
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
    return userstat[userid].getHam();
  }

  public int getunknowncount(int userid) {
    return userstat[userid].getUnknown();
  }

  public int[] getUsers() {
    int nusers = numUsers();
    listofusers = new int[nusers];
    int j=0;
    for(int i=0; i<8; i++) {
      if(userid[i] == 1) {
        listofusers[j]=i;
        j++;
      }
    }
    return listofusers;
  }

  public int numUsers() {
    int count=0;
    for(int i=0; i<8; i++) {
      if(userid[i] == 1) {
        count++;
      }
    }
    return count;
  }
}
