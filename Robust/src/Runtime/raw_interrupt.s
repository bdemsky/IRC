#include <raw_asm.h>

	.text
	.align	2
	.globl	setup_ints
	.ent	setup_ints
setup_ints:	
	# run main program
	# set up switch
#	mtsri	SW_FREEZE, 1
#	la	$8, __sw_main
#	mtsr	SW_PC, $8
#	nop
#	mtsri	SW_FREEZE, 0
	
	# set up dynamic network
	uintoff
	intoff

	# set gdn_cfg
#   la	$8, ((XSIZE-1)<<27)|((YSIZE-1)<<22)|(XOFF<<17)|(YOFF<<12)|(LOG_XSIZE<<9)
	xor $8,$8,$8
#ifdef Y1
	aui $8,$8,(3<<11)|(0 <<6)|(0 <<1)
	ori $8, (0 <<12)|(2<<9)
#elif defined Y2
	aui $8,$8,(3<<11)|(1 <<6)|(0 <<1)
	ori $8, (0 <<12)|(2<<9)
#elif defined Y4
	aui $8,$8,(3<<11)|(3 <<6)|(0 <<1)
	ori $8, (0 <<12)|(2<<9)
#else
	aui $8,$8,(3<<11)|(3 <<6)|(0 <<1)
	ori $8, (0 <<12)|(2<<9)
#endif
	mtsr	GDN_CFG,$8
#	mtsr	PASS,$8

	# set exception vector
    la $3, interrupt_table
#	mtsri PASS, 0xaaa
#	mtsr PASS, $3
    mtsr EX_BASE_ADDR, $3

	# set EX_MASK
	mfsr	$8,EX_MASK
	ori	$8,$8,0x20          # 1 << kVEC_GDN_AVAIL
	mtsr	EX_MASK,$8

	inton
	uinton

	.end	setup_ints


.macro empty_vec fail_code
        mtsri FAIL, \fail_code
1:      b 1b
        nop
        nop
.endm

interrupt_table:

vec_gdn_refill:
        empty_vec 0x2300
vec_gdn_complete:
        empty_vec 0x2301
vec_trace:
        empty_vec 0x2302
vec_extern:
        empty_vec 0x2303
vec_timer:
        empty_vec 0x2304
vec_gdn_avail:
	sw	$2,-8($sp)
#	la	$2,gdn_avail_handler
#	jr	$2
	jal recvMsg 
vec_event_counters:
        empty_vec 0x2306

