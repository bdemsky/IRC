Choosing where to place call to manual prefetching 
===================================================
Since do One move a s per the boundaries of the player

Choice 0
========
1. Prefetch the entire land object

Choice A:
=========
1. prefetch the entire block around player's current position in the beginning
2. In every round keep prefetching the block around the player's current position
   and prefetching the block around the players's goal position when goal is set
3. Do not prefetch if goal is reset to (x,y) (-1,-1)

Choice B:
=========
1. prefetch the entire block around player's current position in the beginning
2. In every round prefetching the block around the players's goal position 
   whenever a goal is set  and if goal's (tx, ty) distance from source is more
   than the boundary BLOCK i.e if(tx - currx > BLOCK || ty - curry > BLOCK)
   then prefetch

Choice C:
=========
1. prefetch the entire block around player's current position in the beginning
2. In every round check if goal is reset => no prefetching
   In every round if player's curr position is reset and its distance is
   greater than old(x,y) position => prefetch area around new position
   In every round if player's goal distance is greater than source then prefetch 
   the area around new goal position



TODO
====
1. Find the trans commit and trans aborts per machine (write a script)
2. Remember that the execution is not deterministic
