public class Sequencer {

      public ByteArray sequence;

      public Segments segmentsPtr;

      /* For removing duplicate segments */
      Hashtable uniqueSegmentsPtr;

      /* For matching segments */
      endInfoEntry endInfoEntries[];
      Table startHashToConstructEntryTables[];

      /* For constructing sequence */
      constructEntry constructEntries[];
      Table hashToConstructEntryTable;

      /* For deallocation */
      long segmentLength;


  /* =============================================================================
   * sequencer_alloc
   * -- Returns NULL on failure
   * =============================================================================
   */
  Sequencer (long myGeneLength, long mySegmentLength, Segments mySegmentsPtr) { 

    long maxNumUniqueSegment = myGeneLength - mySegmentLength + 1;
    int i;
    
    uniqueSegmentsPtr = new Hashtable((int)myGeneLength, -1, -1);

    /* For finding a matching entry */
    endInfoEntries = new endInfoEntry[maxNumUniqueSegment];
    for (i = 0; i < maxNumUniqueSegment; i++) {
      endInfoEntries[i] = new endInfoEntry(true, 1);
    }

    startHashToConstructEntryTables = new Table[mySegmentLength];
    for (i = 1; i < mySegmentLength; i++) { /* 0 is dummy entry */
        startHashToConstructEntryTables[i] = new Table(myGeneLength);
    }
    segmentLength = mySegmentLength;

    /* For constructing sequence */
    constructEntries = new constructEntry[maxNumUniqueSegment];
    
    for (i= 0; i < maxNumUniqueSegment; i++) {
        constructEntries[i] = new constructEntry(null, true, 0, null, null, null, 0, segmentLength);
    }
    hashToConstructEntryTable = new Table(myGeneLength);

    segmentsPtr = mySegmentsPtr;  
   }


  /* =============================================================================
   * sequencer_run
   * =============================================================================
   */

  public static void run (long threadNum, long numOfThreads, Random randomPtr, Sequencer sequencerPtr) {

    //TM_THREAD_ENTER();

//    long threadId = thread_getId();

    long threadId = threadNum;
//    System.out.println("threadNum: " + threadId);

    Segments segmentsPtr = sequencerPtr.segmentsPtr;

    //Sequencer sequencerPtr = (sequencer_t*)argPtr;

    Hashtable         uniqueSegmentsPtr = sequencerPtr.uniqueSegmentsPtr;
    endInfoEntry    endInfoEntries[] = sequencerPtr.endInfoEntries;
    Table         startHashToConstructEntryTables[] = sequencerPtr.startHashToConstructEntryTables;
    constructEntry  constructEntries[] = sequencerPtr.constructEntries;
    Table         hashToConstructEntryTable = sequencerPtr.hashToConstructEntryTable;

    Vector      segmentsContentsPtr = segmentsPtr.contentsPtr;
    long        numSegment          = segmentsContentsPtr.size();
    long        segmentLength       = segmentsPtr.length;

    long i;
    long j;
    long i_start;
    long i_stop;
    long numUniqueSegment;
    long substringLength;
    long entryIndex;
    
    int CHUNK_STEP1 = 12;

    /*
     * Step 1: Remove duplicate segments
     */
//#if defined(HTM) || defined(STM)
    long numThread = numOfThreads;
    {
        /* Choose disjoint segments [i_start,i_stop) for each thread */
        long partitionSize = (numSegment + numThread/2) / numThread; /* with rounding */
        i_start = threadId * partitionSize;
        if (threadId == (numThread - 1)) {
            i_stop = numSegment;
        } else {
            i_stop = i_start + partitionSize;
        }
    }
//#else /* !(HTM || STM) */
//    i_start = 0;
//    i_stop = numSegment;
//#endif /* !(HTM || STM) */
    for (i = i_start; i < i_stop; i+=CHUNK_STEP1) {
//        TM_BEGIN();
        atomic {
            long ii;
            long ii_stop = Math.imin((int)i_stop, (int)(i+CHUNK_STEP1));
            for (ii = i; ii < ii_stop; ii++) {
                ByteArray segment = (ByteArray)segmentsContentsPtr.elementAt((int)ii);
//                TMHASHTABLE_INSERT(uniqueSegmentsPtr, segment, segment);
//                System.out.print("Placing: " + segment + " into uniqueSegmentsPtr...");
                if(uniqueSegmentsPtr.TMhashtable_insert(segment, segment)) {
//                  System.out.println("success!");
                } else {
//                  System.out.println("fail, double entry.");
                }
            } /* ii */
        }
//        TM_END();
    }

//    thread_barrier_wait();
    Barrier.enterBarrier();
    
    
    System.out.println("Past removing duplicate segments");
/*    System.out.println("Uniq segs: ");
    
    int jer = 0;
    for(jer = 0; jer < uniqueSegmentsPtr.numBucket; jer++) {
      List jerPtr = uniqueSegmentsPtr.buckets[jer];
      ListNode jerit = jerPtr.head;
      while(jerit.nextPtr != null) {
        jerit = jerit.nextPtr;
        ByteArray jerba = jerit.dataPtr.firstPtr;
        String jerstr = new String(jerba.array);
        System.out.println(jerstr);
      }
    }
    
    System.out.println("End of uniqsegs");
*/    /*
     * Step 2a: Iterate over unique segments and compute hashes.
     *
     * For the gene "atcg", the hashes for the end would be:
     *
     *     "t", "tc", and "tcg"
     *
     * And for the gene "tcgg", the hashes for the start would be:
     *
     *    "t", "tc", and "tcg"
     *
     * The names are "end" and "start" because if a matching pair is found,
     * they are the substring of the end part of the pair and the start
     * part of the pair respectively. In the above example, "tcg" is the
     * matching substring so:
     *
     *     (end)    (start)
     *     a[tcg] + [tcg]g  = a[tcg]g    (overlap = "tcg")
     */

    /* uniqueSegmentsPtr is constant now */
    numUniqueSegment = uniqueSegmentsPtr.size;
    entryIndex = 0;

//    System.out.println("numUniq: " + numUniqueSegment);

//#if defined(HTM) || defined(STM)
    {
        /* Choose disjoint segments [i_start,i_stop) for each thread */
        long num = uniqueSegmentsPtr.numBucket;
//        System.out.println("num: " + num);
        long partitionSize = (num + numThread/2) / numThread; /* with rounding */
//        System.out.println("num: " + num);
//        System.out.println("numThread: " + numThread);
//        System.out.println("partSize: " + partitionSize);
        i_start = threadId * partitionSize;
        if (threadId == (numThread - 1)) {
            i_stop = num;
        } else {
            i_stop = i_start + partitionSize;
        }
    }
   
    {
        /* Approximate disjoint segments of element allocation in constructEntries */
        long partitionSize = (numUniqueSegment + numThread/2) / numThread; /* with rounding */
        entryIndex = threadId * partitionSize;
    }
//#else /* !(HTM || STM) */
//    i_start = 0;
//    i_stop = uniqueSegmentsPtr.size();
//    entryIndex = 0;
//#endif /* !(HTM || STM) */

//    String uniqueArray[] = new String[uniqueSegmentsPtr.size()];
//    int ind = 0;
//    HashMapIterator iterarian = uniqueSegmentsPtr.iterator(1);
//    String roar;
//    System.out.println("uniqueSegmentsPtr contents: ");
//    while(iterarian.hasNext()) {
//      roar = (String)iterarian.next();
//      uniqueArray[ind++] = roar;
//      System.out.println(" " + roar);
//    }

//    i_stop = Math.imin(ind, (int)i_stop);
//    System.out.println("start: " + i_start + " stop: " + i_stop);
    for (i = i_start; i < i_stop; i++) {
//      System.out.println("i: " + i);
      List chainPtr = uniqueSegmentsPtr.buckets[(int)i];
//      System.out.println("past buckets index");
      ListNode it = chainPtr.head;
      
      while(it.nextPtr != null) {
//      System.out.println("past null it check");

        it = it.nextPtr;    
        ByteArray segment = it.dataPtr.firstPtr;

//        System.out.println("Segment: " + segment);
//      System.out.println("segment[" + i + "]: " + segment);
    //        list_iter_t it;
    //        list_iter_reset(&it, chainPtr);

    //        while (list_iter_hasNext(&it, chainPtr)) {

    //            char* segment = (char*)((pair_t*)list_iter_next(&it, chainPtr))->firstPtr;

        long newj;
        long startHash;
        boolean status;

        /* Find an empty constructEntries entry */
        atomic {
  //            TM_BEGIN();
  //            while (((void*)TM_SHARED_READ_P(constructEntries[entryIndex].segment)) != NULL) {
          while(constructEntries[(int)entryIndex].segment != null) { 
            entryIndex = (entryIndex + 1) % numUniqueSegment; /* look for empty */
          }
  //            constructEntryPtr = &constructEntries[entryIndex];
  //            TM_SHARED_WRITE_P(constructEntryPtr->segment, segment);
          constructEntries[(int)entryIndex].segment = segment;
  //            TM_END();
        }
        
        constructEntry constructEntryPtr = constructEntries[(int)entryIndex];

        entryIndex = (entryIndex + 1) % numUniqueSegment;



        /*
         * Save hashes (sdbm algorithm) of segment substrings
         *
         * endHashes will be computed for shorter substrings after matches
         * have been made (in the next phase of the code). This will reduce
         * the number of substrings for which hashes need to be computed.
         *
         * Since we can compute startHashes incrementally, we go ahead
         * and compute all of them here.
         */
        /* constructEntryPtr is local now */
        
        
//        segment.print();
//        segment.changeIndex(1);
//        segment.print();
        constructEntryPtr.endHash = hashString(segment.newIndex(1));   // CAN BE SWAPPED OUT WITH CHANGE INDEX, FOR SOME REASON SLOWS IT DOWN INSTEAD OF SPEEDING IT UP. DOESNT MAKE SENSE TO US.
//        segment.changeIndex(0);


//        System.out.println("first endHash: " + constructEntryPtr.endHash);
        startHash = 0;
        for (newj = 1; newj < segmentLength; newj++) {
            startHash = (char)(segment.array[segment.offset + (int)newj-1]) + (startHash << 6) + (startHash << 16) - startHash;
            String myStr = new String(segment.array);
//            System.out.println("hash for segment-" + myStr + ": " + startHash);
            atomic {
  //                TM_BEGIN();
  //                status = TMTABLE_INSERT(startHashToConstructEntryTables[j], (ulong_t)startHash, (void*)constructEntryPtr );
              boolean check = startHashToConstructEntryTables[(int)newj].table_insert(startHash, constructEntryPtr);
  //                TM_END();
            }
  //                assert(status);

        }


        /*
         * For looking up construct entries quickly
         */
        startHash = (char)(segment.array[segment.offset + (int)newj-1]) + (startHash << 6) + (startHash << 16) - startHash;
          atomic {
  //            TM_BEGIN();
  //            status = TMTABLE_INSERT(hashToConstructEntryTable, (ulong_t)startHash, (void*)constructEntryPtr);
            hashToConstructEntryTable.table_insert(startHash, constructEntryPtr);
  //            TM_END();
          }
  //            assert(status);
          
      }
    }
    
//    int tempi;
//    for(tempi = 0; tempi < 4; tempi++) {
//      System.out.println("constructEntries[" + tempi + "]: " + constructEntries[tempi].segment);
//    }
    System.out.println("Past calcing hashes for segments");
//    thread_barrier_wait();
    Barrier.enterBarrier();
    
    /*
     * Step 2b: Match ends to starts by using hash-based string comparison.
     */
    for (substringLength = segmentLength-1; substringLength > 0; substringLength--) {

        Table startHashToConstructEntryTablePtr = startHashToConstructEntryTables[(int)substringLength];
        LinkedList buckets[] = startHashToConstructEntryTablePtr.buckets;
        long numBucket = startHashToConstructEntryTablePtr.numBucket;
        
//        System.out.println("Retrieved the buckets.");

        long index_start;
        long index_stop;

//#if defined(HTM) || defined(STM)
        {
            /* Choose disjoint segments [index_start,index_stop) for each thread */
            long partitionSize = (numUniqueSegment + numThread/2) / numThread; /* with rounding */
            index_start = threadId * partitionSize;
            if (threadId == (numThread - 1)) {
                index_stop = numUniqueSegment;
            } else {
                index_stop = index_start + partitionSize;
            }
        }

        /* Iterating over disjoint itervals in the range [0, numUniqueSegment) */
        for (entryIndex = index_start;
             entryIndex < index_stop;
             entryIndex += endInfoEntries[(int)entryIndex].jumpToNext)
        {
            if (!endInfoEntries[(int)entryIndex].isEnd) {
                continue;
            }

            /*  ConstructEntries[entryIndex] is local data */
//            System.out.println("entryIndex: " + entryIndex);
            constructEntry endConstructEntryPtr = constructEntries[(int)entryIndex];
            ByteArray endSegment = endConstructEntryPtr.segment;
            long endHash = endConstructEntryPtr.endHash;
//            System.out.println("endHash: " + endHash);
            LinkedList chainPtr = buckets[(int)(endHash % numBucket)]; /* buckets: constant data */
            LinkedListIterator it = (LinkedListIterator)chainPtr.iterator();
            while (it.hasNext()) {
                constructEntry startConstructEntryPtr = (constructEntry)it.next();
                ByteArray startSegment = startConstructEntryPtr.segment;
                long newLength = 0;

                /* endConstructEntryPtr is local except for properties startPtr/endPtr/length */
                atomic {
//                TM_BEGIN();
//                  System.out.print("Comparing: ");endSegment.print();
//                  System.out.print("and      : ");startSegment.print();
//                  System.out.println("size: " + substringLength);
                  
                  if(startConstructEntryPtr.isStart &&
                      (endConstructEntryPtr.startPtr != startConstructEntryPtr) &&
                      (startSegment.substring(0, (int)substringLength).compareTo(endSegment.newIndex((int)(segmentLength-substringLength))) == 0))
                  {

//                    System.out.println("Match!");
                    startConstructEntryPtr.isStart = false;
                    constructEntry startConstructEntry_endPtr;
                    constructEntry endConstructEntry_startPtr;

                    /* Update endInfo (appended something so no longer end) */
                    endInfoEntries[(int)entryIndex].isEnd = false;
                    /* Update segment chain construct info */
                    startConstructEntry_endPtr = startConstructEntryPtr.endPtr;
                    endConstructEntry_startPtr = endConstructEntryPtr.startPtr;
                    startConstructEntry_endPtr.startPtr = endConstructEntry_startPtr;
                    endConstructEntryPtr.nextPtr = startConstructEntryPtr;
                    endConstructEntry_startPtr.endPtr = startConstructEntry_endPtr;
                    endConstructEntryPtr.overlap = substringLength;
                    newLength = endConstructEntry_startPtr.length + startConstructEntryPtr.length - substringLength;
                    endConstructEntry_startPtr.length = newLength;
                  } else {/* if (matched) */
//                    System.out.println("Non match.");
                  }
//                TM_END();
                }

                if (!endInfoEntries[(int)entryIndex].isEnd) { /* if there was a match */
//                    System.out.println("match means break");
                    break;
                }
            } /* iterate over chain */

        } /* for (endIndex < numUniqueSegment) */

//        thread_barrier_wait();
        Barrier.enterBarrier();
        
        /*
         * Step 2c: Update jump values and hashes
         *
         * endHash entries of all remaining ends are updated to the next
         * substringLength. Additionally jumpToNext entries are updated such
         * that they allow to skip non-end entries. Currently this is sequential
         * because parallelization did not perform better.
.        */

//        System.out.println("Length: " + constructEntries.length);

//        int ellemeno;
//        for(ellemeno = 0; ellemeno < constructEntries.length; ellemeno++) {
//          System.out.println("construct[" + ellemeno + "]: " + constructEntries[ellemeno].segment + " isStart: " + constructEntries[ellemeno].isStart + " length: " + constructEntries[ellemeno].length + " overlap: " + constructEntries[ellemeno].overlap);
//        }


        // BUGGINZ

        if (threadId == 0) {
            if (substringLength > 1) {
              //System.out.println("inside bugginz");
                long index = segmentLength - substringLength + 1;
                /* initialization if j and i: with i being the next end after j=0 */
                for (i = 1; !endInfoEntries[(int)i].isEnd; i+=endInfoEntries[(int)i].jumpToNext) {
                    /* find first non-null */
                }
                //System.out.println("post inner for");
                /* entry 0 is handled seperately from the loop below */
                endInfoEntries[0].jumpToNext = i;
                if (endInfoEntries[0].isEnd) {
                    ByteArray segment = constructEntries[0].segment;
//                    segment.changeOffset((int)index);
                    constructEntries[0].endHash = hashString(segment.newIndex((int)index)); // USE BYTE SUBSTRING FUNCTION
                }
                //System.out.println("post inner if");                
                /* Continue scanning (do not reset i) */
                for (j = 0; i < numUniqueSegment; i+=endInfoEntries[(int)i].jumpToNext) {
                    //System.out.print("i: " + i + " ");
                    //System.out.print("j: " + j + " ");
                    
                    if (endInfoEntries[(int)i].isEnd) {
                    //System.out.println("isEnd");
                        ByteArray segment = constructEntries[(int)i].segment;
                        //System.out.println("segment[" + i + "]: " + segment);
                        constructEntries[(int)i].endHash = hashString(segment.newIndex((int)index)); // USE BYTE SUBSTRING FUNCTION
                        endInfoEntries[(int)j].jumpToNext = Math.imax((int)1, (int)(i - j));
                        j = i;
                    }
                    //System.out.println("done end");
                }
                endInfoEntries[(int)j].jumpToNext = i - j;
            }
        }
        //System.out.println("past threadId0");

//        thread_barrier_wait();
        Barrier.enterBarrier();

    } /* for (substringLength > 0) */
    //System.out.println("Out of for3");

//    thread_barrier_wait();
    Barrier.enterBarrier();
    System.out.println("Past matching and linking segments");
    /*
     * Step 3: Build sequence string
     */
    if (threadId == 0) {

        long totalLength = 0;

//        System.out.println("numUS: " + numUniqueSegment);
//        System.out.println("ind: " + ind);
        //numUniqueSegment
        for (i = 0; i < numUniqueSegment; i++) {
            if (constructEntries[(int)i].isStart) {
              totalLength += constructEntries[(int)i].length;
            }
        }

        //System.out.println("totalLength: " + totalLength);

        ByteArray sequence = sequencerPtr.sequence;

        ByteArray copyPtr = sequence;
        long sequenceLength = 0;

        for (i = 0; i < numUniqueSegment; i++) {
            /* If there are several start segments, we append in arbitrary order  */
            constructEntry constructEntryPtr = constructEntries[(int)i];
//            System.out.println("segment[" + i + "]: " + constructEntryPtr.segment);
            if (constructEntryPtr.isStart) {
//            System.out.println("Start new chain...");
                long newSequenceLength = sequenceLength + constructEntryPtr.length;
                long prevOverlap = 0;
//                assert( newSequenceLength <= totalLength );
//                copyPtr = sequence + sequenceLength;
//                sequenceLength = newSequenceLength;
                do {
                    long numChar = segmentLength - constructEntryPtr.overlap;
//                    if ((copyPtr + numChar) > (sequence + newSequenceLength)) {
//                        System.out.print("ERROR: sequence length != actual length\n");
//                        break;
//                    }
                    copyPtr = constructEntryPtr.segment;
//                    System.out.print("building: ");copyPtr.print();
//                    System.out.println("copyPtr:  " + constructEntryPtr.segment);
//                    System.out.println("overlap: " + prevOverlap);  // OVERLAP MAKESS NOOOO SEEEENNSEEEE
//                    System.out.println("length:  " + constructEntryPtr.length);
//                    System.out.println("numChar: " + numChar);
                    if(sequencerPtr.sequence == null) {
//                      System.out.println("nulled");
                      sequencerPtr.sequence = copyPtr;
                    } else {
//                      System.out.println("not null, concat");
                      sequencerPtr.sequence.concat(copyPtr.newIndex((int)(prevOverlap)));
                    }
//                    System.out.println("sequence: " + sequencerPtr.sequence);
                    prevOverlap = constructEntryPtr.overlap;
                    constructEntryPtr = constructEntryPtr.nextPtr;
                } while (constructEntryPtr != null);
//                assert(copyPtr <= (sequence + sequenceLength));
            }
        }

//        assert(sequence != NULL);
    }
    System.out.println("Past building sequence");
//    System.out.println("Natural run finish.");

//    TM_THREAD_EXIT();

  }

  /* =============================================================================
   * hashString
   * -- uses sdbm hash function
   * =============================================================================
   */
/*  static long hashString (byte str[])
  {
      long hash = 0;

      int index = 0;
      // Note: Do not change this hashing scheme 
      for(index = 0; index < str.length(); index++) {
        char c = str[index];
        hash = c + (hash << 6) + (hash << 16) - hash;
      }
  
      if(hash < 0) hash *= -1;

      return hash;
  }
*/
  static long hashString (ByteArray str)
  {
      //String myStr = new String(str.array);
//      System.out.println("comping hash for string: " + myStr + " offset: " + str.offset);  
  
      long hash = 0;

      int index = 0;
      // Note: Do not change this hashing scheme 
      for(index = str.offset; index < str.array.length; index++) {
        char c = (char)(str.array[index]);
        hash = c + (hash << 6) + (hash << 16) - hash;
      }
  
      if(hash < 0) hash *= -1;
      //System.out.println("hash: " + hash);
      return hash;
  }

  /* =============================================================================
   * compareSegment
   * -- For hashtable
   * =============================================================================
   */   
}
