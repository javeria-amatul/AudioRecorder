package com.redbricklane.zapr.audiorecorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    Context context;

    private              Button      startbtn;
    private              AudioRecord mRecorder;
    private              MediaPlayer mPlayer;
    private static final String      TAG                           = "#AUDIO#";
    private static       String      mFileName                     = null;
    public static final  int         REQUEST_AUDIO_PERMISSION_CODE = 1;
    private              int         RECORDER_SAMPLERATE           = 0;
    private final        int         RECORDER_CHANNELS             = AudioFormat.CHANNEL_IN_MONO;
    private final        int         RECORDER_AUDIO_ENCODING       = AudioFormat.ENCODING_PCM_16BIT;

    private static final String   AUDIO_RECORDER_FOLDER       = "AudioRecorder";
    private static final String   AUDIO_RECORDER_TEMP_FILE    = "record_temp.raw";
    private static final String   AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private              boolean  isRecording                 = false;
    private              Thread   recordingThread             = null;
    private static final int      RECORDER_BPP                = 16;
    private              EditText lengthOfSample, fileNameTxt, arrayLen;
    private int frequency, lengthInSec, bufferSize;
    private String             fileName;
    final   ArrayList<Byte>    audioByteArrayList = new ArrayList<>();
    private ArrayList<short[]> sampleForASequence = new ArrayList<>();

    private String fileId;
    String audioWavFileName, tempAudioDataFileName;
    private              FileOutputStream os;
    private              int[]            samplePerSequence;
    private              int              seqNum     = 0;
    private              boolean          skipBuffer = true;
    private              int              skipItr    = 0;
    private              int              arraySize  = 4096;
    private              Spinner          spinner;
        private static final String[]         values     = {"8000", "16000", "32000", "44100", "48000"};
//    private static final String[]         values     = {"8000"};
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        frequency = 0;

        lengthOfSample = (EditText) findViewById(R.id.length);
        arrayLen = findViewById(R.id.arrayLength);
        fileNameTxt = (EditText) findViewById(R.id.fileName);
        startbtn = (Button) findViewById(R.id.startRec);
        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        adapter = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_spinner_item, values);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        RequestPermissions();
    }


    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_AUDIO_PERMISSION_CODE:
                if (grantResults.length > 0) {
                    boolean permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean permissionToStore = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (permissionToRecord && permissionToStore) {
                        Toast.makeText(getApplicationContext(), "Permission Granted", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    public boolean CheckPermissions() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
    }


    private String getFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);
        if (!file.exists()) {
            file.mkdirs();
        }
        String wavFile = fileName;
        return (file.getAbsolutePath() + "/" + wavFile + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath, AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }


    private void startRecording() {

        audioByteArrayList.clear();
        Log.i(TAG, "frequency" + frequency);

        try {
            tempAudioDataFileName = getTempFilename();
            os = new FileOutputStream(tempAudioDataFileName);
            audioWavFileName = getFilename();
        } catch (Exception e) {
            Log.i(TAG, "Failed to initialize file");
        }

        int secondsRecorded = 0;
        int sampleLengthInSeconds = 1 * lengthInSec;
        int sampleLengthInSecInShort = 0;
        // The granularity of each chunk is set to 1
        int sampleLengthForASequence = 1;
        int sampleLengthInShortForASequence = 0;

        switch (frequency) {
            case 8000:
                sampleLengthInSecInShort = sampleLengthInSeconds * 2;
                sampleLengthInShortForASequence = sampleLengthForASequence * 2;
                break;
            case 16000:
                sampleLengthInSecInShort = sampleLengthInSeconds * 4;
                sampleLengthInShortForASequence = sampleLengthForASequence * 4;
                break;
            case 32000:
                sampleLengthInSecInShort = sampleLengthInSeconds * 8;
                sampleLengthInShortForASequence = sampleLengthForASequence * 8;
                break;
            case 44100:
                sampleLengthInSecInShort = sampleLengthInSeconds * 11;
                sampleLengthInShortForASequence = sampleLengthForASequence * 11;
                break;
            case 48000:
                sampleLengthInSecInShort = sampleLengthInSeconds * 12;
                sampleLengthInShortForASequence = sampleLengthForASequence * 12;
                break;
            default:
                sampleLengthInSecInShort = sampleLengthInSeconds * 2;
                sampleLengthInShortForASequence = sampleLengthForASequence * 2;
                break;
        }

        try {
            RECORDER_SAMPLERATE = frequency;
            samplePerSequence = new int[sampleLengthInSecInShort * arraySize];

            bufferSize = AudioRecord.getMinBufferSize(
                    RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING);
            if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                // check if we can instantiate and have a success
                mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE,
                        RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING,
                        8192 * 15);

                if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "Recorder initialized & started");
                    mRecorder.startRecording();
                    isRecording = true;
                    int read = 0;
                    sampleForASequence.clear();
                    seqNum = 0;
                    while (true && isRecording) {
                        short[] buffer = new short[arraySize];
                        read = mRecorder.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            Log.i(TAG, "buffer value " + skipBuffer);

                            if (skipBuffer) {
                                if (frequency == 8000) {
                                    if (skipItr < 1) {
                                        Log.i(TAG, "SKIPPING " + seqNum);
                                        skipItr++;
                                        continue;
                                    } else {
                                        skipBuffer = false;
                                    }
                                }
                            }
                            Log.i(TAG, Integer.toString(seqNum));

                            if (frequency == 16000) {
                                if (skipItr < 2) {
                                    Log.i(TAG, "SKIPPING for 16000 " + seqNum);
                                    skipBuffer = false;
                                    skipItr++;
                                    continue;
                                }
                            }

                            if (frequency == 32000 || frequency == 44100) {
                                if (skipItr < 4) {
                                    Log.i(TAG, "SKIPPING for 32000, 44100 " + seqNum);
                                    skipBuffer = false;
                                    skipItr++;
                                    continue;
                                }
                            }
                            if (frequency == 48000) {
                                if (skipItr < 8) {
                                    Log.i(TAG, "SKIPPING for 48000 " + seqNum);
                                    skipBuffer = false;
                                    skipItr++;
                                    continue;
                                }
                            }
                            sampleForASequence.add(buffer);
                            seqNum++;
                            addBytesToAudioArray(buffer);
                            buffer = null;

                            if (sampleForASequence.size() >= sampleLengthInShortForASequence) {
                                Log.i(TAG, "Sample recorded at :  " + System.currentTimeMillis() + "dd/MM/yyyy hh:mm:ss.SSS");
                                sampleForASequence.clear();
                                secondsRecorded++;
                            }
                            if (seqNum >= sampleLengthInSecInShort) {
                                Log.i(TAG, "Recording over");
                                stopRecording();
                                break;
                            }

                        } else {
                            Log.i(TAG, "Recorder over due to read > 0 is false");
                            stopRecording();
                            return;
                        }
                    }
                } else {
                    stopRecording();
                    Log.i(TAG, "Recorder not initialized");
                }
            } else {
                Log.i(TAG, "Recording error: Buffer error_bad_value");
            }
        } catch (Exception | Error e) {
            Log.e(TAG, "Exception while recording");
            e.printStackTrace();
        }
    }


    private void stopRecording() {
        try {
            if (null != mRecorder) {
                if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED
                        && mRecorder.getState() != AudioRecord.RECORDSTATE_STOPPED) {
                    skipBuffer = true;
                    isRecording = false;
                    mRecorder.stop();
                    mRecorder.release();
                    mRecorder = null;
                }
                mRecorder.release();

                if (audioByteArrayList != null && audioByteArrayList.size() > 0) {
                    Object[] arrayOfObjects = audioByteArrayList.toArray();
                    byte[] bytes = new byte[arrayOfObjects.length];
                    for (int i = 0; i < arrayOfObjects.length; i++) {
                        bytes[i] = (byte) arrayOfObjects[i];
                    }
                    os.write(bytes, 0, bytes.length);

                    copyWaveFile(tempAudioDataFileName, audioWavFileName);
                    deleteTempFile();
                }
                os.close();

            }

        } catch (Exception | Error ex) {
            Log.e(TAG, "Error while stopping recording" + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());

        file.delete();
        Log.i(TAG, "deleteTempFile1");

    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;

        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_SAMPLERATE * 2;

        byte[] data = new byte[8192 * 14];
        Log.i(TAG, "copyWaveFile1");


        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            Log.i(TAG, "copyWaveFile2");

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);

            }
            Log.i(TAG, "Wav array: " + Arrays.toString(data));

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];
        Log.i(TAG, "WriteWaveFileHeader1");

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        Log.i(TAG, "WriteWaveFileHeader2");

        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = 16;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        Log.i(TAG, "WriteWaveFileHeader3");

        out.write(header, 0, 44);
    }

    private void addBytesToAudioArray(short[] input) {
        int iterations = input.length;

        ByteBuffer byteBuffer = ByteBuffer.allocate(input.length * 2);
        for (int index = iterations - 1; index > 0; --index) {
            byteBuffer.putShort(input[index]);
        }

        byte[] byteArray = byteBuffer.array();

        for (int i = byteArray.length - 1; i >= 0; i--) {
            audioByteArrayList.add(byteBuffer.get(i));
        }
    }

    private void RequestPermissions() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        adapter.notifyDataSetChanged();

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        String label = parent.getItemAtPosition(position).toString();
        int frequency = Integer.parseInt(label);
        Log.i(TAG, "frequency from spinner " + frequency);

        switch (frequency) {
            case 8000:
            case 16000:
            case 32000:
            case 44100:
            case 48000:
                this.frequency = frequency;
                break;
            default:
                this.frequency = 8000;
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void startRecording(View view) {

        lengthInSec = Integer.parseInt(lengthOfSample.getText().toString());
        arraySize = (Integer.parseInt(arrayLen.getText().toString())) / 2;
        Log.i(TAG, "arraysize " + arraySize);
        fileName = "" + System.currentTimeMillis();
        if (lengthInSec > 0) {
            startbtn.setEnabled(false);
            Toast.makeText(getApplicationContext(), "Recording Started", Toast.LENGTH_LONG).show();
            new Recording().execute();
            startbtn.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //enable the button
                    startbtn.setEnabled(true);
                }
            }, lengthInSec * 1000);
//        startRecording();
        }
        else {
            Toast.makeText(getApplicationContext(), "Enter seconds greater than 0", Toast.LENGTH_LONG).show();
        }
    }


    private class Recording extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            startRecording();
            return null;
        }
    }


}