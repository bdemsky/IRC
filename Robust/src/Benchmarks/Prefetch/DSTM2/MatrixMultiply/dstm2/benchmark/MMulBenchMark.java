
package dstm2.benchmark;

import dstm2.AtomicArray;
import dstm2.Thread;
import dstm2.benchmark.Benchmark;
import dstm2.benchmark.Matrix.ArrayHolder;


public class MMulBenchMark implements Benchmark {

	Matrix matrix;
	int SIZE;
	int numThreads;

	int base;
	int increments;
	
	int setV;
	int getV;
	
	public void init(int SIZE,int numThreads) {
		matrix = new Matrix(SIZE,SIZE,SIZE);
		
		this.SIZE = SIZE;
		this.numThreads = numThreads;
		increments = SIZE / numThreads;
		base =0;
		
		matrix.setValues();
		matrix.transpose();
		
		setV =0;
		getV =0;
	}
	
	public Thread createThread(int order)
	{
		MMul mmul;
		try {
			if(order < numThreads-1) {
				mmul = new MMul(matrix,base, base+increments, 0, SIZE);
				base += increments;
			}
			else
				mmul = new MMul(matrix,base,SIZE,0,SIZE);
			return mmul;
		}catch(Exception e)
		{
			e.printStackTrace(System.out);
			return null;
		}
	}

	private class MMul extends Thread {
		
		Matrix mmul;
		
		public int x0;	// # of start row
		public int x1;	// # of end row
		public int y0;	// # of start col
		public int y1;	// # of end col
		
		MMul(Matrix mul,int x0,int x1,int y0,int y1)
		{
			this.mmul = mul;
			this.x0 = x0;
			this.x1 = x1;
			this.y0 = y0;
			this.y1 = y1;
			
		}
				
		public void run() {
			
			AtomicArray<ArrayHolder> la=mmul.a;
			AtomicArray<ArrayHolder> lc=mmul.c;
			AtomicArray<ArrayHolder> lb=mmul.btranspose;

			int M=mmul.M;
			
			ArrayHolder rowHolder;	// temporary variable to get column array
										
			//Use btranspose for cache performance
			for(int i = x0; i< x1; i++){
				
				rowHolder = la.get(i);
				AtomicArray<Double> ai = rowHolder.getAtomicArray();	// a[i][]
				
				rowHolder = lc.get(i);
				AtomicArray<Double> ci = rowHolder.getAtomicArray();	// c[i][]
				
				for (int j = y0; j < y1; j++) {

					double innerProduct=0;
					
					rowHolder = lb.get(j);	// b[j][]
					AtomicArray<Double> bj = rowHolder.getAtomicArray();	// b[j][]
										
					for(int k = 0; k < M; k++) {
						innerProduct += ai.get(k) * bj.get(k);	// innerProduct = a[i][j] * b[j][k];
						getV += 2;
					}
					
					ci.set(j, Double.valueOf(innerProduct));	// c[i][j] = innerProduct; 
					setV++;
				}
			}
		}
	
	}

	
	// print out the matrices to be multiplied
	public void sanityCheck() 
	{
		int chk=0;
		
		for(int i=0;i<SIZE;i++) {
			chk += (i+1)^2;
		}
		ArrayHolder rowHolder = matrix.c.get(0);
		AtomicArray<Double> ci = rowHolder.getAtomicArray();
		System.out.println("C[0][0] = " + ci.get(0));
		System.out.println("Chk = " + chk);
		
	}
	
	public void report()
	{
		
		
//		System.out.println("SetValue Calls : " + setV);
//		System.out.println("GetValue Calls : " + getV);
	}

}

