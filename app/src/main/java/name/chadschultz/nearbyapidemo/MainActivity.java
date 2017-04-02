package name.chadschultz.nearbyapidemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
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

public class MainActivity extends AppCompatActivity implements AccuracyPollListener, ControlListener {

    public static final String TAG = "NearbyAPITest";

    private static final int RC_SIGN_IN = 1;

    private static final String JSON_KEY_GROUP = "group";
    private static final String JSON_KEY_DETAIL = "detail";
    private static final String JSON_KEY_UUID = "uuid";

    private static final String MESSAGE_GROUP_SCREEN = "selectedScreen";
    private static final String MESSAGE_GROUP_CONNECTION = "connectionMode";
    private static final String MESSAGE_GROUP_BEACON = "beacon";
    private static final String MESSAGE_GROUP_DISTANCE_POLL = "distancePoll";

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

    private TextView questionTextView;

    private List<Question> questions;

    //TODO: persist this data?
    boolean presenterMode;
    Distance leftBeaconDistance;
    Distance rightBeaconDistance;
    String uniqueID = UUID.randomUUID().toString();
    int veryAccurateCount, somewhatAccurateCount, somewhatInaccurateCount, veryInaccurateCount;

    //TODO: can I use more than one Google client at the same time? Can I use one client for multiple APIs?
    private GoogleApiClient googleApiClient;
    private Message connectionMessage;
    private Message currentScreenMessage;
    private Message pollResponseMessage;

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

            //Don't worry about reading becaon messages here--we're only interested in onDistanceChanged

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
            if (MESSAGE_GROUP_CONNECTION.equals(group)) {
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
            } else if (MESSAGE_GROUP_SCREEN.equals(group)) {
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
                        DistanceFragment distanceFragment = DistanceFragment.newInstance();
                        replaceFragment(distanceFragment);

                        if (leftBeaconDistance != null && rightBeaconDistance != null) {
                            distanceFragment.updateDistances(leftBeaconDistance, rightBeaconDistance);
                        }
                    }
                } else if (MESSAGE_SCREEN_POLL_RESULTS.equals(detail)) {
                    if (!(getCurrentFragment() instanceof DistanceFragment)) {
                        DistanceFragment distanceFragment = DistanceFragment.newInstance();
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

        questions = new ArrayList<>();
        questionTextView = (TextView) findViewById(R.id.question_textview);
        questionTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Coming soon", Toast.LENGTH_LONG).show();
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
    }

    @Override
    protected void onStop() {
        // the documentation specifically shows these coming before the super call
        unpublish();
        unsubscribe();
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
        }
    }

    private void setupUi(boolean presenterMode) {
        this.presenterMode = presenterMode;
        if (presenterMode) {
            questionTextView.setText(getString(R.string.answer_questions_button, questions.size()));
            replaceFragment(ControlFragment.newInstance());
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
    public void onDistance() {
        changeCurrentScreen(MESSAGE_SCREEN_DISTANCE);
    }

    @Override
    public void onPollResults() {
        changeCurrentScreen(MESSAGE_SCREEN_POLL_RESULTS);
    }

    @Override
    public void onCamera() {
        changeCurrentScreen(MESSAGE_SCREEN_CAMERA);
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
}