package name.chadschultz.nearbyapidemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;


public class AskQuestionDialogFragment extends DialogFragment {

    private static final String KEY_QUESTION = "question";
    private static final String KEY_NAME = "name";

    QuestionListener questionListener;

    String question;
    String name;

    TextInputLayout nameTextInputLayout;
    EditText nameEditText;
    TextInputLayout questionTextInputLayout;
    EditText questionEditText;
    Button unpublishButton;
    Button publishButton;

    public static AskQuestionDialogFragment newInstance(Question question) {
        AskQuestionDialogFragment fragment = new AskQuestionDialogFragment();
        Bundle args = new Bundle();
        if (question != null) {
            args.putString(KEY_QUESTION, question.getQuestion());
            args.putString(KEY_NAME, question.getName());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        questionListener = (QuestionListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        question = getArguments().getString(KEY_QUESTION);
        name = getArguments().getString(KEY_NAME);

        View view = inflater.inflate(R.layout.fragment_ask_question, container, false);

        nameTextInputLayout = (TextInputLayout) view.findViewById(R.id.name_textinputlayout);
        nameEditText = (EditText) view.findViewById(R.id.name_edittext);
        nameEditText.setText(name);
        questionTextInputLayout = (TextInputLayout) view.findViewById(R.id.question_textinputlayout);
        questionEditText = (EditText) view.findViewById(R.id.question_edittext);
        questionEditText.setText(question);
        unpublishButton = (Button) view.findViewById(R.id.unpublish_button);
        unpublishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                unpublishQuestion();
            }
        });
        publishButton = (Button) view.findViewById(R.id.publish_button);
        publishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                publishQuestion();
            }
        });

        updateButtons(question);

        return view;
    }

    private void updateButtons(String question) {
        if (TextUtils.isEmpty(question)) {
            unpublishButton.setVisibility(View.GONE);
            publishButton.setText(R.string.publish_button);
        } else {
            unpublishButton.setVisibility(View.VISIBLE);
            publishButton.setText(R.string.update_button);
        }
    }

    public void unpublishQuestion() {
        questionListener.onUnpublishQuestion();
    }

    public void publishQuestion() {
        String name = nameEditText.getText().toString();
        question = questionEditText.getText().toString();
        if (!TextUtils.isEmpty(question)) {
            questionListener.onPublishQuestion(name, question);
            updateButtons(question);
        }
    }
}

interface QuestionListener {
    void onUnpublishQuestion();
    void onPublishQuestion(String name, String question);
}