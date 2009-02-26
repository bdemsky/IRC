public class Character {

  public static int digit(char ch, int radix) {
    if (ch>='0'&&ch<='9')
      return ch-'0';
    else if (ch>='a'&&ch<='z') {
      int val=(ch-'a')+10;
      if (val<radix)
	return val;
    } else if (ch>='A'&&ch<='Z') {
      int val=(ch-'A')+10;
      if (val<radix)
	return val;
    }
    return -1;
  }

  char value;
  
  public Character( char c ) {
    value = c;
  }

  public Character( Character c ) {
    value = c.value;
  }

  public String toString() {
    return ""+value;
  }
}
