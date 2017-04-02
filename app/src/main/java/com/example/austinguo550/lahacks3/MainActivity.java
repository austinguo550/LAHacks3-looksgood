package com.example.austinguo550.lahacks3;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Text;

import static com.example.austinguo550.lahacks3.SessionService.SessionStatus.PLAYING;
import static java.util.zip.Deflater.BEST_COMPRESSION;


public class MainActivity extends AppCompatActivity {
    /** Called when the activity is first created. */
    // Loopback l = null;
    private byte[] input; // TODO: make reset zip function
    private static final int FILE_SELECT_CODE = 0;

    private String s;
    private int slength;

    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private String filename;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case 200://REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();
    }

    private SessionService mSessionService;
    private boolean mIsBound;

    private ServiceConnection mSessionConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mSessionService = ((SessionService.SessionBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mSessionService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this, SessionService.class), mSessionConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mSessionConnection);
            mIsBound = false;
        }
    }

    Timer refreshTimer = null;

    Handler mHandler = new Handler();
    Handler sendPacks = new Handler();

    TextView textStatus, textListen, zipName;

    List<RadioButton> radioButtons = new ArrayList<RadioButton>();

    Uri mCreateDataUri = null;
    String mCreateDataType = null;
    String mCreateDataExtraText = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            //@Override
            public void onClick(View view) {
                showFileChooser();
            }
        });


        //ActivityCompat.requestPermissions(this,permissions,200);


        textStatus = (TextView) findViewById(R.id.TextStatus);
        textListen = (TextView) findViewById(R.id.TextListen);

        zipName = (TextView) findViewById(R.id.textView);

        Button t = (Button) findViewById(R.id.button);
        t.setOnClickListener(mPlayListener);

        t = (Button) findViewById(R.id.button7);
        t.setOnClickListener(mListenListener);

        radioButtons.add((RadioButton) findViewById(R.id.radioButton6));
        radioButtons.add((RadioButton) findViewById(R.id.radioButton8));
        radioButtons.add((RadioButton) findViewById(R.id.radioButton9));
        radioButtons.add((RadioButton) findViewById(R.id.radioButton10));
        radioButtons.add((RadioButton) findViewById(R.id.radioButton11));

        for (RadioButton button : radioButtons) {
            button.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                public void onCheckedChanged(CompoundButton buttonView,
                                             boolean isChecked) {
                    if (isChecked)
                        processRadioButtonClick(buttonView);
                }
            });
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {

            mCreateDataUri = intent.getData();
            mCreateDataType = intent.getType();

            if (mCreateDataUri == null) {
                mCreateDataUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            }

            mCreateDataExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (mCreateDataUri == null)
                mCreateDataType = null;

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(null));
        }

        doBindService();
    }
    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    Uri uri = data.getData();

                   /* if (filesize >= FILE_SIZE_LIMIT) {
                        Toast.makeText(this,"The selected file is too large. Select a new file with size less than 2mb",Toast.LENGTH_LONG).show();
                    } else { */
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType == null) {
                        String path = getPath(this, uri);
                        if (path == null) {
                            filename = FilenameUtils.getName(uri.toString());
                        } else {
                            File file = new File(path);
                            filename = file.getName();
                        }
                    } else {
                        Uri returnUri = data.getData();
                        Cursor returnCursor = getContentResolver().query(returnUri, null, null, null, null);
                        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
                        returnCursor.moveToFirst();
                        filename = returnCursor.getString(nameIndex);
                        String size = Long.toString(returnCursor.getLong(sizeIndex));
                    }
                    File fileSave = getExternalFilesDir(null);
                    String sourcePath = fileSave.toString();
                    zipName.setText(sourcePath + "/" + filename);
                    try {
                        copyFileStream(new File(sourcePath + "/" + filename), uri,this);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        input = FileUtils.readFileToByteArray(new File(sourcePath + "/" + filename));

                        // Compress bytes array input into result buffer array
                        byte[] buffer = new byte[2000]; //2kb output maximum
                        Deflater compresser = new Deflater(BEST_COMPRESSION);
                        compresser.setInput(input);
                        compresser.finish();
                        int compressedDataLength = compresser.deflate(buffer); //number of bytes written (same as getTotalOut()
                        compresser.end();

                        // Retrieve bytes array from buffer without empty elements
                        input = new byte[compressedDataLength];
                        System.arraycopy(buffer, 0, input, 0, compressedDataLength);

                        s = Base64.encodeToString(input, Base64.DEFAULT);
                        slength = s.length();
                        Toast.makeText(getApplicationContext(), String.valueOf(s.length()), Toast.LENGTH_LONG).show();
                        Toast.makeText(getApplicationContext(), "zip completed with length " + input.length, Toast.LENGTH_LONG).show();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getPath(Context context, Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else
            if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    private void copyFileStream(File dest, Uri uri, Context context)
            throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;

            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            is.close();
            os.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View v) {
            //EditText e = (EditText) findViewById(R.id.editText);
            //String s = e.getText().toString();
            if (input == null)   // do a check to see if the the input is nothing
            {
                Toast.makeText(mSessionService, "Please upload a file.", Toast.LENGTH_LONG).show();

            }
            else {
                sendPacks.post(new Runnable() // have to do this on the UI thread
                {
                    public void run() {
                        mSessionService.sessionReset();
//                        Toast.makeText(getApplicationContext(), String.valueOf(input.length), Toast.LENGTH_LONG);
//               byte[] testBytes = input;
//
                        mSessionService.startSession(s);
                    }
                });
            }
        }
    };

    View.OnClickListener mListenListener = new View.OnClickListener() {
        public void onClick(View v) {
            mSessionService.sessionReset();
            if (mSessionService.getStatus() == SessionService.SessionStatus.NONE
                    || mSessionService.getStatus() == SessionService.SessionStatus.FINISHED) {
                mSessionService.listen();
                ((Button) v).setText("Stop listening");
            } else {
                mSessionService.stopListening();
                ((Button) v).setText("Listen");
            }

        }
    };

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {

        // if( l != null )
        // l.stopLoop();

        super.onPause();

        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }

        mSessionService.stopListening();
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();

        String sent = null;

        if (mCreateDataExtraText != null) {
            sent = mCreateDataExtraText;
        } else if (mCreateDataType != null
                && mCreateDataType.startsWith("text/")) {
            // read the URI into a string

            byte[] b = readDataFromUri(this.mCreateDataUri);
            if (b != null)
                sent = new String(b);

        }

        if (sent != null) {
            EditText e = (EditText) findViewById(R.id.editText);
            e.setText(sent);
        }

        refreshTimer = new Timer();

        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {

                mHandler.post(new Runnable() // have to do this on the UI thread
                {
                    public void run() {
                        updateResults();
                    }
                });

            }
        }, 500, 500);

    }

    private void processRadioButtonClick(CompoundButton buttonView) {
        for (RadioButton button : radioButtons) {
            if (button != buttonView)
                button.setChecked(false);
        }
    }

    private void setRadioGroupUnchecked() {
        for (RadioButton button : radioButtons) {
            button.setChecked(false);
        }
    }

    private void setRadioGroupChecked(SessionService.SessionStatus s) {
        RadioButton rb = null;
        switch (s) {
            case PLAYING:
                rb = (RadioButton) findViewById(R.id.radioButton6);
                break;
            case LISTENING:
                rb = (RadioButton) findViewById(R.id.radioButton8);
                break;
            case HELPING:
                rb = (RadioButton) findViewById(R.id.radioButton9);
                break;
            case SOS:
                rb = (RadioButton) findViewById(R.id.radioButton10);
                break;
            case FINISHED:
                rb = (RadioButton) findViewById(R.id.radioButton11);
                break;
            case NONE:
                setRadioGroupUnchecked();
                return;
        }
        rb.setChecked(true);
    }

    private void updateResults() {
        if (mSessionService.getStatus() == SessionService.SessionStatus.LISTENING) {
            textStatus.setText(mSessionService.getBacklogStatus());
            Log.d("DEBUG", textStatus.toString());
            textListen.setText(mSessionService.getListenString());

            Button b = (Button) findViewById(R.id.button7);
            b.setText("Stop listening");
        } else if (mSessionService.getStatus() == SessionService.SessionStatus.FINISHED) {
            Button b = (Button) findViewById(R.id.button7);
            b.setText("Listen");
            textStatus.setText("");
        } else {
            textStatus.setText("");
        }
        setRadioGroupChecked(mSessionService.getStatus());
    }

	/*
	 * private void encode( String inputFile, String outputFile ) {
	 *
	 * try {
	 *
	 * //There was an output file specified, so we should write the wav
	 * System.out.println("Encoding " + inputFile);
	 * AudioUtils.encodeFileToWav(new File(inputFile), new File(outputFile));
	 *
	 * } catch (Exception e) { System.out.println("Could not encode " +
	 * inputFile + " because of " + e); }
	 *
	 * }
	 */

    private byte[] readDataFromUri(Uri uri) {
        byte[] buffer = null;

        try {
            InputStream stream = getContentResolver().openInputStream(uri);

            int bytesAvailable = stream.available();
            // int maxBufferSize = 1024;
            int bufferSize = bytesAvailable; // Math.min(bytesAvailable,
            // maxBufferSize);
            int totalRead = 0;
            buffer = new byte[bufferSize];

            // read file and write it into form...
            int bytesRead = stream.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
                bytesRead = stream.read(buffer, totalRead, bufferSize);
                totalRead += bytesRead;
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();

        }

        return buffer;
    }

}
