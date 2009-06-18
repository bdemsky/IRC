public class Sequencer {

  public String sequence;

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
  int segmentLength;


  /* =============================================================================
   * sequencer_alloc
   * -- Returns NULL on failure
   * =============================================================================
   */
  public Sequencer (int myGeneLength, int mySegmentLength, Segments mySegmentsPtr) { 

    int maxNumUniqueSegment = myGeneLength - mySegmentLength + 1;
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

  public static void run (int threadNum, int numOfThreads, Random randomPtr, Sequencer sequencerPtr) {

    int threadId = threadNum;

    Segments segmentsPtr = sequencerPtr.segmentsPtr;

    Hashtable         uniqueSegmentsPtr = sequencerPtr.uniqueSegmentsPtr;
    endInfoEntry    endInfoEntries[] = sequencerPtr.endInfoEntries;
    Table         startHashToConstructEntryTables[] = sequencerPtr.startHashToConstructEntryTables;
    constructEntry  constructEntries[] = sequencerPtr.constructEntries;
    Table         hashToConstructEntryTable = sequencerPtr.hashToConstructEntryTable;

    Vector      segmentsContentsPtr = segmentsPtr.contentsPtr;
    int        numSegment          = segmentsContentsPtr.size();
    int        segmentLength       = segmentsPtr.length;

    int i;
    int j;
    int i_start;
    int i_stop;
    int numUniqueSegment;
    int substringLength;
    int entryIndex;

    int CHUNK_STEP1 = 12;

    /*
     * Step 1: Remove duplicate segments
     */
    int numThread = numOfThreads;
    {
      /* Choose disjoint segments [i_start,i_stop) for each thread */
      int partitionSize = (numSegment + numThread/2) / numThread; /* with rounding */
      i_start = threadId * partitionSize;
      if (threadId == (numThread - 1)) {
        i_stop = numSegment;
      } else {
        i_stop = i_start + partitionSize;
      }
    }

    for (i = i_start; i < i_stop; i+=CHUNK_STEP1) {
      atomic {
        int ii;
        int ii_stop = Math.imin(i_stop, (i+CHUNK_STEP1));
        for (ii = i; ii < ii_stop; ii++) {
          String segment = (String)segmentsContentsPtr.elementAt(ii);
          if(!uniqueSegmentsPtr.TMhashtable_insert(segment, segment)) {
            ;
          }
        } /* ii */
      }
    }

    Barrier.enterBarrier();

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
    numUniqueSegment = uniqueSegmentsPtr.size;
    entryIndex = 0;

    {
      /* Choose disjoint segments [i_start,i_stop) for each thread */
      int num = uniqueSegmentsPtr.numBucket;
      int partitionSize = (num + numThread/2) / numThread; /* with rounding */
      i_start = threadId * partitionSize;
      if (threadId == (numThread - 1)) {
        i_stop = num;
      } else {
        i_stop = i_start + partitionSize;
      }
    }

    {
      /* Approximate disjoint segments of element allocation in constructEntries */
      int partitionSize = (numUniqueSegment + numThread/2) / numThread; /* with rounding */
      entryIndex = threadId * partitionSize;
    }

    for (i = i_start; i < i_stop; i++) {
      List chainPtr = uniqueSegmentsPtr.buckets[i];
      ListNode it = chainPtr.head;

      while(it.nextPtr != null) {
        it = it.nextPtr;    
        String segment = it.dataPtr.firstPtr;
        int newj;
        int startHash;
        boolean status;

        /* Find an empty constructEntries entry */
        atomic {
          while(constructEntries[entryIndex].segment != null) { 
            entryIndex = (entryIndex + 1) % numUniqueSegment; /* look for empty */
          }
          constructEntries[entryIndex].segment = segment;
        }

        constructEntry constructEntryPtr = constructEntries[entryIndex];

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
        constructEntryPtr.endHash = hashString(segment.substring(1)); // USE BYTE SUBSTRING FUNCTION

        startHash = 0;
        for (newj = 1; newj < segmentLength; newj++) {
          startHash = segment.charAt((int)newj-1) + (startHash << 6) + (startHash << 16) - startHash;
          atomic {
            boolean check = startHashToConstructEntryTables[newj].table_insert(startHash, constructEntryPtr);
          }

        }


        /*
         * For looking up construct entries quickly
         */
        startHash = segment.charAt((int)newj-1) + (startHash << 6) + (startHash << 16) - startHash;
        atomic {
          hashToConstructEntryTable.table_insert(startHash, constructEntryPtr);
        }
      }
    }

    Barrier.enterBarrier();

    /*
     * Step 2b: Match ends to starts by using hash-based string comparison.
     */
    for (substringLength = segmentLength-1; substringLength > 0; substringLength--) {

      Table startHashToConstructEntryTablePtr = startHashToConstructEntryTables[substringLength];
      LinkedList buckets[] = startHashToConstructEntryTablePtr.buckets;
      int numBucket = startHashToConstructEntryTablePtr.numBucket;

      int index_start;
      int index_stop;

      {
        /* Choose disjoint segments [index_start,index_stop) for each thread */
        int partitionSize = (numUniqueSegment + numThread/2) / numThread; /* with rounding */
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
          entryIndex += endInfoEntries[entryIndex].jumpToNext)
      {
        if (!endInfoEntries[entryIndex].isEnd) {
          continue;
        }

        /*  ConstructEntries[entryIndex] is local data */
        constructEntry endConstructEntryPtr = constructEntries[entryIndex];
        String endSegment = endConstructEntryPtr.segment;
        int endHash = endConstructEntryPtr.endHash;

        LinkedList chainPtr = buckets[(endHash % numBucket)]; /* buckets: constant data */
        LinkedListIterator it = (LinkedListIterator)chainPtr.iterator();
        while (it.hasNext()) {
          constructEntry startConstructEntryPtr = (constructEntry)it.next();
          String startSegment = startConstructEntryPtr.segment;
          int newLength = 0;

          /* endConstructEntryPtr is local except for properties startPtr/endPtr/length */
          atomic {
            if(startConstructEntryPtr.isStart &&
                (endConstructEntryPtr.startPtr != startConstructEntryPtr) &&
                (startSegment.substring(0, (int)substringLength).compareTo(endSegment.substring((int)(segmentLength-substringLength))) == 0))
            {
              startConstructEntryPtr.isStart = false;
              constructEntry startConstructEntry_endPtr;
              constructEntry endConstructEntry_startPtr;

              /* Update endInfo (appended something so no inter end) */
              endInfoEntries[entryIndex].isEnd = false;
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
            }
          }

          if (!endInfoEntries[entryIndex].isEnd) { /* if there was a match */
            break;
          }
        } /* iterate over chain */

      } /* for (endIndex < numUniqueSegment) */

      Barrier.enterBarrier();

      /*
       * Step 2c: Update jump values and hashes
       *
       * endHash entries of all remaining ends are updated to the next
       * substringLength. Additionally jumpToNext entries are updated such
       * that they allow to skip non-end entries. Currently this is sequential
       * because parallelization did not perform better.
       */

      if (threadId == 0) {
        if (substringLength > 1) {
          int index = segmentLength - substringLength + 1;
          /* initialization if j and i: with i being the next end after j=0 */
          for (i = 1; !endInfoEntries[i].isEnd; i+=endInfoEntries[i].jumpToNext) {
            /* find first non-null */
            ;
          }
          /* entry 0 is handled seperately from the loop below */
          endInfoEntries[0].jumpToNext = i;
          if (endInfoEntries[0].isEnd) {
            String segment = constructEntries[0].segment;
            constructEntries[0].endHash = hashString(segment.subString((int)index)); // USE BYTE SUBSTRING FUNCTION
          }
          /* Continue scanning (do not reset i) */
          for (j = 0; i < numUniqueSegment; i+=endInfoEntries[i].jumpToNext) {

            if (endInfoEntries[i].isEnd) {
              String segment = constructEntries[i].segment;
              constructEntries[i].endHash = hashString(segment.substring((int)index)); // USE BYTE SUBSTRING FUNCTION
              endInfoEntries[j].jumpToNext = Math.imax((int)1, (int)(i - j));
              j = i;
            }
          }
          endInfoEntries[j].jumpToNext = i - j;
        }
      }


      Barrier.enterBarrier();

    } /* for (substringLength > 0) */

    Barrier.enterBarrier();

    /*
     * Step 3: Build sequence string
     */
    if (threadId == 0) {
      int totalLength = 0;
      for (i = 0; i < numUniqueSegment; i++) {
        if (constructEntries[i].isStart) {
          totalLength += constructEntries[i].length;
        }
      }

      String sequence = sequencerPtr.sequence;

      String copyPtr = sequence;
      int sequenceLength = 0;

      for (i = 0; i < numUniqueSegment; i++) {
        /* If there are several start segments, we append in arbitrary order  */
        constructEntry constructEntryPtr = constructEntries[i];
        if (constructEntryPtr.isStart) {
          int newSequenceLength = sequenceLength + constructEntryPtr.length;
          int prevOverlap = 0;
          do {
            int numChar = segmentLength - constructEntryPtr.overlap;
            copyPtr = constructEntryPtr.segment;
            if(sequencerPtr.sequence == null) {
              sequencerPtr.sequence = copyPtr;
            } else {
              sequencerPtr.sequence = sequencerPtr.sequence.concat(copyPtr.substring((int)(prevOverlap)));
            }
            prevOverlap = constructEntryPtr.overlap;
            constructEntryPtr = constructEntryPtr.nextPtr;
          } while (constructEntryPtr != null);
        }
      }
    }
  }

  /* =============================================================================
   * hashString
   * -- uses sdbm hash function
   * =============================================================================
   */
  static int hashString (String str)
  {
    int hash = 0;

    int index = 0;
    // Note: Do not change this hashing scheme 
    for(index = 0; index < str.length(); index++) {
      char c = str.charAt(index);
      hash = c + (hash << 6) + (hash << 16) - hash;
    }

    if(hash < 0) hash *= -1;

    return hash;
  }
}