package name.chadschultz.nearbyapidemo;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.Connections.StartAdvertisingResult;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.messages.BleSignal;
import com.google.android.gms.nearby.messages.Distance;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.google.android.gms.nearby.messages.Strategy.DISTANCE_TYPE_EARSHOT;

//TODO: WARNING: problems with Nearby Connections. The walkie-talkie sample app works fine,
//but for me nodes sometimes disconnect for no reason, discovery doesn't always work, and audio recording
//doesn't always work. Use Nexus 5 to send, not Nexus 6P.

public class MainActivity extends AppCompatActivity implements AccuracyPollListener, ControlListener, CameraListener, QuestionListener, AnswerQuestionListener {

    public static final String TAG = "NearbyAPIDemo";

    private static final int PERMISSIONS_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 2;

    private static final String ADVERTISER_NAME = "presenter";
    private static final String SERVICE_ID = BuildConfig.APPLICATION_ID;
    private static final com.google.android.gms.nearby.connection.Strategy CONNECTIONS_STRATEGY = com.google.android.gms.nearby.connection.Strategy.P2P_STAR;

    private static final int RC_SIGN_IN = 1;

    private static final String JSON_KEY_GROUP = "group";
    private static final String JSON_KEY_DETAIL = "detail";
    private static final String JSON_KEY_UUID = "uuid";

    private static final String MESSAGE_GROUP_SCREEN = "selectedScreen";
    private static final String MESSAGE_GROUP_CONNECTION = "connectionMode";
    private static final String MESSAGE_GROUP_BEACON = "beacon";
    private static final String MESSAGE_GROUP_DISTANCE_POLL = "distancePoll";
    private static final String MESSAGE_GROUP_SETTING = "setting";
    private static final String MESSAGE_GROUP_QUESTION = "question";

    private static final String MESSAGE_CONNECT_REGULAR = "connectRegular";
    private static final String MESSAGE_CONNECT_EARSHOT = "Earshot"; //must be less than 10 bytes for AudioBytes
    private static final String MESSAGE_DISCONNECT_BOTH = "disconnectBoth";

    //whatever that custom crap is called, or enums?
    private static final String MESSAGE_SCREEN_INTRO = "screenIntro";
    private static final String MESSAGE_SCREEN_BLANK = "screenBlank";
    private static final String MESSAGE_SCREEN_DISTANCE = "screenDistance";
    private static final String MESSAGE_SCREEN_POLL_RESULTS = "screenPollResults";
    private static final String MESSAGE_SCREEN_CAMERA = "screenCamera";
    private static final String MESSAGE_SCREEN_CONTACT_INFO = "screenContactInfo";

    private static final String MESSAGE_BEACON_LEFT = "left";
    private static final String MESSAGE_BEACON_RIGHT = "right";

    private static final String MESSAGE_POLL_VERY_ACCURATE = "veryAccurate";
    private static final String MESSAGE_POLL_SOMEWHAT_ACCURATE = "somewhatAccurate";
    private static final String MESSAGE_POLL_SOMEWHAT_INACCURATE = "somewhatInaccurate";
    private static final String MESSAGE_POLL_VERY_INACCURATE = "veryInaccurate";

    private static final String JSON_KEY_DISTANCE_BETWEEN_BEACONS = "distanceBetweenBeacons";

    private static final String JSON_KEY_NAME = "name";

    private TextView questionTextView;

    private List<Question> questions;
    private List<Question> answeredQuestions;

    boolean advertising, discovering;

    //TODO: persist this data?
    boolean presenterMode;
    Distance leftBeaconDistance;
    Distance rightBeaconDistance;
    String uniqueID = UUID.randomUUID().toString();
    int veryAccurateCount, somewhatAccurateCount, somewhatInaccurateCount, veryInaccurateCount;
    //Beacons must be physically located exactly this far apart
    double distanceBetweenBeacons = 3.0; //default to 3m / 10 feet
    List<String> connectedEndpointIds; //Only used by host
    Question currentQuestion;

    //TODO: can I use more than one Google client at the same time? Can I use one client for multiple APIs?
    //TODO: do I need a separate client for Connections?
    private GoogleApiClient connectionsGoogleApiClient;
    private GoogleApiClient googleApiClient;
    private Message connectionMessage;
    private Message currentScreenMessage;
    private Message pollResponseMessage;
    private Message settingMessage;
    private Message questionMessage;

    private OnConnectionFailedListener connectionFailedListener = new OnConnectionFailedListener() {

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_LONG).show();
        }
    };

    private ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.i(TAG, "onConnected()");
            subscribe();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.w(TAG, "onConnectionSuspended(" + i +")");
        }
    };

    //TODO display diagnostic messages to the presenter in a textview or toast or something, but not to users?
    //TODO: remember to save and restore and use presenter mode on/off

    //TODO: why have some listeners are variables and some baked into the class?
    private MessageListener mMessageListener = new MessageListener() {
        @Override
        public void onFound(Message message) {

            Log.d(TAG, "onFound(" + new String(message.getContent()));
            //Don't worry about reading beacon messages here--we're only interested in onDistanceChanged

            //TODO: remove?
            if (message.getType().equals(Message.MESSAGE_TYPE_AUDIO_BYTES)) {
                showMessage("Received audio bytes message");

                String messageString = new String(message.getContent());
                if (messageString.equals(MESSAGE_CONNECT_EARSHOT)) {
                    if (getCurrentFragment() instanceof ConnectFragment) {
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.connectedEarshot();
                    }
                }
                //TODO split up different messages into different methods
                return;
            }

            JSONObject jsonObject;
            String group;
            String detail;
            try {
                jsonObject = new JSONObject(new String(message.getContent()));
                group = jsonObject.getString(JSON_KEY_GROUP);
                detail = jsonObject.getString(JSON_KEY_DETAIL);
            } catch (JSONException e) {
                //TODO: better error handling
                throw new RuntimeException(e);
            }
            if (MESSAGE_GROUP_CONNECTION.equals(group) && !presenterMode) {
                if (MESSAGE_CONNECT_REGULAR.equals(detail)) {
                    //TODO simplify logic, reduce repetition
                    if (getCurrentFragment() instanceof ConnectFragment) {
                        //TODO: move to intro fragment if necessary
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.connectedRegular();
                    }
                } else if (MESSAGE_CONNECT_EARSHOT.equals(detail)) {
                    if (getCurrentFragment() instanceof ConnectFragment) {
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.connectedEarshot();
                    }
                } else if (MESSAGE_DISCONNECT_BOTH.equals(detail)) {
                    if (getCurrentFragment() instanceof ConnectFragment) {
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.disconnectBoth();
                    }
                }
            } else if (MESSAGE_GROUP_SCREEN.equals(group) && !presenterMode) {
                if (MESSAGE_SCREEN_INTRO.equals(detail)) {
                    //TODO: should probably search to see if Fragment already exists and if so move it to front?
                    if (!(getCurrentFragment() instanceof ConnectFragment)) {
                        ConnectFragment connectFragment = ConnectFragment.newInstance();
                        replaceFragment(connectFragment);
                    }
                } else if (MESSAGE_SCREEN_BLANK.equals(detail)) {
                    if (!(getCurrentFragment() instanceof BlankFragment)) {
                        BlankFragment blankFragment = BlankFragment.newInstance();
                        replaceFragment(blankFragment);
                    }
                } else if (MESSAGE_SCREEN_DISTANCE.equals(detail)) {
                    DistanceFragment distanceFragment;
                    if (!(getCurrentFragment() instanceof DistanceFragment)) {
                        distanceFragment = DistanceFragment.newInstance(distanceBetweenBeacons);
                        replaceFragment(distanceFragment);
                    } else {
                        distanceFragment = (DistanceFragment) getCurrentFragment();
                    }

                    if (leftBeaconDistance != null && rightBeaconDistance != null) {
                        distanceFragment.updateDistances(leftBeaconDistance, rightBeaconDistance);
                    }
                    updatePollResultsIfNeeded();
                } else if (MESSAGE_SCREEN_POLL_RESULTS.equals(detail)) {
                    if (!(getCurrentFragment() instanceof DistanceFragment)) {
                        DistanceFragment distanceFragment = DistanceFragment.newInstance(distanceBetweenBeacons);
                        replaceFragment(distanceFragment);
                    }
                    DistanceFragment distanceFragment = (DistanceFragment) getCurrentFragment();
                    //TODO:
//                    distanceFragment.updateResults();
                } else if (MESSAGE_SCREEN_CAMERA.equals(detail)) {
                    if (!(getCurrentFragment() instanceof CameraFragment)) {
                        CameraFragment cameraFragment = CameraFragment.newInstance();
                        replaceFragment(cameraFragment);
                        // Set the media volume to max.
                        setVolumeControlStream(AudioManager.STREAM_MUSIC);
                        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        mOriginalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                    }
                } else if (MESSAGE_SCREEN_CONTACT_INFO.equals(detail)) {
                    if (!(getCurrentFragment() instanceof ContactInfoFragment)) {
                        ContactInfoFragment contactInfoFragment = ContactInfoFragment.newInstance();
                        replaceFragment(contactInfoFragment);
                    }
                }
            } else if (MESSAGE_GROUP_DISTANCE_POLL.equals(group)) {
                if (MESSAGE_POLL_VERY_ACCURATE.equals(detail)) {
                    veryAccurateCount++;
                } else if (MESSAGE_POLL_SOMEWHAT_ACCURATE.equals(detail)) {
                    somewhatAccurateCount++;
                } else if (MESSAGE_POLL_SOMEWHAT_INACCURATE.equals(detail)) {
                    somewhatInaccurateCount++;
                } else {
                    veryInaccurateCount++;
                }
                updatePollResultsIfNeeded();
            } else if (MESSAGE_GROUP_SETTING.equals(group)) {
                if (jsonObject.has(JSON_KEY_DISTANCE_BETWEEN_BEACONS)) {
                    try {
                        double distanceBetweenBeacons = jsonObject.getDouble(JSON_KEY_DISTANCE_BETWEEN_BEACONS);
                        updateDistanceBetweenBeacons(distanceBetweenBeacons);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (MESSAGE_GROUP_QUESTION.equals(group)) {
                Question question = new Question();
                question.setQuestion(detail);
                if (jsonObject.has(JSON_KEY_NAME)) {
                    try {
                        question.setName(jsonObject.getString(JSON_KEY_NAME));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                questions.add(question);
                filterQuestions();
                if (getCurrentFragment() instanceof QuestionListFragment) {
                    QuestionListFragment questionListFragment = (QuestionListFragment) getCurrentFragment();
                    questionListFragment.setQuestions(questions);
                }
            }
        }

        @Override
        public void onLost(Message message) {
            Log.d(TAG, "onLost(" + new String(message.getContent()) + ")");

            JSONObject jsonObject;
            String group;
            String detail;
            try {
                jsonObject = new JSONObject(new String(message.getContent()));
                group = jsonObject.getString(JSON_KEY_GROUP);
                detail = jsonObject.getString(JSON_KEY_DETAIL);
            } catch (JSONException e) {
                //TODO: better error handling
                throw new RuntimeException(e);
            }

            //TODO: what if someone changes their response? Probably best to just not let them change
            if (MESSAGE_GROUP_DISTANCE_POLL.equals(group)) {
                if (MESSAGE_POLL_VERY_ACCURATE.equals(detail)) {
                    veryAccurateCount--;
                } else if (MESSAGE_POLL_SOMEWHAT_ACCURATE.equals(detail)) {
                    somewhatAccurateCount--;
                } else if (MESSAGE_POLL_SOMEWHAT_INACCURATE.equals(detail)) {
                    somewhatInaccurateCount--;
                } else {
                    veryInaccurateCount--;
                }
                updatePollResultsIfNeeded();
            } else if (MESSAGE_GROUP_QUESTION.equals(group)) {
                //TODO: refactor out repetition
                Question question = new Question();
                question.setQuestion(detail);
                if (jsonObject.has(JSON_KEY_NAME)) {
                    try {
                        question.setName(jsonObject.getString(JSON_KEY_NAME));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                questions.remove(question);
                if (presenterMode) {
                    questionTextView.setText(getString(R.string.answer_questions_button, questions.size()));
                }
                //TODO: ensure removing via unpublish works
                if (getCurrentFragment() instanceof QuestionListFragment) {
                    QuestionListFragment questionListFragment = (QuestionListFragment) getCurrentFragment();
                    questionListFragment.setQuestions(questions);
                }
            }
        }

        @Override
        public void onDistanceChanged(Message message, Distance distance) {
            String messageString = new String(message.getContent());
            String group;
            String detail;
            try {
                JSONObject jsonObject = new JSONObject(messageString);
                group = jsonObject.getString(JSON_KEY_GROUP);
                detail = jsonObject.getString(JSON_KEY_DETAIL);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            Log.d(TAG, "onDistanceChanged: " + messageString + ", Distance: " + distance);
            if (MESSAGE_GROUP_BEACON.equals(group)) {
                if (MESSAGE_BEACON_LEFT.equals(detail)) {
                    leftBeaconDistance = distance;
                } else if (MESSAGE_BEACON_RIGHT.equals(detail)) {
                    rightBeaconDistance = distance;
                } else {
                    Log.w(TAG, "Don't recognize beacon with message: " + messageString);
                }

                if (leftBeaconDistance != null
                    && rightBeaconDistance != null
                    && getCurrentFragment() instanceof DistanceFragment) {
                    DistanceFragment distanceFragment = (DistanceFragment) getCurrentFragment();
                    distanceFragment.updateDistances(leftBeaconDistance, rightBeaconDistance);
                }
            }
        }

        @Override
        public void onBleSignalChanged(Message message, BleSignal bleSignal) {
            Log.d(TAG, "onBleSignalChanged(" + new String(message.getContent()) + ", " + bleSignal);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectedEndpointIds = new ArrayList<>();

        questions = new ArrayList<>();
        answeredQuestions = new ArrayList<>();
        questionTextView = (TextView) findViewById(R.id.question_textview);
        questionTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (presenterMode) {
                    QuestionListFragment questionListFragment;
                    if (getCurrentFragment() instanceof QuestionListFragment) {
                        questionListFragment = (QuestionListFragment) getCurrentFragment();
                    } else {
                        questionListFragment = QuestionListFragment.newInstance();
                        replaceFragmentAndAddToBackStack(questionListFragment);
                    }
                    questionListFragment.setQuestions(questions);
                } else {
                    AskQuestionDialogFragment askQuestionDialogFragment = AskQuestionDialogFragment.newInstance(currentQuestion);
                    askQuestionDialogFragment.show(getSupportFragmentManager(), "askQuestion");
                }
            }
        });
        questionTextView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
                startActivityForResult(signInIntent, RC_SIGN_IN);
                return false;
            }
        });

        if (savedInstanceState == null) {
            Fragment fragment = ConnectFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, fragment, fragment.getClass().getName())
                    .commit();
            getSupportFragmentManager().executePendingTransactions();
        }

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(connectionCallbacks)
                .enableAutoManage(this, connectionFailedListener)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        connectionsGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.i(TAG, "connectionsGoogleApiClient onConnected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.i(TAG, "connectionsGoogleApiClient onConnectionSuspended");
                    }
                })
                .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.e(TAG, "connectionsGoogleApiClient onConnectionFailed: " + connectionResult);
                    }
                })
                .addApi(Nearby.CONNECTIONS_API)
                .build();

        // Best practice when using Nearby, or at least Nearby Connections. See
        // https://developers.google.com/nearby/connections/android/manage-connections
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ActivityCompat.requestPermissions(this, new String[]{permission.ACCESS_COARSE_LOCATION},
                PERMISSIONS_REQUEST_COARSE_LOCATION);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //TODO: should this be with the other connection initialization?
        Log.d(TAG, "connecting to connectionsGoogleApiClient");
        connectionsGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        // the documentation specifically shows these coming before the super call
        unpublish();
        unsubscribe();

        //TODO: temp see if this crashes, maybe move to onDestroy?
        disconnectFromDevices();

        // Restore the original volume.
        if (!presenterMode) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVolume, 0);
            setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        }

        // Stop all audio-related threads
        if (isRecording()) {
            stopRecording();
        }
        if (isPlaying()) {
            stopPlaying();
        }

        super.onStop();

        if (connectionsGoogleApiClient != null && connectionsGoogleApiClient.isConnected()) {
            Log.d(TAG, "disconnecting from connectionsGoogleApiClient");
            connectionsGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = result.getSignInAccount();
            if (acct != null) {
                Log.d(TAG, acct.getEmail() + " signed in.");
                if (getString(R.string.presenter_email).equalsIgnoreCase(acct.getEmail())) {
                    showMessage(R.string.user_valid);
                    setupUi(true);
                    ActivityCompat.requestPermissions(this, new String[]{permission.RECORD_AUDIO},
                            PERMISSIONS_REQUEST_RECORD_AUDIO);
                } else {
                    showMessage(R.string.user_invalid);
                    onSignOut();
                }
            }
        } else {
            // Signed out, show unauthenticated UI.
            setupUi(false);
            showMessage(R.string.sign_in_failed);
        }
    }

    private void setupUi(boolean presenterMode) {
        this.presenterMode = presenterMode;
        if (presenterMode) {
            questionTextView.setText(getString(R.string.answer_questions_button, questions.size()));
            replaceFragment(ControlFragment.newInstance(distanceBetweenBeacons));
        } else {
            questionTextView.setText(R.string.ask_question_button);
            //TODO: read data on current Fragment that should be displayed, and show that, assuming
            //that we haven't just started the app
            //TODO: temp
            replaceFragment(ConnectFragment.newInstance());
        }
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    private void replaceFragmentAndAddToBackStack(Fragment fragment) {
        //Combine with other method, reduce duplication
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, fragment.getClass().getName())
                .addToBackStack(null)
                .commit();
        //TODO: is this line still necessary?
        getSupportFragmentManager().executePendingTransactions();
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, fragment.getClass().getName())
                .commit();
        //TODO: is this line still necessary?
        getSupportFragmentManager().executePendingTransactions();
    }

    private void showMessage(@StringRes int messageResId) {
        showMessage(getString(messageResId));
    }

    private void showMessage(CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updatePollResultsIfNeeded() {
        if (pollResponseMessage == null) {
            return;
        }

        if (getCurrentFragment() instanceof DistanceFragment) {
            DistanceFragment distanceFragment = (DistanceFragment) getCurrentFragment();
            double sum = veryAccurateCount + somewhatAccurateCount + somewhatInaccurateCount + veryInaccurateCount;
            double veryAccuratePercentage = veryAccurateCount / sum;
            double somewhatAccuratePercentage = somewhatAccurateCount / sum;
            double somewhatInaccuratePercentage = somewhatInaccurateCount / sum;
            double veryInaccuratePercentage = veryInaccurateCount / sum;
            distanceFragment.updateResults(veryAccurateCount, veryAccuratePercentage,
                    somewhatAccurateCount, somewhatAccuratePercentage,
                    somewhatInaccurateCount, somewhatInaccuratePercentage,
                    veryInaccurateCount, veryInaccuratePercentage);
        }
    }

    private void subscribe() {
        Log.i(TAG, "Subscribing.");
        if (googleApiClient != null) {

            //TODO: investigate MessageFilter?
//            Strategy strategy = new Strategy.Builder().setDistanceType(DISTANCE_TYPE_EARSHOT).build();
            Strategy strategy = new Strategy.Builder().setTtlSeconds(Strategy.TTL_SECONDS_INFINITE).build();
            SubscribeOptions subscribeOptions = new SubscribeOptions.Builder()
                    .setStrategy(strategy)
                    .setCallback(new SubscribeCallback() {
                @Override
                public void onExpired() {
                    Log.e(TAG, "Nearby subscription expired!");
                }
            }).build();
//            SubscribeOptions subscribeOptions = new SubscribeOptions.Builder().setStrategy(strategy).build();

            PendingResult<Status> status = Nearby.Messages.subscribe(googleApiClient, mMessageListener, subscribeOptions);
//            PendingResult<Status> status = Nearby.Messages.subscribe(googleApiClient, mMessageListener);
            status.setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    Log.i(TAG, "subscribe result: " + status);
                }
            });
        }
    }

    private void unsubscribe() {
        Log.i(TAG, "Unsubscribing.");
        if (googleApiClient != null) {
            Nearby.Messages.unsubscribe(googleApiClient, mMessageListener);
        }
    }

    private void unpublish() {
        Log.i(TAG, "Unpublishing.");
        //TODO: allow for multiple different messages
        if (connectionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, connectionMessage);
            connectionMessage = null;
        }
    }

    private void changeCurrentScreen(String newScreen) {
        // Leaving camera screen
        if ((discovering || advertising) && !newScreen.equals(MESSAGE_SCREEN_CAMERA)) {
            disconnectFromDevices();
        }

        if (currentScreenMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, currentScreenMessage);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_SCREEN);
            jsonObject.put(JSON_KEY_DETAIL, newScreen);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        currentScreenMessage = new Message(jsonObject.toString().getBytes());
        Log.i(TAG, "Publishing: " + jsonObject.toString());
        PublishOptions publishOptions = new PublishOptions.Builder().setCallback(new PublishCallback() {
            @Override
            public void onExpired() {
                Log.w(TAG, "change current screen message: " + new String(currentScreenMessage.getContent()) + "has expired");
            }
        }).build();
        PendingResult<Status> result = Nearby.Messages.publish(googleApiClient, currentScreenMessage);
        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                Log.i(TAG, "changeCurrentScreen result status: " + status);
            }
        });
    }

    private void voteDistancePoll(String pollResponse) {
        if (pollResponseMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, pollResponseMessage);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_DISTANCE_POLL);
            jsonObject.put(JSON_KEY_DETAIL, pollResponse);
            jsonObject.put(JSON_KEY_UUID, uniqueID);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        pollResponseMessage = new Message(jsonObject.toString().getBytes());
        Nearby.Messages.publish(googleApiClient, pollResponseMessage);

        updatePollResultsIfNeeded();
    }

    private void updateDistanceBetweenBeacons(double distanceBetweenBeacons) {
        this.distanceBetweenBeacons = distanceBetweenBeacons;
        if (getCurrentFragment() instanceof DistanceFragment) {
            DistanceFragment distanceFragment = (DistanceFragment) getCurrentFragment();
            distanceFragment.updateDistanceBetweenBeacons(distanceBetweenBeacons);
        }
    }

    private void startAdvertising() {
        Nearby.Connections.startAdvertising(
                connectionsGoogleApiClient,
                ADVERTISER_NAME,
                SERVICE_ID,
                connectionLifecycleCallback,
                new AdvertisingOptions(com.google.android.gms.nearby.connection.Strategy.P2P_STAR))
            .setResultCallback(
                    new ResultCallback<StartAdvertisingResult>() {
                        @Override
                        public void onResult(@NonNull StartAdvertisingResult result) {
                            if (result.getStatus().isSuccess()) {
                                // Device is advertising
                                Log.i(TAG, "Successfully advertising");
                                advertising = true;
                            } else {
                                int statusCode = result.getStatus().getStatusCode();
                                // Advertising failed - see statusCode for more details
                                Log.e(TAG, "advertising failed with status code " + statusCode + " and status message " + result.getStatus().getStatusMessage());
                                advertising = false;
                            }
                        }
            });
    }

    private void stopAdvertising() {
        Nearby.Connections.stopAdvertising(connectionsGoogleApiClient);
        advertising = false;
        Log.i(TAG, "Stopped advertising");
    }

    // We're using the same callback for advertiser and discoverer. Both are treated identically and must approve,
    // even though the discoverer already made a request to connect. Go figure!
    ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
            // Automatically accept the connection on both sides.
            // The alternative would be to use connectionInfo.getAuthenticationToken() to validate, most likely
            // in a case where two people compare the devices side by side, showing the token on both
            Nearby.Connections.acceptConnection(
                    connectionsGoogleApiClient, endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // We're connected! Can now start sending and receiving data.
                    Log.d(TAG, "Connected to endpoint " + endpointId);
                    //TODO: will this prevent one side from auto-disconnecting?
                    if (!presenterMode) {
                        stopDiscovery();
                        if (getCurrentFragment() instanceof CameraFragment) {
                            ((CameraFragment)getCurrentFragment()).discoveredConnected();
                        }
                    }

                    //TODO: temp
                    if (presenterMode) {
                        connectedEndpointIds.add(endpointId);
                        updateConnectedCount(connectedEndpointIds.size());
//                        byte[] message = "Hello from Presenter".getBytes(Charset.forName("UTF-8"));
//                        Nearby.Connections.sendPayload(connectionsGoogleApiClient, endpointId, Payload.fromBytes(message));
                    }
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    // The connection was rejected by one or both sides.
                    Log.d(TAG, "Could not connect to endpoint " + endpointId);
                    if (!presenterMode) {
                        if (getCurrentFragment() instanceof CameraFragment) {
                            ((CameraFragment)getCurrentFragment()).discoveringFailed();
                        }
                    }
                    break;
            }
        }

        @Override
        public void onDisconnected(String endpointId) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
            connectedEndpointIds.remove(endpointId);
            updateConnectedCount(connectedEndpointIds.size());
            if (!presenterMode) {
                if (getCurrentFragment() instanceof CameraFragment) {
                    ((CameraFragment)getCurrentFragment()).discoveringFailed();
                }
            }
            Log.d(TAG, "Disconnected from endpoint " + endpointId);
        }
    };

    private void updateConnectedCount(int count) {
        if (getCurrentFragment() instanceof ControlFragment) {
            ((ControlFragment) getCurrentFragment()).updateConnectedCount(count);
        }
    }

    PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            Log.d(TAG, String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload));
//            String message = new String(payload.asBytes(), Charset.forName("UTF-8"));
//            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            if (payload.getType() == Payload.Type.STREAM) {
                if (mAudioPlayer != null) {
                    mAudioPlayer.stop();
                    mAudioPlayer = null;
                }

                AudioPlayer player =
                        new AudioPlayer(payload.asStream().asInputStream()) {
                            @WorkerThread
                            @Override
                            protected void onFinish() {
                                runOnUiThread(
                                        new Runnable() {
                                            @UiThread
                                            @Override
                                            public void run() {
                                                mAudioPlayer = null;
                                            }
                                        });
                            }
                        };
                mAudioPlayer = player;
                player.start();
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            Log.d(TAG, String.format(
                    "onPayloadTransferUpdate(endpointId=%s, update=%s)", endpointId, update));
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo discoveredEndpointInfo) {
            Log.d(TAG, "endpoint " + endpointId + " found");
            // Here we're automatically trying to connect to anyone who wants to connect with us. The docs suggest
            // "Depending on your use case, you may wish to instead display a list of discovered devices to the user,
            // allowing them to choose which devices to connect to."
            Nearby.Connections.requestConnection(
                    connectionsGoogleApiClient,
                    ADVERTISER_NAME,
                    endpointId,
                    connectionLifecycleCallback)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (status.isSuccess()) {
                                    // We successfully requested a connection. Now both sides
                                    // must accept before the connection is established.
                                } else {
                                    // Nearby Connections failed to request the connection.
                                }
                            }
                        }
                );
        }

        @Override
        public void onEndpointLost(String endPointId) {
            Log.d(TAG, "endpoint " + endPointId + " lost");
        }
    };

    private void startDiscovery() {
        Nearby.Connections.startDiscovery(
                connectionsGoogleApiClient,
                BuildConfig.APPLICATION_ID,
                endpointDiscoveryCallback,
                new DiscoveryOptions(CONNECTIONS_STRATEGY))
            .setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            if (status.isSuccess()) {
                                Log.i(TAG, "Successfully discovering");
                                discovering = true;
                            } else {
                                int statusCode = status.getStatusCode();
                                Log.i(TAG, "discovering failed with status code " + statusCode + " and status message: " + status.getStatusMessage());
                                discovering = false;
                            }
                        }
                    }
            );
    }

    private void stopDiscovery() {
        Nearby.Connections.stopDiscovery(connectionsGoogleApiClient);
        discovering = false;
        Log.i(TAG, "Stopped discovery");
    }

    private void disconnectFromDevices() {
        connectedEndpointIds = new ArrayList<>();
        if (advertising) {
            stopAdvertising();
        }
        if (discovering) {
            stopDiscovery();
        }
        //TODO: do I need this?
        if (connectedEndpointIds.size() > 0) {
            Nearby.Connections.stopAllEndpoints(googleApiClient);
        }
    }

    private void filterQuestions() {
        List<Question> newQuestionList = new ArrayList<Question>();
        for (Question question : questions) {
            if (!answeredQuestions.contains(question)) {
                newQuestionList.add(question);
            }
        }
        questions.clear();
        questions.addAll(newQuestionList);
        if (presenterMode) {
            questionTextView.setText(getString(R.string.answer_questions_button, questions.size()));
        }
    }

    /* AccuracyPollListener Methods */
    @Override
    public void onVeryAccurateClicked() {
        veryAccurateCount++;
        voteDistancePoll(MESSAGE_POLL_VERY_ACCURATE);
    }

    @Override
    public void onSomewhatAccurateClicked() {
        somewhatAccurateCount++;
        voteDistancePoll(MESSAGE_POLL_SOMEWHAT_ACCURATE);
    }

    @Override
    public void onSomewhatInaccurateClicked() {
        somewhatInaccurateCount++;
        voteDistancePoll(MESSAGE_POLL_SOMEWHAT_INACCURATE);
    }

    @Override
    public void onVeryInaccurateClicked() {
        veryInaccurateCount++;
        voteDistancePoll(MESSAGE_POLL_VERY_INACCURATE);
    }

    /* ControlListener Methods */

    @Override
    public void onConnectEarshot() {
//        if (connectionMessage != null) {
//            Nearby.Messages.unpublish(googleApiClient, connectionMessage);
//        }
//
//        AudioBytes audioBytes = new AudioBytes(MESSAGE_CONNECT_EARSHOT.getBytes());
//        connectionMessage = audioBytes.toMessage();
//
//        showMessage(connectionMessage.toString());
//
//        Nearby.Messages.publish(googleApiClient, connectionMessage);

        if (connectionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, connectionMessage);
        }

        //Different objects for different messages?
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_CONNECTION);
            jsonObject.put(JSON_KEY_DETAIL, MESSAGE_CONNECT_EARSHOT);
        } catch (JSONException e) {
            //TODO: better error handling
            throw new RuntimeException(e);
        }

        Log.d(TAG, "Publishing: " + jsonObject.toString());

        connectionMessage = new Message(jsonObject.toString().getBytes());
//        PublishCallback publishCallback = new PublishCallback() {
//            @Override
//            public void onExpired() {
//                //TODO:
//                super.onExpired();
//            }
//        };
//        PublishOptions publishOptions = new PublishOptions.Builder()
//                .setCallback(publishCallback)

        Strategy earshotStrategy = new Strategy.Builder().setDistanceType(DISTANCE_TYPE_EARSHOT).build();
        PublishOptions earshotPublishOptions = new PublishOptions.Builder().setStrategy(earshotStrategy).build();

        Nearby.Messages.publish(googleApiClient, connectionMessage, earshotPublishOptions);
    }

    @Override
    public void onDisconnectEarshot() {

    }

    @Override
    public void onConnectRegular() {
        if (connectionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, connectionMessage);
        }

        //Different objects for different messages?
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_CONNECTION);
            jsonObject.put(JSON_KEY_DETAIL, MESSAGE_CONNECT_REGULAR);
        } catch (JSONException e) {
            //TODO: better error handling
            throw new RuntimeException(e);
        }

        Log.d(TAG, "Publishing: " + jsonObject.toString());

        connectionMessage = new Message(jsonObject.toString().getBytes());
//        PublishCallback publishCallback = new PublishCallback() {
//            @Override
//            public void onExpired() {
//                //TODO:
//                super.onExpired();
//            }
//        };
//        PublishOptions publishOptions = new PublishOptions.Builder()
//                .setCallback(publishCallback)

        Nearby.Messages.publish(googleApiClient, connectionMessage);
    }
//TODO: should I get rid of the disconnect methods?
    @Override
    public void onDisconnectRegular() {

    }

    @Override
    public void onDisconnectBoth() {
        //TODO: consolidate, clean up
        if (connectionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, connectionMessage);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_CONNECTION);
            jsonObject.put(JSON_KEY_DETAIL, MESSAGE_DISCONNECT_BOTH);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        connectionMessage = new Message(jsonObject.toString().getBytes());
        Nearby.Messages.publish(googleApiClient, connectionMessage);
    }

    @Override
    public void onBlank() {
        changeCurrentScreen(MESSAGE_SCREEN_BLANK);
    }

    @Override
    public void onIntro() {
        changeCurrentScreen(MESSAGE_SCREEN_INTRO);
    }

    @Override
    public void onUpdateDistance(double distanceBetweenBeacons) {
        updateDistanceBetweenBeacons(distanceBetweenBeacons);

        if (settingMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, settingMessage);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_SETTING);
            jsonObject.put(JSON_KEY_DETAIL, "");
            jsonObject.put(JSON_KEY_DISTANCE_BETWEEN_BEACONS, distanceBetweenBeacons);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        settingMessage = new Message(jsonObject.toString().getBytes());
        Nearby.Messages.publish(googleApiClient, settingMessage);
    }

    @Override
    public void onDistance() {
        changeCurrentScreen(MESSAGE_SCREEN_DISTANCE);
    }

    @Override
    public void onPollResults() {
        changeCurrentScreen(MESSAGE_SCREEN_POLL_RESULTS);
    }

    @Override
    public void onCamera() {
//        ActivityCompat.requestPermissions(this, new String[]{permission.RECORD_AUDIO},
//                PERMISSIONS_REQUEST_RECORD_AUDIO);
        changeCurrentScreen(MESSAGE_SCREEN_CAMERA);
        startAdvertising();
    }

    @Override
    public void onContactInfo() {
        changeCurrentScreen(MESSAGE_SCREEN_CONTACT_INFO);
    }

    @Override
    public void onSignOut() {
        //Note: You must confirm that GoogleApiClient.onConnected has been called before you call signOut.
        Auth.GoogleSignInApi.signOut(googleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        Log.d(TAG, "signOut status: " + status);
                        setupUi(false);
                    }
                });
    }

    /* CameraListener Methods */

    @Override
    public void onStartCamera() {
        //TODO - start streaming data
        Log.d(TAG, "onStartCamera()");
        startDiscovery();
    }

    /* QuestionListener Methods */

    @Override
    public void onUnpublishQuestion() {
        if (questionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, questionMessage);
        }
    }

    @Override
    public void onPublishQuestion(String name, String question) {
        if (questionMessage != null) {
            Nearby.Messages.unpublish(googleApiClient, questionMessage);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSON_KEY_GROUP, MESSAGE_GROUP_QUESTION);
            jsonObject.put(JSON_KEY_DETAIL, question);
            jsonObject.put(JSON_KEY_NAME, name);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        questionMessage = new Message(jsonObject.toString().getBytes());
        Log.d(TAG, "Sending: " + new String(questionMessage.getContent()));
        PublishOptions publishOptions = new PublishOptions.Builder().setCallback(new PublishCallback() {
            @Override
            public void onExpired() {
                Log.w(TAG, "question message: " + new String(questionMessage.getContent()) + "has expired");
            }
        }).build();
        Nearby.Messages.publish(googleApiClient, questionMessage, publishOptions);

        currentQuestion = new Question(name, question);
    }

    /* AnswerQuestionListener Methods */

    @Override
    public void onQuestionRead(Question question) {
        answeredQuestions.add(question);
        filterQuestions();
        QuestionListFragment questionListFragment = (QuestionListFragment) getCurrentFragment();
        questionListFragment.setQuestions(questions);
    }



    /** Listens to holding/releasing the volume rocker. */
    private final GestureDetector gestureDetector =
            new GestureDetector(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP) {
                @Override
                protected void onHold() {
                    Log.d(TAG, "onHold");
                    if (connectedEndpointIds.size() > 0) {
                        startRecording();
                    }
                }

                @Override
                protected void onRelease() {
                    Log.d(TAG, "onRelease");
                    stopRecording();
                }
            };

    /** For recording audio as the user speaks. */
    @Nullable private AudioRecorder mRecorder;

    /** For playing audio from other users nearby. */
    @Nullable private AudioPlayer mAudioPlayer;

    /** The phone's original media volume. */
    private int mOriginalVolume;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (presenterMode && gestureDetector.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Stops all currently streaming audio tracks. */
    private void stopPlaying() {
        Log.v(TAG, "stopPlaying()");
        if (mAudioPlayer != null) {
            mAudioPlayer.stop();
            mAudioPlayer = null;
        }
    }

    /** @return True if currently playing. */
    private boolean isPlaying() {
        return mAudioPlayer != null;
    }

    /** Starts recording sound from the microphone and streaming it to all connected devices. */
    private void startRecording() {
        Log.v(TAG, "startRecording()");
        try {
            ParcelFileDescriptor[] payloadPipe = ParcelFileDescriptor.createPipe();

            // Send the first half of the payload (the read side) to Nearby Connections.
            send(Payload.fromStream(payloadPipe[0]));

            // Use the second half of the payload (the write side) in AudioRecorder.
            mRecorder = new AudioRecorder(payloadPipe[1]);
            mRecorder.start();
        } catch (IOException e) {
            Log.e(TAG, "startRecording() failed", e);
            Toast.makeText(this, "recording failed!", Toast.LENGTH_LONG).show();
        }
    }

    private void send(Payload payload) {
        Nearby.Connections.sendPayload(connectionsGoogleApiClient, new ArrayList<>(connectedEndpointIds), payload)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG, "Couldn't send payload. Status code: " + status.getStatusCode() + " Status message: " + status.getStatusMessage());
                                }
                            }
                        });
    }

    /** Stops streaming sound from the microphone. */
    private void stopRecording() {
        Log.v(TAG, "stopRecording()");
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder = null;
        }
    }

    /** @return True if currently streaming from the microphone. */
    private boolean isRecording() {
        return mRecorder != null && mRecorder.isRecording();
    }

    public void permissionGranted() {
        Log.d(TAG, "audio recording permission granted");
    }

    public void permissionNotGranted() {
        Log.d(TAG, "audio recording permission NOT granted");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionGranted();
            } else {
                permissionNotGranted();
            }
        } else if (requestCode == PERMISSIONS_REQUEST_COARSE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "coarse location permission granted");
            } else {
                Log.i(TAG, "coarse location permission granted");
            }
        }
    }

}