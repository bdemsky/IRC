/* Written and copyright 2001 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class Jhttpp2ServerInputStream extends BufferedInputStream {
        private Jhttpp2HTTPSession connection;

    public Jhttpp2ServerInputStream(Jhttpp2Server server,Jhttpp2HTTPSession connection,InputStream a,boolean filter) {
	super(a);
	this.connection=connection;
    }
    public int read_f(byte[] b) {
	return read(b);
    }
}

