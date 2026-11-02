package ru.folio.notes;

import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;

/** Instrumented APK only. DocumentsContract is exercised against an isolated cache directory. */
public final class BackupTestProvider extends DocumentsProvider {
    static final String AUTHORITY="ru.folio.notes.qa.documents";
    File root;public boolean onCreate(){root=new File(getContext().getCacheDir(),"backup-provider-qa");return root.mkdirs()||root.isDirectory();}
    File file(String id)throws FileNotFoundException{if(id.equals("root"))return root;if(!id.matches("[a-zA-Z0-9_. -]+")||id.contains(".."))throw new FileNotFoundException();return new File(root,id);}
    static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE};
    void add(MatrixCursor cursor,File file){MatrixCursor.RowBuilder row=cursor.newRow();for(String column:cursor.getColumnNames()){Object value=null;if(column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID))value=file.equals(root)?"root":file.getName();if(column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME))value=file.getName();if(column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE))value=file.isDirectory()?DocumentsContract.Document.MIME_TYPE_DIR:"application/zip";if(column.equals(DocumentsContract.Document.COLUMN_FLAGS))value=file.isDirectory()?DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE:DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE;if(column.equals(DocumentsContract.Document.COLUMN_SIZE))value=file.length();row.add(value);}}
    public Cursor queryRoots(String[] projection){String[] columns={DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS};MatrixCursor c=new MatrixCursor(projection==null?columns:projection);MatrixCursor.RowBuilder r=c.newRow();for(String column:c.getColumnNames())r.add(column.equals(DocumentsContract.Root.COLUMN_ROOT_ID)?"qa":column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)?"root":column.equals(DocumentsContract.Root.COLUMN_FLAGS)?DocumentsContract.Root.FLAG_SUPPORTS_CREATE:"Folio QA");return c;}
    public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?COLUMNS:projection);add(c,file(id));return c;}
    public Cursor queryChildDocuments(String parent,String[] projection,String order)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?COLUMNS:projection);File[] files=file(parent).listFiles();if(files!=null)for(File f:files)add(c,f);return c;}
    public boolean isChildDocument(String parent,String child){return parent.equals("root")&&!child.equals("root");}
    public String createDocument(String parent,String mime,String name)throws FileNotFoundException{if(!parent.equals("root"))throw new FileNotFoundException();name=name.replaceAll("[^a-zA-Z0-9_. -]","_");File f=file(name);try{if(!f.createNewFile())throw new IOException("Exists");return name;}catch(IOException e){throw new FileNotFoundException(e.getMessage());}}
    public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));}
    public void deleteDocument(String id)throws FileNotFoundException{File f=file(id);if(f.equals(root)||!f.delete())throw new FileNotFoundException();}
}
