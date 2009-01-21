

/*package dstm2.util;


import java.util.Locale;
import java.util.TimeZone;
import sun.util.calendar.CalendarSystem;
import sun.util.calendar.JulianCalendar;
import sun.util.calendar.ZoneInfo;


public class Calendar {
    public final static int ERA = 0;
    public final static int YEAR = 1;
    public final static int MONTH = 2;
    public final static int WEEK_OF_YEAR = 3;
    public final static int DATE = 5;
    protected boolean       isTimeSet;
    protected boolean       areFieldsSet;
    protected boolean       isSet[];
    transient private int   stamp[];
    private int             nextStamp = MINIMUM_USER_STAMP;
    private static final int        MINIMUM_USER_STAMP = 2;
    transient boolean       areAllFieldsSet;
    private boolean         lenient = true;
    protected int           fields[];
    transient private boolean sharedZone = false;
    private TimeZone zone;
    protected long          time;
    public final static int FIELD_COUNT = 17;
    
     public void set(int field, int value)
     {
           if (isLenient() && areFieldsSet && !areAllFieldsSet) {
                computeFields();
           }
           internalSet(field, value);
           isTimeSet = false;
           areFieldsSet = false;
           isSet[field] = true;
           stamp[field] = nextStamp++;
           if (nextStamp == Integer.MAX_VALUE) {
               adjustStamp();
            }
      }
     
     public boolean isLenient()
     {
            return lenient;
     }
     
     final void internalSet(int field, int value)
     {
           fields[field] = value;
     }
     
       public void setTimeZone(TimeZone value)
       {
            zone = value;
            sharedZone = false;


            areAllFieldsSet = areFieldsSet = false;
        }
 
   
        public final void set(int year, int month, int date)
        {
            set(YEAR, year);
            set(MONTH, month);
            set(DATE, date);
   }
        
     public static Calendar getInstance()
     {
           Calendar cal = createCalendar(TimeZone.getDefault(), Locale.getDefault());
           cal.sharedZone = true;
           return cal;
     }
     
      private static Calendar createCalendar(TimeZone zone,
                                           Locale aLocale)
      {                                            
   
          // else create the default calendar
           return new GregorianCalendar(zone, aLocale);
      }
      
       public void setTimeInMillis(long millis) {
           // If we don't need to recalculate the calendar field values,
          // do nothing.
          if (time == millis && isTimeSet && areFieldsSet && areAllFieldsSet
              && (zone instanceof ZoneInfo) && !((ZoneInfo)zone).isDirty()) {
                return;
         }
            time = millis;
          isTimeSet = true;
          areFieldsSet = false;
            computeFields();
           areAllFieldsSet = areFieldsSet = true;
      }
       
       protected Calendar(TimeZone zone, Locale aLocale)
       {
             fields = new int[FIELD_COUNT];
             isSet = new boolean[FIELD_COUNT];
             stamp = new int[FIELD_COUNT];
             this.zone = zone;
             setWeekCountData(aLocale);
        } 
       
 
 
}*/
