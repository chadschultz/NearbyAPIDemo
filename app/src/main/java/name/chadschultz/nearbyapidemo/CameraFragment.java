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


public class CameraFragment extends Fragment {

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

        if (NetworkUtil.isConnectedToNetwork(getActivity())) {
            cameraListener.onStartCamera();
        } else {
            noConnection();
        }
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
}

interface CameraListener {
    void onStartCamera();
}
