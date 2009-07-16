#define MAP_T                       RBTree
#define MAP_ALLOC(hash, cmp)        RBTree.alloc(cmp)
#define MAP_INSERT(map, key, data)  map.insert(key, data)
#define MAP_CONTAINS(map, key)      map.contains(key)
#define MAP_FIND(map,key)           map.get(key)
#define MAP_REMOVE(map,key)         map.deleteNode(key)

public class Stream {
  int percentAttack;
  Random randomPtr;
  Vector_t allocVectorPtr;
  Queue_t packetQueuePtr;
  MAP_T attackMapPtr;

  public Stream() {}


  /* alloc */
  public static Stream alloc(int percentAttack) {
    Stream streamPtr = new Stream();

    if (percentAttack < 0 || percentAttack > 100) {
      System.out.print("Error: Invalid percentAttack value\n");
      System.exit(0);
    }
    streamPtr.percentAttack = percentAttack;

    streamPtr.randomPtr = new Random();
    streamPtr.allocVectorPtr = Vector_t.vector_alloc(1);
    if (streamPtr.allocVectorPtr == null) {
      System.out.print("Error: Vector allocation failed\n");
      System.exit(0);
    }
    streamPtr.packetQueuePtr = Queue_t.queue_alloc(1);
    if (streamPtr.packetQueuePtr == null) {
      System.out.print("Error: Queue allocation failed\n");
      System.exit(0);
    }
    streamPtr.attackMapPtr = MAP_ALLOC(0,0);
    if (streamPtr.attackMapPtr == null) {
      System.out.print("Error: MAP_ALLOC failed\n");
      System.exit(0);
    }
    return streamPtr;
  }

  /* splintIntoPackets
   * -- Packets will be equal-size chunks except for last one, which will have
   *    all extra bytes
   */
  private void splitIntoPackets(byte[] str,int flowId, Random randomPtr,
      Vector_t allocVectorPtr, Queue_t packetQueuePtr) 
  {
    int numByte = str.length;
    int numPacket = randomPtr.random_generate() % numByte + 1;
    int numDataByte = numByte / numPacket;

    int i;
    int p;
    boolean status;
    int beginIndex = 0;
    int endIndex;
    int z;
    for (p = 0; p < (numPacket - 1); p++) {
      Packet bytes = new Packet(numDataByte);
      if (bytes == null) {
        System.out.printString("Error: Packet class allocation failed\n");
        System.exit(-1);
      }
      status = allocVectorPtr.vector_pushBack(bytes);
      if (status == false) {
        System.out.printString("Error: Vector pushBack failed\n");
        System.exit(-1);
      }
      bytes.flowId = flowId;
      bytes.fragmentId = p;
      bytes.numFragment = numPacket;
      bytes.length = numDataByte;
      endIndex = beginIndex + numDataByte;

            
      for(i=beginIndex,z=0;i <endIndex;z++,i++) {
            bytes.data[z] = str[i];
      }

      status = packetQueuePtr.queue_push(bytes);
      if (status == false) {
        System.out.printString("Error: Queue push failed\n");
        System.exit(0);
      }
      beginIndex = endIndex;

    }
  
    int lastNumDataByte = numDataByte + numByte % numPacket;
    Packet bytes = new Packet(lastNumDataByte);
    if (bytes == null) {
      System.out.printString("Error: Packet class allocation failed\n");
      System.exit(0);
    }
    bytes.flowId = flowId;
    bytes.fragmentId = p;
    bytes.numFragment = numPacket;
    bytes.length = lastNumDataByte;
    endIndex = numByte;
    
    for(i=beginIndex,z=0;i<endIndex;z++,i++) {
        bytes.data[z] = str[i];
    }
    status = packetQueuePtr.queue_push(bytes);

    if (status == false) {
      System.out.printString("Error: Queue push failed\n");
      System.exit(0);
    }
  }

  /*==================================================
  /* stream_generate 
   * -- Returns number of attacks generated
  /*==================================================*/

  public int generate(Dictionary dictionaryPtr,int numFlow, int seed, int maxLength)
  {
    int numAttack = 0;
    ERROR error = new ERROR();

    Detector detectorPtr = Detector.alloc();

    if(detectorPtr == null) 
    {
        System.out.println("Assertion in Stream.generate");
        System.exit(1);
    }
    detectorPtr.addPreprocessor(2); // preprocessor_toLower

    randomPtr.random_seed(seed);
    packetQueuePtr.queue_clear();

    int range = '~' - ' ' + 1;
    if (range <= 0) {
      System.out.printString("Assert failed range <= 0\n");
      System.exit(0);
    }

    int f;
    boolean status;
    for (f = 1; f <= numFlow; f++) {
      byte[] c;
      if ((randomPtr.random_generate() % 100) < percentAttack) {
        int s = randomPtr.random_generate() % dictionaryPtr.global_numDefaultSignature;
        String str = dictionaryPtr.get(s);
        c = str.getBytes();

        status = MAP_INSERT(attackMapPtr, f, c);
        if (status == false) {
          System.out.printString("Assert failed: status is false\n");
          System.exit(0);
        }
        numAttack++;
      } else {
        /* Create random string */
        int length = (randomPtr.random_generate() % maxLength) + 1;
        
        int l;
        c = new byte[length+1];
        for (l = 0; l < length; l++) {
          c[l] =(byte) (' ' + (char) (randomPtr.random_generate() % range));
        }
        status = allocVectorPtr.vector_pushBack(c);

        if(!status) {
            System.out.println("Assert faiiled status is null.");
            System.exit(0);
        }


        int err = detectorPtr.process(c);
        if (err == error.SIGNATURE) {
          status = MAP_INSERT(attackMapPtr, f, c);
        
          System.out.println("Never here");
          if (!status) {
            System.out.printString("Assert failed status is null\n");
            System.exit(0);
          }
          numAttack++;
        }
      }

      splitIntoPackets(c, f, randomPtr, allocVectorPtr, packetQueuePtr);

    }
    packetQueuePtr.queue_shuffle(randomPtr);

    return numAttack;
  }

  /*========================================================
   * stream_getPacket
   * -- If none, returns null
   *  ======================================================
   */
  Packet getPacket() 
  {
    return (Packet)packetQueuePtr.queue_pop();
  }

  /* =======================================================
   * stream_isAttack
   * =======================================================
   */
  boolean isAttack(int flowId)
  {
    return MAP_CONTAINS(attackMapPtr, flowId);
  }

}
