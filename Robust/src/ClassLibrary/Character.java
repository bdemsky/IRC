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

  public static boolean isDigit(char ch) {
    // TODO This is a temparory implementation, there are other groups of digits
    // besides '0' ~ '9'
    if (ch>='0'&&ch<='9')
      return true;
    else
      return false;
  }

  char value;

  public Character(char c) {
    value = c;
  }

  public Character(Character c) {
    value = c.value;
  }

  public String toString() {
    return ""+value;
  }

  public static boolean isWhitespace(char character) {
    boolean returnValue;
    if ( (character == '\t') ||
         (character == '\n') ||
         (character == ' ') ||
         (character == '\u000C') ||
         (character == '\u001C') ||
         (character == '\u001D') ||
         (character == '\u001E') ||
         (character == '\u001F')) {
      returnValue = true;
    } else {
      returnValue = false;
    }
    return returnValue;
  }

  public static final int MIN_RADIX = 2;
  public static final int MAX_RADIX = 36;

  public static char forDigit(int digit, int radix) {
    if ((digit >= radix) || (digit < 0)) {
      return '\0';
    }
    if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX)) {
      return '\0';
    }
    if (digit < 10) {
      return (char)('0' + digit);
    }
    return (char)('a' - 10 + digit);
  }
}
