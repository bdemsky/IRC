#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__

struct SESE {
  
};

struct SESE* mlpInit();

void mlpEnqueue   ( struct SESE* sese );
void mlpBlock     ( struct SESE* sese );
void mlpNotifyExit( struct SESE* sese );

#endif /* __MLP_RUNTIME__ */
