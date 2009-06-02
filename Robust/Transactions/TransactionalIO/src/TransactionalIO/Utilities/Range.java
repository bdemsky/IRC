/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.Utilities;


/**
 *
 * @author navid
 */
public class Range implements Comparable {

    public long start;
    public long end;

    public Range() {
    }

    public Range(long start, long end) {
        this.start = start;
        this.end = end;
    }

  
  
    public Range intersection(Range secondrange) {
        if ((secondrange.start <= this.start) && (this.start <= secondrange.end)) {
            return new Range(this.start, Math.min(this.end, secondrange.end));
        } else if ((secondrange.start <= this.end) && (this.end <= secondrange.end)) {
            return new Range(Math.max(this.start, secondrange.start), this.end);
        } else if ((this.start <= secondrange.start) && (secondrange.end <= this.end)) {
            return new Range(secondrange.start, secondrange.end);
        } else {
            return null;
        }
    }

    public boolean hasIntersection(Range secondrange) {
        if ((secondrange.start <= this.start) && (this.start <= secondrange.end)) {
            return true;
        } else if ((secondrange.start <= this.end) && (this.end <= secondrange.end)) {
            return true;
        } else if ((this.start <= secondrange.start) && (secondrange.end <= this.end)) {
            return true;
        } else {
            return false;
        }
    }
    

    public boolean includes(Range secondrange) {
        if (this.start <= secondrange.start && secondrange.end <= this.end) {
            return true;
        } else {
            return false;
        }
    }
    
    public boolean includes(long secondrangestart, long secondrangeend) {
        if (this.start <= secondrangestart && secondrangeend <= this.end) {
            return true;
        } else {
            return false;
        }
    }

    public Range[] minus(Range[] intersectedranges, int size) {
        Range[] tmp = new Range[size + 1];
        
        int counter = 0;
        if (this.start < intersectedranges[0].start) {
            tmp[counter] = new Range(this.start, intersectedranges[0].start);
            counter++;
        }
        for (int i = 1; i < size; i++) {
            tmp[counter] = new Range(intersectedranges[i - 1].end, intersectedranges[i].start);
            counter++;
        }
        if (this.end > intersectedranges[size - 1].end) {
            tmp[counter] = new Range(intersectedranges[size - 1].end, this.end);
            counter++;
        }
        Range[] result = new Range[counter];
        for (int i = 0; i < counter; i++) {
            result[i] = tmp[i];
        }
        return result;
    }

    public int compareTo(Object arg0) {

        Range tmp = (Range) arg0;
        if (this.start < tmp.start) {
            return -1;
        } else if (this.start == tmp.start) {
            return 0;
        } else {
            return 1;
        }
    }
}
