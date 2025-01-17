#include "dstm.h"
#include "addPrefetchEnhance.h"
#include <signal.h>
#include <fcntl.h>

extern int numTransAbort;
extern int numTransCommit;
extern int nchashSearch;
extern int nmhashSearch;
extern int nprehashSearch;
extern int ndirtyCacheObj;
extern int nRemoteSend;
extern int nSoftAbort;
extern int bytesSent;
extern int bytesRecv;
extern int totalObjSize;
extern unsigned int myIpAddr;
extern int getResponse;
extern int sendRemoteReq;

void handle();
extern pfcstats_t *evalPrefetch;

/*
void transStatsHandler(int sig, siginfo_t* info, void *context) {
#ifdef TRANSSTATS
  FILE *fp;
  if ((fp = fopen("/tmp/client_stats.txt", "a+")) == NULL) {
    exit(-1);
  }
  fprintf(fp, "******  Transaction Stats   ******\n");
  fprintf(fp, "myIpAddr = %x\n", myIpAddr);
  fprintf(fp, "numTransAbort = %d\n", numTransAbort);
  fprintf(fp, "numTransCommit = %d\n", numTransCommit);
  fprintf(fp, "nchashSearch = %d\n", nchashSearch);
  fprintf(fp, "nmhashSearch = %d\n", nmhashSearch);
  fprintf(fp, "nprehashSearch = %d\n", nprehashSearch);
  fprintf(fp, "ndirtyCacheObj = %d\n", ndirtyCacheObj);
  fprintf(fp, "nRemoteReadSend = %d\n", nRemoteSend);
  fprintf(fp, "nSoftAbort = %d\n", nSoftAbort);
  fprintf(fp, "bytesSent = %d\n", bytesSent);
  fprintf(fp, "bytesRecv = %d\n", bytesRecv);
  fprintf(fp, "totalObjSize= %d\n", totalObjSize);
  fprintf(fp, "**********************************\n");
  fflush(fp);
  fclose(fp);
  exit(0);
#endif
}
*/

void transStatsHandler(int sig, siginfo_t* info, void *context) {
#ifdef TRANSSTATS
  printf("******  Transaction Stats   ******\n");
  printf("myIpAddr = %x\n", myIpAddr);
  printf("numTransCommit = %d\n", numTransCommit);
  printf("numTransAbort = %d\n", numTransAbort);
  printf("nchashSearch = %d\n", nchashSearch);
  printf("nmhashSearch = %d\n", nmhashSearch);
  printf("nprehashSearch = %d\n", nprehashSearch);
  printf("ndirtyCacheObj = %d\n", ndirtyCacheObj);
  printf("nRemoteReadSend = %d\n", nRemoteSend);
  printf("nSoftAbort = %d\n", nSoftAbort);
  printf("bytesSent = %d\n", bytesSent);
  printf("bytesRecv = %d\n", bytesRecv);
  printf("totalObjSize= %d\n", totalObjSize);
  printf("sendRemoteReq= %d\n", sendRemoteReq);
  printf("getResponse= %d\n", getResponse);
  printf("**********************************\n");
  fflush(stdout);
  exit(0);
#endif

#ifdef RECOVERYSTATS
  fflush(stdout);
  exit(0);
#endif
}

void handle() {
#if defined(TRANSSTATS) || defined(RECOVERYSTATS)
  struct sigaction siga;
  siga.sa_handler = NULL;
  siga.sa_flags = SA_SIGINFO;
  siga.sa_flags = 0;
  siga.sa_sigaction = &transStatsHandler;
  sigemptyset(&siga.sa_mask);
  sigaction(SIGUSR1, &siga, 0);
#endif
}
/*

   double getMax(double *array, int size) {
   int i;
   double max = array[0];
   for(i = 0; i < size; i++) { // for 2 MCS
    if(max <= array[i])
      max = array[i];
   }
   return max;
   }

   double getMin(double *array, int size) {
   int i;
   double min = array[0];
   for(i = 0; i < size; i++) { //for 2 MCs
    if(min > array[i])
      min = array[i];
   }
   return min;
   }

   int getthreadid() {
   int val;
   if(((128<<24)|(195<<16)|(175<<8)|84) == myIpAddr)
    val = 0;
   else if(((128<<24)|(195<<16)|(175<<8)|86) == myIpAddr)
    val = 1;
   else if(((128<<24)|(195<<16)|(175<<8)|87) == myIpAddr)
    val = 2;
   else if(((128<<24)|(195<<16)|(175<<8)|88) == myIpAddr)
    val = 3;
   else
    val = 4;
   printf("threadid/mid = %d\n", val);
   return val;
   }

   double getfast(int siteid, int threadid) {
   int i, j, k;
   double fast = 0.0;
   //for(i = 0; i < 2; i++) { // for 2 MC
   for(i = 0; i < 5; i++) { // for 5 MC
    if(i == threadid)
      continue;
    for(k= 0; k<countstats[i]; k++) {
      if(fast < threadstats[i][siteid][k])
        fast = threadstats[i][siteid][k];
    }
   }
   return fast;
   }

   void sortascending() {
   int i;
   for(i = 0 ; i < 5; i++) {

   }
   }

   void bubblesort() {
   const int size = 5; // 5MCS
   int siteid;
   for(siteid = 0; siteid < 15; siteid++) {
    int k;
    for(k=0; k<counttransCommit; k++) {
      int i;
      for(i=0; i < size-1; i++) {
        int j;
        for(j=0; j < size-1-i; j++) {
          if(threadstats[j][siteid][k] > threadstats[j+1][siteid][k]) {
            double temp;
            temp = threadstats[j+1][siteid][k];
            threadstats[j+1][siteid][k] = threadstats[j][siteid][k];
            threadstats[j][siteid][k] = temp;
          }
        }
      } //end of sorting
    } // end for each transaction
   } // end for each siteid
   }

   double avgofthreads(int siteid, int threadid) {
   double total = 0.0;
   int k;
   for(k = 0; k<counttransCommit; k++) {
    total += threadstats[threadid][siteid][k];
   }
   double avg = 0.0;
   avg = total/counttransCommit;
   return avg;
   }

   double avgfast(int siteid, int threadid) {
   int i, j, k;
   double fast;
   for(k = 0; k<counttransCommit; k++) {
    fast = 0.0;
    for(i = 0; i <5; i++) { //for 5 mC
      if(i == threadid)
        continue;
      if(fast < threadstats[i][siteid][k]) {
        fast = threadstats[i][siteid][k];
      }
    }
    avgfasttime[k] = fast;
   }
   double total= 0.0;
   for(k = 0; k<counttransCommit; k++) {
    total += avgfasttime[k];
   }
   return (total/counttransCommit);
   }

   double avgslow(int siteid, int threadid) {
   int i, j, k;
   double slow;
   for(k = 0; k<counttransCommit; k++) {
    slow = 1.0;
    for(i = 0; i < 2; i++) { //for 2 mC
      if(i == threadid)
        continue;
      if(slow > threadstats[i][siteid][k]) {
        slow = threadstats[i][siteid][k];
      }
    }
    avgslowtime[k] =  slow;
   }
   double total= 0.0;
   for(k = 0; k<counttransCommit; k++) {
    total += avgslowtime[k];
   }
   return (total/counttransCommit);
   }

   double getslowest(int siteid, int threadid) {
   int i, j, k;
   double slow = 1.0;
   //for(i = 0; i < 2; i++) { // for 2 MC
   for(i = 0; i < 5; i++) { // for 5 MC
    if(i == threadid)
      continue;
    for(k= 0; k<countstats[i]; k++) {
      if(slow > threadstats[i][siteid][k]) {
        slow = threadstats[i][siteid][k];
      }
    }
   }
   return slow;
   }

   double getavg(int siteid, int threadid) {
   double total=0.0;
   int i, j, k;
   int totalcount = 0;
   //for(i = 0; i < 2; i++) { //for 2 MC
   for(i = 0; i < 5; i++) { //for 5 MC
    if(i == threadid)
      continue;
    for(k= 0; k<countstats[i]; k++) {
      total += threadstats[i][siteid][k];
    }
    totalcount +=countstats[i];
   }
   double avg = total/totalcount;
   return avg;
   }

   double getavgperthd(int siteid, int threadid) {
   double total=0.0;
   int i, j, k;
   for(k= 0; k<countstats[threadid]; k++) {
    total += threadstats[threadid][siteid][k];
   }
   double avg = total/countstats[threadid];
   return avg;
   }
 */
