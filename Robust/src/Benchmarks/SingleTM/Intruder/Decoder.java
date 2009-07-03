/* =============================================================================
 *
 * decoder.java
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


#define MAP_T                       RBTree
#define MAP_ALLOC(hash, cmp)        RBTree.alloc(cmp)
#define MAP_INSERT(map, key, data)  map.insert(key, data)
#define MAP_CONTAINS(map, key)      map.contains(key);
#define MAP_FIND(map,key)           map.get(key);
#define MAP_INSERT(map,key,data)    map.insert(key,data);
#define MAP_REMOVE(map,key)         map.deleteNode(key);

public class Decoder {

    MAP_T fragmentedMapPtr;     /* contains list of packet_t* */
    Queue_t decodedQueuePtr;    /* contains decoded_t*  */

    public Decoder() {}

/* =============================================================================
 * decoder_alloc
 * =============================================================================
 decoder_t* decoder_alloc ();
  */
    public static Decoder alloc() {
        Decoder decoderPtr;

        decoderPtr = new Decoder();
        if(decoderPtr) {
            decoderPtr.fragementedMapPtr = MAP_ALLOC(0,0);

            if(decoderPtr.fragementedMapPtr == null)
            {
                System.out.println("Assert in Decoder.alloc");
                System.exit(1);
            }
            
            decoderPtr.decodedQueuePtr = Queue_t.alloc(1024);
            if(decoderPtr.decodedQueuePtr == null)
            {
                System.out.println("Assert in Decoder.alloc");
                System.exit(1);
            }

            return decoderPtr;            
    }


/* =============================================================================
 * decoder_free
 * =============================================================================
 void decoder_free (decoder_t* decoderPtr);
 */
    public free(Decoder decoderPtr) {
        fragmentedMapPtr = null;
        decodedQueuePtr = null;
        decoderPtr = null;
    }


/* =============================================================================
 * decoder_process
 * =============================================================================
 error_t decoder_process (decoder_t* decoderPtr, char* bytes, long numByte);
 */
    public int process(Packet bytes,int numByte)
    {
        boolean status;

        /*
         * Basic error checing
         */

        if (numByte < PACKET_HEADER_LENGTH) {
            return ERROR.SHORT;
        }

        /* need to look into it later */
        Packet packetPtr = (Packet)bytes;
        int flowId = packetPtr.flowId;
        int fragmentId = packetPtr.fragmentId;
        int numFragment = packetPtr.numFragment;
        int length = packetPtr.length;

        if (flowId < 0) {
            return ERROR.FLOWID;
        }

        if ((fragmentedId < 0) || (fragmentId >= numFragment)) {
            return ERROR.FRAGMENTID;
        }

        if (length < 0) {
            return ERROR.LENGTH;
        }
    
        /*
         * Add to fragmented map for reassembling
         */

        if (numFragment > 1) {

            List_t fragmentListPtr = (List_t)MAP_FIND(fragmentedMapPtr,flowId);

            if (fragmentListPtr == null) {

                fragmentListPtr = List_t.alloc(1);      // packet_compareFragmentId
                if(fragmentListPtr == null) {
                    System.out.println("Assertion in process");
                    System.exit(1);
                }

                status = MAP_INSERT(fragmentedMapPtr,flowId,fragmentListPtr);
                if(status == null) {
                    System.out.println("Assertion in process");
                    System.exit(1);                                
                }
            } else {

                List_Iter it;
                it.reset(fragmentListPtr);
                
                if(it.hasNext(fragmentListPtr) == null) {
                    System.out.println("Assertion in process");
                    System.exit(1);
                }

                Packet firstFragmentPtr = (Packet)it.next(fragmentListPtr);
                int expectedNumFragment = firstFragmentPtr.numFragment;

                if (numFragment != expectedNumFragment) {
                    status = MAP_REMOVE(fragmentedMapPtr,flowId);
                    if(status == null) {
                        System.out.println("Assertion in process");
                        System.exit(1);
                    }

                    status = fragmentListPtr.insert(packetPtr);
                    if(status == null) {
                        System.out.println("Assertion in process");
                        System.exit(1);
                    }

                    /*
                     * If we have all thefragments we can reassemble them
                     */

                    if(fragmentListPtr.getSize() ==  numFragment) {

                        int numByte = 0;
                        int i = 0;
                        it.reset(fragmentListPtr);

                        while (it.hasNext(fragmentListPtr)) {
                            Packet fragmentPtr = (Packet)it.next(fragmentListPtr);

                            if(fragmentPtr.fragmentId != i) {
                                status = MAP_REMOVE(fragmentedMapPtr,(flowId);
                                
                                if(status == null) {
                                    System.out.println("Assertion in process");
                                    System.exit(1);
                                }
                                    return ERROR.INCOMPLETE; /* should be sequential */
                            }
                                numByte += fragmentPtr.length;
                                i++;
                        }

                        char[] data = new char[numByte+1];
                        if(data == null) {
                             System.out.println("Assertion in process");
                             System.exit(1);
                        }

                        data[numByte] = '\0';
                        int dst = 0;            // index of data
                        int i;
                        it.reset(fragmentListPtr);
                        while(it.hasNext(fragmentLIstPtr)) {
                            Packet fragmentPtr = (Packet)it.next(fragmentListPtr);

                            for(i=0;i<fragmentPtr.length;i++)
                                data[dst++] = fragmentPtr.data[i];
                        }

                        if(dst != data + numByte) {
                            System.out.println("Assertion in process");                           
                            System.exit(1);
                        }

                        Decoded decodedPtr = new Decoded();
                        if(decodedPtr == null) {
                             System.out.println("Assertion in process");                    
                             System.exit(1);
                        }

                        decodedPtr.flowId = flowId;
                        decodedPtr.data = String.copyValueOf(data);

                        status = decodededQueuePtr.queue_push(decodedPtr);

                        if(status == null) {
                            System.out.println("Assertion in process");                           
                            System.exit(1);
                        }

                        status = MAP_REMOVE(fragmentedMapPtr,flowId);

                        if(status == null) {
                            System.out.println("Assertion in process");                           
                            System.exit(1);
                        }
                    }
                }
            } else {

                /*
                 * This is the only fragment, so it is ready
                 */

                if (fragmentId != 0) {
                    return ERROR.FRAGMENTID;
                }

                String data = new String();
                if(data == null) {
                    System.out.println("Assertion in process");                           
                    System.exit(1);
                }

                data.concat(packetPtr.data);
                
                Decoded decodedPtr = new Decoded();
                if(decodedPtr == null) {
                    System.out.println("Assertion in process");                           
                    System.exit(1);
                }

                decodedPtr.flowId = flowId;
                decodedPtr.data = data;

                status = decodedQueuePtr.queue_push(decodedPtr);
                
                if(status == null) {
                    System.out.println("Assertion in process");                           
                    System.exit(1);
                }

                return ERROR.NONE;

            }




/* =============================================================================
 * TMdecoder_process
 * =============================================================================
 error_t TMdecoder_process (TM_ARGDECL  decoder_t* decoderPtr, char* bytes, long numByte);
 */


/* =============================================================================
 * decoder_getComplete
 * -- If none, returns NULL
 * =============================================================================
 char* decoder_getComplete (decoder_t* decoderPtr, long* decodedFlowIdPtr); */
    public String getComplete(int[] decodedFlowId) {
        String data;
        Decoded decodedPtr = (Decoded)decodedQueuePtr.queue_pop();

        if(decodedPtr) {
            decodedFlowId[0] = decodedPtr.flowId;
            data = decodedPtr.data;
            decodedPtr = null;
        } else {
            decodedFlowId = -1;
            data= null;
        }

        return data;
    }



/* =============================================================================
 * TMdecoder_getComplete
 * -- If none, returns NULL
 * =============================================================================
 char* TMdecoder_getComplete (TM_ARGDECL  decoder_t* decoderPtr, long* decodedFlowIdPtr);
 */
}

/* =============================================================================
 *
 * End of decoder.java
 *
 * =============================================================================
 */