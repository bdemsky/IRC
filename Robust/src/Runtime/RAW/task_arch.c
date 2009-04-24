#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"

__attribute__((always_inline)) inline void initialization() {
} // initialization()

__attribute__((always_inline)) inline void initCommunication() {
#ifdef INTERRUPT
  if (corenum < NUMCORES) {
    // set up interrupts
    setup_ints();
    raw_user_interrupts_on();
  }
#endif
}

__attribute__((always_inline)) inline void fakeExecution()  {
  // handle communications
  while(true) {
	  receiveObject();
  }
}

#ifdef USEIO
int main(void) {
#else
void begin() {
#endif // #ifdef USEIO
  run(NULL);
}

__attribute__((always_inline)) inline void terminate()  {
	raw_test_done(1);
}

// helper function to compute the coordinates of a core from the core number
#define calCoords(core_num, coordX, coordY) \
  *(coordX) = (core_num) % raw_get_array_size_x();\
  *(coordY) = core_num / raw_get_array_size_x();

// transfer an object to targetcore
// format: object
inline void transferObject(struct transObjInfo * transObj) {//  __attribute__((always_inline)){
  void * obj = transObj->objptr;
  int type=((int *)obj)[0];
  int targetcore = transObj->targetcore;  

  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  // for 32 bit machine, the size of fixed part is always 3 words
  int msgsize = 3 + transObj->length * 2;
  int i = 0;

  struct ___Object___ * newobj = (struct ___Object___ *)obj;

  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending msg, set sand msg flag
  gdn_send(msgHdr);                     
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0);
#endif
  gdn_send(msgsize);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(msgsize);
#endif
  gdn_send((int)obj);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(obj);
#endif
  for(i = 0; i < transObj->length; ++i) {
    int taskindex = transObj->queues[2*i];
    int paramindex = transObj->queues[2*i+1];
    gdn_send(taskindex);
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(taskindex);
#endif
    gdn_send(paramindex);
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(paramindex);
#endif
  }
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  ++(self_numsendobjs);
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_1 (int targetcore, int n0) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 1;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_2 (int targetcore, int n0, int n1) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 2;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
#endif
  gdn_send(n1);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n1);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_3 (int targetcore, int n0, int n1, int n2) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 3;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
#endif
  gdn_send(n1);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n1);
#endif
  gdn_send(n2);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n2);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_4 (int targetcore, int n0, int n1, int n2, int n3) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 4;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
#endif
  gdn_send(n1);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n1);
#endif
  gdn_send(n2);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n2);
#endif
  gdn_send(n3);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n3);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_5 (int targetcore, int n0, int n1, int n2, int n3, int n4) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 5;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
#endif
  gdn_send(n1);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n1);
#endif
  gdn_send(n2);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n2);
#endif
  gdn_send(n3);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n3);
#endif
  gdn_send(n4);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n4);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void send_msg_6 (int targetcore, int n0, int n1, int n2, int n3, int n4, int n5) {
  // send this msg
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  msglength = 6;

  // get the x-coord and y-coord of the target core
  calCoords(corenum, &self_x, &self_y);
  calCoords(targetcore, &target_x, &target_y);

  // mark to start sending the msg
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msglength, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(n0);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n0);
#endif
  gdn_send(n1);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n1);
#endif
  gdn_send(n2);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n2);
#endif
  gdn_send(n3);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n3);
#endif
  gdn_send(n4);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n4);
#endif
  gdn_send(n5);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(n5);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  // mark to end sending the msg
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
	  // get the msg from outmsgdata[]
	  // length + target + msg
	  outmsgleft = outmsgdata[outmsgindex++];
	  int target = outmsgdata[outmsgindex++];
	  calCoords(target, &target_x, &target_y);
	  // mark to start sending the msg
	  isMsgSending = true;
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                                 self_y, self_x,
                                 target_y, target_x);
	  gdn_send(msgHdr);                           
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xbbbb);
	  BAMBOO_DEBUGPRINT(0xb000 + target);             // targetcore
#endif
	  while(outmsgleft-- > 0) {
		  gdn_send(outmsgdata[outmsgindex++]);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG(outmsgdata[outmsgindex - 1]);
#endif
	  }
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xffff);
#endif
	  // mark to end sending the msg
	  isMsgSending = false;
	  BAMBOO_START_CRITICAL_SECTION_MSG();
	  // check if there are still msg hanging
	  if(outmsgindex == outmsglast) {
		  // no more msgs
		  outmsgindex = outmsglast = 0;
		  isMsgHanging = false;
	  }
	  BAMBOO_CLOSE_CRITICAL_SECTION_MSG();
  }
}

__attribute__((always_inline)) inline void cache_msg_2 (int targetcore, int n0, int n1) {
  // cache this msg
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xdede);
#endif
  isMsgHanging = true;
  // cache the msg in outmsgdata and send it later
  // msglength + target core + msg
  outmsgdata[outmsglast++] = 2;
  outmsgdata[outmsglast++] = targetcore;
  outmsgdata[outmsglast++] = n0;
  outmsgdata[outmsglast++] = n1;
}

__attribute__((always_inline)) inline void cache_msg_3 (int targetcore, int n0, int n1, int n2) {
  // cache this msg
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xdede);
#endif
  isMsgHanging = true;
  // cache the msg in outmsgdata and send it later
  // msglength + target core + msg
  outmsgdata[outmsglast++] = 3;
  outmsgdata[outmsglast++] = targetcore;
  outmsgdata[outmsglast++] = n0;
  outmsgdata[outmsglast++] = n1;
  outmsgdata[outmsglast++] = n2;
}

__attribute__((always_inline)) inline void cache_msg_4 (int targetcore, int n0, int n1, int n2, int n3) {
  // cache this msg
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xdede);
#endif
  isMsgHanging = true;
  // cache the msg in outmsgdata and send it later
  // msglength + target core + msg
  outmsgdata[outmsglast++] = 4;
  outmsgdata[outmsglast++] = targetcore;
  outmsgdata[outmsglast++] = n0;
  outmsgdata[outmsglast++] = n1;
  outmsgdata[outmsglast++] = n2;
  outmsgdata[outmsglast++] = n3;
}

__attribute__((always_inline)) inline void cache_msg_6 (int targetcore, int n0, int n1, int n2, int n3, int n4, int n5) {
  // cache this msg
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xdede);
#endif
  isMsgHanging = true;
  // cache the msg in outmsgdata and send it later
  // msglength + target core + msg
  outmsgdata[outmsglast++] = 6;
  outmsgdata[outmsglast++] = targetcore;
  outmsgdata[outmsglast++] = n0;
  outmsgdata[outmsglast++] = n1;
  outmsgdata[outmsglast++] = n2;
  outmsgdata[outmsglast++] = n3;
  outmsgdata[outmsglast++] = n4;
  outmsgdata[outmsglast++] = n5;
}

__attribute__((always_inline)) inline int receiveMsg() {
  if(gdn_input_avail() == 0) {
#ifdef DEBUG
    if(corenum < NUMCORES) {
      BAMBOO_DEBUGPRINT(0xd001);
    }
#endif
    return -1;
  }
#ifdef PROFILE
  /*if(isInterrupt && (!interruptInfoOverflow)) {
     // BAMBOO_DEBUGPRINT(0xffff);
     interruptInfoArray[interruptInfoIndex] = RUNMALLOC_I(sizeof(struct interrupt_info));
     interruptInfoArray[interruptInfoIndex]->startTime = raw_get_cycle();
     interruptInfoArray[interruptInfoIndex]->endTime = -1;
     }*/
#endif
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xcccc);
#endif
  while((gdn_input_avail() != 0) && (msgdataindex < msglength)) {
    msgdata[msgdataindex] = gdn_receive();
    if(msgdataindex == 0) {
		if(msgdata[0] > 0xc) {
			msglength = 3;
		} else if (msgdata[0] == 0xc) {
			msglength = 1;
		} else if(msgdata[0] > 8) {
			msglength = 4;
		} else if(msgdata[0] == 8) {
			msglength = 6;
		} else if(msgdata[0] > 5) {
			msglength = 2;
		} else if (msgdata[0] > 2) {
			msglength = 4;
		} else if (msgdata[0] == 2) {
			msglength = 5;
		} else if (msgdata[0] > 0) {
			msglength = 4;
		}
    } else if((msgdataindex == 1) && (msgdata[0] == 0)) {
      msglength = msgdata[msgdataindex];
    }
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]);
#endif
    msgdataindex++;
  }
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  return msgdataindex;
}

#ifdef PROFILE
__attribute__((always_inline)) inline void profileTaskStart(char * taskname) {
  if(!taskInfoOverflow) {
	  TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
	  taskInfoArray[taskInfoIndex] = taskInfo;
	  taskInfo->taskName = taskname;
	  taskInfo->startTime = raw_get_cycle();
	  taskInfo->endTime = -1;
	  taskInfo->exitIndex = -1;
	  taskInfo->newObjs = NULL;
  }
}

__attribute__((always_inline)) inline void profileTaskEnd() {
  if(!taskInfoOverflow) {
	  taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	  taskInfoIndex++;
	  if(taskInfoIndex == TASKINFOLENGTH) {
		  taskInfoOverflow = true;
	  }
  }
}
#endif  // #ifdef PROFILE

#endif // #ifdef TASK
