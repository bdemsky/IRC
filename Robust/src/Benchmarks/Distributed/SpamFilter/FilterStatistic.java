public class FilterStatistic {
  int unknown;
  int spam;
  int ham;

  // -------------------------------------------------------

  public FilterStatistic() {
    this.spam = 0;
    this.ham = 0;
    this.unknown = 0;
  }

  public FilterStatistic(int spam, int ham, int unknown) {
    this.spam = spam;
    this.ham = ham;
    this.unknown = unknown;
  }

  public int getChecked() {
    return getSpam() + getHam() + getUnknown();
  }

  public int getHam() {
    return ham;
  }

  public int getSpam() {
    return spam;
  }

  public void setHam(int i) {
    ham = i;
  }

  public void setSpam(int i) {
    spam = i;
  }

  public int getUnknown() {
    return unknown;
  }

  public void setUnknown(int u) {
    unknown = u;
  }

  public void increaseSpam() {
    setSpam(getSpam() + 1);
  }

  public void increaseHam() {
    setHam(getHam() + 1);
  }

  public void increaseUnknown() {
    setUnknown(getUnknown() + 1);
  }

  public String toString() {
    String str = "Filterstats_spam_"+spam;
    str += "_ham_" +ham;
    str += "_unknown_"+unknown;
    return str;
  }
}
