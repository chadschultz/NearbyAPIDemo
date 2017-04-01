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


public class ControlFragment extends Fragment {

    private ControlListener controlListener;

    private boolean earshotConnected;
    private boolean regularConnected;

    private Button earshotModeButton;
    private Button regularModeButton;
    private Button blankButton;
    private Button introButton;
    private Button distanceButton;
    private Button pollResultsButton;
    private Button cameraButton;
    private Button contactInfoButton;
    private Button signOutButton;

    public static ControlFragment newInstance() {
        ControlFragment fragment = new ControlFragment();
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
        distanceButton = (Button) view.findViewById(R.id.distance_button);
        distanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                controlListener.onDistance();
            }
        });
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
                controlListener.onCamera();
            }
        });
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
}

interface ControlListener {
    void onConnectEarshot();
    void onDisconnectEarshot(); //TODO:
    void onConnectRegular();
    void onDisconnectRegular(); //TODO:
    void onDisconnectBoth();
    void onBlank();
    void onIntro();
    void onDistance();
    void onPollResults();
    void onCamera();
    void onContactInfo();
    void onSignOut();
}
