package Analysis.SSJava;

public class WriteAge {

  private int writeAge;

  public WriteAge(int age) {
    this.writeAge = age;
  }

  public int getAge() {
    return writeAge;
  }

  public WriteAge copy() {
    return new WriteAge(writeAge);
  }

  public void inc() {
    if (writeAge <= DefinitelyWrittenCheck.MAXAGE) {
      writeAge++;
    }
  }

  public int hashCode() {
    return 31 + writeAge;
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof WriteAge)) {
      return false;
    }
    WriteAge other = (WriteAge) obj;
    if (writeAge != other.writeAge) {
      return false;
    }
    return true;
  }

  public String toString() {
    if (writeAge > DefinitelyWrittenCheck.MAXAGE) {
      return "many";
    }
    return Integer.toString(writeAge);
  }

}
