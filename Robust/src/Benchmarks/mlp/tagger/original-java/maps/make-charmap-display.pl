# perl script
# makes tagged text to show contents of char map

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
	
	if ($line =~ /<char:(\w+)><font:(\w*)>/) {
		$charname = $1;
		$fontname = $2;
		print RESULT "\\$charname\t$charname\t$fontname\\\\\n";
		}
	}
close (SOURCE);
close (RESULT);
