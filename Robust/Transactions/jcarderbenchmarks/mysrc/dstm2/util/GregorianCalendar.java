/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*package dstm2.util;

import java.util.Locale;
import java.util.TimeZone;
import sun.util.calendar.CalendarSystem;
import sun.util.calendar.Gregorian;
import sun.util.calendar.JulianCalendar;



public class GregorianCalendar extends Calendar{



       private static final Gregorian gcal = CalendarSystem.getGregorianCalendar();
       private static JulianCalendar jcal;
       public GregorianCalendar(TimeZone zone, Locale local)
       {
            super(zone, aLocale);
            gdate = (BaseCalendar.Date) gcal.newCalendarDate(zone);
            setTimeInMillis(System.currentTimeMillis())
       }
       
        protected void computeFields() {
            int mask = 0;
            if (isPartiallyNormalized()) {
                // Determine which calendar fields need to be computed.
               mask = getSetStateFields();
               int fieldMask = ~mask & ALL_FIELDS;
               // We have to call computTime in case calsys == null in
               // order to set calsys and cdate. (6263644)
               if (fieldMask != 0 || calsys == null) {
                 mask |= computeFields(fieldMask,
                 mask & (ZONE_OFFSET_MASK|DST_OFFSET_MASK));
                 assert mask == ALL_FIELDS;
               }
            } else {
               mask = ALL_FIELDS;
               computeFields(mask, 0);
            }
            // After computing all the fields, set the field state to `COMPUTED'.
            setFieldsComputed(mask);
      }

      
}*/
