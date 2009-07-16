/* =============================================================================
 *
 * detector.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */


public class Detector {

    Dictionary dictionaryPtr;
    Vector_t preprocessorVectorPtr;

    public Detector() {}

/* =============================================================================
 * detector_alloc
 * =============================================================================
 detector_t* detector_alloc ();
 */
    public static Detector alloc() {
        Detector detectorPtr = new Detector();

        if(detectorPtr != null) {
            detectorPtr.dictionaryPtr = new Dictionary();
            if(detectorPtr.dictionaryPtr == null) {
                System.out.println("Assertion in Detector.alloc");
                System.exit(1);
            }

            detectorPtr.preprocessorVectorPtr = Vector_t.vector_alloc(1);
            if(detectorPtr.preprocessorVectorPtr == null) {
                 System.out.println("Assertion in Detector.alloc");               
                 System.exit(1);
            }
        }

            return detectorPtr;
    }

/* =============================================================================
 * Pdetector_alloc
 * =============================================================================
 detector_t* Pdetector_alloc ();
 */


/* =============================================================================
 * detector_free
 * =============================================================================
 void detector_free (detector_t* detectorPtr);
 */


/* =============================================================================
 * Pdetector_free
 * =============================================================================
 void Pdetector_free (detector_t* detectorPtr);
 */


/* =============================================================================
 * detector_addPreprocessor
 * =============================================================================
 void detector_addPreprocessor (detector_t* detectorPtr, preprocessor_t p);
 */
    public void addPreprocessor(int p) {
        boolean status = preprocessorVectorPtr.vector_pushBack(new Integer(p));
        if(!status) {
            System.out.println("Assertion in Detector.addPreprocessor");
            System.exit(1);
        }
    }


/* =============================================================================
 * detector_process
 * =============================================================================
 * error_t detector_process (detector_t* detectorPtr, char* str);
 */
    public int process(byte[] str) 
    {
        /*
         * Apply preprocessors
         */

        int p;
        int numPreprocessor = preprocessorVectorPtr.vector_getSize();
        int i;
        for(p = 0; p < numPreprocessor; p++) {
            Integer preprocessor = (Integer)preprocessorVectorPtr.vector_at(p);
            if(preprocessor.intValue() == 1) {
                System.out.println("NOOOOOOOOOOOOO");
            }
            else if(preprocessor.intValue() == 2) {
                for(i=0;i<str.length;i++)
                {
                    if(str[i] >'A' && str[i] < 'Z')
                    {
                        str[i] = (byte)(str[i] + (byte)32);
                    }
                }


            }
            else {
                System.out.println("NOOOOOOOOOOOOO");
            }
        }

        /*
         * Check against signatures of known attacks
         */

        ERROR err = new ERROR();
//        System.out.print("str = \"" + str+ "\"");
        String signature = dictionaryPtr.match(new String(str));
//      System.out.println("\tSign = \"" + signature+ "\"");
        if(signature != null) {
            return err.SIGNATURE;
        }

        return err.NONE;
    }
}

/* =============================================================================
 *
 * End of detector.java
 *
 * =============================================================================
 */
