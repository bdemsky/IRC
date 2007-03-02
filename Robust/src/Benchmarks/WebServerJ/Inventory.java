import java.util.*;
import java.net.*;
import java.io.*;

public class Inventory {
    // Transaction variables
    int numitems;
    HashMap map;
    int balance;
    
    // Constructor
    public Inventory(){
	map = new HashMap();
	balance=100000;
    }
    
    public Inventory(int howmany) {
	numitems = howmany;// howmany keeps track of the number of items 
	// in the inventory
	map = new HashMap();
    }
    
    // Add item to a list of inventory
    public synchronized void additem(String name, int quantity, int price){
	ItemInfo newitem = new ItemInfo(quantity, price);
	balance-=quantity*price;
	
	// Get the item from hash
	if (map.containsKey(name) == false) {
	    map.put(name, newitem);
	} else {
	    ItemInfo i = (ItemInfo) map.get(name);
	    i.quantity += quantity;
	    i.price = price;
	    map.put(name, i);
	}
    }	
    
    // Buy item from a given list of inventory	
    public synchronized int buyitem(String name, int quantity){
	if (map.containsKey(name) == false) {
	    //		System.printString("Error - Item does not exist");
	    return -1;
	} else {
	    ItemInfo i = (ItemInfo) map.get(name);
	    if (i.quantity == 0) {
		//			System.printString("Error - Item unavailable");
		return -1;
	    }
	    if ((i.quantity-quantity) < 0 ) {
		//			System.printString("Error - Available qty is less: Cannot Buy\n");
		return -1;
	    } else {
		i.quantity -= quantity;
		map.put(name, i);
		balance+=quantity*i.price;
		return i.price;
	    }
	}
    }

    //Display the inventory list
    public synchronized void inventory(Socket s){
	try {
	    OutputStream sockout=s.getOutputStream();
	Iterator i = map.keySet().iterator();// Gets key from the hashmap= name of item
	Iterator j = map.values().iterator();//Gets the value from hashmap 
	int totalvalue=balance;
	while (i.hasNext() == true) {
	    StringBuffer sb = new StringBuffer("");
	    Object o = i.next();
	    String name = o.toString();
	    ItemInfo oo = (ItemInfo) j.next();
	    sb.append(name);
	    sb.append(" ");
	    Integer q = new Integer(oo.quantity);
	    sb.append(q.toString());
	    sb.append(" ");
	    Integer p = new Integer(oo.price);
	    sb.append(p.toString());
	    sb.append("\n");
	    totalvalue+=oo.quantity*oo.price;
	    sockout.write(sb.toString().getBytes());
	}
	StringBuffer sb=new StringBuffer("");
	sb.append("Total value: ");
	sb.append((new Integer(totalvalue)).toString());
	sb.append("\n");
	sockout.write(sb.toString().getBytes());
	} catch (Exception e) {
	    e.printStackTrace();
	}	
    }
}
