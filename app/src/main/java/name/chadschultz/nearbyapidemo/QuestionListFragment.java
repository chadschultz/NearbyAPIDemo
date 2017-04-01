package name.chadschultz.nearbyapidemo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class QuestionListFragment extends Fragment {

    private AnswerQuestionListener answerQuestionListener;

    private ListView listView;
    private QuestionAdapter adapter;

    public QuestionListFragment newInstance() {
        QuestionListFragment fragment = new QuestionListFragment();
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        answerQuestionListener = (AnswerQuestionListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_question_list, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listView = (ListView) view.findViewById(R.id.listview);
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int position, long id) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.delete_question_title)
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                answerQuestionListener.onQuestionRead(adapter.getItem(position));
                            }
                        })
                        .show();
                return false;
            }
        });
        adapter = new QuestionAdapter(getActivity(), new ArrayList<Question>());
        listView.setAdapter(adapter);
    }

    public void setQuestions(List<Question> questions) {
        adapter.setQuestions(questions);
        listView.setAdapter(adapter);
    }

    private static class QuestionAdapter extends BaseAdapter {
        private final LayoutInflater layoutInflater;
        private final List<Question> questions;

        public QuestionAdapter(Context context, List<Question> questions) {
            this.layoutInflater = LayoutInflater.from(context);
            this.questions = questions;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            QuestionViewHolder holder;
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.row_question, parent, false);

                holder = new QuestionViewHolder();
                holder.nameTextView = (TextView)convertView.findViewById(R.id.name_textview);
                holder.questionTextView = (TextView)convertView.findViewById(R.id.question_textview);
                convertView.setTag(holder);
            } else {
                holder = (QuestionViewHolder)convertView.getTag();
            }

            Question question = questions.get(position);
            holder.nameTextView.setText(question.getName());
            holder.questionTextView.setText(question.getQuestion());

            return convertView;
        }

        @Override
        public int getCount() {
            return questions.size();
        }

        public Question getItem(int position) {
            return questions.get(position);
        }

        void setQuestions(List<Question> questions) {
            questions.clear();
            this.questions.addAll(questions);
            notifyDataSetChanged();
        }

        private static class QuestionViewHolder {
            TextView nameTextView;
            TextView questionTextView;
        }
    }
}

interface AnswerQuestionListener {
    void onQuestionRead(Question question);
}
