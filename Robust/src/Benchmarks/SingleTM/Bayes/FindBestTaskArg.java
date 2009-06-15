public class FindBestTaskArg {
  int toId;
  Learner learnerPtr;
  Query queries;
  Vector_t queryVectorPtr;
  Vector_t parentQueryVectorPtr;
  int numTotalParent;
  float basePenalty;
  float baseLogLikelihood;
  BitMap bitmapPtr;
  Queue workQueuePtr;
  Vector_t aQueryVectorPtr;
  Vector_t bQueryVectorPtr;

  public FindBestTaskArg() {
  }
}
