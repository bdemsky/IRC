#!/usr/bin/perl
##
## fetch_stat.pl
##
## Fetches and summarizes stats from Dell PowerConnect 27xx
## Written by Joakim Andersson, joakim at iqcc dot se
##
## 1) Install the Perl-modules you might be missing
## 2) Adjust the settings in the settings-file
## 3) Run the script
##
## Arguments: 
## clear_stats	 - Clear the portstats when script starts
## clear_session - Forces a cached session to be ignored, useful for testing only 
## clear_wraps   - Forgets about previous counter-wraps
## settings=file - Read settings from this file
##
## For changelog please visit http://www.iqcc.se
##

my $ver = '1.42 (2007-04-19)';

use 5.004;
use strict;
use Digest::MD5  qw(md5_hex);
use LWP::UserAgent;
use Net::HTTP;
use Time::HiRes;

####################################################
##
## File containing settings
##

my $SETTINGS_FILE = 'settings.txt';

##################################################

my @do_delta = qw(goodOctetRCV rxGood goodOctetSND txGood rx64Octets rx65TO127Octets rx128TO255Octets rx256TO511Octets rx512TO1023Octets rx1024ToMa );

my $TR_COLOR = '#FFFF80';
my %graph_names = ('bps' => 'bits per second', 'pps' => 'packets per second');
my %graph_rev_type = ('bps' => 'pps', 'pps' => 'bps');

my $cookie = '';

my %SET = ( 'VERBOSE' => 3 );	# Settings
my %STATS = ();			# To maintain delta-stats 
my %OLD_STATS = ();		# To cache previous values
my %PORTS = ();			# To maintain non-statistics info about each port
my %WRAPS = ();			# To maintain counter-wraps
my %system = ();		# System-info
my @GRAPHS = ();		# Graphs to create
my @do_ports = ();

# Setting time
my %time = ();
&upd_time();

# Looking for valid args
my $clear_session = 0;
my $clear_stat = 0;
my $clear_wraps = 0;

foreach (@ARGV)
{
	($_, my $value) = split(/=/);
	
	if (/^clear_stats?$/)
	{
		$clear_stat = 1;
	}
	elsif (/^clear_sessions?$/)
	{
		$clear_session = 1;
	}
	elsif (/^clear_wraps?$/)
	{
		$clear_wraps = 1;
	}
	elsif (/^settings$/ && $value)
	{
		$SETTINGS_FILE = $value;
	}
}

my $numberOfPorts = 0;
my $numberOfTrunks = 0;

# Reading settings
# Will be refreshed when changed later
if (&refresh_settings() == 2)
{
	##
	## Errors in the settings!
	##
	
	die "Errors exists in '$SETTINGS_FILE', unable to start fetch_stat.pl $ver!";
}

# Settings was OK
&log("fetch_stat.pl $ver started at $time{'date_time'}", $SET{'VERBOSE'});

# Creating UserAgent-object
my $ua = LWP::UserAgent->new();
$ua->agent("Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");

# Reads cached cookie, if wanted
if ($SET{'COOKIE_FILE'})
{
	open (COOKIE, $SET{'COOKIE_FILE'}) || warn "Unable to open cookie-file $SET{'COOKIE_FILE'}, first run ever?\n";
	$cookie = <COOKIE>;
	close COOKIE;
}

if ($clear_session && $cookie)
{
	$cookie = '';
	print "Cleared old session-cookie\n" if ($SET{'VERBOSE'});
}
elsif ($cookie)
{
	$cookie =~ tr/\n\r\t//d;
	print "Trying to reuse old session-cookie '$cookie'\n" if ($SET{'VERBOSE'});
	
	# Fetches system info
	# Will reset $cookie if failure
	print "Polling link- and system info...\n" if ($SET{'VERBOSE'} >= 2);
	&get_system_info;
}

##
## If we don't have a valid session login and fetch system info again
##

unless ($cookie)
{
	&login;
	print "Polling link- and system info...\n" if ($SET{'VERBOSE'} >= 2);
	&get_system_info;
}


##
## Reading or clearing wraps
##

if ($SET{'WRAPS_FILE'})
{
	if ($clear_wraps || $clear_stat)
	{
		##
		## Removes cached wraps
		##
		
		if (-e $SET{'WRAPS_FILE'})
		{
			&log("Clearing cached wraps", $SET{'VERBOSE'});
			unlink $SET{'WRAPS_FILE'};
		}
	}
	else
	{
		##
		## Reads cached wraps
		##
		
		open (WRAPS, $SET{'WRAPS_FILE'});
		while (<WRAPS>)
		{
			my ($name, $value) = split(/\t/, $_, 2);
			chop $value;
			
			$WRAPS{$name} = $value;
			#print "$name = $value\n";
		}
		close WRAPS;
	}
}

##
## Loops forever, hopefully
##

my $loop = 0;
my $runtime = 'n/a';
my $maxlen_portname = 10;
my $autoadded_ports = 0;

my %do_ports = {};
#my @do_ports = ();

print "\n" if ($SET{'VERBOSE'});	#Init complete\n
$| = 1;

while ( $loop == 0)
{
    $loop++;
	##
	## Refreshes settings if needed
	##
	
	my $settings_changed = &refresh_settings();
	
	if ($settings_changed == 2)
	{
		##
		## Errors!
		##
		
		&log("Errors found in the settings, keeping the old settings!", $SET{'VERBOSE'});
		#sleep $SET{'REFRESH_RATE'};
		#next;
		$settings_changed = 0;
	}
	elsif ($settings_changed || ($loop == 1) || $autoadded_ports)
	{
		##
		## New settings read, or first loop
		## Also if ports has been auto-added
		##
		
		%do_ports = %{$SET{'PORTS'}};	# Ports on the switch

		##
		## Finds longest portname + clears stats if wanted
		##
		
		$maxlen_portname = 10;
		foreach my $portnum (@do_ports)
		{
			my $this_len = length($do_ports{$portnum});
			$maxlen_portname = $this_len if ($maxlen_portname < $this_len);
			
			##
			## Clears stats of these ports if program was called with arg clear_stat
			##
			
			if ($clear_stat)
			{
				print "Clearing stats for port $portnum...\n" if ($SET{'VERBOSE'});
				&clear_stat($portnum);
			}
		}

		if ($clear_stat)
		{
		    exit;
		}

		$autoadded_ports = 0;
	}
	
	##
	## Login if we miss a valid session
	##
	
	&login unless ($cookie);
	
	# Remembers the time when this loop started
	my $start_time = [ Time::HiRes::gettimeofday() ];
	
	# Updates time
	&upd_time();
	
	##
	## Polling every defined port
	##
	
	my $wraps_occured = 0;
	print "Polling stats ($time{'time'})...\n" if ($SET{'VERBOSE'} >= 2);
	
	foreach my $portnum (@do_ports)
	{
		##
		## Initiates the hashes
		##
		
		my $STAT = { 'time' => [ Time::HiRes::gettimeofday() ] };
		my $OLD_STAT = $STATS{$portnum};
		my $PORT = $PORTS{$portnum} ||= {};
		
		if ($SET{'VERBOSE'} >= 2)
		{
			# Builds the info
			my $name = "$portnum:";
			print $name, ' ' x (6 - length($name));
			
			$name = $do_ports{$portnum};	#"$do_ports{$portnum}...";
			print $name, ' ' x ($maxlen_portname - length($name) + 2);
		}
		
		##
		## Is it a switchport or a local interface?
		##
		
		if ($portnum =~ /^T?\d+$/)
		{
			##
			## Polls this port on the switch
			##
			
			my $arg = ($portnum =~ /^T(\d+)$/) ? "TrunkNo=$1" : "PortNo=$portnum";
			
			# Fetches the page
			my $res = $ua->get("http://$SET{'SWITCH'}/portStats.htm?$arg",
			       'Cookie' => $cookie
				 );
			
			&log_n_die("Unable to fetch /portStats.htm?$arg - ".$res->status_line) unless ($res->is_success);
			
			# Parsing content
			my $data = $res->content;
			$data =~ tr/\r//d;
			
			if ($data =~ /^<title>Login<\/title>$/m)
			{
				##
				## Invalid session!
				##
				
				&log("Session invalid", $SET{'VERBOSE'});
				
				$cookie = '';
				last;
			}
			
			##
			## Parses the data, is stored in javascript-variables in the beginning of the page
			##
			
			my $row = 0;
			my $wraps = 0;
			my @wraps = ();
			
			foreach (split(/\n+/, $data))
			{
				$row++;
				#print "RAD $row: $_\n";
				
				last if (/^<\/script>$/);	# May need adjustment for future firmwares
				next unless (/^var (.+)="(\d*)";$/);
				
				my $name = $1;
				next if ($STAT->{$name});		# Skips already found variables, a bug in the current firmware
				
				my $value = reverse $2;		# Values are stored reversed in the HTML-page
				$value += 0;				# Perhaps unneeded, but zero is better that nothing
				
				# To determine wraps we need the current value
				# Ugly as hell
				$STAT->{"curr:$name"} = $value;
				
				##
				## Adding previous wraps of this counter
				##
				
				if ($WRAPS{"$portnum:$name"})
				{
					$value += 4294967296 * $WRAPS{"$portnum:$name"};
				}
				
				##
				## Looking for wrapped counters
				## 32bit counters is not enough
				##			
				
				if ($OLD_STAT && $value < $OLD_STAT->{$name})
				{
					##
					## We guess this is a wrap
					## With 32bit counters 4294967296 is the max value
					## Resets of the statistics may cause problems here
					##
					
					if ($wraps < 2)
					{
						##
						## Max two wraps per poll is considered valid
						##
						
						my $tmp = ++$WRAPS{"$portnum:$name"};
						my $new = $STAT->{"curr:$name"};
						my $old = $OLD_STAT->{"curr:$name"};
						
						&log("$do_ports{$portnum} (port $portnum): Probable wrap of $name has occured. $old -> $new. (tot $OLD_STAT->{$name} -> $value, wraps $tmp)", $SET{'VERBOSE'} >= 3);
						
						$value += 4294967296;
						
						$wraps++;
						$wraps_occured++;
						push (@wraps, $name);
					}
					else
					{
						##
						## Hmmm, this is probably *not* a wrap
						## Resetting the wrap-counter and old stats
						##
					
						&log("$do_ports{$portnum} (port $portnum): Wasn't wraps, most likely a reset of the stats instead. Resetting our old stats and wraps", $SET{'VERBOSE'} >= 3);
						
						undef $OLD_STAT;
						delete $OLD_STATS{$portnum};
						
						# Reverting previous wraps
						foreach (@wraps)
						{
							$STAT->{$_} = $STAT->{"curr:$_"};
						}
						@wraps = ();
						
						# Cleaning wraps for this port
						foreach (keys %WRAPS)
						{
							next unless (/^$portnum:/);
							#print "Deleted $_: $WRAPS{$_}\n";
							delete $WRAPS{$_};
						}
					}
				}
				
				# Memorizing this counter
				$STAT->{$name} = $value;
			}
			
			unless ($STAT->{'portIndex'} || $STAT->{'portTrunkIndex'})
			{
				##
				## Hmmmmm, didn't find any of those expected variables
				##
				
				if ($portnum =~ /^T(\d+)$/ && ($numberOfTrunks < $1))
				{
					# Port out of bounds
					&log_n_die("Unable to poll trunk $1 ($portnum), because max supported by switch is $numberOfTrunks");
				}
				elsif ($portnum =~ /^(\d+)$/ && ($numberOfPorts < $1))
				{
					# Trunk out of bounds
					&log_n_die("Unable to poll port $1, because max supported by switch is $numberOfPorts");
				}
				
				# Should always recieve portIndex, just a failsafe
				&log("Bug? Unable to poll port $portnum, unknown cause", $SET{'VERBOSE'});
				print $data;
				exit;
			}
		}
		elsif ($portnum =~ /^C\d+$/)
		{
			##
			## This was a local iptables' chain
			## Variables are added to $STAT if successful
			##
			
			my $def = ${$SET{'CHAINS'}}{$portnum};
			unless (&poll_chain($def, $STAT))
			{
				##
				## The poll failed
				##
				
				&log("Poll of chain $portnum definition '$def' failed!");
				next;
			}
		}
		else
		{
			##
			## This was assumes to be a local interface
			## Variables are added to $STAT if successful
			##
			
			unless (&poll_interface($portnum, $STAT))
			{
				##
				## The poll failed
				##
				
				&log("Poll of local interface '$portnum' failed!");
				next;
			}
		}
		
		##
		## Do we have old info, so we can compute delta-values?
		##
		
		if (defined $OLD_STAT)
		{
			##
			## Computing delta values of some variables
			##
			
			# Elapsed time since last run
			$STAT->{'delta_time'} = Time::HiRes::tv_interval($OLD_STAT->{'time'}, $STAT->{'time'});
			
			if ($STAT->{'delta_time'} > 0)
			{
				##
				## Calcs delta for some variables
				##
				
				foreach (@do_delta)
				{
					my $diff = $STAT->{$_} - $OLD_STAT->{$_};
					$STAT->{"delta_$_"} = $diff / $STAT->{'delta_time'};
					
					if ($diff < 0)
					{
						##
						## Should never get a negative value!
						##
						
						&log("Negative delta! Port $portnum, variable $_: now '$STAT->{$_}', prev '$OLD_STAT->{$_}' = diff '$diff'", $SET{'VERBOSE'});
					}
				}
				
				##
				## Calcs errors/sec per in/out
				##
				
				# error-pps in
				my $value = 0;
				$value += ($STAT->{$_} - $OLD_STAT->{$_}) foreach (qw(rxFCS rxUnderSize rxOverSize rxFragment rxJabber));
				$STAT->{'err_in_pps'} = $value / $STAT->{'delta_time'};
				
				# error-pps out
				$value = 0;
				$value += ($STAT->{$_} - $OLD_STAT->{$_}) foreach (qw(txDrop txCollisions));
				$STAT->{'err_out_pps'} = $value / $STAT->{'delta_time'};
				
				##
				## Show stats in the shell?
				##
				
				if ($SET{'VERBOSE'} >= 2)
				{
					my @values = ();
					
					foreach (qw(goodOctetRCV rxGood goodOctetSND txGood))
					{
						my $value = $STAT->{"delta_$_"};
						
						if ($PORT->{'has_link'} || $value > 0)
						{
							if (/Octet/)
							{
								# Bytes -> bits
								$value = &shorten_num($value*8,undef,1024) . 'bps';
							}
							else
							{
								# Packets
								$value = &shorten_num($value,undef,1000) . 'pps';
							}
						}
						else
						{
							$value = '-';
						}
						push (@values, $value);
					}
					
					print "out:", ' ' x (8 - length($values[0])), $values[0];
					print " / ", ' ' x (8 - length($values[1])), $values[1];
					
					print "     in:", ' ' x (8 - length($values[2])), $values[2];
					print " / ", ' ' x (8 - length($values[3])), $values[3];
					
					my $tot_err_pps = $STAT->{'err_in_pps'}+$STAT->{'err_out_pps'};
					if ($tot_err_pps > 0)
					{
						$tot_err_pps = &shorten_num($tot_err_pps,undef,1000) . 'pps';
					}
					else
					{
						$tot_err_pps = '-';
					}
					print "     err:", ' ' x (8 - length($tot_err_pps)), $tot_err_pps;
					
					my $show = &shorten_num($STAT->{'delta_time'}, 6);
					print "     delta: ${show}s";
				}
			}
			
			##
			## Shall we write to RRD-file
			##
			
			if ($SET{'RRD_FILE_PREFIX'} && !$SET{"RRD_SKIP_PORT_$portnum"})
			{
				##
				## Yep, we shall save RRD data for this port
				##
				
				my $filename = $SET{'RRD_FILE_PREFIX'} . "_$portnum.rrd";
				my $status = 1;
				
				unless (-e $filename)
				{
					# Doesn't exist, create it
					($status, my $message) = &create_rrd($filename);
					&log("Port $portnum: $message", $SET{'VERBOSE'} >= 4);
					
					if ($status == 0)
					{
						# Will not try create RRD for this port again
						$SET{"RRD_SKIP_PORT_$portnum"} = 1;
						&log("Create failed, disabling RRD for port $portnum", 1);
					}
				}
				
				if ($status == 1)
				{
					##
					## No errors found, writing
					##
					## Order of data:
					## kbps out and in
					## pps out and in
					## error-pps out and in
					##
					
					my @values = ();
					
					foreach (qw(delta_goodOctetSND delta_goodOctetRCV
								delta_txGood delta_rxGood
								err_out_pps err_in_pps
							))
					{
						my $value = $STAT->{$_};
						$value *= 8/1024 if (/Octet/);	# Octets -> kbit
						$value =~ s/(\.\d\d)\d+$/$1/;
						push(@values, $value);
					}
					
					# Uses the timestamp from our previous Time::HiRes gettimeofday
					# HOWEVER if we're syncing to RRD and we're in sync the loop-start-time is used
					my $timestamp = ${$STAT->{'time'}}[0];
					$timestamp = $time{'unixtime'} if ($SET{'rrd_in_sync'});
					
					#print "\n$timestamp - ", scalar localtime( $timestamp ), "\n";
					
					my $string = join(':', $timestamp, @values);
					
					##
					## Updates the RRD
					##
					
					RRDs::update($filename, $string);
					
					my $error = &RRDs::error;
					&log("Unable to update RRD '$filename' with '$string'!: $error", $SET{'VERBOSE'} >= 1) if ($error);
				}
			}
			
			##
			## Is this port considered active?
			## Used when we hide inactive ports in the HTML
			##
			
			$PORT->{'is_active'} = $PORT->{'has_link'};
			if (!$PORT->{'is_active'} && ($STAT->{'delta_txGood'} || $STAT->{'delta_rxGood'}))
			{
				##
				## According to last polling of linkinfo the port is down, but
				## the count of packets travelling the port has increased since
				## last loop, so we assume it's gone up
				##
				
				$PORT->{'is_active'} = 1;
				$STAT->{'link_changed'} = 1;
			}
		}
				
		# Memorizes the previous stat for this port
		$OLD_STATS{$portnum} = $STATS{$portnum} if ($STATS{$portnum});
		$STATS{$portnum} = $STAT;
		
		print "\n" if ($SET{'VERBOSE'} >= 2);
	}
	
	##
	## Writes cached wraps
	##
	
	if ($wraps_occured > 0 && $SET{'WRAPS_FILE'})
	{
		#&log("Saving wraps-cache", $SET{'VERBOSE'} >= 2);
		
		open (WRAPS, ">$SET{'WRAPS_FILE'}") || &log("Failed to save wraps-cache to file '$SET{'WRAPS_FILE'}'!", $SET{'VERBOSE'});
		foreach (sort keys %WRAPS)
		{
			print WRAPS "$_\t$WRAPS{$_}\n";
		}
		close WRAPS;
	}
	
	# Ends loop here if we don't have a valid session
	next unless ($cookie);
	
	##
	## Checking and logging increased errors, but only if a logfile is defined
	##
	
	if ($SET{'LOG_FILE'})
	{
		foreach my $portnum (@do_ports)
		{
			my $STAT = $STATS{$portnum};
			next unless ($STAT->{'delta_time'});
			
			my $OLD_STAT = $OLD_STATS{$portnum};
			
			foreach (qw(txDrop txCollisions rxFCS rxUnderSize rxOverSize rxFragment rxJabber))
			{
				my $diff = $STAT->{$_} - $OLD_STAT->{$_};
				next unless ($diff > 0);

				my $deltatime = &shorten_num($STAT->{'delta_time'}, 4);	# Only stripping decimals
				my $rate = &shorten_num($diff / $STAT->{'delta_time'}, 3, 1000);
				
				# Log and possibly print if verbose >= 3
				&log("$do_ports{$portnum} (port $portnum): $_ has increased by $diff the last $deltatime secs (rate ${rate}pps)", $SET{'VERBOSE'} >= 3);
			}
		}
	}
	
	##
	## We doesn't fetches system and linkinfo every loop, since it takes a while
	##
	
	if ($settings_changed || ($loop % $SET{'POLL_LINKINFO_EVERY_NTH_LOOP'} == 0))
	{
		##
		## Also fetches general system-info now
		##
		
		print "Polling link- and system info...\n" if ($SET{'VERBOSE'} >= 2);
		&get_system_info;
	}
	else
	{
		# Won't fetch, so we assumes the switch has been up
		# We assumes that the whole loop will take refresh_rate seconds 
		$system{'sysUpTime'} += $SET{'REFRESH_RATE'}*100;
		&parse_sysuptime;
	}
	
	# Ends loop here if we don't have a valid session
	next unless ($cookie);
	
	##
	## Generates graphs
	##
	
	if ($SET{'HTML_FILE'} && $SET{'GRAPH_BPS_EVERY_NTH_LOOP'} && (($loop+1) % $SET{'GRAPH_BPS_EVERY_NTH_LOOP'} == 0))
	{
		print "Graphing BPS...\n" if ($SET{'VERBOSE'} >= 2);
		&generate_rrd_graphs('bps');
	}
	
	if ($SET{'HTML_FILE'} && $SET{'GRAPH_PPS_EVERY_NTH_LOOP'} && (($loop+1) % $SET{'GRAPH_PPS_EVERY_NTH_LOOP'} == 0))
	{
		print "Graphing PPS...\n" if ($SET{'VERBOSE'} >= 2);
		&generate_rrd_graphs('pps');
	}
	
	##
	## Generates web-page if wanted
	##
	
	if ($SET{'HTML_FILE'})
	{
		$runtime = Time::HiRes::tv_interval($start_time, [ Time::HiRes::gettimeofday() ]);
		&generate_html;
	}
	
	##
	## Pauses
	##
	
	$runtime = Time::HiRes::tv_interval($start_time, [ Time::HiRes::gettimeofday() ]);
	my $sleep = $SET{'REFRESH_RATE'} - $runtime;
	
	##
	## RRD creates "sharper" graphs if data is polled near each RRD_STEP-interval
	## If wanted we try to 'slide' to stay near that interval
	##
	
	if ($SET{'RRD_FILE_PREFIX'} && $SET{'SYNC_SLEEP_TO_RRD'})
	{
		# If using the default RRD_STEP 30s, we get a value between 0 and +30
		# 0 is the ideal point
		# Negative means we need to sleep a bit shorter to get near 0
		# Positive means we need to sleep a bit longer to get near 0
		my $half_step = $SET{'RRD_STEP'} / 2;
		my $diff = $half_step - ($time{'unixtime'}+$half_step) % $SET{'RRD_STEP'};
		
		if ($diff != 0)
		{
			##
			## Need adjusting
			##
			
			# Max 5 seconds each loop
			$diff = 5 if ($diff > 5);
			$diff = -5 if ($diff < -5);
			$sleep += $diff;
			
			$diff = '+'.$diff if ($diff > 0);
			&log("Adjusting sleep with $diff secs to sync with RRD_STEP = $SET{'RRD_STEP'} secs", $SET{'VERBOSE'});
			$SET{'rrd_in_sync'} = 0;
		}
		elsif ($SET{'rrd_in_sync'} != 1)
		{
			print "Sync with RRD_STEP = $SET{'RRD_STEP'} secs is OK\n" if ($SET{'VERBOSE'} >= 2);
			$SET{'rrd_in_sync'} = 1;
		}
	}
	
	# Always sleeps atleast 2 secs!
	$sleep = 2 if ($sleep < 1);
	
	print "\n" if ($SET{'VERBOSE'} > 1);
#	Time::HiRes::sleep($sleep);
}

exit;


##
## Create the HTML-page
##

sub generate_html
{
	#print "HTML refresh: $refresh\n";
	
	open (HTML, ">$SET{'HTML_FILE'}") || warn "Unable to write to html-file '$SET{'HTML_FILE'}'!";
	print HTML <<END;
<html>
<head>
<title>Statistics for $SET{'SWITCH'} - $system{'systemName'} $system{'locationName'}</title>
<meta http-equiv="Refresh" content="$SET{'REFRESH_RATE'}">
</head>

<body bgcolor="White" text="Black" link="Black" vlink="Black">

<center>
<p>
<table border="0" cellpadding="4" cellspacing="0" style="font-family: Verdana, Geneva, Arial, Helvetica, sans-serif; font-size: 10;" width="980">

<tr align=left valign=bottom>
<th align=right rowspan=2 bgcolor="silver">#</th>
<th rowspan=2 bgcolor="silver">Equipment</th>
<th colspan=4 bgcolor="lime"><a title="When polling a local interface, it's traffic transmitted from it">OUT: Switch -&gt; Equipment:</a></th>
<th colspan=2 bgcolor="red">Errors</th>
<td bgcolor="silver"></td>
<th colspan=4 bgcolor="lime"><a title="When polling a local interface, it's traffic recieved by it">IN: Equipment -&gt; Switch:</a></th>
<th colspan=5 bgcolor="red">Errors</th>
</tr>
<tr align=left valign=bottom>
<th bgcolor="lime" colspan=2><a title="goodOctetSND (bytes), txGood (packets)">TOT</a></th>
<th bgcolor="lime"><a title="txBroadcast (packets)">Bcast</a></th>
<th bgcolor="lime"><a title="txMulticast (packets)">Mcast</a></th>
<th bgcolor="red"><a title="txDrop (packets)">Drop</a></th>
<th bgcolor="red"><a title="txCollisions (packets)">Coll</a></th>
<td bgcolor="silver"></td>

<th bgcolor="lime" colspan=2><a title="goodOctetRCV (bytes), rxGood (packets)">TOT</a></th>
<th bgcolor="lime"><a title="rxBroadcast (packets)">Bcast</a></th>
<th bgcolor="lime"><a title="rxMulticast (packets)">Mcast</a></th>
<th bgcolor="red"><a title="rxFCS (packets)">FCS</a></th>
<th bgcolor="red"><a title="rxUnderSize (packets)">Usize</a></th>
<th bgcolor="red"><a title="rxOverSize (packets)">Osize</a></th>
<th bgcolor="red"><a title="rxFragment (packets)">Frag</a></th>
<th bgcolor="red"><a title="rxJabber (packets)">Jabb</a></th>
</tr>
END
	
	foreach my $portnum (@do_ports)
	{
		##
		## Each port
		##
		
		my $STAT = $STATS{$portnum};
		my $OLD_STAT = $OLD_STATS{$portnum};
		my $PORT = $PORTS{$portnum};
		my $is_err = 0;
		
		# Hides ports that are not active
		next if ($SET{'HIDE_INACTIVE_PORTS'} && !$PORT->{'is_active'} && !$SET{"ALWAYS_SHOW_PORT_$portnum"});
		
		##
		## Shall we add a link and/or title?
		##
		
		my $portnum_link = '';
		my $portnum_title = '';
		
		if ($PORT->{'rrd_bps_file'})
		{
			# Link to BPS-graph
			$portnum_link = $PORT->{'rrd_bps_file'};
			$portnum_title = "Click to view graphs";
		}
		elsif ($PORT->{'rrd_pps_file'})
		{
			# Link to PPS-graph
			$portnum_link = $PORT->{'rrd_pps_file'};
			$portnum_title = "Click to view graphs";
		}
		
		if ($portnum =~ /^C\d+$/)
		{
			# Chain
			my @def = split(/\s*,\s*/, ${$SET{'CHAINS'}}{$portnum});
			
			$portnum_title .= ", " if ($portnum_title);
			$portnum_title .= "OUT = chain $def[0] row $def[1]";
			$portnum_title .= ", IN = chain $def[2] row $def[3]" if ($def[2]);
		}
		
		$portnum_link  = "href=\"$portnum_link\"" if ($portnum_link);
		$portnum_title = "title=\"$portnum_title\"" if ($portnum_title);
		
		##
		## Writing
		##
		
		print HTML <<END;
<tr align=right valign=top onmouseover="this.style.backgroundColor='$TR_COLOR'" onmouseout="this.style.backgroundColor=''">
<th bgcolor="silver"><a $portnum_link $portnum_title>$portnum</a></th>
<td align=left>$do_ports{$portnum}</td>
END
			
		foreach (qw(
goodOctetSND txGood txBroadcast txMulticast
is_err txDrop txCollisions no_err
space
goodOctetRCV rxGood rxBroadcast rxMulticast
is_err rxFCS rxUnderSize rxOverSize rxFragment rxJabber no_err
			))	# txError rxError 
		{
			##
			## Visar värden
			##
			
			if ($_ eq 'space')
			{
				print HTML "<td></td>";
				next;
			}
			elsif ($_ eq 'is_err')
			{
				$is_err = 1;
				next;
			}
			elsif ($_ eq 'no_err')
			{
				$is_err = 0;
				next;
			}
			
			my $value = my $real_value = $STAT->{$_};
			my $diff = $value - $OLD_STAT->{$_};
			my $make_boldred = ($is_err && defined $OLD_STAT->{$_} && ($diff > 0));
			$diff = ($make_boldred) ? " (+$diff)" : '';
			

			# Exact value for mouse-over
			my $value_sep = &add_sep($real_value);
			
			# Writing da stuff
			print HTML "<td><a title=\"$value_sep$diff\">$value</a></td>";
		}
		
		print HTML "</tr>\n";
	}
	
	print HTML <<END;
</table>

<br><br><br>
<table border="0" cellpadding="4" cellspacing="0" style="font-family: Verdana, Geneva, Arial, Helvetica, sans-serif; font-size: 10;" width=980>

<tr align=left valign=bottom>
<th align=right rowspan=2 bgcolor="silver">#</th>
<th rowspan=2 bgcolor="silver">Equipment</th>
<th bgcolor="lime"></th>
<th bgcolor="silver" width=0></th>
<th colspan=23 bgcolor="lime">IN: Equipment -> Switch: Amount of packets per framesize</th>
</tr>
<tr align=center valign=bottom>
<th bgcolor="lime">Link</th>
<th bgcolor="silver" width=0></th>
<th bgcolor="lime" colspan=3><a title="rx64Octets (packets, percent, packets/s)">64 byte</a></th>
<td bgcolor="silver" width=0></td>
<th bgcolor="lime" colspan=3><a title="rx65TO127Octets (packets, percent, packets/s)">65-127 byte</a></th>
<td bgcolor="silver" width=0></td>
<th bgcolor="lime" colspan=3><a title="rx128TO255Octets (packets, percent, packets/s)">128-255 byte</a></th>
<td bgcolor="silver" width=0></td>
<th bgcolor="lime" colspan=3><a title="rx256TO511Octets (packets, percent, packets/s)">256-511 byte</a></th>
<td bgcolor="silver" width=0></td>
<th bgcolor="lime" colspan=3><a title="rx512TO1023Octets (packets, percent, packets/s)">512-1024 byte</a></th>
<td bgcolor="silver" width=0></td>
<th bgcolor="lime" colspan=3><a title="rx1024ToMa (packets, percent, packets/s)">1025- byte</a></th>
</tr>
END
	
	foreach my $portnum (@do_ports)
	{
		##
		## Each port
		##
		
		next unless ($portnum =~ /^T?\d+$/);	# No such info for local interfaces
		
		my $STAT = $STATS{$portnum};
		my $OLD_STAT = $OLD_STATS{$portnum};
		my $PORT = $PORTS{$portnum};
		
		# Hides ports that are not up, if wanted
		next if ($SET{'HIDE_INACTIVE_PORTS'} && !$PORT->{'is_active'} && !$SET{"ALWAYS_SHOW_PORT_$portnum"});
		
		##
		## Shall we add a link to graphs?
		##
		
		my $graph_link = '';
		
		if ($PORT->{'rrd_bps_file'})
		{
			# Link to BPS-graph
			$graph_link = $PORT->{'rrd_bps_file'};
		}
		elsif ($PORT->{'rrd_pps_file'})
		{
			# Link to PPS-graph
			$graph_link = $PORT->{'rrd_pps_file'};
		}
		
		$graph_link = "href=\"$graph_link\" title=\"Click to view graphs\"" if ($graph_link);
		
		##
		## Make linkinfo bold if it recently changed
		##
		
		my $link = $PORT->{'link'};
		$link = 'up?' if ($link eq 'down' && $PORT->{'is_active'});
		$link = "<b>$link</b>" if ($STAT->{'link_changed'});
		
		##
		## Writing
		##
		
		print HTML <<END;
<tr align=right valign=top onmouseover="this.style.backgroundColor='$TR_COLOR'" onmouseout="this.style.backgroundColor=''">
<th bgcolor="silver"><a $graph_link>$portnum</a></th>
<td align=left>$do_ports{$portnum}</td>
<td align=right><a title="$PORT->{'link_change_at'}">$link</a></td>
<td></td>
END
		
		foreach (qw( rx64Octets rx65TO127Octets rx128TO255Octets rx256TO511Octets rx512TO1023Octets rx1024ToMa ))
		{
			##
			## The amount of packets
			##
			
			my $tmp = $STAT->{$_};
			my $value_sep = &add_sep($tmp);
			my $value = $tmp;
			my $proc = 0;
			eval { $proc = int($tmp / $STAT->{'rxGood'} * 100) };
			
			
			print HTML "<td><a title=\"$value_sep\">$value</a></td>";
			
			##
			## Percent of total
			##
			
			
			print HTML "<td>$proc\%</td>";
			
			##
			## Delta
			##
			
			my $delta = $STAT->{"delta_$_"};
			
			if ($delta > 0)
			{
				$value = &shorten_num($delta, undef, 1000) . 'pps';
				$value = "<b>$value</b>" if ($SET{'BOLD_ABOVE_PPS'} && $delta > $SET{'BOLD_ABOVE_PPS'} && $value =~ /pps$/);
				$value = "<font color=\"silver\">$value</font>" if ($SET{'GREY_BELOW_PPS'} && $delta < $SET{'GREY_BELOW_PPS'});
			}
			else
			{
				$value = '';
			}
			
			$value_sep = &add_sep($delta);
			
			print HTML "<td><a title=\"$value_sep\">$value</a></td>";
			print HTML "<td></td>" unless ($_ eq 'rx1024ToMa');
		}
		
		print HTML "</tr>\n";
	}
	
	my $created = localtime;
	my $tmp = $runtime;
	$tmp =~ s/(\.\d\d).+$/$1/;
	
	# ServiceTag $system{'serviceTag'}, Serial $system{'serialNum'}
	print HTML <<END;
</table>

<p>
<font color=silver>Generated by fetch_stat.pl ver $ver written by Joakim Andersson - http://www.iqcc.se</font>
</div>

</center>

</body>
</html>
END
	
	close HTML;
}

##
## Logga in mot Dell-switchen för att få sessions-cookien
##

sub login
{
	##
	## Fetched the loginpage to get the initial Session-value
	##
	
	&log("Logging in into $SET{'SWITCH'}...", $SET{'VERBOSE'});
	
	my $res = $ua->get("http://$SET{'SWITCH'}/login11.htm");
	&log_n_die("Unable to fetch /login11.htm - ".$res->status_line) unless ($res->is_success);
	
	my $data = $res->content;
	my $session = '';
	
	# Finds the Session-value, needed to compute the password that is sent when logging in
	if ($data =~ /^<input type="hidden" name="Session" value="(.*)">/m)
	{
		$session = $1;
	}
	
	unless ($session)
	{
		&log_n_die("Unable to find variable Session in the page! $data");
	}
	
	if ($session =~ /^0+$/)
	{
		&log_n_die("Session $session is invalid! You may need to wait a while, or possibly reboot the switch");
	}
	
	print "Found 'Session': $session\n" if ($SET{'VERBOSE'} >= 2);
	#$session = '8c6e05969fb0748f7fa239628432ffe4';
	
	##
	## Logging in to fetch the cookie
	##
	
	my $md5_password = md5_hex($SET{'USERNAME'} . $SET{'PASSWORD'} . $session);
	print "Computed MD5: $md5_password\n" if ($SET{'VERBOSE'} >= 2);
	
	my $sock = Net::HTTP->new(Host => $SET{'SWITCH'}) || die $@;
	$sock->write_request(POST => "/tgi/login.tgi",
					'User-Agent' => "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)",
					'Content-Type' => 'application/x-www-form-urlencoded',
					'Cache-Control' => 'no-cache',
					'Accept' => '*/*',
					"Username=$SET{'USERNAME'}&Password=$md5_password&Session=$session"
			);
	
	my($code, $mess, %h) = $sock->read_response_headers;
	
	unless ($code == 302)
	{
		&log_n_die("Didn't recieve code 302 when logging in! $mess");
	}
	
	##
	## Finding the Cookie we should recieve when logged in successfully
	##
	## Set-Cookie : SSID=0f9a03b3d15acd65af5dfcf6550c5eb6; path=/
	##
	
	unless ($h{'Set-Cookie'})
	{
		foreach (sort keys %h)
		{
			print "$_ = $h{$_}\n";
		}
			
		&log_n_die("Didn't recieve cookie! $h{'Set-Cookie'}");
	}
	
	$h{'Set-Cookie'} =~ /(SSID=.+?)\;/ || &log_n_die("Unable to interpret SSID from $h{'Set-Cookie'}!");
	$cookie = $1;
	
	&log("Logged in, using session-cookie: $cookie", $SET{'VERBOSE'});
	
	##
	## Caching the session for future runs
	## Prevents hanging of the web-interface if script is restarted un too often
	##
	
	if ($SET{'COOKIE_FILE'})
	{
		open (COOKIE, ">$SET{'COOKIE_FILE'}") || &log_n_die("Unable to save the cookie to file $SET{'COOKIE_FILE'}!");
		print COOKIE $cookie;
		close COOKIE;
	}
	else
	{
		print "Doesn't cache session\n" if ($SET{'VERBOSE'} >= 2);
	}
}

##
## Resets stats for a port
##

sub clear_stat
{
	my $portnum = shift;
	
	# Port- / Trunk-specifik options
	my $arg = ($portnum =~ /^T(\d+)$/) ? "TrunkNo=$1&TrunkNo\$select=$1" : "PortNo=$portnum&PortNo\$select=$portnum";

	my $sock = Net::HTTP->new(Host => $SET{'SWITCH'}) || die $@;
	$sock->write_request(POST => "/tgi/portstats.tgi",
			'User-Agent' => "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)",
			'Content-Type' => 'application/x-www-form-urlencoded',
			'Cache-Control' => 'no-cache',
			'Accept' => '*/*',
			'Cookie' => $cookie,
			'Referer' => "http://$SET{'SWITCH'}/portStats.htm",
			"$arg&RefreshRate\$select=15000&clrCnts=OK"
	);
	
	my ($code, $mess, %h) = $sock->read_response_headers;
	#print "$code : $mess\n";
	
	#foreach (sort keys %h)
	#{
	#	print "  $_ : $h{$_}\n";
	#}
	
	#unless ($code == 302)
	#{
	#	print "Fick inte tillbaka code 302! $mess\n";
	#}
	
	#my $buf;
	#$sock->read_entity_body($buf, 1024);
	#print $buf;
	
	return ($code == 302);		# 302 = Probably OK
}

##
## Reading systeminfo that doesn't change
##

sub get_system_info
{
	my $res = $ua->get("http://$SET{'SWITCH'}/portConfig.htm",
	       'Cookie' => $cookie
		 );
	
	&log_n_die("Unable to fetch /portConfig.htm - ".$res->status_line) unless ($res->is_success);
	
	# Content
	my $data = $res->content;
	$data =~ tr/\r//d;
	
	if ($data =~ /^<title>Login<\/title>$/m)
	{
		##
		## Invalid session!
		##
		
		&log("Session invalid", $SET{'VERBOSE'});
		
		$cookie = '';
		return 0;
	}
	
	##
	## Finds the info we needs
	##
	
	my $row = 0;
	my %vars = ();
	foreach (split(/\n+/, $data))
	{
		$row++;
		#print "RAD $row: $_\n";
		
		last if (/^<\/script>$/);	# May need adjustment in future firmwares
		next unless (/^var (.+)="(\d*)";$/);
		
		my $name = $1;
		my $value = $2;
		
		#next if ($stat{$name});
		#print "  $name -> $value\n";
		$vars{$name} = $value;
	}
	
	##
	## Parses info about every port
	##
	
	# Setting NumOfPorts if we don't have it
	$numberOfPorts  = $vars{'numberOfPorts'};
	$numberOfTrunks = $vars{'numberOfTrunks'};
	
	my $tot = $numberOfPorts + $numberOfTrunks;
	
	foreach my $num (1 .. $tot)
	{
		my $trunknum = $num - $numberOfPorts;
		my $portnum = $num;
		$portnum = "T$trunknum" if ($trunknum > 0);	# It's a trunk
		
		my $PORT = $PORTS{$portnum} ||= { 'link_changes' => 0, 'link_change_at' => 'No change detected' };
		
		# var portLinkList="0101110101011100000000";
		# var portSpeedList="0202110201012200333333";
		# var portDuplexList="0101110101011100222222";
		# var portANList="1111111111111111222222";
		# var portBPList="0000000000000000222222";
		# var portFcList="0100000100000000333333";
		
		my $index = $num - 1;
		my $portLink   = substr($vars{'portLinkList'},   $index, 1);
		my $portSpeed  = substr($vars{'portSpeedList'},  $index, 1);
		my $portDuplex = substr($vars{'portDuplexList'}, $index, 1);
		#my $portAN     = substr($vars{'portANList'},     $index, 1);
		my $portFc     = substr($vars{'portFcList'},     $index, 1);
		my $portBP     = substr($vars{'portBPList'},     $index, 1);
		
		#$PORT->{'link'} = '-' if ($PORT->{'link'} eq 'up?');
		my $link = 'down';
		
		if ($portLink)
		{
			$link = ('10','100','1000','active')[$portSpeed];
			$link .= ('/Half','/Full','')[$portDuplex];
			$link .= '/FC' if ($portFc == 1);
			$link .= '/BP' if ($portBP == 1);
		}
		
		if ($PORT->{'link'} && ($link ne $PORT->{'link'}))
		{
			&log("$do_ports{$portnum} (port $portnum): Change of link detected; $PORT->{'link'} -> $link", $SET{'VERBOSE'} >= 3);
			
			# To show in the HTML
			$PORT->{'link_changes'}++;
			$PORT->{'link_change_at'} = "Change detected $time{'date_time'}; $PORT->{'link'} -> $link. $PORT->{'link_changes'} changes";
			
			# Triggers the link to be shown in bold
			$STATS{$portnum}->{'link_changed'} = 1;
		}
		
		$PORT->{'link'} = $link;
		$PORT->{'is_active'} = $PORT->{'has_link'} = $portLink;
		
		if ($SET{'AUTOADD_ACTIVE_PORTS'} && $portLink && !defined $SET{'PORTS'}->{$portnum})
		{
			##
			## We shall autoadd active ports
			##
			
			if ($trunknum > 0)
			{
				$SET{'PORTS'}->{$portnum} = "Trunk $trunknum";
				&log("Auto-added trunk $portnum as $portnum, link detected", $SET{'VERBOSE'} >= 1);
			}
			else
			{
				$SET{'PORTS'}->{$portnum} = "Port $portnum";
				&log("Auto-added port $portnum, link detected", $SET{'VERBOSE'} >= 1);
			}
			
			$autoadded_ports = 1;
		}
	}
	return 1;
}


##
## Rewrites 1024 as 1k, with a given number of digits
##

sub shorten_num
{
	my $num = shift;
	my $digits = shift || 3;
	my $base = shift || 1024;
	my $unit = '';
	
	foreach (qw(k M G T P E Z Y))
	{
		last if ($num < $base);
		
		$unit = $_;
		$num /= $base;
	}
	
	$num = ($num >= 1000) ? substr($num, 0, 4) : substr($num, 0, $digits);
	$num =~ s/(\.\d*)0+$/$1/;
	$num =~ s/\.$//;
	
	return $num . $unit;
}

sub log
{
	my $text = shift;
	my $print_too = shift;
	
	print "$text\n" if ($print_too);
	
	unless (fileno(LOG))
	{
		return unless ($SET{'LOG_FILE'});
		open (LOG, ">>$SET{'LOG_FILE'}") || die("Unable to write to $SET{'LOG_FILE'}!");
		
		select LOG;
		$| = 1;
		select STDOUT;
	}

	print LOG "$time{'date_time'}: $text\n";
}

sub log_n_die
{
	my $text = shift;
	&log($text);
	die $text;
}


sub upd_time
{
	my $unixtime = shift || time;

	my ($sec,$min,$hour,$daynum,$monnum,$year,$weekdaynum) = localtime($unixtime);
	$sec = "0$sec" if ($sec < 10);
	$min = "0$min" if ($min < 10);
	$hour = "0$hour" if ($hour < 10);
	my $day = $daynum;
	$day = "0$day" if ($day < 10);
	my $mon = $monnum + 1;
	$mon = "0$mon" if ($mon < 10);
	$year += 1900;

	%time = (sec => $sec, min => $min, hour => $hour, day => $day, daynum => $daynum, weekdaynum => $weekdaynum, mon => $mon, monnum => $monnum, year => $year, unixtime => $unixtime, date_time => "$year-$mon-$day $hour:$min:$sec", date => "$year-$mon-$day", 'time' => "$hour:$min:$sec");
}

##
## Reads the settings
## Returns 0 when fails
##

sub refresh_settings
{
	##
	## Checks if the settings-file has changed
	##
	
	my $file_modified = (stat($SETTINGS_FILE))[9];
	die "Unable to access settings-file '$SETTINGS_FILE'!" unless ($file_modified);
	
	if ($file_modified eq $SET{'_modified'})
	{
		# 0 = No change
		return 0;
	}
	
	##
	## Changed, re-reading settings
	##
	
	my %NEW_SET = ();
	$NEW_SET{'_modified'} = $file_modified;
	
	if ($SET{'_modified'})
	{
		&log("Refreshing settings from $SETTINGS_FILE...", $SET{'VERBOSE'});
	}
	
	# Prevents attempts to re-read settings if errors are found
	$SET{'_modified'} = $NEW_SET{'_modified'};
	
	##
	## Reads the settings
	##
	
	open (SET, $SETTINGS_FILE) || &log_n_die("Unable to open settings-file '$SETTINGS_FILE'!");
	while (<SET>)
	{
		next if (/^#/);		# Ignoring comments
		tr/\n\r//d;			# Stripping linefeeds
		s/\s+$//;			# Stripping trailing white spaces
		next unless ($_);	# Ignores empty lines
		
		my ($name, $value) = split(/\s*=\s*/, $_, 2);
		
		$NEW_SET{$name} = $value;
	}
	close SET;
	
	##
	## Checking basic required variables
	##
	
	my $errors = 0;
	
	foreach (qw(SWITCH USERNAME PASSWORD REFRESH_RATE POLL_LINKINFO_EVERY_NTH_LOOP
				VERBOSE GRAPHS_PER_ROW GRAPH_WIDTH GRAPH_HEIGHT))
	{
		next if (defined $NEW_SET{$_});
		
		&log("Error: Required setting $_ is not defined", 1);
		$errors++;
	}
	
	##
	## Checking numerical-only settings
	##
	
	foreach (qw(REFRESH_RATE POLL_LINKINFO_EVERY_NTH_LOOP VERBOSE
				AUTOADD_ACTIVE_PORTS HIDE_INACTIVE_PORTS
				GREY_BELOW_PPS BOLD_ABOVE_PPS GREY_BELOW_BPS
				BOLD_ABOVE_BPS GREY_BELOW_PROC
				GRAPH_BPS_EVERY_NTH_LOOP GRAPH_PPS_EVERY_NTH_LOOP
				RRD_STEP GRAPH_WIDTH GRAPH_HEIGHT GRAPHS_PER_ROW
				GRAPH_SLOPE_MODE ))
	{
		next unless (defined $NEW_SET{$_});
		next if ($NEW_SET{$_} =~ /^\d+$/);
		
		# Permits simple expressions
		if ($NEW_SET{$_} =~ /^[\d\-+*\/\.\,]+$/)
		{
			my $new_value = eval $NEW_SET{$_};
			if ($new_value =~ /^(\d+)(\.\d+)?$/)
			{
				#print "$new_value = $1 (stripped $2);\n";
				$NEW_SET{$_} = $1;
			}
			else
			{
				&log("Error: Setting $_; evaluation of expression '$NEW_SET{$_}' failed $!", 1);				
				$errors++;
			}
			next;
		}
		
		&log("Error: Setting $_ requires a numerical integer value, has value '$NEW_SET{$_}'", 1);
		$errors++;
	}
	
	##
	## Checking >1 values
	##
	
	foreach (qw(REFRESH_RATE POLL_LINKINFO_EVERY_NTH_LOOP RRD_STEP
				GRAPH_WIDTH GRAPH_HEIGHT))
	{
		next unless (defined $NEW_SET{$_});
		next if ($NEW_SET{$_} >= 1);
		
		&log("Error: Setting $_ requires a numerical integer value larger that zero, has value '$NEW_SET{$_}'", 1);
		$errors++;
	}
	
	##
	## Checking if RRDs should be written and the module can be loaded
	##
	
	if ($NEW_SET{'RRD_FILE_PREFIX'})
	{
		##
		## Shall log to RRD
		##
		
		eval('use RRDs;');
		if ($@)
		{
			##
			## Unable to load the RRDs-module!
			##
			
			$NEW_SET{'RRD_FILE_PREFIX'} = '';
			&log("Setting 'RRD_FILE_PREFIX' is set, but the perl-module RRDs.pm could not be loaded! Disabling RRD", 1);
		}
	}
	
	##
	## Shall some ports be excluded from RRD and graphing?
	##
	
	foreach my $portnum (split(/\s*,\s*/, $NEW_SET{'RRD_SKIP_THESE_PORTS'}))
	{
		# Rewrites the list for easy checking
		$NEW_SET{"RRD_SKIP_PORT_$portnum"} = 1;
	}
	
	##
	## If hiding inactive ports, always show these ports
	##
	
	foreach my $portnum (split(/\s*,\s*/, $NEW_SET{'ALWAYS_SHOW_THESE_PORTS'}))
	{
		# Rewrites the list for easy checking
		$NEW_SET{"ALWAYS_SHOW_PORT_$portnum"} = 1;
	}

	foreach my $portnum (split(/\s*,\s*/, $NEW_SET{'SHOW_THESE_PORTS'}))
	{
		# Rewrites the list for easy checking
	    @do_ports=(@do_ports,$portnum);
	}
	
	##
	## Parsing ports and graphs
	##
	
	my %PORTS = ();
	my %CHAINS = ();
	my @GRAPHS = ();
	my @RRD_RRA = ();
	
	foreach (sort keys %NEW_SET)
	{
		#print "$_\n";
		
		if (/^(PORT|TRUNK|INTERFACE)\s*(.+)$/)
		{
			##
			## This is a switch PORT/TRUNK or a local interface
			## PORT 1 = Router
			##
			
			my $port = $2;
			$port = "T$2" if ($1 eq 'TRUNK');
			#$port = "I$2" if ($1 eq 'INTERFACE');
			# Skips already defined ports
			if ($PORTS{$port})
			{
				&log("Warn: Port $port is already defined, ignoring this definition", 1);
				next;
			}
			
			# Memorizing this name
			$PORTS{$port} = $NEW_SET{$_};
		}
		elsif (/^CHAIN\s*(\d+)\s*(.+)$/)
		{
			##
			## This is a CHAIN
			## CHAIN 1 joxx:-1 = Router
			##
			
			my $port = "C$1";
			
			# Skips already defined ports
			if ($PORTS{$port})
			{
				&log("Warn: Port $port is already defined, ignoring this definition", 1);
				next;
			}
			
			$CHAINS{$port} = $2;
			
			# Memorizing this name
			$PORTS{$port} = $NEW_SET{$_};
		}
		elsif (/^GRAPH\s*(\d+)\s*\"(.+)\"$/)
		{
			##
			## This is a GRAPH
			## GRAPH "Past 3 hours"	= 60*60*3
			##
			
			my $index = $1;
			my $name = $2;
			
			# Permits simple expressions
			if ($NEW_SET{$_} =~ /^[\d\-+*\/\.\,]+$/)
			{
				my $new_value = eval $NEW_SET{$_};
				if ($new_value =~ /^(\d+)(\.\d+)?$/)
				{
					#print "$new_value = $1 (stripped $2);\n";
					$NEW_SET{$_} = $1;
				}
				else
				{
					&log("Error: Setting $_; evaluation of expression '$NEW_SET{$_}' failed $!", 1);				
					$errors++;
					next;
				}
			}
			
			# Memorizing this graph
			push (@GRAPHS, [$index, $name, $NEW_SET{$_}] );
		}
		elsif (/^RRD_RRA/)
		{
			##
			## This is a RRD RRA-statement
			## RRD_RRA 1 = RRA:AVERAGE:0.5:1:360
			##
			
			# Memorizing this RRA-definition
			push (@RRD_RRA, $NEW_SET{$_});
		}
	}
	
	if ($NEW_SET{'SYNC_SLEEP_TO_RRD'} && ($NEW_SET{'RRD_STEP'} != $NEW_SET{'REFRESH_RATE'}))
	{
		&log("Can only sync sleep to RRD_STEP if REFRESH_RATE == RRD_STEP", 1);
		$NEW_SET{'SYNC_SLEEP_TO_RRD'} = 0;
	}
	
	# No ports defined?
	unless (%PORTS)
	{
		&log("Err:  No PORTs to poll is defined!", 1);
		$errors++;
	}
	
	# Memorizing the ports
	$NEW_SET{'PORTS'} = \%PORTS;
	
	# No graphs defined?
	if (($NEW_SET{'GRAPH_BPS_EVERY_NTH_LOOP'} || $NEW_SET{'GRAPH_PPS_EVERY_NTH_LOOP'}) && !@GRAPHS)
	{
		&log("Err:  No GRAPHs to create is defined!", 1);
		$errors++;
	}

	# Is selected GRAPH_OVERVIEW defined? FIXME
	#if ($NEW_SET{'GRAPH_RRD_EVERY_NTH_LOOP'} && !defined $GRAPHS[ $NEW_SET{'GRAPH_OVERVIEW'}-1 ])
	#{
	#	&log("Err:  Selected GRAPH_OVERVIEW $NEW_SET{'GRAPH_OVERVIEW'} does not exist!", 1);
	#	$errors++;
	#}
	
	# Memorizing the ports
	$NEW_SET{'GRAPHS'} = \@GRAPHS;

	# No RRD RRAs defined?
	if ($NEW_SET{'RRD_FILE_PREFIX'} && !@RRD_RRA)
	{
		&log("Err:  No RRD_RRAs is defined! Cannot create RRD-files without any", 1);
		$errors++;
	}
	
	# Memorizing the RRA definitions
	$NEW_SET{'RRD_RRA'} = \@RRD_RRA;
	
	# Memorizing the chains
	$NEW_SET{'CHAINS'} = \%CHAINS;
	
	##
	## Returns a 2 if basic errors are encountered
	##
	
	return 2 if ($errors);
	
	# Activating settings
	%SET = %NEW_SET;
	close LOG;
	
	# 1 = Settings changed
	return 1;
}

##
## Rewrites sysUpTime to a readable time
##

sub parse_sysuptime
{
	my $secs = $system{'sysUpTime'};
	$secs =~ s/\d\d$//;	# Strips last two digits to only have seconds left
	
	my $uptime = '';
	my $days = int($secs / 86400);
	if ($days)
	{
		$secs -= $days*86400;
		$uptime .= "${days}d";
	}
	my $hours = int($secs / 3600);
	if ($hours || $days)
	{
		$secs -= $hours*3600;
		$uptime .= "${hours}h";
	}
	my $mins = int($secs / 60);
	$uptime .= "${mins}m";
	#$secs -= $mins*60;
	#$uptime .= "${mins}m${secs}s";
	
	$system{'uptime'} = $uptime;
}

##
## 12345678 -> 12'345'678
##

sub add_sep
{
	my $value = shift;
	$value =~ s/(\.\d+)$//;
	my $decimals = ($1 && $value < 10) ? $1 : '';
	$value = reverse $value;
	
	$value =~ s/(\d\d\d)/$1\'/g;
	$value =~ s/\'$//;
	return (reverse $value) . substr($decimals, 0, 3);
}

##
## Creates a empty RRD-file
##

sub create_rrd
{
	my $filename = shift || &error("No filename given to create as a RRD-file!");
	
	# Will not overwrite a existing file
	return (1,"File '$filename' already existed!") if (-e $filename);
	
	# Creating RRD-file
	RRDs::create(
		$filename,
		'--step',
		$SET{'RRD_STEP'},
		'DS:kbps_out:GAUGE:60:0:1000000',
		'DS:kbps_in:GAUGE:60:0:1000000',
		'DS:pps_out:GAUGE:60:0:10000000',
		'DS:pps_in:GAUGE:60:0:10000000',
		'DS:err_pps_out:GAUGE:60:0:10000000',
		'DS:err_pps_in:GAUGE:60:0:10000000',
		@{$SET{'RRD_RRA'}}
	);
	
	my $error = &RRDs::error;
	return(0, "Unable to create RRD-file '$filename'!: $error") if ($error);
	
	my $size = -s $filename;
	unless ($size > 0)
	{
		unlink $filename;	# Removes the file so we won't try to write to it later
		return(0, "Unable to create RRD-file '$filename'!: Zero size file");
	}
	
	return (1, "File '$filename' created, size $size bytes");
}

##
## Generate graphs
##

sub generate_rrd_graphs
{
	my $type = shift || 'bps';	# bps or pps
	
	##
	## Yep, let's create a bunch of files 
	##
	
	$SET{'HTML_FILE'} =~ /^(.*\/)?([^\/]+)(\.[^.]+)$/;
	my $path = $1;		# path/to/
	my $base = $2;		# stats-file
	my $suffix = $3;	# .html
	
	#print "$path - $base - $suffix\n";
	
	# How often the graph-page will change
	my $refresh = $SET{'REFRESH_RATE'} * $SET{'GRAPH_' . uc $type . '_EVERY_NTH_LOOP'};
	
	# Shall we also graph the other type? Otherwise no links
	my $graph_other_type = $SET{'GRAPH_' . uc $graph_rev_type{$type} . '_EVERY_NTH_LOOP'};
	
	my $ucname = ucfirst $graph_names{$type};
	my $rev = $graph_rev_type{$type};	# bps -> pps, pps -> bps
	my $switch = $system{'systemName'} || $SET{'SWITCH'};
	
	my @index_graphs = ();		# We will create the page last
	
	##
	## Creates a overview for this type
	##
	
	foreach my $portnum (@do_ports)
	{
		##
		## Does each port
		##
		
		# Skip this port
		next if ($SET{"RRD_SKIP_PORT_$portnum"});
		
		my $rrd_filename = $SET{'RRD_FILE_PREFIX'} . "_$portnum.rrd";
		next unless (-s $rrd_filename);	# Will not proceed unless the rrd-data exist
		
		##
		## Writes indexfile for each port that will contain the graphs
		##
		
		my @port_graphs = ();		# We will create the page last
		
		my $rrd_port_file = "${base}_${type}_${portnum}$suffix";
		$PORTS{$portnum}->{"rrd_${type}_file"} = $rrd_port_file;	# Remembering
		
		##
		## Creating graphs
		##
		
		my @graph_args = ('--interlaced','--lazy');
		push (@graph_args, '--slope-mode') if ($SET{'GRAPH_SLOPE_MODE'});
		
		foreach my $graph_ref (@{$SET{'GRAPHS'}})
		{
			my $i			= $graph_ref->[0];
			my $graph_name	= $graph_ref->[1];
			my $graph_secs	= $graph_ref->[2];
			
			my $image_filename = "${base}_${type}_$portnum-$i.png";
			#unlink $image_filename if ($erase_images);
			
			##
			## Mega graph command
			##
			
			my $averages = my $xsize = my $ysize = 0;
			
			if ($type eq 'bps')
			{
				##
				## Bits/s
				##
				
				($averages,$xsize,$ysize) = RRDs::graph("$path$image_filename",
					"--start=end-$graph_secs",
					"--title=$do_ports{$portnum} ($portnum) on $switch - $graph_name",
					"--vertical-label=$graph_names{$type}",
					'--base=1024',
					'--imgformat=PNG',
					"--width=$SET{'GRAPH_WIDTH'}",
					"--height=$SET{'GRAPH_HEIGHT'}",
					'--alt-y-mrtg',
					@graph_args,
					"DEF:b=$rrd_filename:kbps_out:AVERAGE",
					"DEF:a=$rrd_filename:kbps_in:AVERAGE",
					'CDEF:B=b,1024,*',
					'CDEF:A=a,1024,*',
					'AREA:B#00FF00:bit/s OUT',
					'VDEF:B_AVERAGE=B,AVERAGE',
					'GPRINT:B_AVERAGE:Avg\: %7.2lf%s ',
					'VDEF:B_MAX=B,MAXIMUM',
					'GPRINT:B_MAX:Max\: %7.2lf%s ',
					'VDEF:B_LAST=B,LAST',
					'GPRINT:B_LAST:Last\: %7.2lf%s\n',
					'LINE1:A#0000FF:bit/s IN ',
					'VDEF:A_AVERAGE=A,AVERAGE',
					'GPRINT:A_AVERAGE:Avg\: %7.2lf%s ',
					'VDEF:A_MAX=A,MAXIMUM',
					'GPRINT:A_MAX:Max\: %7.2lf%s ',
					'VDEF:A_LAST=A,LAST',
					'GPRINT:A_LAST:Last\: %7.2lf%s'
				);
			}
			else
			{
				##
				## Packets/s
				##
				
				($averages,$xsize,$ysize) = RRDs::graph("$path$image_filename",
					"--start=end-$graph_secs",
					"--title=$do_ports{$portnum} ($portnum) on $switch - $graph_name",
					"--vertical-label=$graph_names{$type}",
					'--base=1000',
					'--imgformat=PNG',
					"--width=$SET{'GRAPH_WIDTH'}",
					"--height=$SET{'GRAPH_HEIGHT'}",
					'--alt-y-mrtg',
					@graph_args,
					"DEF:d=$rrd_filename:pps_out:AVERAGE",
					"DEF:c=$rrd_filename:pps_in:AVERAGE",
					"DEF:f=$rrd_filename:err_pps_out:AVERAGE",
					"DEF:e=$rrd_filename:err_pps_in:AVERAGE",
					'AREA:d#00FF00:Pkts/s OUT    ',
					'VDEF:d_AVERAGE=d,AVERAGE',
					'GPRINT:d_AVERAGE:Avg\: %7.2lf%s',
					'VDEF:d_MAX=d,MAXIMUM',
					'GPRINT:d_MAX:Max\: %7.2lf%s',
					'VDEF:d_LAST=d,LAST',
					'GPRINT:d_LAST:Last\: %7.2lf%s\n',
					'LINE1:c#0000FF:Pkts/s IN     ',
					'VDEF:c_AVERAGE=c,AVERAGE',
					'GPRINT:c_AVERAGE:Avg\: %7.2lf%s',
					'VDEF:c_MAX=c,MAXIMUM',
					'GPRINT:c_MAX:Max\: %7.2lf%s',
					'VDEF:c_LAST=c,LAST',
					'GPRINT:c_LAST:Last\: %7.2lf%s\n',
					'LINE1:f#FFA500:Err pkts/s OUT',
					'VDEF:f_AVERAGE=f,AVERAGE',
					'GPRINT:f_AVERAGE:Avg\: %7.2lf%s',
					'VDEF:f_MAX=f,MAXIMUM',
					'GPRINT:f_MAX:Max\: %7.2lf%s',
					'VDEF:f_LAST=f,LAST',
					'GPRINT:f_LAST:Last\: %7.2lf%s\n',
					'LINE1:e#FF0000:Err pkts/s IN ',
					'VDEF:e_AVERAGE=e,AVERAGE',
					'GPRINT:e_AVERAGE:Avg\: %7.2lf%s',
					'VDEF:e_MAX=e,MAXIMUM',
					'GPRINT:e_MAX:Max\: %7.2lf%s',
					'VDEF:e_LAST=e,LAST',
					'GPRINT:e_LAST:Last\: %7.2lf%s'
				);
			}
			
			my $error = &RRDs::error;
			&log("Unable to create graph '$path$image_filename'! $error", 1) if ($error);
			
			##
			## We create graphs first and then the page
			##
			
			push (@port_graphs,  "<img src=\"$image_filename\" width=$xsize height=$ysize>");
			push (@index_graphs, "<a href=\"$rrd_port_file\"><img src=\"$image_filename\" width=$xsize height=$ysize border=0></a>") if ($i == $SET{'GRAPH_OVERVIEW'});
			
			# This graph is done
		}
		
		##
		## All graphs for this port done, create the page
		##
		
		my $switch_link = ($graph_other_type) ? "<br><a href=\"${base}_${rev}_$portnum$suffix\">Switch to $graph_names{$rev}</a>" : '';

		open(HTML, ">$path$rrd_port_file");
		print HTML <<END;
<html>
<head>
<title>$ucname for $do_ports{$portnum} (port $portnum) on $SET{'SWITCH'} - $system{'systemName'} $system{'locationName'}</title>
<meta http-equiv="Refresh" content="$refresh">
</head>

<body bgcolor="White" text="Black" link="Black" vlink="Black" alink="Red" style="font-family: Verdana, Geneva, Arial, Helvetica, sans-serif; font-size: 9;">
<center>
<div style="font-size: 13; font-weight: bold;">$ucname - $do_ports{$portnum} (port $portnum)</div>
$switch_link
<br><a href="${base}_$type$suffix">Show overview for $graph_names{$type}</a>

<p>
END
			
		my $i = 0;
		foreach (@port_graphs)
		{
			print HTML $_;
			print HTML "<br>\n" if ($SET{'GRAPHS_PER_ROW'} && (++$i % $SET{'GRAPHS_PER_ROW'} == 0));
		}
		
		print HTML <<END;
<p>Page updated $time{'date_time'}, refreshes every $refresh sec
		
</center>
</body>
</html>
END
		close HTML;
		# Page for BPS or PPS done för this port
	}
	
	##
	## Writing the index-page for this type
	##
	
	my $type_index_file = "${base}_${type}$suffix";
	my $switch_link = ($graph_other_type) ? "<br><a href=\"${base}_$rev$suffix\">Switch to $graph_names{$rev}</a>" : '';
	
	open(HTML, ">$path$type_index_file");
	print HTML <<END;
<html>
<head>
<title>Overview $graph_names{$type} on $SET{'SWITCH'} - $system{'systemName'} $system{'locationName'}</title>
<meta http-equiv="Refresh" content="$refresh">
</head>

<body bgcolor="White" text="Black" link="Black" vlink="Black" alink="Red" style="font-family: Verdana, Geneva, Arial, Helvetica, sans-serif; font-size: 9;">
<center>
<div style="font-size: 13; font-weight: bold;">$ucname - Overview</div>
$switch_link
<br><a href="$base$suffix">Return to statistics</a>
<p>
END
	
	my $i = 0;
	foreach (@index_graphs)
	{
		print HTML $_;
		print HTML "<br>\n" if ($SET{'GRAPHS_PER_ROW'} && (++$i % $SET{'GRAPHS_PER_ROW'} == 0));
	}
	
	print HTML <<END;
<p>Page updated $time{'date_time'}, refreshes every $refresh sec

</center>
</body>
</html>
END
	close HTML;
	
	##
	## All ports done
	##
	
	return 1;
}

##
## Poll local interface
## We could easily have done this buffered instead
##

sub poll_interface
{
	my $interface = shift;
	my $STAT = shift || {};
	my $ok = 0;
	
	if ($SET{'USE_ETHTOOL'})
	{
		##
		## Poll using ethtool, hopefully
		##

		unless (open (ETHTOOL, "$SET{'USE_ETHTOOL'} -S $interface |"))
		{
			##
			## Unable to poll successfully!
			##
			
			&log("Unable to run command '$SET{'USE_ETHTOOL'} -S $interface'! Disabling polling using ethtool", 1);
			$SET{'USE_ETHTOOL'} = '';
		}
		else
		{
			##
			## We were able to run the command (hopefully)
			##
			
			my %E = ();
			
			while (<ETHTOOL>)
			{
				#     rx_packets: 6207689
				#     tx_packets: 2174409
				#     rx_bytes: 4192981116
				
				chomp;
				s/^\s*//;
				my ($name, $value) = split(/\s*\:\s*/, $_, 2);
				next unless ($value =~ /^\d+$/);	# Makes sure we don't take crap
				
				$E{$name} = $value;
			}
			close ETHTOOL;
			
			##
			## Sanity-check
			##
			
			my $not_ok = 0;
			
			foreach (qw(rx_bytes rx_packets tx_bytes tx_packets))
			{
				next if (defined $E{$_});
				
				&log("Ethtool didn't provide expected variable $_!", 1);
				$not_ok++;
			}
			
			if ($not_ok)
			{
				&log("Disabling polling using ethtool, ($SET{'USE_ETHTOOL'} -S $interface)", 1);
				$SET{'USE_ETHTOOL'} = '';
			}
			else
			{
				##
				## Okay, I'm not certain about the more unusual errors like FCS
				##
				
				$STAT->{'goodOctetRCV'}	= $E{'rx_bytes'};
				$STAT->{'rxGood'}		= $E{'rx_packets'};
				$STAT->{'rxBroadcast'}	= $E{'rx_broadcast'} if (defined $E{'rx_broadcast'});
				$STAT->{'rxMulticast'}	= $E{'rx_multicast'} if (defined $E{'rx_multicast'});
				$STAT->{'rxFCS'}		= $E{'rx_align_errors'} + $E{'rx_crc_errors'} if (defined $E{'rx_align_errors'} || defined $E{'rx_crc_errors'});
				$STAT->{'rxUnderSize'}	= $E{'rx_short_length_errors'} if (defined $E{'rx_short_length_errors'});
				$STAT->{'rxOverSize'}	= $E{'rx_long_length_errors'} if (defined $E{'rx_long_length_errors'});
				$STAT->{'rxFragment'}	= '';	# Available?
				$STAT->{'rxJabber'}		= '';	# Available?
				
				$STAT->{'goodOctetSND'}	= $E{'tx_bytes'};
				$STAT->{'txGood'}		= $E{'tx_packets'};
				$STAT->{'txBroadcast'}	= $E{'tx_broadcast'} if (defined $E{'tx_broadcast'});
				$STAT->{'txMulticast'}	= $E{'tx_multicast'} if (defined $E{'tx_multicast'});
				$STAT->{'txDrop'}		= $E{'tx_dropped'} if (defined $E{'tx_dropped'});
				$STAT->{'txCollisions'}	= $E{'collisions'} if (defined $E{'collisions'});
				
				$ok = 1;
			}
		}
	}
	
	unless ($SET{'USE_ETHTOOL'})
	{
		##
		## Don't poll using ethtool
		## Opens /proc/net/dev to read some statistics
		##
		
		unless (open (DEV, '/proc/net/dev'))
		{
			&log("Unable to open '/proc/net/dev'!", 1);
			return 0;
		}
		
		# Searching for this interface
		while (<DEV>)
		{
			#Inter-|   Receive                                                |  Transmit
			# face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
			#  eth0:230864891  964901    0    0    0     0          0         0 867172607  936222    0    0    0     0       0          0
			
			if (/^\s*$interface\:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)$/)
			{
				##
				## Remembering some data
				##
				
				$STAT->{'goodOctetRCV'}	= $1;
				$STAT->{'rxGood'}		= $2;
				$STAT->{'rxFCS'}		= $6;
				$STAT->{'rxUnderSize'}	= '';
				$STAT->{'rxOverSize'}	= '';
				$STAT->{'rxFragment'}	= '';
				$STAT->{'rxJabber'}		= '';
				
				$STAT->{'goodOctetSND'}	= $9;
				$STAT->{'txGood'}		= $10;
				$STAT->{'txDrop'}		= $12;
				$STAT->{'txCollisions'}	= $14;
				
				$ok = 1;
				
				last;
			}
		}
		close DEV;
	}
	
	return $ok;
}

##
## Polls values from a local iptables' chain
##

sub poll_chain
{
	my $chain = shift;
	my $STAT = shift || {};
	my $ok = 0;
	
	$STAT->{'goodOctetRCV'}	= 0;
	$STAT->{'rxGood'}		= 0;
	$STAT->{'rxFCS'}		= '';
	$STAT->{'rxUnderSize'}	= '';
	$STAT->{'rxOverSize'}	= '';
	$STAT->{'rxFragment'}	= '';
	$STAT->{'rxJabber'}		= '';
	
	$STAT->{'goodOctetSND'}	= 0;
	$STAT->{'txGood'}		= 0;
	$STAT->{'txDrop'}		= '';
	$STAT->{'txCollisions'}	= '';
	
	##
	## Reads info from iptables
	## chain1,1,chain2,1
	##
	
	my @def = split(/\s*,\s*/, $chain);
	my @chains = (['OUT',$def[0],$def[1]], ['IN',$def[2],$def[3]]);
	
	foreach my $ref (@chains)
	{
		my $type = $$ref[0];
		my $chain = $$ref[1];
		my $row = $$ref[2] || -1;	# Defaults to last row
		next unless ($chain);
		
		unless (open (CHAIN, "iptables -vxn -L $chain |"))
		{
			&log("Unable to execute command 'iptables -vxn -L $chain'!", 1);
			return 0;
		}
		
		my @lines = <CHAIN>;
		close CHAIN;
		
		# Make -1 become the last actual row
		# If 4 lines total, 4-1 = 3 = lines[3] = last row
		$row = scalar @lines + $row if ($row < 0);
		
		# Pick the line to parse
		my $line = $lines[$row];
		
		# Parse that line, expect the first two lines to be packets and bytes
		#     7477   478570 RETURN     all  --  *      *       0.0.0.0/0            0.0.0.0/0
		unless ($line =~ /^\s*(\d+)\s+(\d+)\s/)
		{
			&log("Parsing chain '$chain'; row $row contained '$line', unable to parse!", 1);
			return 0;
		}
		
		#print "$type: $chain - row $row = $line -> pkts $1, bytes $2\n";
		
		if ($type eq 'OUT')
		{
			$STAT->{'goodOctetSND'}	= $2;
			$STAT->{'txGood'}		= $1;
		}
		else
		{
			$STAT->{'goodOctetRCV'}	= $2;
			$STAT->{'rxGood'}		= $1;
		}
	}
	return 1;
}
