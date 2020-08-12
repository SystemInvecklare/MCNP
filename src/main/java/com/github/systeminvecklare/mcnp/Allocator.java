package com.github.systeminvecklare.mcnp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/*package-protected*/ class Allocator implements IAllocator, IReleasable {
	private final byte[] data;
	private final List<BorrowedByteArray> cachedArrays = new ArrayList<BorrowedByteArray>();
	
	public Allocator(int size) {
		this.data = new byte[size];
		this.cachedArrays.add(new BorrowedByteArray(this.data, 0, this.data.length));
	}

	@Override
	public synchronized IBorrowedByteArray obtain(int size) {
		if(size < 0) {
			throw new IllegalArgumentException("Size must be positive");
		}
		if(size == 0) {
			return new BorrowedByteArray(new byte[0], 0, 0);
		}
		BorrowedByteArray prospect = null;
		Iterator<BorrowedByteArray> iterator = cachedArrays.iterator();
		while(iterator.hasNext()) {
			BorrowedByteArray borrowedByteArray = iterator.next();
			if(borrowedByteArray.getLength() >= size) {
				iterator.remove();
				prospect = borrowedByteArray;
				break;
			}
		}
		
		if(prospect != null) {
			if(prospect.getLength() == size) {
				return prospect;
			} else {
				BorrowedByteArray firstPart = new BorrowedByteArray(data, prospect.offset, size);
				BorrowedByteArray secondPart = new BorrowedByteArray(data, prospect.offset+size, prospect.getLength()-size);
				addToCache(secondPart);
				return firstPart;				
			}
		} else {
			//Create new unrecoverable ByteArray
			return new BorrowedByteArray(new byte[size], 0, size);
		}
	}

	private synchronized void free(IBorrowedByteArray byteArray) {
		BorrowedByteArray borrowedByteArray = (BorrowedByteArray) byteArray;
		if(borrowedByteArray.ownerBlock == this.data) {
			addToCache(borrowedByteArray);
		}
	}
	
	private synchronized void addToCache(BorrowedByteArray array) {
		cachedArrays.add(array);
		Collections.sort(cachedArrays, new Comparator<BorrowedByteArray>() {
			@Override
			public int compare(BorrowedByteArray o1, BorrowedByteArray o2) {
				return Integer.compare(o1.offset, o2.offset);
			}
		});
		
		// Merge arrays
		for(int index = 0; index < cachedArrays.size()-1; ++index) {
			BorrowedByteArray chunk = cachedArrays.get(index);
			BorrowedByteArray nextChunk = cachedArrays.get(index+1);
			if(chunk.getEnd() == nextChunk.offset) {
				BorrowedByteArray merged = new BorrowedByteArray(data, chunk.offset, chunk.getLength()+nextChunk.getLength());
				cachedArrays.remove(index+1);
				cachedArrays.set(index, merged);
				index--; //Redo index
				continue;
			}
		}
	}

	@Override
	public synchronized void release() {
		// TODO Auto-generated method stub
		
	}
	
	private class BorrowedByteArray implements IBorrowedByteArray {
		private final ByteArray byteArray;
		private final byte[] ownerBlock;
		private final int offset;
		
		public BorrowedByteArray(byte[] ownerBlock, int offset, int length) {
			this.byteArray = new ByteArray(ownerBlock, offset, length);
			this.ownerBlock = ownerBlock;
			this.offset = offset;
		}


		@Override
		public void release() {
			free(this);
		}

		@Override
		public ByteArray getByteArray() {
			return byteArray;
		}
		
		private int getEnd() {
			return offset+byteArray.getLength();
		}
		
		private int getLength() {
			return byteArray.getLength();
		}
	}
}
