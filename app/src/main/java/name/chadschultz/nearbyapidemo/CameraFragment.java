package name.chadschultz.nearbyapidemo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;


public class CameraFragment extends Fragment {

    ProgressBar progressBar;
    ImageView cameraImageView;

    public static CameraFragment newInstance() {
        return new CameraFragment();
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
    }

    public void connected() {
        progressBar.setVisibility(View.GONE);
        cameraImageView.setVisibility(View.VISIBLE);
    }

    public void updateCameraImage() {
        //TODO: how does this work? Need to update with each frame.
    }

}
