public class QuickSort {
	public QuickSort() {
	}

	public void quick_srt(int array[],int low, int n){
		int lo = low;
		int hi = n;
		if (lo >= n) {
			return;
		}
		int mid = array[(lo + hi) / 2];
		while (lo < hi) {
			while (lo<hi && array[lo] < mid) {
				lo++;
			}
			while (lo<hi && array[hi] > mid) {
				hi--;
			}
			if (lo < hi) {
				int T = array[lo];
				array[lo] = array[hi];
				array[hi] = T;
			}
		}
		if (hi < lo) {
			int T = hi;
			hi = lo;
			lo = T;
		}
		quick_srt(array, low, lo);
		if(lo == low) {
			low = lo+1;
		}
		else {
			low = lo;
		}
		quick_srt(array, low, n);
	}

	public static void main(String[] args) {
		int i;
		int j;
		QArray myArray[] = new QArray[2];
		for(i = 0; i<2; i++) {
			myArray[i] = new QArray();
		}
		QuickSort qsort = new QuickSort();
		System.printString("Values Before sorting\n");
		for(i = 0; i<2; i++){ 
			for(j = 0; j<10; j++){ 
				System.printInt(myArray[i].mya[j]);
				System.printString("\t");
			}
			System.printString("\n");
		}
		for(i = 0; i<2; i++){ 
			qsort.quick_srt(myArray[i].mya, 0, 9);
		}
		System.printString("Values After sorting\n");
		for(i = 0; i<2; i++){ 
			for(j = 0; j<10; j++){ 
				System.printInt(myArray[i].mya[j]);
				System.printString("\t");
			}
			System.printString("\n");
		}
		System.printString("\n");
	}
}

public class QArray {
	public int mya[];
	public QArray() {
		mya = new int[10];
		mya[0] = 65;
		mya[1] = 26;
		mya[2] = 5;
		mya[3] = 49;
		for(int i = 4; i<9; i++)
			mya[i] = 10*i;
		mya[9] = 72;
	}
}
