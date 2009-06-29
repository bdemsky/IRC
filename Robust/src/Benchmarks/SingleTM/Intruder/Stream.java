#define MAP_T                       RBTree
#define MAP_ALLOC(hash, cmp)        RBTree()
#define MAP_INSERT(map, key, data)  map.rbtree_insert(key, data)
#define MAP_CONTAINS(map, key)      map.rbtree_contains(key);

public class Stream {
  int percentAttack;
  Random randomPtr;
  Vector_t allocVectorPtr;
  Queue packetQueuePtr;
  MAP_T attackMapPtr;

  public Stream(int percentAttack) {
    Stream streamPtr;

    if (percentAttack < 0 || percentAttack > 100) {
      System.out.print("Error: Invalid percentAttack value\n");
      System.exit(0);
    }
    this.percentAttack = percentAttack;
    randomPtr = new Random();
    allocVectorPtr = vector_alloc(1);
    if (allocVectorPtr == null) {
      System.out.print("Error: Vector allocation failed\n");
      System.exit(0);
    }
    packetQueuePtr = queue_alloc(1);
    if (packetQueuePtr == null) {
      System.out.print("Error: Queue allocation failed\n");
      System.exit(0);
    }
    attackMapPtr = MAP_ALLOC(null, null);
    if (attackMapPtr == null) {
      System.out.print("Error: MAP_ALLOC failed\n");
      System.exit(0);
    }
  }

  public void splitIntoPackets(String str, int flowId, Random randomPtr,
      Vector_t allocVectorPtr, Queue packetQueuePtr) 
  {
    int numByte = str.length();
    int numPacket = randomPtr.random_generate() % numByte + 1;
    int numDataByte = numByte / numPacket;

    int p;
    for (p = 0; p < (numPacket - 1); p++) {
      Packet bytes = new Packet(numDataByte);
      if (bytes == null) {
        System.out.printString("Error: Packet class allocation failed\n");
        System.exit(-1);
      }
      boolean status;
      status = allocVectorPtr.vector_pushBack(bytes);
      if (status == false) {
        System.out.printString("Error: Vector pushBack failed\n");
        System.exit(-1);
      }
      bytes.flowId = flowId;
      bytes.fragmentId = p;
      bytes.numFragment = numPacket;
      bytes.length = numDataByte;
      int beginIndex = p * numDataByte;
      int endIndex = beginIndex + numDataByte;
      String tmpstr = str.subString(beginIndex, endIndex);
      bytes.data = new String(tmpstr);
      status = packetQueuePtr.queue_push(bytes);
      if (status == false) {
        System.out.printString("Error: Queue push failed\n");
        System.exit(0);
      }
    }
    boolean status;
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
    int beginIndex = p * numDataByte;
    int endIndex = beginIndex + lastNumDataByte;
    String tmpstr = str.subString(beginIndex, endIndex);
    bytes.data = new String(tmpstr);
    status = packetQueuePtr.queue_push(bytes);
    if (status == false) {
      System.out.printString("Error: Queue push failed\n");
      System.exit(0);
    }
  }

  int stream_generate(Stream streamPtr, Dictionary dictionaryPtr,
      int numFlow, int seed, int maxLength)
  {
    int numAttack = 0;

    int percentAttack = streamPtr.percentAttack;
    Random randomPtr = streamPtr.randomPtr;
    Vector_t allocVectorPtr = streamPtr.allocVectorPtr;
    Queue packetQueuePtr = streamPtr.packetQueuePtr;
    MAP_T attackMapPtr = streamPtr.attackMapPtr;

    Detector detectorPtr = new Detector();
    //detectorPtr.detector_addPreprocessor(
    randomPtr.random_seed();
    packetQueuePtr.queue_clear();

    int range = '~' - ' ' + 1;
    if (range <= 0) {
      System.out.printString("Assert failed range <= 0\n");
      System.exit(0);
    }

    int f;
    for (f = 1; f <= numFlow; f++) {
      String str;
      if ((randomPtr.random_generate() % 100) < percentAttack) {
        int s = randomPtr.random_generate() % global_numDefaultSignature;
        str = dictionaryPtr.dictionary_get(s);
        boolean status = MAP_INSERT(attackMapPtr, f, str);
        if (status == false) {
          System.out.printString("Assert failed: status is false\n");
          System.exit(0);
        }
        numAttack++;
      } else {
        /* Create random string */
        int length = (randomPtr.random_generate() % maxLength) + 1;
        str = new String[length+1]; 
        boolean status = allocVectorPtr.vector_pushBack(str);
        if (status == null) {
          System.out.printString("Assert failed: status is null\n");
          System.exit(0);
        }
        char c[] = str.toCharArray();
        for (l = 0; l < length; l++) {
          c[l] = ' ' + (char) randomPtr.random_generate() % range;
        }
        c[l] = 0;
        String str2 = new String(c);
        int err = detectorPtr.detector_process(str2);
        if (err == ERROR_SIGNATURE) {
          boolean status = MAP_INSERT(attackMapPtr, f, str);
          if (status == null) {
            System.out.printString("Assert failed status is null\n");
            System.exit(0);
          }
          numAttack++;
        }
      }
      splitIntoPackets(str, f, randomPtr, allocVectorPtr, packetQueuePtr);
    }
    packetQueuePtr.queue_shuffle(randomPtr);

    return numAttack;
  }

  String stream_getPacket(Stream streamPtr) 
  {
    return streamPtr.queue_pop();
  }

  boolean stream_isAttack(Stream streamPtr, int flowId)
  {
    return MAP_CONTAINS(streamPtr.attackMapPtr, flowId);
  }

}
