
package dstm2.benchmark;

import dstm2.AtomicArray;
import dstm2.atomic;
import dstm2.factory.Factory;
import dstm2.Thread;

/* atomic interface 
 * the factory will create this interface's instance
 */
/* this class will use atomic double array */
public class Matrix
{
	static Factory<ArrayHolder> ArrayHolderfactory = Thread.makeFactory(ArrayHolder.class);
//	static Factory<Double> INodeFactory = Thread.makeFactory(double.class);
	
	public int L, M, N;
	public AtomicArray<ArrayHolder> a;				// double[][] a;
	public AtomicArray<ArrayHolder> b;				//      ''	  b;
	public AtomicArray<ArrayHolder> c; 			//		''	  c;
	public AtomicArray<ArrayHolder> btranspose;	// 		''	  btranspose;
	
	// create all matrices using factory.create()
	public Matrix(int L, int M, int N) {
		this.L = L;
		this.M = M;
		this.N = N;
//		a = new INode[L][M];
//		b = new INode[M][N]; 
//		c = new INode[L][N]; 
//		btranspose = new INode[N][M];
		
		ArrayHolder arrayHolder;	// temporary variable to initiate 2dim array
		AtomicArray<Double> doubleArray;
		
		System.out.println("L = " + L);
		System.out.println("M = " + M);
		System.out.println("N = " + N);
		
		
		// set up matrix a[L][M]
		a = new AtomicArray<ArrayHolder>(ArrayHolder.class,L);	// create row of atomic array 
		
		for(int i=0;i<L;i++)
		{
			arrayHolder = ArrayHolderfactory.create();	// will be the row
			
			doubleArray = new AtomicArray<Double>(Double.class,M);	// create column
			 
			arrayHolder.setAtomicArray(doubleArray);	// set the pointer to double array 
			
			a.set(i,arrayHolder);
			
		}
		
		
		// set up matrix b[M][N]
		b = new AtomicArray<ArrayHolder>(ArrayHolder.class,M);	// create row of atomic array 
		
		for(int i=0;i<M;i++)
		{
			arrayHolder = ArrayHolderfactory.create();	// will be the row 
			
			doubleArray = new AtomicArray<Double>(Double.class,N);	// create column
			 
			arrayHolder.setAtomicArray(doubleArray);	// set the pointer to double array
			
			b.set(i,arrayHolder);
			
		}
		
		// set up matrix c[L][N]
		c = new AtomicArray<ArrayHolder>(ArrayHolder.class,L);	// create row of atomic array 
		
		for(int i=0;i<L;i++)
		{
			arrayHolder = ArrayHolderfactory.create();	// will be the row 
			
			doubleArray = new AtomicArray<Double>(Double.class,N);	// create column
			 
			arrayHolder.setAtomicArray(doubleArray);	// set the pointer to double array
			
			c.set(i,arrayHolder);
			
		}
		
		// set up matrix btranspose[N][M]
		btranspose = new AtomicArray<ArrayHolder>(ArrayHolder.class,N);	// create row of atomic array 
		
		for(int i=0;i<L;i++)
		{
			arrayHolder = ArrayHolderfactory.create();	// will be the row 
			
			doubleArray = new AtomicArray<Double>(Double.class,M);	// create column
			
			if(null == doubleArray)
			{
				System.out.println("no heap space???");
		
			}
			
			if(null == arrayHolder)
			{
				System.out.println("no heap space???");
			}
			 
			arrayHolder.setAtomicArray(doubleArray);	// set the pointer to double array
			
			btranspose.set(i,arrayHolder);
			
		}
			
	}

	// initialize matrices
	public void setValues() {
		
		ArrayHolder arrayHolder;	// temporary variable to initiate 2dim array
		
		
		// initiate matrix A[L][M]
		for(int i = 0; i < L; i++) {	// row
			arrayHolder = a.get(i);
            AtomicArray<Double> ai = arrayHolder.getAtomicArray();	// get the column of array
			
			for(int j = 0; j < M; j++) {
				ai.set(j,Double.valueOf(j+1));	// a[i][j] = j+1
			}
		}

		// initiate matrix B[M][N]
		for(int i = 0; i < M; i++) {	// row

			arrayHolder = b.get(i);
			AtomicArray<Double> bi = arrayHolder.getAtomicArray();	// get the column of array
			for(int j = 0; j < N; j++) {
				bi.set(j,Double.valueOf(j+1));	// b[i][j] = j+1
			}
		}
		
		// initiate matrix C[L][N]
		for(int i = 0; i < L; i++) {

			arrayHolder = c.get(i);
			AtomicArray<Double> ci = arrayHolder.getAtomicArray();	// get the column of array
		
			for(int j = 0; j < N; j++) {
				ci.set(j,Double.valueOf(0));	// c[i][j] = 0
			}
//			System.out.print(ci.get(0) + " ");
		}

		// initiate matrix btranspose[N][M]
		for(int i = 0; i < N; i++) {

			arrayHolder = btranspose.get(i);
			AtomicArray<Double> btransposei = arrayHolder.getAtomicArray();	// get the column of array

			for(int j = 0; j < M; j++) {

				btransposei.set(j,Double.valueOf(0));	// btranspose[i][j] = 0
			}
//			System.out.println("Value of i = " + i);
		}
	}

	// initialize btranspose
	// it doesn't have parameter because there is only one transpose
	public void transpose() {
		
		ArrayHolder b_arrayHolder;
		ArrayHolder btrans_arrayHolder;
		
		for(int row = 0; row < M; row++) {
			
			b_arrayHolder = b.get(row);	// b[row][]
			AtomicArray<Double> brow= b_arrayHolder.getAtomicArray();
			
            for(int col = 0; col < N; col++) {
            	btrans_arrayHolder = btranspose.get(col);	// btranspose[col][]
            	AtomicArray<Double> btranspose_col = btrans_arrayHolder.getAtomicArray();
            	
				btranspose_col.set(row, brow.get(col));	//  btranspose[col][row] = b[row][col]
				
			}
		}
	}
	
  
	  @atomic public interface ArrayHolder {
		AtomicArray<Double> getAtomicArray();
		void setAtomicArray(AtomicArray<Double> d);
	  }
	
}
