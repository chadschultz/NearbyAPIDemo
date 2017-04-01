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
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AccuracyPollListener, ControlListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int RC_SIGN_IN = 1;

    private static final String JSON_KEY_GROUP = "group";
    private static final String JSON_KEY_DETAIL = "detail";

    private static final String MESSAGE_GROUP_SCREEN = "selectedScreen";
    private static final String MESSAGE_GROUP_CONNECTION = "connectionMode";

    private static final String MESSAGE_CONNECT_REGULAR = "connectRegular";
    private static final String MESSAGE_CONNECT_EARSHOT = "connectEarshot";
    private static final String MESSAGE_DISCONNECT_BOTH = "disconnectBoth";

    private TextView questionTextView;

    private List<Question> questions;

    //TODO: can I use more than one Google client at the same time? Can I use one client for multiple APIs?
    private GoogleApiClient googleApiClient;
    private Message connectionMessage;
    private Message currentScreenMessage;

    //TODO: have a standing Message for what the current screen should be?
    //TODO: perhaps another Message to indicate connection type?

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

    //TODO: why have some listeners are variables and some baked into the class?
    private MessageListener mMessageListener = new MessageListener() {
        @Override
        public void onFound(Message message) {

            showMessage("Received: " + new String(message.getContent()));

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
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.connectedRegular();
                    }
                } else if (MESSAGE_DISCONNECT_BOTH.equals(detail)) {
                    if (getCurrentFragment() instanceof ConnectFragment) {
                        ConnectFragment connectFragment = (ConnectFragment) getCurrentFragment();
                        connectFragment.disconnectBoth();
                    }
                }
            }
        }

        @Override
        public void onLost(Message message) {
            super.onLost(message);
        }

        @Override
        public void onDistanceChanged(Message message, Distance distance) {
            super.onDistanceChanged(message, distance);
        }

        @Override
        public void onBleSignalChanged(Message message, BleSignal bleSignal) {
            super.onBleSignalChanged(message, bleSignal);
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

    private void subscribe() {
        Log.i(TAG, "Subscribing.");
        if (googleApiClient != null) {


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

    /* AccuracyPollListener Methods */
    @Override
    public void onVeryAccurateClicked() {

    }

    @Override
    public void onSomewhatAccurateClicked() {

    }

    @Override
    public void onSomewhatInaccurateClicked() {

    }

    @Override
    public void onVeryInaccurateClicked() {

    }

    /* ControlListener Methods */

    @Override
    public void onConnectEarshot() {

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

    }

    @Override
    public void onIntro() {

    }

    @Override
    public void onDistance() {

    }

    @Override
    public void onPollResults() {

    }

    @Override
    public void onCamera() {

    }

    @Override
    public void onContactInfo() {

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

interface AccuracyPollListener {
    void onVeryAccurateClicked();

    void onSomewhatAccurateClicked();

    void onSomewhatInaccurateClicked();

    void onVeryInaccurateClicked();
}