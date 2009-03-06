/**
 ** Tree and its properties
 **/
public class TreeType {
  private int age;

  public TreeType() {
    age = 0;
  }

  public int getage() {
    return age;
  }

  public void incrementage() {
    age++;
  }

  public void incrementTenYrs() {
    age = age + 10;
  }

  public void incrementFiveYrs() {
    age = age + 5;
  }

  public int hashCode() {
    return age;
  }

  public String toString() {
    return String.valueOf(age);
  }
}
