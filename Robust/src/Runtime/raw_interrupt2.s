#include <raw_asm.h>

	.text
	.align	2
	.globl	setup_ints
	.ent	setup_ints
setup_ints:	
	# set up dynamic network
	uintoff
	intoff

	# set gdn_cfg
	xor $8,$8,$8
	aui $8,$8,(3<<11)|(1 <<6)|(0 <<1)
	ori $8, (0 <<12)|(2<<9)
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
	jr $31
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
	mtsri PASS, 0xef00
	uintoff

	addiu   $sp,$sp,-64
	sw      $31,0x3c($sp)
	sw      $30,0x38($sp)
	sw      $23,0x34($sp)
	sw      $22,0x30($sp)
	sw      $21,0x2c($sp)
	sw      $20,0x28($sp)
	sw      $19,0x24($sp)
	sw      $18,0x20($sp)
	sw      $17,0x1c($sp)
	sw      $16,0x18($sp)
	sw      $7,0x14($sp)
	sw      $6,0x10($sp)
	sw      $5,0xc($sp)
	sw      $4,0x8($sp)
	sw      $3,0x4($sp)
	sw      $2,0x0($sp)

	jal receiveObject

	lw      $31,0x3c($sp)
	lw      $30,0x38($sp)
	lw      $23,0x34($sp)
	lw      $22,0x30($sp)
	lw      $21,0x2c($sp)
	lw      $20,0x28($sp)
	lw      $19,0x24($sp)
	lw      $18,0x20($sp)
	lw      $17,0x1c($sp)
	lw      $16,0x18($sp)
	lw      $7,0x14($sp)
	lw      $6,0x10($sp)
	lw      $5,0xc($sp)
	lw      $4,0x8($sp)
	lw      $3,0x4($sp)
	lw      $2,0x0($sp)
	addiu   $sp,$sp,64

	mtsri PASS, 0xefff
	dret
vec_event_counters:
        empty_vec 0x2306

