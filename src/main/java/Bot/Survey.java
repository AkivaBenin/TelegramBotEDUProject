package Bot;

import java.util.List;

public class Survey {
    private final String creator;
    private final List<Question> questions;

    public Survey(String creator, List<Question> questions) {
        this.creator = creator;
        this.questions = questions;
    }

    public String getCreator() {
        return creator;
    }

    public List<Question> getQuestions() {
        return questions;
    }
}

class Question {
    private final String text;
    private final List<String> options;

    public Question(String text, List<String> options) {
        this.text = text;
        this.options = options;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return options;
    }
}
