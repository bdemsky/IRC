Readme file for Tuplesoup v0.1.1 released by Kasper J. Jeppesen September 7, 2007.

If you have any comments you can reach the developers of tuplesoup at kjj@solidosystems.com

[ Introduction ]
Tuplesoup is a small easy to use Java based framework for storing and retrieving simple hashes.
The latest version of Tuplesoup is always available from http://sourceforge.net/projects/tuplesoup

[ License ]
Tuplesoup has been released as open source under the BSD license by Solido Systems. See the file license.txt for the full license.

[ Documentation ]
By an amazing combination of laziness and procrastination I have not yet created an actual site for Tuplesoup. However, you can find a some posts about the design and usage of tuplesoup on my tech blog http://syntacticsirup.blogspot.com/

[ Installation ]
The tuplesoup distribution contains a jar file which can be placed in your java extensions folder or added to your classpath. No further installation steps are necesary.

[ Update ]
There is currently no procedures needed to perform an update other than just replacing the jar file. However, you should always read the changelog for any update procedures necesary between the file formats of future releases.

[ Changelog ]
 * 0.1.2
    - Made table an interface, instantiate DualFileTable to get the same behaviour as before
    - Added HashedTable which provides better multi threaded performance for single row queries
 * 0.1.1
    - First public release