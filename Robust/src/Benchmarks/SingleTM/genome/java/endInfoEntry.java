  public class endInfoEntry {
      boolean isEnd;
      long jumpToNext;
      
      public endInfoEntry() {
        isEnd = false;
        jumpToNext = 0;
      }
      public endInfoEntry(boolean myEnd, long myNext) {
        isEnd = myEnd;
        jumpToNext = myNext;
      }
  }
