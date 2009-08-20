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

__attribute__((always_inline)) inline void send_msg_1 (int targetcore, 
		                                                   int n0) {
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

__attribute__((always_inline)) inline void send_msg_2 (int targetcore, 
		                                                   int n0, 
																											 int n1) {
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

__attribute__((always_inline)) inline void send_msg_3 (int targetcore, 
		                                                   int n0, 
																											 int n1, 
																											 int n2) {
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

__attribute__((always_inline)) inline void send_msg_4 (int targetcore, 
		                                                   int n0, 
																											 int n1, 
																											 int n2, 
																											 int n3) {
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

__attribute__((always_inline)) inline void send_msg_5 (int targetcore, 
		                                                   int n0, 
																											 int n1, 
																											 int n2, 
																											 int n3, 
																											 int n4) {
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

__attribute__((always_inline)) inline void send_msg_6 (int targetcore, 
		                                                   int n0, 
																											 int n1, 
																											 int n2, 
																											 int n3, 
																											 int n4, 
																											 int n5) {
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

__attribute__((always_inline)) inline void cache_msg_2 (int targetcore, 
		                                                    int n0, 
																												int n1) {
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

__attribute__((always_inline)) inline void cache_msg_3 (int targetcore, 
		                                                    int n0, 
																												int n1, 
																												int n2) {
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

__attribute__((always_inline)) inline void cache_msg_4 (int targetcore, 
		                                                    int n0, 
																												int n1, 
																												int n2, 
																												int n3) {
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

__attribute__((always_inline)) inline void cache_msg_5 (int targetcore, 
		                                                    int n0, 
																												int n1, 
																												int n2, 
																												int n3, 
																												int n4) {
  // cache this msg
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xdede);
#endif
  isMsgHanging = true;
  // cache the msg in outmsgdata and send it later
  // msglength + target core + msg
  outmsgdata[outmsglast++] = 5;
  outmsgdata[outmsglast++] = targetcore;
  outmsgdata[outmsglast++] = n0;
  outmsgdata[outmsglast++] = n1;
  outmsgdata[outmsglast++] = n2;
  outmsgdata[outmsglast++] = n3;
  outmsgdata[outmsglast++] = n4;
}


__attribute__((always_inline)) inline void cache_msg_6 (int targetcore, 
		                                                    int n0, 
																												int n1, 
																												int n2, 
																												int n3, 
																												int n4, 
																												int n5) {
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

bool getreadlock(void * ptr) {
  int targetcore = 0;
  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
    int deny = 0;
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
	deny = processlockrequest(0, lock2require, (int)ptr, BAMBOO_NUM_OF_CORE, BAMBOO_NUM_OF_CORE, false);
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa018);
		}
	}
    return true;
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, LOCKREQUEST, 0, (int)ptr, 
				       lock2require, BAMBOO_NUM_OF_CORE);
  }
  return true;
}

bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache);
bool getwritelock_I_r(void* lock, void* redirectlock, int core, bool cache);

void releasereadlock(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

  if(targetcore == BAMBOO_NUM_OF_CORE) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa019);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value--;
    }
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, LOCKRELEASE, 0, (int)ptr, reallock);
  }
}

// redirected lock request
bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  int targetcore = 0;
  
  if(core == BAMBOO_NUM_OF_CORE) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }  
  targetcore = ((int)redirectlock >> 5) % BAMBOO_TOTALCORE;
  
  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
    int deny = processlockrequest(0, (int)redirectlock, (int)ptr, BAMBOO_NUM_OF_CORE, core, cache);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(core == BAMBOO_NUM_OF_CORE) {
			if(lockobj == (int)ptr) {
				if(deny) {
					lockresult = 0;
				} else {
					lockresult = 1;
					RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
				}
				lockflag = true;
#ifndef INTERRUPT
				reside = true;
#endif
			} else {
				// conflicts on lockresults
				BAMBOO_EXIT(0xa01a);
			}
			return true;
		} else {
			// send lock grant/deny request to the root requiring core
			// check if there is still some msg on sending
			if((!cache) || (cache && !isMsgSending)) {
				send_msg_4(core, deny==1?REDIRECTDENY:REDIRECTGROUNT, 0, 
						       (int)ptr, (int)redirectlock);
			} else {
				cache_msg_4(core, deny==1?REDIRECTDENY:REDIRECTGROUNT, 0, 
						        (int)ptr, (int)redirectlock);
			}
		}
	}
  } else {
	// redirect the lock request
	// for 32 bit machine, the size is always 6 words
	if((!cache) || (cache && !isMsgSending)) {
		send_msg_6(targetcore, REDIRECTLOCK, 0, (int)ptr, lock2require, 
				       core, BAMBOO_NUM_OF_CORE);
	} else {
		cache_msg_6(targetcore, REDIRECTLOCK, 0, (int)ptr, lock2require, 
				        core, BAMBOO_NUM_OF_CORE);
	}
  }
  return true;
}

// not reentrant
bool getwritelock(void * ptr) {
  int targetcore = 0;

  // for 32 bit machine, the size is always 5 words
  //int msgsize = 5;

  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe551);
  BAMBOO_DEBUGPRINT_REG(lockobj);
  BAMBOO_DEBUGPRINT_REG(lock2require);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
    int deny = 0;
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
	deny = processlockrequest(1, lock2require, (int)ptr, BAMBOO_NUM_OF_CORE, BAMBOO_NUM_OF_CORE, false);
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe555);
    BAMBOO_DEBUGPRINT_REG(lockresult);
#endif
    if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa01b);
		}
	}
    return true;
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, LOCKREQUEST, 1, (int)ptr, lock2require, 
				       BAMBOO_NUM_OF_CORE);
  }
  return true;
}

void releasewritelock(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe661);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa01c);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
    }
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, LOCKRELEASE, 1, (int)ptr, reallock);
  }
}

bool getwritelock_I(void * ptr) {
  int targetcore = 0;
  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe561);
  BAMBOO_DEBUGPRINT_REG(lockobj);
  BAMBOO_DEBUGPRINT_REG(lock2require);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
	int deny = processlockrequest(1, (int)lock2require, (int)ptr, BAMBOO_NUM_OF_CORE, BAMBOO_NUM_OF_CORE, false);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0);
#endif
			} else {
				lockresult = 1;
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(1);
#endif
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa01e);
		}
		return true;
	}
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, LOCKREQUEST, 1, (int)ptr, lock2require, 
				       BAMBOO_NUM_OF_CORE);
  }
  return true;
}

// redirected lock request
bool getwritelock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  int targetcore = 0;

  if(core == BAMBOO_NUM_OF_CORE) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }
  targetcore = ((int)redirectlock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe571);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG((int)redirectlock);
  BAMBOO_DEBUGPRINT_REG(core);
  BAMBOO_DEBUGPRINT_REG((int)cache);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif


  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
	int deny = processlockrequest(1, (int)redirectlock, (int)ptr, BAMBOO_NUM_OF_CORE, core, cache);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(core == BAMBOO_NUM_OF_CORE) {
			if(lockobj == (int)ptr) {
				if(deny) {
					lockresult = 0;
				} else {
					lockresult = 1;
					RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
				}
				lockflag = true;
#ifndef INTERRUPT
				reside = true;
#endif
			} else {
				// conflicts on lockresults
				BAMBOO_EXIT(0xa01f);
			}
			return true;
		} else {
			// send lock grant/deny request to the root requiring core
			// check if there is still some msg on sending
			if((!cache) || (cache && !isMsgSending)) {
				send_msg_4(core, deny==1?REDIRECTDENY:REDIRECTGROUNT, 1, 
							     (int)ptr, (int)redirectlock);
			} else {
				cache_msg_4(core, deny==1?REDIRECTDENY:REDIRECTGROUNT, 1, 
						        (int)ptr, (int)redirectlock);
			}
		}
	}
  } else {
	// redirect the lock request
	// for 32 bit machine, the size is always 6 words
	if((!cache) || (cache && !isMsgSending)) {
		send_msg_6(targetcore, REDIRECTLOCK, 1, (int)ptr, (int)redirectlock, 
				       core, BAMBOO_NUM_OF_CORE);
	} else {
		cache_msg_6(targetcore, REDIRECTLOCK, 1, (int)ptr, (int)redirectlock, 
				        core, BAMBOO_NUM_OF_CORE);
	}
  }
  return true;
}

void releasewritelock_I(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe681);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa020);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
    }
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, LOCKRELEASE, 1, (int)ptr, reallock);
  }
}

void releasewritelock_I_r(void * lock, void * redirectlock) {
  int targetcore = 0;
  int reallock = (int)lock;
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe691);
  BAMBOO_DEBUGPRINT_REG((int)lock);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa021);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe692);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
      lockvalue->value++;
	  lockvalue->redirectlock = (int)redirectlock;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
    }
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, REDIRECTRELEASE, 1, (int)lock, (int)redirectlock);
  }
}

/* this function is to process lock requests. 
 * can only be invoked in receiveObject() */
// if return -1: the lock request is redirected
//            0: the lock request is approved
//            1: the lock request is denied
__attribute__((always_inline)) int processlockrequest(int locktype, int lock, int obj, int requestcore, int rootrequestcore, bool cache) {
  int deny = 0;
  if( ((lock >> 5) % BAMBOO_TOTALCORE) != BAMBOO_NUM_OF_CORE ) {
	  // the lock should not be on this core
#ifndef TILERA
	  BAMBOO_DEBUGPRINT_REG(requestcore);
	  BAMBOO_DEBUGPRINT_REG(lock);
	  BAMBOO_DEBUGPRINT_REG(BAMBOO_NUM_OF_CORE);
#endif
	  BAMBOO_EXIT(0xa017);
  }
  if(!RuntimeHashcontainskey(locktbl, lock)) {
	  // no locks for this object
	  // first time to operate on this shared object
	  // create a lock for it
	  // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe110);
#endif
#endif
	  if(locktype == 0) {
		  lockvalue->value = 1;
	  } else {
		  lockvalue->value = -1;
	  }
	  RuntimeHashadd_I(locktbl, lock, (int)lockvalue);
  } else {
	  int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe111);
#endif
#endif
	  RuntimeHashget(locktbl, lock, &rwlock_obj);
	  lockvalue = (struct LockValue *)(rwlock_obj);
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT_REG(lockvalue->redirectlock);
#endif
#endif
	  if(lockvalue->redirectlock != 0) {
		  // this lock is redirected
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe112);
#endif
#endif
		  if(locktype == 0) {
			  getreadlock_I_r((void *)obj, (void *)lockvalue->redirectlock, rootrequestcore, cache);
		  } else {
			  getwritelock_I_r((void *)obj, (void *)lockvalue->redirectlock, rootrequestcore, cache);
		  }
		  return -1;  // redirected
	  } else {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
		  if(0 == lockvalue->value) {
			  if(locktype == 0) {
				  lockvalue->value = 1;
			  } else {
				  lockvalue->value = -1;
			  }
		  } else if((lockvalue->value > 0) && (locktype == 0)) {
			  // read lock request and there are only read locks
			  lockvalue->value++;
		  } else {
			  deny = 1;
		  }
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
	  }
  }
  return deny;
}

__attribute__((always_inline)) void processlockrelease(int locktype, int lock, int redirectlock, bool isredirect) {
	if(!RuntimeHashcontainskey(locktbl, lock)) {
    // no locks for this object, something is wrong
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(lock);
#endif
		BAMBOO_EXIT(0xa00b);
	} else {
		int rwlock_obj = 0;
		struct LockValue * lockvalue = NULL;
		RuntimeHashget(locktbl, lock, &rwlock_obj);
		lockvalue = (struct LockValue*)(rwlock_obj);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe884);
		BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
		if(locktype == 0) {
			lockvalue->value--;
		} else {
			lockvalue->value++;
		}
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
		if(isredirect) {
			lockvalue->redirectlock = redirectlock;
		}
	}
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

// output the profiling data
void outputProfileData() {
#ifdef USEIO
  FILE * fp;
  char fn[50];
  int self_y, self_x;
  char c_y, c_x;
  int i;
  int totaltasktime = 0;
  int preprocessingtime = 0;
  int objqueuecheckingtime = 0;
  int postprocessingtime = 0;
  //int interruptiontime = 0;
  int other = 0;
  int averagetasktime = 0;
  int tasknum = 0;

  for(i = 0; i < 50; i++) {
    fn[i] = 0;
  }

  calCoords(corenum, &self_y, &self_x);
  c_y = (char)self_y + '0';
  c_x = (char)self_x + '0';
  strcat(fn, "profile_");
  strcat(fn, &c_x);
  strcat(fn, "_");
  strcat(fn, &c_y);
  strcat(fn, ".rst");

  if((fp = fopen(fn, "w+")) == NULL) {
    fprintf(stderr, "fopen error\n");
    return;
  }

  fprintf(fp, "Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    int duration = tmpTInfo->endTime - tmpTInfo->startTime;
    fprintf(fp, "%s, %d, %d, %d, %d", tmpTInfo->taskName, tmpTInfo->startTime, tmpTInfo->endTime, duration, tmpTInfo->exitIndex);
	// summarize new obj info
	if(tmpTInfo->newObjs != NULL) {
		struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
		struct RuntimeIterator * iter = NULL;
		while(0 == isEmpty(tmpTInfo->newObjs)) {
			char * objtype = (char *)(getItem(tmpTInfo->newObjs));
			if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
				int num = 0;
				RuntimeHashget(nobjtbl, (int)objtype, &num);
				RuntimeHashremovekey(nobjtbl, (int)objtype);
				num++;
				RuntimeHashadd(nobjtbl, (int)objtype, num);
			} else {
				RuntimeHashadd(nobjtbl, (int)objtype, 1);
			}
			//fprintf(stderr, "new obj!\n");
		}

		// output all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			fprintf(fp, ", %s, %d", objtype, num);
		}
	}
	fprintf(fp, "\n");
    if(strcmp(tmpTInfo->taskName, "tpd checking") == 0) {
      preprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "post task execution") == 0) {
      postprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "objqueue checking") == 0) {
      objqueuecheckingtime += duration;
    } else {
      totaltasktime += duration;
      averagetasktime += duration;
      tasknum++;
    }
  }

  if(taskInfoOverflow) {
    fprintf(stderr, "Caution: task info overflow!\n");
  }

  other = totalexetime - totaltasktime - preprocessingtime - postprocessingtime;
  averagetasktime /= tasknum;

  fprintf(fp, "\nTotal time: %d\n", totalexetime);
  fprintf(fp, "Total task execution time: %d (%f%%)\n", totaltasktime, ((double)totaltasktime/(double)totalexetime)*100);
  fprintf(fp, "Total objqueue checking time: %d (%f%%)\n", objqueuecheckingtime, ((double)objqueuecheckingtime/(double)totalexetime)*100);
  fprintf(fp, "Total pre-processing time: %d (%f%%)\n", preprocessingtime, ((double)preprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Total post-processing time: %d (%f%%)\n", postprocessingtime, ((double)postprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Other time: %d (%f%%)\n", other, ((double)other/(double)totalexetime)*100);

  fprintf(fp, "\nAverage task execution time: %d\n", averagetasktime);

  fclose(fp);
#else
  int i = 0;
  int j = 0;

  BAMBOO_DEBUGPRINT(0xdddd);
  // output task related info
  for(i= 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    char* tmpName = tmpTInfo->taskName;
    int nameLen = strlen(tmpName);
    BAMBOO_DEBUGPRINT(0xddda);
    for(j = 0; j < nameLen; j++) {
      BAMBOO_DEBUGPRINT_REG(tmpName[j]);
    }
    BAMBOO_DEBUGPRINT(0xdddb);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->startTime);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->endTime);
	BAMBOO_DEBUGPRINT_REG(tmpTInfo->exitIndex);
	if(tmpTInfo->newObjs != NULL) {
		struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
		struct RuntimeIterator * iter = NULL;
		while(0 == isEmpty(tmpTInfo->newObjs)) {
			char * objtype = (char *)(getItem(tmpTInfo->newObjs));
			if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
				int num = 0;
				RuntimeHashget(nobjtbl, (int)objtype, &num);
				RuntimeHashremovekey(nobjtbl, (int)objtype);
				num++;
				RuntimeHashadd(nobjtbl, (int)objtype, num);
			} else {
				RuntimeHashadd(nobjtbl, (int)objtype, 1);
			}
		}

		// ouput all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			int nameLen = strlen(objtype);
			BAMBOO_DEBUGPRINT(0xddda);
			for(j = 0; j < nameLen; j++) {
				BAMBOO_DEBUGPRINT_REG(objtype[j]);
			}
			BAMBOO_DEBUGPRINT(0xdddb);
			BAMBOO_DEBUGPRINT_REG(num);
		}
	}
    BAMBOO_DEBUGPRINT(0xdddc);
  }

  if(taskInfoOverflow) {
    BAMBOO_DEBUGPRINT(0xefee);
  }

  // output interrupt related info
  /*for(i = 0; i < interruptInfoIndex; i++) {
       InterruptInfo* tmpIInfo = interruptInfoArray[i];
       BAMBOO_DEBUGPRINT(0xddde);
       BAMBOO_DEBUGPRINT_REG(tmpIInfo->startTime);
       BAMBOO_DEBUGPRINT_REG(tmpIInfo->endTime);
       BAMBOO_DEBUGPRINT(0xdddf);
     }

     if(interruptInfoOverflow) {
       BAMBOO_DEBUGPRINT(0xefef);
     }*/

  BAMBOO_DEBUGPRINT(0xeeee);
#endif
}
#endif  // #ifdef PROFILE

#endif // #ifdef TASK
