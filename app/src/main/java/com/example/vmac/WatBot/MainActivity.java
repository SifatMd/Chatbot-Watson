package com.example.vmac.WatBot;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.assistant.v1.Assistant;
import com.ibm.watson.developer_cloud.assistant.v1.model.InputData;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageOptions;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageResponse;

import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeakerLabel;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.vmac.WatBot.Translate.Post;
import static com.example.vmac.WatBot.Translate.host;
import static com.example.vmac.WatBot.Translate.params;
import static com.example.vmac.WatBot.Translate.path;
import static com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper.REQUEST_PERMISSION;


public class MainActivity extends AppCompatActivity {


    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;
    //private Map<String,Object> context = new HashMap<>();
    private com.ibm.watson.developer_cloud.assistant.v1.model.Context context = null;
    StreamPlayer streamPlayer;
    private boolean initialRequest;
    private boolean permissionToRecordAccepted = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static String TAG = "MainActivity";
    private static final int RECORD_REQUEST_CODE = 101;
    private boolean listening = false;
    private SpeechToText speechService;
    private TextToSpeech textToSpeech;
    private MicrophoneInputStream capture;
    private Context mContext;
    private String workspace_id;
    private String conversation_iam_apikey;
    private String conversation_url;
    private String STT_username;
    private String STT_password;
    private String TTS_username;
    private String TTS_password;
    private SpeakerLabelsDiarization.RecoTokens recoTokens;
    private MicrophoneHelper microphoneHelper;

    public ArrayList<String> arrayBangla = new ArrayList<>();
    public ArrayList<String> arrayEnglish = new ArrayList<>();


    public String reply;
    public String question;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);



        mContext = getApplicationContext();
        conversation_iam_apikey = mContext.getString(R.string.conversation_iam_apikey);
        conversation_url = mContext.getString(R.string.conversation_url);
        workspace_id = mContext.getString(R.string.workspace);
        STT_username = mContext.getString(R.string.speech_text_iam_apikey);
        STT_password = mContext.getString(R.string.speech_text_url);
        TTS_username = mContext.getString(R.string.text_speech_iam_apikey);
        TTS_password = mContext.getString(R.string.text_speech_url);


        inputMessage = (EditText) findViewById(R.id.message);
        btnSend = (ImageButton) findViewById(R.id.btn_send);
        btnRecord= (ImageButton) findViewById(R.id.btn_record);
        String customFont = "Montserrat-Regular.ttf";
        Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
        inputMessage.setTypeface(typeface);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList);
        microphoneHelper = new MicrophoneHelper(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;
        sendMessage();


        //Watson Text-to-Speech Service on Bluemix
        textToSpeech = new TextToSpeech();
        textToSpeech.setUsernameAndPassword(TTS_username, TTS_password);


        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        }


        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), recyclerView, new ClickListener() {
            @Override
            public void onClick(View view, final int position) {
                Thread thread = new Thread(new Runnable() {
                    public void run() {
                        Message audioMessage;
                        try {

                            audioMessage =(Message) messageArrayList.get(position);
                            streamPlayer = new StreamPlayer();
                            if(audioMessage != null && !audioMessage.getMessage().isEmpty())
                                //Change the Voice format and choose from the available choices
                                streamPlayer.playStream(textToSpeech.synthesize(audioMessage.getMessage(), Voice.EN_LISA).execute());
                            else
                                streamPlayer.playStream(textToSpeech.synthesize("No Text Specified", Voice.EN_LISA).execute());

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();
            }

            @Override
            public void onLongClick(View view, int position) {
                recordMessage();

            }
        }));

        btnSend.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(checkInternetConnection()) {
                    sendMessage();
                }
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                recordMessage();
            }
        });
    };

    // Speech-to-Text Record Audio permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
            case RECORD_REQUEST_CODE: {

                if (grantResults.length == 0
                        || grantResults[0] !=
                        PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                }
                return;
            }

            case MicrophoneHelper.REQUEST_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
       // if (!permissionToRecordAccepted ) finish();

    }

    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                MicrophoneHelper.REQUEST_PERMISSION);
    }

    // Sending a message to Watson Conversation Service
    private void sendMessage() {

        final String temp = this.inputMessage.getText().toString().trim();
        //final String inputmessage = this.inputMessage.getText().toString().trim();

        int maxx = 0;
        int len = 0;
        int i=0, j=0;

//
//
//        try {
//            for ( i = 0; i < arrayBangla.size(); i++) {
//                String bangla = arrayBangla.get(i);
//                for ( j = 0; j < bangla.length(); j++) {
//                    if (bangla.charAt(j) == temp.charAt(j)) {
//                        if (j >= len) {
//                            len = j;
//                            maxx = i;
//
//                        }
//                    } else break;
//                }
//            }
//        }catch (Exception e){
//            maxx = i;
//        }
//
//
//        String value = arrayEnglish.get(maxx);
//
//        final String inputmessage = value;

        final String inputmessage = temp;

        if(!this.initialRequest) {
            Message inputMessage = new Message();
            inputMessage.setMessage(temp);
            inputMessage.setId("1");
            System.out.println("temp is " + temp);
            messageArrayList.add(inputMessage);
        }
        else
        {
            Message inputMessage = new Message();
            //inputMessage.setMessage(inputmessage);
            inputMessage.setId("100");
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(),"Tap on the message for Voice",Toast.LENGTH_LONG).show();

       }

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

        Thread thread = new Thread(new Runnable(){
            public void run() {
                try {

                    IamOptions iamOptions = new IamOptions.Builder().apiKey(conversation_iam_apikey).build();
                    Assistant service = new Assistant("2018-09-20", iamOptions);

                    // Start assistant with empty message.
                    MessageOptions options = new MessageOptions.Builder(workspace_id).build();

                    InputData input = new InputData.Builder(inputmessage).build();
                    options = new MessageOptions.Builder(workspace_id).input(input).context(context).build();

                    MessageResponse response = service.message(options).execute();

//                    service.setApiKey(conversation_iam_apikey);
//                    service.setEndPoint(conversation_url);
//                    InputData input = new InputData.Builder(inputmessage).build();
//                    MessageOptions options = new MessageOptions.Builder(workspace_id).input(input).context(context).build();
//                    MessageResponse response = service.message(options).execute();

                    //Passing Context of last conversation
                    if(response.getContext() !=null)
                    {
                        //context.clear();
                        context = response.getContext();

                    }
                    if(response!=null)
                    {
                        if(response.getOutput()!=null && response.getOutput().containsKey("text"))
                        {

                            ArrayList responseList = (ArrayList) response.getOutput().get("text");
                            if(null !=responseList && responseList.size()>0){

                                System.out.println("Responseeeeee: " + responseList.get(0));
                                reply = responseList.get(0).toString();

                                new EnglishToBangla().execute();

                                //outMessage.setMessage((String)responseList.get(0));


                            }
//                            Thread thread = new Thread(new Runnable() {
//                                public void run() {
//                                    Message audioMessage;
//                                    try {
//
//                                        audioMessage = outMessage;
//                                        streamPlayer = new StreamPlayer();
//                                        if(audioMessage != null && !audioMessage.getMessage().isEmpty())
//                                            //Change the Voice format and choose from the available choices
//                                            streamPlayer.playStream(textToSpeech.synthesize(audioMessage.getMessage(), Voice.EN_LISA).execute());
//                                        else
//                                            streamPlayer.playStream(textToSpeech.synthesize("No Text Specified", Voice.EN_LISA).execute());
//
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                            });
//                            thread.start();
                        }




                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }


    //for translation purpose
    private class EnglishToBangla extends AsyncTask<Void, Void, String> {
        private ProgressDialog progress = null;

        protected void onError(Exception ex) {

        }

        public String prettify(String json_text) throws JSONException {
            JsonParser parser = new JsonParser();
            JsonElement json = parser.parse(json_text);

            JSONArray array = new JSONArray(json_text);
            JSONObject obj = array.getJSONObject(0);
            //System.out.println("what" + obj.toString());

            String resourceObject = obj.getString("translations");

            JSONArray array1 = obj.getJSONArray("translations");
            JSONObject obj1 = array1.getJSONObject(0);
            resourceObject = obj1.getString("text");

            System.out.println("outputttttt: "+resourceObject);
            //translatedText = resourceObject;


            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            //return gson.toJson(json);
            return resourceObject;
        }

        public String Translate () throws Exception {
            URL url = new URL (host + path + params);

            List<Translate.RequestBody> objList = new ArrayList<Translate.RequestBody>();
            //objList.add(new Translate.RequestBody("How are you today?"));
            //objList.add(new Translate.RequestBody(translateedittext.getText().toString())); //have to assign text which is received
            objList.add(new Translate.RequestBody(reply));
            String content = new Gson().toJson(objList);

            return Post(url, content);
        }


        @Override
        protected String doInBackground(Void... params) {

            try {
                String response = Translate();
                //System.out.println("Yayyyyyyyyyyyyyyyy: " + response);
                String xx = prettify(response);
                System.out.println (xx);
                //Typeface face=Typeface.createFromAsset(getAssets(), "fonts/Bangla.ttf");

                System.out.println("In Backgroundddddd: " + xx);
                reply = xx;

                return xx;

            }
            catch (Exception e) {
                System.out.println (e);
            }
            return null;

        }



        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPreExecute() {
            //start the progress dialog
            //progress = ProgressDialog.show(MainActivity.this, null, "Translating...");
            //progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            //progress.setIndeterminate(true);
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String result) {
//            progress.dismiss();


//            Typeface myTypeFace = Typeface.createFromAsset(getAssets(), "fonts/Bangla.ttf");
//            translated = (TextView) findViewById(R.id.translated);
//            translated.setTypeface(myTypeFace);
//            translated.setText(result);
            System.out.println("Banglaaaaaa: " + reply);
            final Message outMessage=new Message();

            outMessage.setMessage(reply);
            outMessage.setId("2");
            messageArrayList.add(outMessage);
            runOnUiThread(new Runnable() {
                public void run() {
                    mAdapter.notifyDataSetChanged();
                    if (mAdapter.getItemCount() > 1) {
                        recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount()-1);

                    }

                }
            });
            super.onPostExecute(result);
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

    } //end for translation


    //Record a message via Watson Speech to Text
    private void recordMessage() {
        //mic.setEnabled(false);
        speechService = new SpeechToText();
        speechService.setUsernameAndPassword(STT_username, STT_password);


        if(listening != true) {
            capture = microphoneHelper.getInputStream(true);
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        speechService.recognizeUsingWebSocket(capture, getRecognizeOptions(), new MicrophoneRecognizeDelegate());
                    } catch (Exception e) {
                        showError(e);
                    }
                }
            }).start();
            listening = true;
            Toast.makeText(MainActivity.this,"Listening....Click to Stop", Toast.LENGTH_LONG).show();

        } else {
            try {
                microphoneHelper.closeInputStream();
                listening = false;
                Toast.makeText(MainActivity.this,"Stopped Listening....Click to Start", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Check Internet Connection
     * @return
     */
    private boolean checkInternetConnection() {
        // get Connectivity Manager object to check connection
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        // Check for network connections
        if (isConnected){
            return true;
        }
        else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }

    }

    //Private Methods - Speech to Text
    private RecognizeOptions getRecognizeOptions() {
        return new RecognizeOptions.Builder()
                //.continuous(true)
                .contentType(ContentType.OPUS.toString())
                //.model("en-UK_NarrowbandModel")
                .interimResults(true)
                .inactivityTimeout(2000)
                //TODO: Uncomment this to enable Speaker Diarization
                //.speakerLabels(true)
                .build();
    }

    //Watson Speech to Text Methods.
    private class MicrophoneRecognizeDelegate implements RecognizeCallback {
        @Override
        public void onTranscription(SpeechResults speechResults) {
            //TODO: Uncomment this to enable Speaker Diarization
            /*recoTokens = new SpeakerLabelsDiarization.RecoTokens();
            if(speechResults.getSpeakerLabels() !=null)
            {
                recoTokens.add(speechResults);
                Log.i("SPEECHRESULTS",speechResults.getSpeakerLabels().get(0).toString());


            }*/
            if(speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                showMicText(text);
            }
        }

        @Override public void onConnected() {

        }

        @Override public void onError(Exception e) {
            showError(e);
            enableMicButton();
        }

        @Override public void onDisconnected() {
            enableMicButton();
        }

        @Override
        public void onInactivityTimeout(RuntimeException runtimeException) {

        }

        @Override
        public void onListening() {

        }

        @Override
        public void onTranscriptionComplete() {

        }
    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                inputMessage.setText(text);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                btnRecord.setEnabled(true);
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }



}



