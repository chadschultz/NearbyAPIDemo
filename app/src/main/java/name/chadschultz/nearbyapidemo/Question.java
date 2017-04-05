package name.chadschultz.nearbyapidemo;

public class Question {
    private String name;
    private String question;

    public Question() {
    }

    public Question(String name, String question) {
        this.name = name;
        this.question = question;
    }

    public String getName() {
        return name;
    }

    public String getQuestion() {
        return question;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Question question1 = (Question) o;

        if (name != null ? !name.equals(question1.name) : question1.name != null) return false;
        return question != null ? question.equals(question1.question) : question1.question == null;

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (question != null ? question.hashCode() : 0);
        return result;
    }
}
