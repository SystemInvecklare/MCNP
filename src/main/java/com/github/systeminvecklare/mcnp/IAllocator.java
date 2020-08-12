package com.github.systeminvecklare.mcnp;

/*package-protected*/ interface IAllocator {
	IBorrowedByteArray obtain(int size);
	
	interface IBorrowedByteArray extends IReleasable {
		ByteArray getByteArray();
	}
}
