public class Inventory {
	// Inventory flags
	flag TransInitialize;

	// Transaction variables
	int numitems;
	int current;//keeps track of current position
	int totalvalue;
	
	// Item properties
	String item_name[];
	int item_quantity[];
	int item_price[];

	// Constructor
	public Inventory(){
		current = 0;
	}

	public Inventory(int howmany) {
		numitems = howmany;// howmany keeps track of the number of items 
				   // in the inventory
		current = 0;
		item_name = new String[howmany];
		item_quantity = new int [howmany];
		item_price = new int [howmany];
		for (int i = 0; i < howmany; i++) {
			item_name[i] = "";
			item_quantity[i] = 0;
			item_price[i] = 0;
 		}
	}

	// Add item to a list of inventory
	public int additem(String name, int quantity, int price){
		//check current value
		if(current>=numitems){
			System.printString("Cannot add any further items");
			return -1;
		}
		// Search thru existing items
		for(int i=0; i<numitems;i++){
			if(item_name[i]== name){
				item_quantity[i]+= quantity;
				return 0;
			}
		}
		// Add new item if not found
		item_name[current]= name;
		item_quantity[current]= quantity;
		item_price[current]= price;
		current++;
		return 0;
	}	

	// Buy item from a given list of inventory	
	public int buyitem(String name, int quantity, int price){
		//Search through existing items	
		for(int i=0; i<numitems;i++){
			if(item_name[i]== name){
				item_quantity[i]-= quantity;
				if (item_quantity[i]<=0){// if the quantity falls 
							// to zero 
					current--;
				}
				totalvalue = quantity*price;
				return 0;
			}
		}
		System.printString("Cannot find the item in the inventory");
		return -1;
			
	}

	//Display the inventory list
	public int inventory(){
		Integer tmp;
		for(int i=0; i<current; i++){
			System.printString(" The items are ");
			System.printString(item_name[i]);	
		//	System.printString(" The sale is ");
			System.printString(" The quantity of item is ");
			tmp = new Integer(item_quantity[i]);
			System.printString(tmp.toString());
			System.printString(" The price of item is ");
			tmp = new Integer(item_price[i]);
			System.printString(tmp.toString());
		}
		return 0;
	}	
}
