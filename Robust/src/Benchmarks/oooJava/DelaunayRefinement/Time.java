import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

public final class Time {
	
    public Time() {
    }
	
    public static long getNewTimeId() {
        return getNewTimeId(5);
    }
	
    public static long getNewTimeId(int collections) {
        for(int i = 0; i < collections; i++)
            System.gc();
		
        counter++;
        times.put(Long.valueOf(counter), new Pair(Long.valueOf(milliTime()), Long.valueOf(milliGcTime())));
        return counter;
    }
	
    public static long elapsedTime(long id) {
        Pair startTimes = (Pair)times.get(Long.valueOf(id));
        return elapsedTime(((Long)startTimes.getFirst()).longValue(), ((Long)startTimes.getSecond()).longValue());
    }
	
    private static long elapsedTime(long startTime, long startGcTime) {
        return (milliTime() - startTime - milliGcTime()) + startGcTime;
    }
	
    public static long elapsedTime(long id, boolean includeGc) {
        Pair elapsedGcTimes = elapsedAndGcTime(id);
        long elapsedTime = ((Long)elapsedGcTimes.getFirst()).longValue();
        long gcTime = ((Long)elapsedGcTimes.getSecond()).longValue();
        return includeGc ? elapsedTime : elapsedTime - gcTime;
    }
	
    public static Pair elapsedAndGcTime(long id) {
        long milliTime = milliTime();
        long milliGcTime = milliGcTime();
        Pair startTimes = (Pair)times.get(Long.valueOf(id));
        long startTime = ((Long)startTimes.getFirst()).longValue();
        long startGcTime = ((Long)startTimes.getSecond()).longValue();
        return new Pair(Long.valueOf(milliTime - startTime), Long.valueOf(milliGcTime - startGcTime));
    }
	
    private static long milliTime() {
        return System.nanoTime() / 0xf4240L;
    }
	
    public static long milliGcTime() {
        long result = 0L;
        for(Iterator iterator = garbageCollectorMXBeans.iterator(); iterator.hasNext();) {
            GarbageCollectorMXBean garbageCollectorMXBean = (GarbageCollectorMXBean)iterator.next();
            result += Math.max(0L, garbageCollectorMXBean.getCollectionTime());
        }
		
        return result;
    }
	
    private static final List garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private static long counter = 0x0L;
    private static Map times = new HashMap();
	
}
