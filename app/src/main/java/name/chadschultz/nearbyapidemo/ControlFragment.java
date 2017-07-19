package name.chadschultz.nearbyapidemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;


public class ControlFragment extends Fragment {

    private static final String KEY_DISTANCE_BETWEEN_BEACONS = "distanceBetweenBeacons";

    private ControlListener controlListener;

    private boolean earshotConnected;
    private boolean regularConnected;

    private Button earshotModeButton;
    private Button regularModeButton;
    private Button blankButton;
    private Button introButton;
    private EditText updateDistanceEditText;
    private Button updateDistanceButton;
    private Button distanceButton;
    private Button pollResultsButton;
    private Button cameraButton;
    private TextView connectedCountTextView;
    private Button contactInfoButton;
    private Button signOutButton;

    private double distanceBetweenBeacons;

    public static ControlFragment newInstance(double distanceBetweenBeacons) {
        ControlFragment fragment = new ControlFragment();
        Bundle args = new Bundle();
        args.putDouble(KEY_DISTANCE_BETWEEN_BEACONS, distanceBetweenBeacons);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        controlListener = (ControlListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        earshotModeButton = (Button) view.findViewById(R.id.earshot_mode_button);
        earshotModeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleEarshotMode();
            }
        });
        updateEarshot();
        regularModeButton = (Button) view.findViewById(R.id.regular_mode_button);
        regularModeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRegularMode();
            }
        });
        updateRegular();
        blankButton = (Button) view.findViewById(R.id.blank_button);
        blankButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onBlank();
            }
        });
        introButton = (Button) view.findViewById(R.id.intro_button);
        introButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onIntro();
            }
        });
        updateDistanceEditText = (EditText) view.findViewById(R.id.update_distance_edittext);
        updateDistanceBetweenBeacons(getArguments().getDouble(KEY_DISTANCE_BETWEEN_BEACONS));
        updateDistanceButton = (Button) view.findViewById(R.id.update_distance_button);
        updateDistanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                distanceBetweenBeacons = Double.valueOf(updateDistanceEditText.getText().toString());
                controlListener.onUpdateDistance(distanceBetweenBeacons);
            }
        });
        distanceButton = (Button) view.findViewById(R.id.distance_button);
        distanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onDistance();
            }
        });
        //TODO: remove the poll results option?
        pollResultsButton = (Button) view.findViewById(R.id.poll_results_button);
        pollResultsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onPollResults();
            }
        });
        cameraButton = (Button) view.findViewById(R.id.camera_button);
        cameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onCameraButtonClicked();
            }
        });
        connectedCountTextView = (TextView) view.findViewById(R.id.connected_count_textview);
        updateConnectedCount(0);
        contactInfoButton = (Button) view.findViewById(R.id.contact_info_button);
        contactInfoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onContactInfo();
            }
        });
        signOutButton = (Button) view.findViewById(R.id.sign_out_button);
        signOutButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onSignOut();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(KEY_DISTANCE_BETWEEN_BEACONS, distanceBetweenBeacons);
    }

    public void toggleEarshotMode() {
        if (earshotConnected) {
            controlListener.onDisconnectEarshot();
            disconnectBoth();
        } else {
            controlListener.onConnectEarshot();
            earshotConnected = true;
            updateEarshot();
        }
    }

    public void onEarshotConnected() {
        earshotConnected = true;
        updateEarshot();
    }

    public void onEarshotDisconnected() {
        earshotConnected = false;
        updateEarshot();
    }

    public void updateEarshot() {
        String earshot = getString(R.string.connection_mode_earshot);
        if (earshotConnected) {
            String connected = getString(R.string.connection_state_connected);
            earshotModeButton.setText(getString(R.string.connect_button, earshot, connected));
        } else {
            String disconnected = getString(R.string.connection_state_disconnected);
            earshotModeButton.setText(getString(R.string.connect_button, earshot, disconnected));
        }
    }

    public void toggleRegularMode() {
        if (regularConnected) {
            controlListener.onDisconnectRegular();
            disconnectBoth();
        } else {
            controlListener.onConnectRegular();
            regularConnected = true;
            updateRegular();
        }
    }

    //TODO: do away with disconnect methods?
    public void updateRegular() {
        String regular = getString(R.string.connection_mode_regular);
        if (regularConnected) {
            String connected = getString(R.string.connection_state_connected);
            regularModeButton.setText(getString(R.string.connect_button, regular, connected));
        } else {
            String disconnected = getString(R.string.connection_state_disconnected);
            regularModeButton.setText(getString(R.string.connect_button, regular, disconnected));
        }
    }

    public void disconnectBoth() {
        //TODO: redundancy
        regularConnected = false;
        earshotConnected = false;
        updateRegular();
        updateEarshot();
        controlListener.onDisconnectBoth();
    }

    public void updateDistanceBetweenBeacons(double distanceBetweenBeacons) {
        this.distanceBetweenBeacons = distanceBetweenBeacons;
        updateDistanceEditText.setText(String.format(Locale.US, "%.2f", distanceBetweenBeacons));
    }

    private void onCameraButtonClicked() {
        controlListener.onCamera();
    }

    public void updateConnectedCount(int count) {
        connectedCountTextView.setText(getString(R.string.connected_count, count));
    }

}

interface ControlListener {
    void onConnectEarshot();
    void onDisconnectEarshot(); //TODO:
    void onConnectRegular();
    void onDisconnectRegular(); //TODO:
    void onDisconnectBoth();
    void onBlank();
    void onIntro();
    void onUpdateDistance(double distanceBetweenBeacons);
    void onDistance();
    void onPollResults();
    void onCamera();
    void onContactInfo();
    void onSignOut();
}
