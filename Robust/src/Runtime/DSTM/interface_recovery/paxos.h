/* Paxo Algorithm: 
 * Executes when the known leader has failed.  
 * Guarantees consensus on next leader among all live hosts.  */

#ifndef _PAXOS_H_
#define _PAXOS_H_

#include "dstm.h"

#define WAIT_TIME 3

/*********************************
 * Paxos Messages
 *******************************/
#define PAXOS_PREPARE							40	
#define PAXOS_PREPARE_REJECT			41
#define PAXOS_PREPARE_OK				  42
#define PAXOS_ACCEPT							43
#define PAXOS_ACCEPT_REJECT				44
#define PAXOS_ACCEPT_OK						45
#define PAXOS_LEARN								46
#define DELETE_LEADER							47


/* Paxo's algorithm */

/* coordinator side */
int paxos(int* hostIpAddrs,int* liveHosts,unsigned int myIpAddr,int numHostsInSystem,int numLiveHostsInSystem);
int paxosPrepare();
int paxosAccept();
void paxosLearn();

/* participant side */
void paxosPrepare_receiver(int acceptfd);
void paxosAccept_receiver(int acceptfd);
int paxosLearn_receiver(int acceptfd);

#endif
