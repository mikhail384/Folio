package ru.folio.notes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only, grant-only access to temporary Folio page previews shared to ChatGPT. */
public final class ChatGPTShareProvider extends ContentProvider {
    static final String AUTHORITY="ru.folio.notes.chatgptshare";

    @Override public boolean onCreate(){return getContext()!=null;}

    private File resolve(Uri uri){
        if(uri==null||!AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1)return null;
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("[0-9a-f-]{36}\\.png"))return null;
        try{
            File root=new File(getContext().getCacheDir(),"chatgpt-share").getCanonicalFile();
            File file=new File(root,name).getCanonicalFile();
            return root.equals(file.getParentFile())?file:null;
        }catch(IOException ignored){return null;}
    }

    @Override public String getType(Uri uri){File file=resolve(uri);return file!=null&&file.isFile()?"image/png":null;}

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        File file=resolve(uri);if(file==null||!file.isFile())return null;
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns,1);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=file.getName();
            else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();
        }
        cursor.addRow(row);return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        File file=resolve(uri);
        if(!"r".equals(mode)||file==null||!file.isFile())throw new FileNotFoundException("Folio preview unavailable");
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Read only");}
}
