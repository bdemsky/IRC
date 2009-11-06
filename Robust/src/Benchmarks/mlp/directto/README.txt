Any statement *** in asterix *** is an assertion that
disjoint reachability analysis should be able to verify.

The DirectTo benchmark executes a list of messages from 
an input file to solve a safe aircraft routing problem.


The D2 (direct-to) singleton object has a singleton 
reference to:
 -ReadWrite, reads input file, creates messages
 -MessageList, commands to execute for building problem
 -Static, structure of static constants from input file
 -AircraftList, all types of aircraft in problem
 -FlightList, list of flights in algorithm
 -FixList, list of fixes algorithm computes
 -Algorithm, reads from Static
 -TrajectorySynthesizer, reads from Static
 -Flight, why a singleton Flight object?
so:
*** all memory in the program should be reachable from
at most one of any singleton (D2, Static, etc) ***


MessageList has a Vector of Message objects, where each
one specifys an effect for other structures such as 
setting a constant in the Static singleton, or adding
an aircraft type to the Aircraft list, etc.  Message
objects themselves only have references that are freshly
allocated, so:
*** Message objects should be disjoint ***
ANALYSIS FALSELY REPORTS SHARING


The ReadWrite singleton appends new Message objects
to the MessageList and has no references of its own,
*** Nothing is reachable from a ReadWrite ***


The Static singleton has primitive members, so
*** Nothing is reachable from a Static ***


AircraftList has a Vector of Aircraft objects, where
each one has a String for type and some primitive
attributes, where the type is generated from a
StringTokenizer (getting a token gets a new String),
and in practice is disjoint for every Aircraft, so:
*** Aircraft objects should be disjoint *** 
VERIFIED BY ANALYSIS


FixList has a Vector of Fix objects, where each one
has a String name and a Point2d (an alternate point
in a flight plan?).  I believe they are not modified
after being built from freshly allocated tokenizing,
*** Fix objects should be disjoint ***
ANALYSIS FALSELY REPORTS SHARING


FlightList has a Vector of Flight objects which have
several fresh, set-once references (flightID String, 
a Track, etc).
*** Flight objects may share Aircraft (types) ***
VERIFIED BY ANALYSIS

...and other objects also.


In Message.executeMessage(), if you comment out every
message handler except any one of these, they cause 
Message objects to show sharing:
 - FlightList.addFlightPlan()
 - FlightList.amendFlightInfo()
 - FlightList.amendFlightPlan() (still a quick analysis!)
 - FlightList.sendingAircraft()
