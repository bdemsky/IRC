

public class convertURNHex extends Preprocessor
{
    public convertURNHex() {
        super();
    }
    public static void process(String str)
    {
      char[] str_char = str.toCharArray();
      int src = 0;
      int dst = 0;                                                          
      char c;

      while((c = str_char[src]) != '\0') {
          if (c == '%') {
              char[] hex = new char[3];
              hex[0] = toLowerCase((int)str_char[src+1]);
              hex[1] = toLowerCase((int)str_char[src+2]);
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
      
      str = new String(str_char);
    }

    private static char toLowerCase(int a) {
        
        if(a >= 65 && a <= 90)
        {
            return (char)(a + 32);  // difference 'a'(97) - 'A'(65) = 32
        }
        else return (char)a;


    }
}
