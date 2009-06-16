MAINCLASS=Bayes
SRC=tmp${MAINCLASS}.java \
	Adtree.java \
	AdtreeNode.java \
	AdtreeVary.java \
	tmpData.java \
	FindBestTaskArg.java \
	tmpLearner.java \
	LearnerTask.java \
	tmpNet.java \
	NetNode.java \
	Operation.java \
	Query.java \
	tmpSort.java \
	tmpBitMap.java \
	tmpIntList.java \
	IntListNode.java \
	tmpQueue.java \
	../common/Random.java \
	../common/Vector_t.java \
	../common/ListNode.java \
	../common/List.java \
	../common/QuickSort.java \
	IntVector.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java

FLAGS=-mainclass ${MAINCLASS} -thread -nooptimize -debug

default:
	cpp Bayes.java > tmp1Bayes.java
	cpp Data.java > tmp1Data.java
	cpp Net.java > tmp1Net.java
	cpp Sort.java > tmp1Sort.java
	cpp ../common/BitMap.java > tmp1BitMap.java
	cpp Queue.java > tmp1Queue.java
	cpp -DLEARNER_TRY_REMOVE -DLEARNER_TRY_REVERSE Learner.java > tmp1Learner.java
	cpp -DLIST_NO_DUPLICATES IntList.java > tmp1IntList.java
	./extractLines
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm tmp1Bayes.java
	rm tmpBayes.java
	rm tmp1Learner.java
	rm tmpLearner.java
	rm tmp1IntList.java
	rm tmpIntList.java
	rm tmp1Data.java
	rm tmpData.java
	rm tmp1Net.java
	rm tmpNet.java
	rm tmp1Sort.java
	rm tmpSort.java
	rm tmp1BitMap.java
	rm tmpBitMap.java
	rm tmp1Queue.java
	rm tmpQueue.java
	rm -rf tmpbuilddirectory
	rm *.bin
