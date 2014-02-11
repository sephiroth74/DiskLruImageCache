DiskLruImageCache
=================

Simple file based image cache


Include
=================

Just add the following line to your build.gradle dependencies

	compile "it.sephiroth.android.library.disklruimagecache:DiskLruImageCache:1.0.0"


Usage
=================

    DiskLruImageCache cache = new DiskLruImageCache( context, "my-unique-name", Integer.MAX_VALUE );

Read an entry from the cache

    DiskLruImageCache.BitmapEntry entry = cache.get( "image-1", Metadata.class );

Write an entry

    Metadata metadata = new Metadata();
    metadata.value = 1;
    DiskLruImageCache.BitmapEntry<Metadata> entry;
    entry = new DiskLruImageCache.BitmapEntry<Metadata>( bitmap, metadata );
    boolean success = cache.put( "image-1", entry, Bitmap.CompressFormat.JPEG, 70 );

The Metadata must be an instance of Parcelable, in this example is:

	static class Metadata implements Parcelable {

		int value;

		public static final Parcelable.Creator<Metadata> CREATOR = new Parcelable.Creator<Metadata>() {
			public Metadata createFromParcel ( Parcel source ) {
				final Metadata f = new Metadata();
				f.value = source.readInt();
				return f;
			}

			@Override
			public Metadata[] newArray ( final int i ) {
				return new Metadata[0];
			}
		};

		@Override
		public int describeContents () {
			return 0;
		}

		@Override
		public void writeToParcel ( final Parcel parcel, final int i ) {
			parcel.writeInt( value );
		}
	}

