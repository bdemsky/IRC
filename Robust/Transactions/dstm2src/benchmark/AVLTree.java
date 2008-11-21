/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import dstm2.Thread;
import dstm2.atomic;
import dstm2.factory.Factory;
import java.util.Iterator;

/**
 *
 * @author navid
 */
public class AVLTree extends IntSetBenchmark{
    
    static Factory<AvlNode> factory = Thread.makeFactory(AvlNode.class);
        
    protected AvlNode root;
    
    @atomic public interface AvlNode
    {
        AvlNode getLeft(); 
        void setLeft(AvlNode value);
     
        AvlNode getRight();
        void setRight(AvlNode value);
        
       int getHeight();    
        void setHeight(int value);
    
        int getElement();
        void setElement(int value);
        
    }
        
 

        public boolean insert( int x )
        {
          //  System.out.println(Thread.currentThread() + " null? " +root);
            AVLTree.this.root = insert( x, AVLTree.this.root );
            return true;
        }

       


    /*    public Comparable find( Comparable x )
        {
            return elementAt( find( x, root ) );
        }*/

        public boolean isEmpty( )
        {
            return this.root == null;
        }


        public void printTree( )
        {
            if( isEmpty( ) )
                System.out.println( "Empty tree" );
            else
                printTree( this.root );
        }

        private Comparable elementAt( AvlNode t )
        {
            return t == null ? null : t.getElement();
        }

  
        private AvlNode insert( int x, AvlNode t )
        {
          //  if (root != null)
             //   System.out.println(Thread.currentThread() + " gozo" + root.getElement());
            
            if( t == null ){
              //  if (t==root)
            //    System.out.println(Thread.currentThread() + " root? " +root);
            //    System.out.println(Thread.currentThread() + " ff " +t);
                t = factory.create();
             //   System.out.println(Thread.currentThread() + " gg " +t);
                t.setElement(x);
                t.setLeft(null);
                t.setRight(null);
            }
            
            else if( x < t.getElement()  )
            {
                t.setLeft(insert( x, t.getLeft() ));
                if( height( t.getLeft() ) - height( t.getRight() ) == 2 )
                    if( x < t.getLeft().getElement() )
                        t = leftRoate( t );
                    else
                        t = doubleLeftRotae( t );
            }
            else if( x > t.getElement())
            {
                t.setRight(insert( x, t.getRight() ));
                if( height( t.getRight() ) - height( t.getLeft() ) == 2 )
                    if( x > t.getRight().getElement() )
                        t = rightRotate( t );
                    else
                        t = doubleRightRotate( t );
            }
            else;
            
            t.setHeight(max( height( t.getLeft() ), height( t.getRight() ) ) + 1);
            return t;
        }

 
        public void makeEmpty( )
        {
            this.root = null;
        }
   
   /*     private AvlNode find( Comparable x, AvlNode t )
        {
            while( t != null )
                if( x.compareTo( t.getElement() ) < 0 )
                    t = t.getLeft();
                else if( x.compareTo( t.getElement() ) > 0 )
                    t = t.getRight();
                else
                    return t;    // Match

            return null;   // No match
        }*/

 
        private void printTree( AvlNode t )
        {
            if( t != null )
            {
                printTree( t.getLeft() );
                System.out.println( t.getElement());
                printTree( t.getRight() );
            }
        }

       
        private static int height( AvlNode t )
        {
            return t == null ? -1 : t.getHeight();
        }


        private static int max( int lhs, int rhs )
        {
            return lhs > rhs ? lhs : rhs;
        }

        private static AvlNode leftRoate( AvlNode k2 )
        {
          
            AvlNode k1 = k2.getLeft();
            k2.setLeft(k1.getRight());
            k1.setRight(k2);
            k2.setHeight(max( height( k2.getLeft()), height( k2.getRight() ) ) + 1);
            k1.setHeight(max( height( k1.getLeft() ), k2.getHeight()) + 1);
            return k1;
        }

 
        private static AvlNode rightRotate( AvlNode k1 )
        {
             
            AvlNode k2 = k1.getRight();
            k1.setRight(k2.getLeft());
            k2.setLeft(k1);
            k1.setHeight(max( height(k1.getLeft()), height( k1.getRight())) + 1);
            
            k2.setHeight(max( height(k2.getRight()),k1.getHeight()) + 1);
            
            return k2;
        }

        private static AvlNode doubleLeftRotae( AvlNode k3 )
        {
            k3.setLeft(rightRotate( k3.getLeft() ));
            return leftRoate( k3 );
        }

        private static AvlNode doubleRightRotate( AvlNode k1 )
        {
            k1.setRight(leftRoate( k1.getRight()));
            return rightRotate( k1 );
        }

    
    protected void init() {
     //   this.root = factory.create();

    }

    public Thread createThread(int which) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

 

  

    @Override
    public Iterator<Integer> iterator() {
       throw new UnsupportedOperationException("Not supported yet.");
        
    }

    @Override
    public boolean contains(int v) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean remove(int v) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Thread createThread(int which, char sample, int start) {
        throw new UnsupportedOperationException("Not supported yet.");
    }



}
