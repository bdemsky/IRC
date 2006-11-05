public class Inventory {
	// Inventory flags
	flag TransInitialize;

	// Transaction variables
	int numitems;
	HashMap map;
	
	// Constructor
	public Inventory(){
	}

	public Inventory(int howmany) {
		numitems = howmany;// howmany keeps track of the number of items 
				   // in the inventory
		map = new HashMap(numitems);
	}

	// Add item to a list of inventory
	public int additem(String name, int quantity, int price){
		ItemInfo newitem = new ItemInfo(quantity, price);
		
		// Get the item from hash
		if (map.containsKey(name) == false) {
			if (map.size() > numitems) {
				System.printString("Error - Items overflow");
				return -1;
			}
			map.put(name, newitem);
		} else {
			ItemInfo i = map.get(name);
			i.quantity += quantity;
			i.price = price;
			map.put(name, i);
		}
		
		return 0;
	}	

	// Buy item from a given list of inventory	
	public int buyitem(String name, int quantity, int price){
		if (map.containsKey(name) == false) {
			System.printString(name);
			System.printString("Error - Item does not exist");
			return -1;
		} else {
			ItemInfo i = map.get(name);
			if (i.quantity == 0) {
				System.printString("Error - Item unavailable");
				return -1;
			}
			i.quantity -= quantity;
			map.put(name, i);
			return 0;
		}
	}

	//Display the inventory list
	public String inventory(){
		HashMapIterator i = new HashMapIterator(map, 0);
		HashMapIterator j = new HashMapIterator(map, 1);
		StringBuffer sb = new StringBuffer("");
		while (i.hasNext() == true) {
			Object o = i.next();
			String name = o.toString();
			System.printString(name);
			ItemInfo oo = j.next();
			sb.append(name);
			sb.append(" ");
			Integer q = new Integer(oo.quantity);
			sb.append(q.toString());
			sb.append(" ");
			Integer p = new Integer(oo.price);
			sb.append(p.toString());
			sb.append("\n");
		}
		String item = new String(sb);	
		return item;	
	}	
}
