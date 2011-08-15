public class SideInfoBuffer {

	/**
	 * The frame buffer that holds the data for the current frame.
	 */
	private final int[] framebuffer = new int[BUFFER_INT_SIZE];

	/**
	 * Maximum size of the frame buffer.
	 */
	private static final int BUFFER_INT_SIZE = 433;

	/**
	 * Index into <code>framebuffer</code> where the next bits are retrieved.
	 */
	private int wordpointer;

	/**
	 * Number (0-31, from MSB to LSB) of next bit for get_bits()
	 */
	private int bitindex;

	private int main_data_begin;

	public int getMain_data_begin() {
		return main_data_begin;
	}

	public void setMain_data_begin(int main_data_begin) {
		this.main_data_begin = main_data_begin;
	}

	private final int bitmask[] = {
			0, // dummy
			0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F,
			0x0000003F, 0x0000007F, 0x000000FF, 0x000001FF, 0x000003FF,
			0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF, 0x00007FFF,
			0x0000FFFF, 0x0001FFFF };

	public int get_bits(int number_of_bits) {
		int returnvalue = 0;
		int sum = bitindex + number_of_bits;
		// System.out.println("bitindex=" + bitindex + " wordpointer="
		// + wordpointer);
		// E.B
		// There is a problem here, wordpointer could be -1 ?!
		if (wordpointer < 0)
			wordpointer = 0;
		// E.B : End.

		if (sum <= 32) {
			// all bits contained in *wordpointer
			returnvalue = (framebuffer[wordpointer] >>> (32 - sum))
					& bitmask[number_of_bits];
			// returnvalue = (wordpointer[0] >> (32 - sum)) &
			// bitmask[number_of_bits];
			if ((bitindex += number_of_bits) == 32) {
				bitindex = 0;
				wordpointer++; // added by me!
			}
			return returnvalue;
		}

		// E.B : Check that ?
		// ((short[])&returnvalue)[0] = ((short[])wordpointer + 1)[0];
		// wordpointer++; // Added by me!
		// ((short[])&returnvalue + 1)[0] = ((short[])wordpointer)[0];
		int Right = (framebuffer[wordpointer] & 0x0000FFFF);
		wordpointer++;
		int Left = (framebuffer[wordpointer] & 0xFFFF0000);
		returnvalue = ((Right << 16) & 0xFFFF0000)
				| ((Left >>> 16) & 0x0000FFFF);

		returnvalue >>>= 48 - sum; // returnvalue >>= 16 - (number_of_bits - (32
									// - bitindex))
		returnvalue &= bitmask[number_of_bits];
		bitindex = sum - 32;
		return returnvalue;
	}

	public void setBuffer(int idx, int value) {
		framebuffer[idx] = value;
	}

}