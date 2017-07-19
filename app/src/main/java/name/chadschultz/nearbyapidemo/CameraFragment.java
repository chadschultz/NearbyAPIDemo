package name.chadschultz.nearbyapidemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

//TODO: I had originally planned to stream images from the camera over Nearby Connections
//due to lack of time, tried to adapt the walkie-talkie sample app for audio instead
public class CameraFragment extends Fragment {

    private static final String TAG = CameraFragment.class.getSimpleName();

    public static final int PERMISSIONS_REQUEST_COARSE_LOCATION = 3;

    CameraListener cameraListener;

    ProgressBar progressBar;
    ImageView cameraImageView;
    TextView errorTextView;

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        cameraListener = (CameraListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        cameraImageView = (ImageView) view.findViewById(R.id.camera_imageview);
        errorTextView = (TextView) view.findViewById(R.id.error_textview);

        cameraListener.onStartCamera();

        errorTextView.setText(R.string.discovering);
        errorTextView.setVisibility(View.VISIBLE);
    }

    public void noConnection() {
        progressBar.setVisibility(View.GONE);
        cameraImageView.setVisibility(View.GONE);
        errorTextView.setVisibility(View.VISIBLE);
    }

    public void connected() {
        progressBar.setVisibility(View.GONE);
        cameraImageView.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
    }

    public void updateCameraImage(byte[] payload) {
        //TODO: how does this work? Need to update with each frame.
    }

    public void discoveredConnected() {
        errorTextView.setText(R.string.discovered_connected);
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void discoveringFailed() {
        errorTextView.setText(R.string.discovering_failed);
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void discoveringDisconnected() {
        errorTextView.setText(R.string.discovered_disconnected);
        progressBar.setVisibility(View.INVISIBLE);
    }
}

interface CameraListener {
    void onStartCamera();
}
