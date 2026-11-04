package com.leve.teclado;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Entrega as imagens guardadas para o app onde você está colando (só leitura, só com permissão
 * concedida na hora de colar). Substitui o FileProvider do AndroidX sem precisar da biblioteca.
 */
public final class ClipImageProvider extends ContentProvider {

    static final String AUTHORITY = "com.leve.teclado.clipimg";

    static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    static String extFor(String mime) {
        if (mime == null) return ".png";
        switch (mime) {
            case "image/jpeg": return ".jpg";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/heic": return ".heic";
            case "image/bmp": return ".bmp";
            default: return ".png";
        }
    }

    static String mimeOf(String name) {
        if (name.endsWith(".jpg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".heic")) return "image/heic";
        if (name.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..") || getContext() == null)
            throw new FileNotFoundException();
        File f = new File(new File(getContext().getFilesDir(), "clipimg"), name);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        return n == null ? null : mimeOf(n);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = "imagem" + extFor(mimeOf(f.getName()));
            else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
