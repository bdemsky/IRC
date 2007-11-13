GRAPH=testGraph

all: view

view: PNGs
	eog *.png &

PNGs: DOTs
	d2p *.dot

DOTs: run

run:
	java -classpath ../.. Analysis.OwnershipAnalysis.TestOwnership

clean:
	rm -f *~
	rm -f *.dot
	rm -f *.png
