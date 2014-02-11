package it.sephiroth.android.library.disklruimagecache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class DiskLruImageCache {

	private static final String LOG_TAG = "DiskLruImageCache";

	private static final int APP_VERSION = 2;
	private static final int APP_VALUES = 2;

	private static final int BITMAP_INDEX = 0;
	private static final int METADATA_INDEX = 1;

	private final static Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
	private final static int DEFAULT_COMPRESS_QUALITY = 60;

	final DiskLruCache mDiskCache;

	public DiskLruImageCache ( Context context, final String name, int maxSize ) throws IOException {
		File dir = getCacheDir( context, name );
		mDiskCache = DiskLruCache.open( dir, APP_VERSION, APP_VALUES, maxSize );
	}

	public <T extends Parcelable> BitmapEntry<T> get ( final String key, Class<T> metadataClass ) throws ClassNotFoundException {
		Log.i( LOG_TAG, "get: " + key );
		DiskLruCache.Snapshot snapshot = null;
		try {
			snapshot = mDiskCache.get( makeKey( key ) );
			if( null != snapshot ) {
				T metadata = readMetadata( snapshot, metadataClass );
				Bitmap bitmap = read( snapshot );
				return new BitmapEntry( bitmap, metadata );
			}
		} catch( IOException e ) {
			e.printStackTrace();
		} finally {
			if( null != snapshot ) {
				snapshot.close();
			}
		}
		return null;
	}

	public <T extends Parcelable> boolean put ( final String key, BitmapEntry<T> bitmap ) throws IOException {
		return put( key, bitmap, DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY );
	}

	public <T extends Parcelable> boolean put ( final String key, BitmapEntry<T> bitmap, Bitmap.CompressFormat format, int quality ) throws IOException {
		Log.i( LOG_TAG, "put: " + key );

		DiskLruCache.Editor editor = null;
		try {
			editor = mDiskCache.edit( makeKey( key ) );
			if( null == editor ) {
				Log.w( LOG_TAG, "editor is null" );
				return false;
			}

			writeMetadata( editor, bitmap.getMetadata() );

			if( write( editor, bitmap.getBitmap(), format, quality ) ) {
				Log.d( LOG_TAG, "flushing..." );
				mDiskCache.flush();
				editor.commit();
				return true;
			} else {
				Log.w( LOG_TAG, "failed to write bitmap" );
				editor.abort();
			}
		} catch( IOException e ) {
			e.printStackTrace();
			Log.e( LOG_TAG, "put failed", e );

			try {
				if( null != editor ) {
					editor.abort();
				}
			} catch( IOException e1 ) {
				Log.w( LOG_TAG, "abort failed", e1 );
			}
		}
		return false;
	}

	private Bitmap read ( DiskLruCache.Snapshot snapshot ) {
		InputStream stream = snapshot.getInputStream( BITMAP_INDEX );
		return BitmapFactory.decodeStream( stream );
	}

	private boolean write ( final DiskLruCache.Editor editor, final Bitmap bitmap, final Bitmap.CompressFormat format, final int quality ) throws IOException {
		OutputStream out = null;

		try {
			out = new BufferedOutputStream( editor.newOutputStream( BITMAP_INDEX ), Utils.IO_BUFFER_SIZE );
			return bitmap.compress( format, quality, out );
		} finally {
			if( null != out ) {
				IOUtils.closeQuietly( out );
			}
		}
	}

	private void writeMetadata (
			DiskLruCache.Editor editor, Parcelable metadata ) throws IOException {
		OutputStream oos = null;
		try {

			final Parcel p1 = Parcel.obtain();
			p1.writeParcelable( metadata, 0 );
			byte[] bytes = p1.marshall();
			p1.recycle();

			oos = editor.newOutputStream( METADATA_INDEX );
			oos.write( bytes );

		} finally {
			IOUtils.closeQuietly( oos );
		}
	}

	private <T extends Parcelable> T readMetadata ( DiskLruCache.Snapshot snapshot, Class<T> cls ) throws IOException, ClassNotFoundException {
		InputStream ois = null;
		Parcel parcel = null;
		try {
			ois = snapshot.getInputStream( METADATA_INDEX );
			byte[] bytes = IOUtils.toByteArray( ois );

			parcel = Parcel.obtain();
			parcel.unmarshall( bytes, 0, bytes.length );
			parcel.setDataPosition( 0 );

			return parcel.readParcelable( cls.getClassLoader() );

		} finally {
			if( null != ois ) {
				IOUtils.closeQuietly( ois );
			}

			if( null != parcel ) {
				parcel.recycle();
			}
		}
	}

	public long size () {
		return mDiskCache.size();
	}

	public void remove ( final String key ) throws IOException {
		Log.i( LOG_TAG, "remove: " + key );
		mDiskCache.remove( makeKey( key ) );
	}

	public boolean containsKey ( String key ) {
		Log.i( LOG_TAG, "containsKey: " + key );

		DiskLruCache.Snapshot snapshot = null;
		try {
			snapshot = mDiskCache.get( makeKey( key ) );
			return snapshot != null;
		} catch( IOException e ) {
			e.printStackTrace();
		} finally {
			if( snapshot != null ) {
				snapshot.close();
			}
		}

		return false;

	}

	public long getMaxSize () {
		return mDiskCache.getMaxSize();
	}

	public boolean isClosed () {
		return mDiskCache.isClosed();
	}

	public synchronized void close () throws IOException {
		Log.i( LOG_TAG, "close" );
		mDiskCache.close();
	}

	public synchronized void delete () throws IOException {
		Log.i( LOG_TAG, "delete" );
		mDiskCache.delete();
	}

	public File getDirectory () {
		return mDiskCache.getDirectory();
	}

	private String makeKey ( final String key ) {
		return DigestUtils.md5Hex( key )
				.toLowerCase();
	}

	public static File getCacheDir ( Context context, final String name ) {
		Log.i( LOG_TAG, "getCacheDir: " + name );

		final String storageState = Environment.getExternalStorageState();
		final File cacheDir;
		if( Environment.MEDIA_CHECKING.equals( storageState ) || Environment.MEDIA_MOUNTED.equals( storageState ) || ! Utils.isExternalStorageRemovable() ) {
			cacheDir = Utils.getExternalCacheDir( context );
		} else {
			cacheDir = context.getCacheDir();
		}

		return new File( cacheDir, name );
	}

	public static final class BitmapEntry<K extends Parcelable> {

		private final Bitmap bitmap;
		private final K metadata;

		public BitmapEntry ( Bitmap bitmap, K metadata ) {
			this.bitmap = bitmap;
			this.metadata = metadata;
		}

		public Bitmap getBitmap () {
			return bitmap;
		}

		public K getMetadata () {
			return metadata;
		}
	}
}
