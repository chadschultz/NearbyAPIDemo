package name.chadschultz.nearbyapidemo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


public class ConnectFragment extends Fragment {

    TextView connectTextView;
    ImageView standUpImageView;

    public static ConnectFragment newInstance() {
        ConnectFragment fragment = new ConnectFragment();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_connect, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        connectTextView = (TextView) view.findViewById(R.id.connect_textview);
        standUpImageView = (ImageView) view.findViewById(R.id.stand_up_imageview);
    }

    public void connectedEarshot() {
        connectTextView.setText(getString(R.string.connection_status_earshot));
        standUpImageView.setVisibility(View.VISIBLE);
    }

    public void connectedRegular() {
        connectTextView.setText(getString(R.string.connection_status_regular));
        standUpImageView.setVisibility(View.VISIBLE);
    }

    public void disconnectBoth() {
        //Different text and a sit down icon?
        connectTextView.setText(R.string.connection_status_waiting);
        standUpImageView.setVisibility(View.GONE);
    }
}
