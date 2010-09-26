/*
 * mlp_lock.h
 *
 *  Created on: Sep 25, 2010
 *      Author: stephey
 */

//NOTE these files are the fake locks so I can test without compiling entire compiler

#ifndef MLP_LOCK_H_
#define MLP_LOCK_H_

long LOCKXCHG(long * ptr1, long ptr2);

#endif /* MLP_LOCK_H_ */
