public class Sequencer {

      public char* sequence;

      public Segments segmentsPtr;

      /* For removing duplicate segments */
      Hashmap uniqueSegmentsPtr;

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
    long i;

    uniqueSegmentsPtr = new Hashmap(myGeneLength);

    /* For finding a matching entry */
    endInfoEntries = new endInfoEntry[maxNumUniqueSegment];
    for (i = 0; i < maxNumUniqueSegment; i++) {
      endInfoEntries[i].isEnd = TRUE;
        endInfoEntries[i].jumpToNext = 1;
    }
    startHashToConstructEntryTables = new Table[mySegmentLength];
    for (i = 1; i < mySegmentLength; i++) { /* 0 is dummy entry */
        startHashToConstructEntryTables[i] = new Table(myGeneLength);
    }
    segmentLength = mySegmentLength;

    /* For constructing sequence */
    constructEntries = new ContructEntry[maxNumUniqueSegment];
    
    for (i= 0; i < maxNumUniqueSegment; i++) {
        constructEntries[i].isStart = TRUE;
        constructEntries[i].segment = NULL;
        constructEntries[i].endHash = 0;
        constructEntries[i].startPtr = constructEntries[i];
        constructEntries[i].nextPtr = NULL;
        constructEntries[i].endPtr = constructEntries[i];
        constructEntries[i].overlap = 0;
        constructEntries[i].length = segmentLength;
    }
    hashToConstructEntryTable = new Table(geneLength);

    segmentsPtr = mySegmentsPtr;  
  }


  /* =============================================================================
   * sequencer_run
   * =============================================================================
   */

  void run () {

    //TM_THREAD_ENTER();

    long threadId = thread_getId();

    //Sequencer sequencerPtr = (sequencer_t*)argPtr;

    Hashmap         uniqueSegmentsPtr;
    endInfoEntry    endInfoEntries[];
    Hashmap         startHashToConstructEntryTables[];
    constructEntry  constructEntries[];
    Hashmap         hashToConstructEntryTable;

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

    /*
     * Step 1: Remove duplicate segments
     */
#if defined(HTM) || defined(STM)
    long numThread = thread_getNumThread();
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
#else /* !(HTM || STM) */
    i_start = 0;
    i_stop = numSegment;
#endif /* !(HTM || STM) */
    for (i = i_start; i < i_stop; i+=CHUNK_STEP1) {
        TM_BEGIN();
        {
            long ii;
            long ii_stop = MIN(i_stop, (i+CHUNK_STEP1));
            for (ii = i; ii < ii_stop; ii++) {
                string segment = segmentsContentsPtr.get(ii);
                TMHASHTABLE_INSERT(uniqueSegmentsPtr,
                                   segment,
                                   segment);
            } /* ii */
        }
        TM_END();
    }

    thread_barrier_wait();

    /*
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
    numUniqueSegment = hashtable_getSize(uniqueSegmentsPtr);
    entryIndex = 0;

#if defined(HTM) || defined(STM)
    {
        /* Choose disjoint segments [i_start,i_stop) for each thread */
        long num = uniqueSegmentsPtr->numBucket;
        long partitionSize = (num + numThread/2) / numThread; /* with rounding */
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
#else /* !(HTM || STM) */
    i_start = 0;
    i_stop = uniqueSegmentsPtr->numBucket;
    entryIndex = 0;
#endif /* !(HTM || STM) */

    for (i = i_start; i < i_stop; i++) {

        list_t* chainPtr = uniqueSegmentsPtr->buckets[i];
        list_iter_t it;
        list_iter_reset(&it, chainPtr);

        while (list_iter_hasNext(&it, chainPtr)) {

            char* segment =
                (char*)((pair_t*)list_iter_next(&it, chainPtr))->firstPtr;
            constructEntry_t* constructEntryPtr;
            long j;
            ulong_t startHash;
            bool_t status;

            /* Find an empty constructEntries entry */
            TM_BEGIN();
            while (((void*)TM_SHARED_READ_P(constructEntries[entryIndex].segment)) != NULL) {
                entryIndex = (entryIndex + 1) % numUniqueSegment; /* look for empty */
            }
            constructEntryPtr = &constructEntries[entryIndex];
            TM_SHARED_WRITE_P(constructEntryPtr->segment, segment);
            TM_END();
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
            constructEntryPtr->endHash = (ulong_t)hashString(&segment[1]);

            startHash = 0;
            for (j = 1; j < segmentLength; j++) {
                startHash = (ulong_t)segment[j-1] +
                            (startHash << 6) + (startHash << 16) - startHash;
                TM_BEGIN();
                status = TMTABLE_INSERT(startHashToConstructEntryTables[j],
                                        (ulong_t)startHash,
                                        (void*)constructEntryPtr );
                TM_END();
                assert(status);
            }

            /*
             * For looking up construct entries quickly
             */
            startHash = (ulong_t)segment[j-1] +
                        (startHash << 6) + (startHash << 16) - startHash;
            TM_BEGIN();
            status = TMTABLE_INSERT(hashToConstructEntryTable,
                                    (ulong_t)startHash,
                                    (void*)constructEntryPtr);
            TM_END();
            assert(status);
        }
    }

    thread_barrier_wait();

    /*
     * Step 2b: Match ends to starts by using hash-based string comparison.
     */
    for (substringLength = segmentLength-1; substringLength > 0; substringLength--) {

        table_t* startHashToConstructEntryTablePtr =
            startHashToConstructEntryTables[substringLength];
        list_t** buckets = startHashToConstructEntryTablePtr->buckets;
        long numBucket = startHashToConstructEntryTablePtr->numBucket;

        long index_start;
        long index_stop;

#if defined(HTM) || defined(STM)
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
#else /* !(HTM || STM) */
        index_start = 0;
        index_stop = numUniqueSegment;
#endif /* !(HTM || STM) */

        /* Iterating over disjoint itervals in the range [0, numUniqueSegment) */
        for (entryIndex = index_start;
             entryIndex < index_stop;
             entryIndex += endInfoEntries[entryIndex].jumpToNext)
        {
            if (!endInfoEntries[entryIndex].isEnd) {
                continue;
            }

            /*  ConstructEntries[entryIndex] is local data */
            constructEntry_t* endConstructEntryPtr =
                &constructEntries[entryIndex];
            char* endSegment = endConstructEntryPtr->segment;
            ulong_t endHash = endConstructEntryPtr->endHash;

            list_t* chainPtr = buckets[endHash % numBucket]; /* buckets: constant data */
            list_iter_t it;
            list_iter_reset(&it, chainPtr);

            /* Linked list at chainPtr is constant */
            while (list_iter_hasNext(&it, chainPtr)) {

                constructEntry_t* startConstructEntryPtr =
                    (constructEntry_t*)list_iter_next(&it, chainPtr);
                char* startSegment = startConstructEntryPtr->segment;
                long newLength = 0;

                /* endConstructEntryPtr is local except for properties startPtr/endPtr/length */
                TM_BEGIN();

                /* Check if matches */
                if (TM_SHARED_READ(startConstructEntryPtr->isStart) &&
                    (TM_SHARED_READ_P(endConstructEntryPtr->startPtr) != startConstructEntryPtr) &&
                    (strncmp(startSegment,
                             &endSegment[segmentLength - substringLength],
                             substringLength) == 0))
                {
                    TM_SHARED_WRITE(startConstructEntryPtr->isStart, FALSE);

                    constructEntry_t* startConstructEntry_endPtr;
                    constructEntry_t* endConstructEntry_startPtr;

                    /* Update endInfo (appended something so no longer end) */
                    TM_LOCAL_WRITE(endInfoEntries[entryIndex].isEnd, FALSE);

                    /* Update segment chain construct info */
                    startConstructEntry_endPtr =
                        (constructEntry_t*)TM_SHARED_READ_P(startConstructEntryPtr->endPtr);
                    endConstructEntry_startPtr =
                        (constructEntry_t*)TM_SHARED_READ_P(endConstructEntryPtr->startPtr);

                    assert(startConstructEntry_endPtr);
                    assert(endConstructEntry_startPtr);
                    TM_SHARED_WRITE_P(startConstructEntry_endPtr->startPtr,
                                      endConstructEntry_startPtr);
                    TM_LOCAL_WRITE_P(endConstructEntryPtr->nextPtr,
                                     startConstructEntryPtr);
                    TM_SHARED_WRITE_P(endConstructEntry_startPtr->endPtr,
                                      startConstructEntry_endPtr);
                    TM_SHARED_WRITE(endConstructEntryPtr->overlap, substringLength);
                    newLength = (long)TM_SHARED_READ(endConstructEntry_startPtr->length) +
                                (long)TM_SHARED_READ(startConstructEntryPtr->length) -
                                substringLength;
                    TM_SHARED_WRITE(endConstructEntry_startPtr->length, newLength);
                } /* if (matched) */

                TM_END();

                if (!endInfoEntries[entryIndex].isEnd) { /* if there was a match */
                    break;
                }
            } /* iterate over chain */

        } /* for (endIndex < numUniqueSegment) */

        thread_barrier_wait();

        /*
         * Step 2c: Update jump values and hashes
         *
         * endHash entries of all remaining ends are updated to the next
         * substringLength. Additionally jumpToNext entries are updated such
         * that they allow to skip non-end entries. Currently this is sequential
         * because parallelization did not perform better.
.        */

        if (threadId == 0) {
            if (substringLength > 1) {
                long index = segmentLength - substringLength + 1;
                /* initialization if j and i: with i being the next end after j=0 */
                for (i = 1; !endInfoEntries[i].isEnd; i+=endInfoEntries[i].jumpToNext) {
                    /* find first non-null */
                }
                /* entry 0 is handled seperately from the loop below */
                endInfoEntries[0].jumpToNext = i;
                if (endInfoEntries[0].isEnd) {
                    constructEntry_t* constructEntryPtr = &constructEntries[0];
                    char* segment = constructEntryPtr->segment;
                    constructEntryPtr->endHash = (ulong_t)hashString(&segment[index]);
                }
                /* Continue scanning (do not reset i) */
                for (j = 0; i < numUniqueSegment; i+=endInfoEntries[i].jumpToNext) {
                    if (endInfoEntries[i].isEnd) {
                        constructEntry_t* constructEntryPtr = &constructEntries[i];
                        char* segment = constructEntryPtr->segment;
                        constructEntryPtr->endHash = (ulong_t)hashString(&segment[index]);
                        endInfoEntries[j].jumpToNext = MAX(1, (i - j));
                        j = i;
                    }
                }
                endInfoEntries[j].jumpToNext = i - j;
            }
        }

        thread_barrier_wait();

    } /* for (substringLength > 0) */


    thread_barrier_wait();

    /*
     * Step 3: Build sequence string
     */
    if (threadId == 0) {

        long totalLength = 0;

        for (i = 0; i < numUniqueSegment; i++) {
            constructEntry_t* constructEntryPtr = &constructEntries[i];
            if (constructEntryPtr->isStart) {
              totalLength += constructEntryPtr->length;
            }
        }

        sequencerPtr->sequence = (char*)P_MALLOC((totalLength+1) * sizeof(char));
        char* sequence = sequencerPtr->sequence;
        assert(sequence);

        char* copyPtr = sequence;
        long sequenceLength = 0;

        for (i = 0; i < numUniqueSegment; i++) {
            constructEntry_t* constructEntryPtr = &constructEntries[i];
            /* If there are several start segments, we append in arbitrary order  */
            if (constructEntryPtr->isStart) {
                long newSequenceLength = sequenceLength + constructEntryPtr->length;
                assert( newSequenceLength <= totalLength );
                copyPtr = sequence + sequenceLength;
                sequenceLength = newSequenceLength;
                do {
                    long numChar = segmentLength - constructEntryPtr->overlap;
                    if ((copyPtr + numChar) > (sequence + newSequenceLength)) {
                        TM_PRINT0("ERROR: sequence length != actual length\n");
                        break;
                    }
                    memcpy(copyPtr,
                           constructEntryPtr->segment,
                           (numChar * sizeof(char)));
                    copyPtr += numChar;
                } while ((constructEntryPtr = constructEntryPtr->nextPtr) != NULL);
                assert(copyPtr <= (sequence + sequenceLength));
            }
        }

        assert(sequence != NULL);
        sequence[sequenceLength] = '\0';
    }

    TM_THREAD_EXIT();

  }


  private class endInfoEntry {
      boolean isEnd;
      long jumpToNext;
  }

  private class constructEntry {
      boolean isStart;
      String segment;
      long endHash;
      constructEntry startPtr;
      constructEntry nextPtr;
      constructEntry endPtr;
      long overlap;
      long length;
  }

  private class sequencer_run_arg {
      Sequencer sequencerPtr;
      Segments segmentsPtr;
      long preAllocLength;
      String returnSequence; /* variable stores return value */
  } 
  /* =============================================================================
   * hashString
   * -- uses sdbm hash function
   * =============================================================================
   */
  static long hashString (String str)
  {
      long hash = 0;
      long c;

      /* Note: Do not change this hashing scheme */
      while ((c = str++) != '\0') {
          hash = c + (hash << 6) + (hash << 16) - hash;
      }

      return (long)hash;
  }


  /* =============================================================================
   * hashSegment
   * -- For hashtable
   * =============================================================================
   */
  static long hashSegment (string keyPtr)
  {
      return (long)hash_sdbm(keyPtr); /* can be any "good" hash function */
  }


  /* =============================================================================
   * compareSegment
   * -- For hashtable
   * =============================================================================
   */
  static long compareSegment (pair_t* a, pair_t* b)
  {
      return strcmp((char*)(a->firstPtr), (char*)(b->firstPtr));
  }
   
}