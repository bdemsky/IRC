# perl script
# makes character map
# input line: number tab name
# output line: <char:name><font:><index:number>

# generate file names for input
$sourcefile = $ARGV[0];
# generate file names for output
# trim off suffix starting with dot
($base = $sourcefile) =~ s/(.+)\..*/$1/;
$resultfile = "$base.processed.txt";

print "Converting $sourcefile to $resultfile ...\n";
open (SOURCE, "<$sourcefile");
open (RESULT, ">$resultfile");

LINE: while ($line = <SOURCE>) {
	next LINE if $line =~ /^#/;		# skip if line matches pattern
	next LINE if $line =~ /^\s*$/;	# skip if source line is empty
	
	if ($line =~ /^(\w+)\s+(\w+)/) {
		$number = $1;
		$name = $2;
		print RESULT "<char:$name><font:><index:$number>\n";
		}
	}

close (SOURCE);
close (RESULT);
