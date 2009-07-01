

public class convertURNHex extends Preprocessor
{
    public convertURNHex() {
        super();
    }
    public void process(String str)
    {
      char[] str_char = str.toCharArray();
      int src = 0;
      int dst = 0;                                                          
      char c;

      while((c = str_char[src]) != '\0') {
          if (c == '%') {
              char[] hex = new char[3];
              hex[0] = Character.toLowerCase((str_char[src+1]));
              hex[1] = Character.toLowerCase((str_char[src+2]));
              hex[2] = '\0';
                         
              String a = String.valueOf(hex);
              int i = Integer.parseInt(a,16);
              src+=2;
              str_char[src] = (char)i;
          }
          str_char[dst] = str_char[src];
          dst++;
          src++;
      }
      str_char[dst] = '\0';
      
      str = String.valueOf(str_char);
    }
}
