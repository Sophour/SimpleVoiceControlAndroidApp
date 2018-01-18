package com.example.sophour.voicecontrol;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private static final String KEYWORD_SEARCH = "key";
    private static final String COMMANDS_SEARCH = "commands";

    private static final String KEYWORD =  "wakeup";
    private static final String MORE_COMMAND = "more";
    private static final String LESS_COMMAND = "less";
    private static final String COOKIE_COMMAND = "cookie";

    // For UI
    private static int partialRecognitionsCounter = 0;
    private static int resultRecognitionsCounter = 0;

    // To handle permission request
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer mRecognizer;

    private TextView mPartialWordTextView,
            mResultWordTextView,
            mPartialCounterTextView,
            mResultCounterTextView,
            mInstructionsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInstructionsTextView = (TextView)findViewById(R.id.textView_instruction);
        mPartialCounterTextView = (TextView)findViewById(R.id.textView_onPartialResult);
        mResultCounterTextView = (TextView)findViewById(R.id.textView_onResult);
        mPartialWordTextView = (TextView)findViewById(R.id.textView_onPartialWord);
        mResultWordTextView = (TextView)findViewById(R.id.textView_onResultWord);

        // Let user know that app is loading
        mInstructionsTextView.setText(R.string.prepare_recognizer_instruction);

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        runRecognizerSetup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runRecognizerSetup();
            } else {
                finish();
            }
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        mRecognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en"))  // The name of the dir we've put our model into
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setBoolean("-remove_noise", true)
                .setKeywordThreshold(1e-10f)
                .getRecognizer();
        mRecognizer.addListener(this);

        // Create keyword-activation search
        mRecognizer.addKeyphraseSearch(KEYWORD_SEARCH, KEYWORD);

        // Create jsgf grammar-based commands search
        File menuGrammar = new File(assetsDir, "commands.jsgf");
        mRecognizer.addGrammarSearch(COMMANDS_SEARCH, menuGrammar);

    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming process
        // and it involves IO, so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    mInstructionsTextView.setText(R.string.recognizer_setup_error + result.toString());
                } else {
                    switchSearch(KEYWORD_SEARCH);
                }
            }
        }.execute();
    }

    private void switchSearch(String whatToSearch) {
        mRecognizer.stop();

        switch (whatToSearch){
            case KEYWORD_SEARCH:
                mInstructionsTextView.setText(R.string.start_app_instruction);
                mRecognizer.startListening(whatToSearch);
                break;
            case COMMANDS_SEARCH:
                mInstructionsTextView.setText(R.string.commands_instruction);
                mRecognizer.startListening(whatToSearch, 8000);
                // If user doesn't speak for 8 secs the app switches back
                // to the keyword spotting in onTimeout() method
                break;
            default:
                mInstructionsTextView.setText("?");
                break;
        }
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String probableUserSpeech = hypothesis.getHypstr();
        mPartialWordTextView.setText(probableUserSpeech);

        if (probableUserSpeech.equals(KEYWORD))
            switchSearch(COMMANDS_SEARCH);
        else if (probableUserSpeech.equals(MORE_COMMAND)){
            partialRecognitionsCounter++;
            mPartialCounterTextView.setText(String.valueOf(partialRecognitionsCounter));
        }
        else if (probableUserSpeech.equals(LESS_COMMAND)){
            partialRecognitionsCounter--;
            mPartialCounterTextView.setText(String.valueOf(partialRecognitionsCounter));
        }
        mPartialWordTextView.setText(probableUserSpeech);
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String probableUserSpeech = hypothesis.getHypstr();

        if (probableUserSpeech.equals(MORE_COMMAND)){
            resultRecognitionsCounter++;
            mResultCounterTextView.setText(String.valueOf(resultRecognitionsCounter));
        }
        else if (probableUserSpeech.equals(LESS_COMMAND)){
            resultRecognitionsCounter--;
            mResultCounterTextView.setText(String.valueOf(resultRecognitionsCounter));
        }
        else if (probableUserSpeech.equals(COOKIE_COMMAND))
            Toast.makeText(this, "WHERE?!", Toast.LENGTH_SHORT).show();

        mResultWordTextView.setText(probableUserSpeech);

    }

    @Override
    public void onEndOfSpeech() {
        if (!mRecognizer.getSearchName().equals(KEYWORD_SEARCH))
            switchSearch(COMMANDS_SEARCH);
    }


    @Override
    public void onTimeout() {
        switchSearch(KEYWORD_SEARCH);
    }

    @Override
    public void onError(Exception e) {
        mInstructionsTextView.setText(e.getMessage());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mRecognizer != null) {
            mRecognizer.cancel();
            mRecognizer.shutdown();
        }
    }


    @Override
    public void onBeginningOfSpeech() {

    }

}
