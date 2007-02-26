public class generate {

    public static void main(String x[]) {
	int j=1;
	int cost=1;
	System.out.println("#!/bin/bash");
	for(int i=0;i<2200;i++) {
	    int price=cost*50;
	    int jbuy=j-1;
	    System.out.println("wget http://$1:9000/trans_add_f"+i+"_"+j+"_"+price);
	    System.out.println("wget http://$1:9000/trans_buy_f"+i+"_"+jbuy);
	    if (cost>3) cost=1; else cost++;
	    if (j>4) j=1; else j++;
	}
	System.out.println("wget http://$1:9000/trans_inventory");
    }


}
