#ifndef INLINESTM_H
#define INLINESTM_H
#ifdef DELAYCOMP

#ifndef READSET
#define CHECKREADS(x) 0
#endif

#define LIGHTWEIGHTCOMMIT(commitmethod, primitives, locals, params, label) \
  if (GETLOCKS()||CHECKREADS()) {					\
    if (unlikely(needtocollect)) checkcollect(&___locals___);		\
    goto label;								\
  }									\
  ptrstack.maxcount=0;							\
  primstack.count=0;							\
  branchstack.count=0;							\
  commitmethod(params, locals, primitives);				\
  RELEASELOCKS();							\
  FREELIST();

#ifdef READSET
static inline int CHECKREADS() {
  rdchashlistnode_t *rd_curr=rd_c_list;
  int retval=0;
  rdchashlistnode_t *ptr=rd_c_table;
  rdchashlistnode_t *top=&ptr[rd_c_size];

  while(likely(rd_curr!=NULL)) {
    unsigned int version=rd_curr->version;
    struct ___Object___ * objptr=rd_curr->key;
    objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t));
    if(likely(header->lock>0)) {//doesn't matter what type of lock...
      if(unlikely(version!=header->version)) {
	retval=1;break;
      }
    } else {
      if(likely(version==header->version)) {
	dchashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)objptr) & dc_c_mask)>>4];
	do {
	  if(node->key == objptr) {
	    goto nextloop;
	  }
	  node = node->next;
	} while(node!=NULL);
	retval=1;break;
      }
    }
  nextloop:
    if (likely(rd_curr>=ptr&&rd_curr<top)) {
      //zero in list
      rd_curr->key=NULL;
      rd_curr->next=NULL;
    }
    rd_curr=rd_curr->lnext;
  }

  if (unlikely(retval)) {
    while(likely(rd_curr!=NULL)) {
      if (likely(rd_curr>=ptr&&rd_curr<top)) {
	//zero in list
	rd_curr->key=NULL;
	rd_curr->next=NULL;
      }
      rd_curr=rd_curr->lnext;
    }
    while(rd_c_structs->next!=NULL) {
      rdcliststruct_t *next=rd_c_structs->next;
      free(rd_c_structs);
      rd_c_structs=next;
    }
    rd_c_structs->num = 0;
    rd_c_numelements = 0;
    rd_c_list=NULL;
    
    lwreset(NULL);
    return 1;
  }

  while(rd_c_structs->next!=NULL) {
    rdcliststruct_t *next=rd_c_structs->next;
    free(rd_c_structs);
    rd_c_structs=next;
  }
  rd_c_structs->num = 0;
  rd_c_numelements = 0;
  rd_c_list=NULL;

  return 0;
}
#endif

static inline void FREELIST() {
  dchashlistnode_t *ptr = dc_c_table;
  dchashlistnode_t *top=&ptr[dc_c_size];
  dchashlistnode_t *tmpptr=dc_c_list;
  while(tmpptr!=NULL) {
    dchashlistnode_t *next=tmpptr->lnext;
    if (tmpptr>=ptr&&tmpptr<top) {
      /*zero in list	*/
      tmpptr->key=NULL;
      tmpptr->next=NULL;
    }
    tmpptr=next;
  }
  while(dc_c_structs->next!=NULL) {
    dcliststruct_t *next=dc_c_structs->next;
    free(dc_c_structs);
    dc_c_structs=next;
  }
  dc_c_structs->num = 0;
  dc_c_numelements = 0;
  dc_c_list=NULL;
}

static inline void RELEASELOCKS() {
  dchashlistnode_t *dc_curr = dc_c_list;
  while(likely(dc_curr!=NULL)) {
    struct ___Object___ * objptr=dc_curr->key;
    objheader_t *header=&((objheader_t *)objptr)[-1];
#ifdef STMARRAY
    if (objptr->type>=NUMCLASSES) {
      rwwrite_unlock(&header->lock);
    } else {
#endif
      write_unlock(&header->lock);
#ifdef STMARRAY
    }
#endif
    dc_curr=dc_curr->lnext;
  }
  primstack.count=0;
  ptrstack.count=0;
  branchstack.count=0;				      
}

static inline int GETLOCKS() {
  dchashlistnode_t *dc_curr = dc_c_list;
  while(likely(dc_curr!=NULL)) {
    struct ___Object___ * objptr=dc_curr->key;
    objheader_t *header=&((objheader_t *)objptr)[-1];
#ifdef STMARRAY
    if (objptr->type>=NUMCLASSES) {
      if (unlikely(!rwwrite_trylock(&header->lock))) {
#ifdef READSET
	rd_t_chashreset();
#endif
	lwreset(dc_curr);
	return 1;
      }
    } else 
#endif
    if(unlikely(!write_trylock(&header->lock))) {
#ifdef READSET
      rd_t_chashreset();
#endif
      lwreset(dc_curr);
      return 1;
    }
    dc_curr=dc_curr->lnext;
  }
  return 0;
}

#endif
#endif
