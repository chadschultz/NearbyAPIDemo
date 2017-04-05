package name.chadschultz.nearbyapidemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.Connections.ConnectionRequestListener;
import com.google.android.gms.nearby.connection.Connections.EndpointDiscoveryListener;
import com.google.android.gms.nearby.messages.BleSignal;
import com.google.android.gms.nearby.messages.Distance;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.google.android.gms.nearby.messages.Strategy.DISTANCE_TYPE_EARSHOT;

//TODO: add a way for the presenter to change the distance between the beacons?

public class MainActivity extends AppCompatActivity implements AccuracyPollListener, ControlListener, CameraListener, QuestionListener, AnswerQuestionListener {

    public static final String TAG = "NearbyAPIDemo";

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

    boolean advertising;

    //TODO: persist this data?
    boolean presenterMode;
    Distance leftBeaconDistance;
    Distance rightBeaconDistance;
    String uniqueID = UUID.randomUUID().toString();
    int veryAccurateCount, somewhatAccurateCount, somewhatInaccurateCount, veryInaccurateCount;
    //Beacons must be physically located exactly this far apart
    double distanceBetweenBeacons = 4.0; //default to 4m / 10 feet
    List<String> connectedEndpointIds; //Only used by host
    Question currentQuestion;

    //TODO: can I use more than one Google client at the same time? Can I use one client for multiple APIs?
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

            showMessage("onConnected()");

            subscribe();
        }

        @Override
        public void onConnectionSuspended(int i) {
            showMessage("onConnectionSuspended(" + i + ")");

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
            showMessage("Received: " + new String(message.getContent()));

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
                    if (!(getCurrentFragment() instanceof DistanceFragment)) {
                        DistanceFragment distanceFragment = DistanceFragment.newInstance(distanceBetweenBeacons);
                        replaceFragment(distanceFragment);

                        if (leftBeaconDistance != null && rightBeaconDistance != null) {
                            distanceFragment.updateDistances(leftBeaconDistance, rightBeaconDistance);
                        }
                    }
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
                    QuestionListFragment questionListFragment = QuestionListFragment.newInstance();
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
                //TODO: ensure removing via unpublish works
                if (getCurrentFragment() instanceof QuestionListFragment) {
                    QuestionListFragment questionListFragment = QuestionListFragment.newInstance();
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
    private SubscribeOptions mSubscribeOptions = new SubscribeOptions.Builder().build();
    private PublishOptions mPublishOptions;

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
                    QuestionListFragment questionListFragment = QuestionListFragment.newInstance();
                    replaceFragment(questionListFragment);
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
                .addApi(Nearby.CONNECTIONS_API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // Best practice when using Nearby, or at least Nearby Connections. See
        // https://developers.google.com/nearby/connections/android/manage-connections
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        // the documentation specifically shows these coming before the super call
        unpublish();
        unsubscribe();

        //TODO: temp see if this crashes, maybe move to onDestroy?
        disconnectFromDevices();

        super.onStop();
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

    private void showConnectFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (!(currentFragment instanceof ConnectFragment)) {
            replaceFragment(ConnectFragment.newInstance());
        }
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
            Strategy strategy = new Strategy.Builder().setDistanceType(DISTANCE_TYPE_EARSHOT).build();
            SubscribeOptions subscribeOptions = new SubscribeOptions.Builder().setStrategy(strategy).build();

//            Nearby.Messages.subscribe(googleApiClient, mMessageListener, mSubscribeOptions);
            PendingResult<Status> status = Nearby.Messages.subscribe(googleApiClient, mMessageListener);
            status.setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    showMessage("subscribe result: " + status);
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
        if (advertising && !newScreen.equals(MESSAGE_SCREEN_CAMERA)) {
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
        Nearby.Messages.publish(googleApiClient, currentScreenMessage);
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

    Connections.MessageListener hostConnectionsMessageListener = new Connections.MessageListener() {
        @Override
        public void onMessageReceived(String remoteEndpointId, byte[] payload, boolean isReliable) {
            Log.e(TAG, "hostConnectionsMessageListener should not be receiving messages");
        }

        @Override
        public void onDisconnected(String remoteEndpointId) {
            Log.d(TAG, "hostConnectionsMessageListener.onDisconnected(" + remoteEndpointId + ")");
        }
    };

    //Host receives this callback whenever a device wants to connect
    private ConnectionRequestListener connectionRequestListener = new ConnectionRequestListener() {
        @Override
        public void onConnectionRequest(final String remoteEndpointId, final String remoteEndpointName, byte[] handshakeData) {
            if (presenterMode) {
                byte[] myPayload = null;
                // Automatically accept all requests
                Nearby.Connections.acceptConnectionRequest(googleApiClient, remoteEndpointId,
                        myPayload, hostConnectionsMessageListener).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.d(TAG, "Connected to endpoint: " + remoteEndpointId + " " + remoteEndpointName);
                            connectedEndpointIds.add(remoteEndpointId);
                        } else {
                            Log.w(TAG, "Failed to connect to endpoint: " + remoteEndpointId + " " + remoteEndpointName);
                        }
                    }
                });
            } else {
                // Clients should not be advertising and will reject all connection requests.
                Nearby.Connections.rejectConnectionRequest(googleApiClient, remoteEndpointId);
                Log.e(TAG, "Audience devices should not receive connection requests");
            }
        }
    };

    private void startAdvertising() {
        //TODO: move network check to Fragment?
        if (!NetworkUtil.isConnectedToNetwork(this)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.error_no_connection)
                    .show();
            return;
        }

        // Advertising with an AppIdentifer lets other devices on the
        // network discover this application and prompt the user to
        // install the application.
        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        // The advertising timeout is set to run indefinitely
        // Positive values represent timeout in milliseconds
        long NO_TIMEOUT = 0L;

        //When you pass in null for the name parameter, the API constructs a default name based on the device model (for example, “LGE Nexus 5”)
        String name = null;
        //TODO: temp
        Log.e(TAG, "about to start advertising!");
        //What the heck?? NO callbacks are happening now?
        Nearby.Connections.startAdvertising(googleApiClient, name, appMetadata, NO_TIMEOUT,
                connectionRequestListener).setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
            @Override
            public void onResult(Connections.StartAdvertisingResult result) {
                if (result.getStatus().isSuccess()) {
                    // Device is advertising
                    Log.i(TAG, "Successfully advertising");
                    showMessage("Successfully advertising");
                    changeCurrentScreen(MESSAGE_SCREEN_CAMERA);
                    advertising = true;
                } else {
                    int statusCode = result.getStatus().getStatusCode();
                    // Advertising failed - see statusCode for more details
                    Log.e(TAG, "advertising failed with status code " + statusCode);
                    showMessage("advertising failed with status code " + statusCode);
                    advertising = false;
                }
            }
        });
    }

    EndpointDiscoveryListener endpointDiscoveryListener = new EndpointDiscoveryListener() {
        @Override
        public void onEndpointFound(String endpointId, String serviceId, String name) {
            Log.d(TAG, "onEndpointFound(" + endpointId + ", " + serviceId + ", " + name + ")");
            connectTo(endpointId, name);
        }

        @Override
        public void onEndpointLost(String endpointId) {
            Log.d(TAG, "onEndpointLost(" + endpointId + ")");
        }
    };

    /**
     * Only call this method after checking to ensure we are connected to Wi-Fi
     */
    private void startDiscovery() {
        String serviceId = getString(R.string.service_id);

        // Set an appropriate timeout length in milliseconds
        long DISCOVER_TIMEOUT = 1000L;

        // Discover nearby apps that are advertising with the required service ID.
        Nearby.Connections.startDiscovery(googleApiClient, serviceId, DISCOVER_TIMEOUT, endpointDiscoveryListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            // Device is discovering
                            Log.d(TAG, "Connections is successfully discovering");
                        } else {
                            int statusCode = status.getStatusCode();
                            Log.w(TAG, "Connections Discovery failed with statusCode of " + statusCode);
                        }
                    }
                });
    }

    Connections.MessageListener connectionsMessageListener = new Connections.MessageListener() {

        @Override
        public void onMessageReceived(String remoteEndpointId, byte[] payload, boolean isReliable) {
            //TODO: make more efficient
            if (getCurrentFragment() instanceof CameraFragment) {
                CameraFragment cameraFragment = (CameraFragment) getCurrentFragment();
                cameraFragment.updateCameraImage(payload);
            }
        }

        @Override
        public void onDisconnected(String remoteEndpointId) {
            Log.d(TAG, "connectionsMessageListener onDisconnected(" + remoteEndpointId + ")");
        }
    };

    private void connectTo(String remoteEndpointId, final String remoteEndpointName) {
        // Send a connection request to a remote endpoint. By passing 'null' for
        // the name, the Nearby Connections API will construct a default name
        // based on device model such as 'LGE Nexus 5'.
        String myName = null;
        byte[] myPayload = null;
        Nearby.Connections.sendConnectionRequest(googleApiClient, myName,
                remoteEndpointId, myPayload, new Connections.ConnectionResponseCallback() {
                    @Override
                    public void onConnectionResponse(String remoteEndpointId, Status status,
                                                     byte[] bytes) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Nearby Connections successfully connected");
                        } else {
                            Log.w(TAG, "Nearby Connections connection failed with status code of " + status.getStatusCode());
                        }
                    }
                }, connectionsMessageListener);
    }

    private void disconnectFromDevices() {
        connectedEndpointIds = new ArrayList<>();
        if (advertising) {
            Nearby.Connections.stopAdvertising(googleApiClient);
            advertising = false;
        }
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

        showMessage("Publishing: " + jsonObject.toString());

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

        showMessage("Publishing: " + jsonObject.toString());

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
            Nearby.Messages.unpublish(googleApiClient, settingMessage);
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
        //TODO: why is host not receiving??
        showMessage("Sending: " + new String(questionMessage.getContent()));
        Nearby.Messages.publish(googleApiClient, questionMessage);

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
}