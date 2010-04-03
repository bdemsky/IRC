/* Paxo Algorithm: 
 * Executes when the known leader has failed.  
 * Guarantees consensus on next leader among all live hosts.  */

#include "paxos.h"

/******************************
 * Global variables for Paxos
 ******************************/
int n_a;
unsigned int v_a;
int n_h;
int my_n;
unsigned int leader;
unsigned int origleader;
unsigned int temp_v_a;
int paxosRound;

unsigned int myIpAddr;
int numHostsInSystem;
int numLiveHostsInSystem;

int* hostIpAddrs;
int* liveHosts;

int paxos(int* hostIp,int* hosts,unsigned int myIp,int nHosts,int nLiveHosts)
{
	int origRound = paxosRound;
	origleader = leader;
	int ret = -1;

  numHostsInSystem = nHosts;
  numLiveHostsInSystem =nLiveHosts;
  myIpAddr = myIp;
  hostIpAddrs = hostIp;
  liveHosts = hosts;

	do {
		ret = paxosPrepare();		// phase 1
		if (ret == 1) {
			ret = paxosAccept();	// phase 2
			if (ret == 1) {
				paxosLearn();				// phase 3
				break;
			}
		}
		// Paxos not successful; wait and retry if new leader is not yet slected
		sleep(WAIT_TIME);		
		if(paxosRound != origRound)
			break;
	} while (ret == -1);

#ifdef DEBUG
	printf("\n>> Debug : Leader : [%s]\t[%u]\n", midtoIPString(leader),leader);
#endif

	return leader;
}

int paxosPrepare()
{
  struct sockaddr_in remoteAddr;
	char control;
	int remote_n;
	int remote_v;
	int tmp_n = -1;
	int cnt = 0;
	int sd;
	int i;
	temp_v_a = v_a;
	my_n = n_h + 1;

#ifdef DEBUG
	printf("[Prepare]...\n");
#endif

	temp_v_a = myIpAddr;	// if no other value is proposed, make this machine the new leader

	for (i = 0; i < numHostsInSystem; ++i) {
		control = PAXOS_PREPARE;
		if(!liveHosts[i]) 
			continue;

		if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
			printf("paxosPrepare(): socket create error\n");
			continue;
		} else {
      bzero(&remoteAddr, sizeof(remoteAddr));
      remoteAddr.sin_family = AF_INET;
      remoteAddr.sin_port = htons(LISTEN_PORT);
      remoteAddr.sin_addr.s_addr = htonl(hostIpAddrs[i]);
//        printf("%s -> open sd : %d to %s\n",__func__,sd,midtoIPString(hostIpAddrs[i]));

      if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
        printf("%s -> socket connect error\n",__func__);
        continue;
      }

  		send_data(sd, &control, sizeof(char)); 	
	  	send_data(sd, &my_n, sizeof(int));
  		int timeout = recv_data(sd, &control, sizeof(char));
	  	if ((sd == -1) || (timeout < 0)) {
		  	continue;
  		}

	  	switch (control) {
		  	case PAXOS_PREPARE_OK:
			  	cnt++;
  				recv_data(sd, &remote_n, sizeof(int));
	  			recv_data(sd, &remote_v, sizeof(int));
		  		if(remote_v != origleader) {
			  		if (remote_n > tmp_n) {
				  		tmp_n = remote_n;
					  	temp_v_a = remote_v;
  					}
	  			}
		  		break;
  			case PAXOS_PREPARE_REJECT:
	  		 	break;
		  }
      close(sd);
    }  
	}

	if (cnt >= (numLiveHostsInSystem / 2)) {		// majority of OK replies
		return 1;
		}
		else {
			return -1;
		}
}

int paxosAccept()
{
  struct sockaddr_in remoteAddr;
	char control;
	int i;
	int cnt = 0;
	int sd;
	int remote_v = temp_v_a;

	for (i = 0; i < numHostsInSystem; ++i) {
		control = PAXOS_ACCEPT;
	  
    if(!liveHosts[i]) 
			continue;

    if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
			printf("paxosPrepare(): socket create error\n");
			continue;
		} else {                                                                             		
      bzero(&remoteAddr, sizeof(remoteAddr));                                                
      remoteAddr.sin_family = AF_INET;
      remoteAddr.sin_port = htons(LISTEN_PORT);
      remoteAddr.sin_addr.s_addr = htonl(hostIpAddrs[i]);

      if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
        printf("%s -> socket connect error\n",__func__);
        continue;
      }
    
      send_data(sd, &control, sizeof(char));
	  	send_data(sd, &my_n, sizeof(int));
		  send_data(sd, &remote_v, sizeof(int));

  		int timeout = recv_data(sd, &control, sizeof(char));
	  	if (timeout < 0) {
        close(sd);
			  continue;  
  		}

  		switch (control) {
	  		case PAXOS_ACCEPT_OK:
		  		cnt++;
			  	break;
  			case PAXOS_ACCEPT_REJECT:
	  			break;
		  }
      close(sd);
    }
	}

	if (cnt >= (numLiveHostsInSystem / 2)) {
		return 1;
	}
	else {
		return -1;
	}
}

void paxosLearn()
{
	char control;
  struct sockaddr_in remoteAddr;
	int sd;
	int i;

#ifdef DEBUG
	printf("[Learn]...\n");
#endif

	control = PAXOS_LEARN;

	for (i = 0; i < numHostsInSystem; ++i) {
		if(!liveHosts[i]) 
			continue;
		if(hostIpAddrs[i] == myIpAddr)
		{
			leader = v_a;
			paxosRound++;
			continue;
		}

    if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
			printf("paxosPrepare(): socket create error\n");
			continue;
		} else {
      bzero(&remoteAddr, sizeof(remoteAddr));
      remoteAddr.sin_family = AF_INET;
      remoteAddr.sin_port = htons(LISTEN_PORT);
      remoteAddr.sin_addr.s_addr = htonl(hostIpAddrs[i]);
//        printf("%s -> open sd : %d to %s\n",__func__,sd,midtoIPString(hostIpAddrs[i]));

      if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
        printf("%s -> socket connect error\n",__func__);
			  continue;
      }

		  send_data(sd, &control, sizeof(char));
  		send_data(sd, &v_a, sizeof(int));

      close(sd);    
    }  
	}
}

void paxosPrepare_receiver(int acceptfd)
{
  int val;
  char control;

  recv_data((int)acceptfd, &val, sizeof(int));
	
  if (val <= n_h) {
	  control = PAXOS_PREPARE_REJECT;
		send_data((int)acceptfd, &control, sizeof(char));
	}
	else {
		n_h = val;
		control = PAXOS_PREPARE_OK;
                    
		send_data((int)acceptfd, &control, sizeof(char));
		send_data((int)acceptfd, &n_a, sizeof(int));
		send_data((int)acceptfd, &v_a, sizeof(int));
	}
}

void paxosAccept_receiver(int acceptfd) 
{
  int n,v;
  char control;

  recv_data((int)acceptfd, &n, sizeof(int));
	recv_data((int)acceptfd, &v, sizeof(int));
	
  if (n < n_h) {
	  control = PAXOS_ACCEPT_REJECT;
		send_data((int)acceptfd, &control, sizeof(char));
	}
	else {
		n_a = n;
		v_a = v;
		n_h = n;
		control = PAXOS_ACCEPT_OK;
		send_data((int)acceptfd, &control, sizeof(char));
	}
}


int paxosLearn_receiver(int acceptfd)
{
  int v;

	recv_data((int)acceptfd, &v, sizeof(int));
  leader = v_a;
	paxosRound++;
  v_a = 0;

  return leader;
}


