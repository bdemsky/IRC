package TransactionalIO.Utilities;

/* =============================================================
 * SmallSQL : a free Java DBMS library for the Java(tm) platform
 * =============================================================
 *
 * (C) Copyright 2004-2007, by Volker Berlin.
 *
 * Project Info:  http://www.smallsql.de/
 *
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, 
 * USA.  
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 *
 * ---------------
 * Utils.java
 * ---------------
 * Author: Volker Berlin
 * 
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.sql.SQLException;


public class Conversions {

	
	
	
	public static int long2int(long value){
		if(value > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		if(value < Integer.MIN_VALUE)
			return Integer.MIN_VALUE;
		return (int)value;
	}
	
	public static long double2long(double value){
		if(value > Long.MAX_VALUE)
			return Long.MAX_VALUE;
		if(value < Long.MIN_VALUE)
			return Long.MIN_VALUE;
		return (long)value;
	}



    public static float bytes2float( byte[] bytes ){
        return Float.intBitsToFloat( bytes2int( bytes ) );
    }

    public static double bytes2double( byte[] bytes ){
        return Double.longBitsToDouble( bytes2long( bytes ) );
    }

    public static long bytes2long( byte[] bytes ){
        long result = 0;
        int length = Math.min( 8, bytes.length);
        for(int i=0; i<length; i++){
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    public static int bytes2int( byte[] bytes ){
        int result = 0;
        int length = Math.min( 4, bytes.length);
        for(int i=0; i<length; i++){
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    static byte[] double2bytes( double value ){
        return long2bytes(Double.doubleToLongBits(value));
    }

    public static byte[] float2bytes( float value ){
        return int2bytes(Float.floatToIntBits(value));
    }

    public static byte[] long2bytes( long value ){
        byte[] result = new byte[8];
        result[0] = (byte)(value >> 56);
        result[1] = (byte)(value >> 48);
        result[2] = (byte)(value >> 40);
        result[3] = (byte)(value >> 32);
        result[4] = (byte)(value >> 24);
        result[5] = (byte)(value >> 16);
        result[6] = (byte)(value >> 8);
        result[7] = (byte)(value);
        return result;
    }
    
    public static int money2int( long value ) {
		if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
		else if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		else return (int) value;
	}

	public static byte[] int2bytes( int value ){
		byte[] result = new byte[4];
		result[0] = (byte)(value >> 24);
		result[1] = (byte)(value >> 16);
		result[2] = (byte)(value >> 8);
		result[3] = (byte)(value);
		return result;
	}



   

    private static int hexDigit2int(char digit){
        if(digit >= '0' && digit <= '9') return digit - '0';
        digit |= 0x20;
        if(digit >= 'a' && digit <= 'f') return digit - 'W'; // -'W'  ==  -'a' + 10
        throw new RuntimeException();
    }

 


    static boolean string2boolean( String val){
        try{
            return Double.parseDouble( val ) != 0;
        }catch(NumberFormatException e){/*ignore it if it not a number*/}
        return "true".equalsIgnoreCase( val ) || "yes".equalsIgnoreCase( val ) || "t".equalsIgnoreCase( val );
    }
	
	
	static long doubleToMoney(double value){
		if(value < 0)
			return (long)(value * 10000 - 0.5);
		return (long)(value * 10000 + 0.5);
	}

    static int indexOf( char value, char[] str, int offset, int length ){
        value |= 0x20;
        for(int end = offset+length;offset < end; offset++){
            if((str[offset] | 0x20) == value) return offset;
        }
        return -1;
    }

    public static int indexOf( int value, int[] list ){
        int offset = 0;
        for(int end = list.length; offset < end; offset++){
            if((list[offset]) == value) return offset;
        }
        return -1;
    }

    public static int indexOf( byte[] value, byte[] list, int offset ){
        int length = value.length;
        loop1:
        for(int end = list.length-length; offset <= end; offset++){
            for(int i=0; i<length; i++ ){
                if(list[offset+i] != value[i]){
                    continue loop1;
                }
            }
            return offset;
        }
        return -1;
    }

    public static int compareBytes( byte[] leftBytes, byte[] rightBytes){
        int length = Math.min( leftBytes.length, rightBytes.length );
        int comp = 0;
        for(int i=0; i<length; i++){
            if(leftBytes[i] != rightBytes[i]){
                comp = leftBytes[i] < rightBytes[i] ? -1 : 1;
                break;
            }
        }
        if(comp == 0 && leftBytes.length != rightBytes.length){
            comp = leftBytes.length < rightBytes.length ? -1 : 1;
        }
        return comp;
    }
	

}