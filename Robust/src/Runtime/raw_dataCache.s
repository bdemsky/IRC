.text
.global flushAddr
.global invalidateAddr
.global flushCacheline

	
flushAddr:
# arguments come in on $4 and $5
# $4 has the address
# $5 has the length, eventually

	afl $4, 0
	jr    $31
	

invalidateAddr:
# arguments come in on $4 and $5
# $4 has the address
# $5 has the length, eventually

	ainv $4, 0
	jr $31


flushCacheline:
# arguments come in on $4
# $4 has the base tag address

	tagfl $4
	jr $31

