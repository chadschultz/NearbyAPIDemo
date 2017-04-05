package name.chadschultz.nearbyapidemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.nearby.messages.Distance;


public class DistanceFragment extends Fragment {

    private static final String TAG = MainActivity.TAG;

    private static final String KEY_DISTANCE_BETWEEN_BEACONS = "distanceBetweenBeacons";

    AccuracyPollListener accuracyPollListener;

    ProgressBar progressBar;
    TextView rawDistanceTextView;
    TextView calculatedDistanceTextView;
    TextView accuracyQuestionTextView;
    Button veryAccurateButton;
    Button somewhatAccurateButton;
    Button somewhatInaccurateButton;
    Button veryInaccurateButton;
    TextView accuracyResultsTextView;

    double distanceBetweenBeacons;

    public static DistanceFragment newInstance(double distanceBetweenBeacons) {
        Log.d(TAG, "DistanceFragment.newInstance(" + distanceBetweenBeacons + ")");
        DistanceFragment fragment = new DistanceFragment();
        Bundle args = new Bundle();
        args.putDouble(KEY_DISTANCE_BETWEEN_BEACONS, distanceBetweenBeacons);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        accuracyPollListener = (AccuracyPollListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_distance, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        rawDistanceTextView = (TextView) view.findViewById(R.id.raw_distance_textview);
        calculatedDistanceTextView = (TextView) view.findViewById(R.id.calculated_distance_textview);
        accuracyQuestionTextView = (TextView) view.findViewById(R.id.accuracy_question_textview);
        veryAccurateButton = (Button) view.findViewById(R.id.very_accurate_button);
        veryAccurateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                accuracyPollListener.onVeryAccurateClicked();
            }
        });
        somewhatAccurateButton = (Button) view.findViewById(R.id.somewhat_accurate_button);
        somewhatAccurateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                accuracyPollListener.onSomewhatAccurateClicked();
            }
        });
        somewhatInaccurateButton = (Button) view.findViewById(R.id.somewhat_inaccurate_button);
        somewhatInaccurateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                accuracyPollListener.onSomewhatInaccurateClicked();
            }
        });
        veryInaccurateButton = (Button) view.findViewById(R.id.very_inaccurate_button);
        veryInaccurateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                accuracyPollListener.onVeryInaccurateClicked();
            }
        });
        accuracyResultsTextView = (TextView) view.findViewById(R.id.accuracy_results_textview);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(KEY_DISTANCE_BETWEEN_BEACONS, distanceBetweenBeacons);
    }

    //should only be called after both distances have been reported at least once
    public void updateDistances(Distance leftBeaconToPhone, Distance rightBeaconToPhone) {
        progressBar.setVisibility(View.GONE);
        rawDistanceTextView.setVisibility(View.VISIBLE);
        calculatedDistanceTextView.setVisibility(View.VISIBLE);
        accuracyQuestionTextView.setVisibility(View.VISIBLE);
        veryAccurateButton.setVisibility(View.VISIBLE);
        somewhatAccurateButton.setVisibility(View.VISIBLE);
        somewhatInaccurateButton.setVisibility(View.VISIBLE);
        veryInaccurateButton.setVisibility(View.VISIBLE);

        rawDistanceTextView.setText(getString(R.string.raw_distance, leftBeaconToPhone.getMeters(), rightBeaconToPhone.getMeters()));

        TriangulatedDistance triangulatedDistance = new TriangulatedDistance(distanceBetweenBeacons, leftBeaconToPhone.getMeters(), rightBeaconToPhone.getMeters());

        if (triangulatedDistance.error) {
            calculatedDistanceTextView.setText(R.string.calculated_distance_error);
        } else {
            String side;
            double absoluteHorizontalDistance;
            if (triangulatedDistance.horizontalDistance < 0) {
                side = getString(R.string.left_side);
                absoluteHorizontalDistance = Math.abs(triangulatedDistance.horizontalDistance);
            } else {
                side = getString(R.string.right_side);
                absoluteHorizontalDistance = Math.abs(triangulatedDistance.horizontalDistance);
            }
            calculatedDistanceTextView.setText(getString(R.string.calculated_distance, triangulatedDistance.verticalDistance, side, absoluteHorizontalDistance));
        }
    }

    public void updateResults(int veryAccurateCount, double veryAccuratePercentage,
                              int somewhatAccurateCount, double somewhatAccuratePercentage,
                              int somewhatInaccurateCount, double somewhatInaccuratePercentage,
                              int veryInaccurateCount, double veryInaccuratePercentage) {
        rawDistanceTextView.setVisibility(View.GONE);
        calculatedDistanceTextView.setVisibility(View.GONE);
        accuracyQuestionTextView.setVisibility(View.GONE);
        veryAccurateButton.setVisibility(View.GONE);
        somewhatAccurateButton.setVisibility(View.GONE);
        somewhatInaccurateButton.setVisibility(View.GONE);
        veryInaccurateButton.setVisibility(View.GONE);
        accuracyResultsTextView.setVisibility(View.VISIBLE);

        StringBuilder sb = new StringBuilder(getString(R.string.accuracy_question));
        sb.append(getString(R.string.accuracy_results_row, getString(R.string.very_accurate), veryAccurateCount, veryAccuratePercentage * 100));
        sb.append(getString(R.string.accuracy_results_row, getString(R.string.very_accurate), somewhatAccurateCount, somewhatAccuratePercentage * 100));
        sb.append(getString(R.string.accuracy_results_row, getString(R.string.very_accurate), somewhatInaccurateCount, somewhatInaccuratePercentage * 100));
        sb.append(getString(R.string.accuracy_results_row, getString(R.string.very_accurate), veryInaccurateCount, veryInaccuratePercentage * 100));
        accuracyResultsTextView.setText(sb.toString());
    }

    public void updateDistanceBetweenBeacons(double distanceBetweenBeacons) {
        Log.d(TAG, "distanceBetweenBeacons is now: " + distanceBetweenBeacons);
        this.distanceBetweenBeacons = distanceBetweenBeacons;
    }

    private class TriangulatedDistance {
        boolean error;
        double distanceBetweenBeacons;
        double leftBeaconToPhone;
        double rightBeaconToPhone;
        double verticalDistance; //height of triangle
        double horizontalDistance; //how far to the left of center (negative for right of center)

        TriangulatedDistance(double distanceBetweenBeacons, double leftBeaconToPhone, double rightBeaconToPhone) {
            this.distanceBetweenBeacons = distanceBetweenBeacons;
            this.leftBeaconToPhone = leftBeaconToPhone;
            this.rightBeaconToPhone = rightBeaconToPhone;

            if (!isValidTriangle(distanceBetweenBeacons, leftBeaconToPhone, rightBeaconToPhone)) {
                error = true;
                return;
            }

            double halfPerimeter = (distanceBetweenBeacons + leftBeaconToPhone + rightBeaconToPhone) / 2;
            //Heron's formula
            double area = Math.sqrt(halfPerimeter * (halfPerimeter - distanceBetweenBeacons) * (halfPerimeter - leftBeaconToPhone) * (halfPerimeter - rightBeaconToPhone));
            //Refactor common formula for area of triangle to solve for height
            double height = (2 * area) / distanceBetweenBeacons;
            verticalDistance = height;

            //Pythagorean theorem
            double horizontalDistanceFromLeftBeacon = Math.sqrt(Math.pow(leftBeaconToPhone, 2) - Math.pow(height, 2));
            double centerPoint = distanceBetweenBeacons / 2;
            //Negative: left of center. Positive: right of center
            horizontalDistance = -1 * (centerPoint - horizontalDistanceFromLeftBeacon);
        }
    }

    /**
     * Can the three side lengths form a triangle? Use the Triangle Inequality Theorem. Our distance
     * readings might be so inaccurate that we cannot triangulate.
     * @param sideA
     * @param sideB
     * @param sideC
     * @return
     */
    static boolean isValidTriangle(double sideA, double sideB, double sideC) {
        return (sideA + sideB > sideC &&
                sideB + sideC > sideA &&
                sideC + sideA > sideB);
    }
}

//TODO: temp
class MyDistance implements Distance {
    @Override
    public int getAccuracy() {
        return Accuracy.LOW;
    }

    @Override
    public double getMeters() {
        return 1.4;
    }

    @Override
    public int compareTo(@NonNull Distance distance) {
        return 0;
    }
}

interface AccuracyPollListener {
    void onVeryAccurateClicked();

    void onSomewhatAccurateClicked();

    void onSomewhatInaccurateClicked();

    void onVeryInaccurateClicked();
}