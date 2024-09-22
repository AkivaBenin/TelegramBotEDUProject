package Bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SurveyBot extends TelegramLongPollingBot {

    private final CommunityAndSurveyManager communityAndSurveyManager = new CommunityAndSurveyManager();
    private final Map<Long, Boolean> userSurveyInCreation = new HashMap<>();
    private boolean isAnswerInProgress = false;
    private boolean isWaitingForDelay = false;
    private final List<Question> currentQuestions = new ArrayList<>();
    private final List<String> currentAnswers = new ArrayList<>();
    private final Map<String, Map<String, Integer>> responses = new ConcurrentHashMap<>();
    private String currentQuestionText = "";
    private int questionCount = 0;
    private final AtomicBoolean surveyActive = new AtomicBoolean(false);
    private Timer pollTimer;
    private final Map<Date, Long> scheduledPolls = new HashMap<>();
    private final Map<Long, Set<String>> respondedQuestions = new HashMap<>();

    @Override
    public String getBotToken() {
        return "7508874948:AAFS8EDMzBoObvieLXsn1nTclwy09SZL_xM";
    }

    @Override
    public String getBotUsername() {
        return "SurveyEDU_bot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText().toLowerCase();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();

        if (isJoiningCommunity(messageText)) {
            handleCommunityJoin(chatId, username);
        } else if (messageText.startsWith("/create_survey")) {
            handleSurveyCreationRequest(chatId, username);
        } else if (userSurveyInCreation.getOrDefault(chatId, false) && !isWaitingForDelay) {
            handleSurveyCreationProcess(chatId, messageText);
        } else if (isWaitingForDelay) {
            handleDelayInput(chatId, messageText);
        }
    }

    private boolean isJoiningCommunity(String messageText) {
        return messageText.equals("/start") || messageText.equals("hi") || messageText.equals("היי");
    }

    private void handleCommunityJoin(Long chatId, String username) {
        if (!communityAndSurveyManager.isMember(username)) {
            communityAndSurveyManager.addMember(username, chatId);
            sendWelcomeMessage(chatId, username);
            notifyNewMember(username);
        } else {
            sendMessage(chatId, "אתה כבר חבר בקהילה.");
        }
    }

    private void handleSurveyCreationRequest(Long chatId, String username) {
        if (canStartNewSurvey()) {
            startSurveyCreation(chatId, username);
        } else {
            sendMessage(chatId, "לא ניתן ליצור סקר כרגע. סקר אחר מתוכנן ב-5 הדקות הקרובות. אנא נסה שוב מאוחר יותר.");
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String username = update.getCallbackQuery().getFrom().getUserName();
        handleSurveyResponse(chatId, messageId, username, callbackData);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean canStartNewSurvey() {
        Calendar now = Calendar.getInstance();
        Date currentTime = now.getTime();
        return scheduledPolls.keySet().stream()
                .noneMatch(pollTime -> Math.abs(pollTime.getTime() - currentTime.getTime()) < 300000);
    }

    private void startSurveyCreation(Long chatId, String username) {
        if (!communityAndSurveyManager.isMember(username)) {
            sendMessage(chatId, "עליך להצטרף לקהילה תחילה על ידי שליחת 'hi' או 'היי' או /start.");
            return;
        }
        if (surveyActive.get()) {
            sendMessage(chatId, "כבר יש סקר פעיל. נא להמתין עד לסיום הסקר הנוכחי.");
        } else if (communityAndSurveyManager.getCommunitySize() < 2) {
            sendMessage(chatId, "לא מספיק חברים ליצירת סקר. יש צורך ב-3 חברים לפחות.");
        } else {
            initializeSurvey(chatId);
        }
    }

    private void initializeSurvey(Long chatId) {
        sendMessage(chatId, "בוא ניצור סקר! אנא הכנס את השאלה הראשונה שלך.");
        userSurveyInCreation.put(chatId, true);
        isAnswerInProgress = false;
        currentQuestions.clear();
        currentAnswers.clear();
        questionCount = 0;
        currentQuestionText = "";
    }

    private void handleSurveyCreationProcess(Long chatId, String messageText) {
        if (!userSurveyInCreation.getOrDefault(chatId, false)) {
            sendMessage(chatId, "אין סקר פעיל כרגע.");
            return;
        }

        if (messageText.equalsIgnoreCase("/finish")) {
            askForDelay(chatId);
            return;
        }

        if (isAnswerInProgress) {
            handleAnswerInProgress(chatId, messageText);
        } else {
            handleNewQuestion(chatId, messageText);
        }
    }

    private void handleAnswerInProgress(Long chatId, String messageText) {
        if (messageText.equalsIgnoreCase("/done")) {
            if (currentAnswers.size() >= 2) {
                completeQuestion(chatId);
            } else {
                sendMessage(chatId, "אתה צריך לפחות 2 תשובות אפשריות לסיים את השאלה.");
            }
        } else {
            currentAnswers.add(messageText);
            sendMessage(chatId, "הכנסת " + currentAnswers.size() + " תשובות. אתה יכול להוסיף עוד (עד 4 תשובות) או להקליד '/done' לסיום השאלה.");
            if (currentAnswers.size() >= 4) {
                completeQuestion(chatId);
            }
        }
    }

    private void handleNewQuestion(Long chatId, String messageText) {
        if (questionCount >= 3) {
            sendMessage(chatId, "הגעת למספר המקסימלי של 3 שאלות. הקלד '/finish' לסיום הסקר.");
            return;
        }

        if (currentQuestionText.isEmpty()) {
            questionCount++;
            currentQuestionText = messageText;
            sendMessage(chatId, "הוספת שאלה " + questionCount + ". עכשיו הכנס את התשובה הראשונה האפשרית לשאלה זו.");
            isAnswerInProgress = true;
        }
    }

    private void completeQuestion(Long chatId) {
        currentQuestions.add(new Question(currentQuestionText, new ArrayList<>(currentAnswers)));
        isAnswerInProgress = false;
        currentQuestionText = "";
        currentAnswers.clear();
        sendMessage(chatId, questionCount < 3 ?
                "השאלה נוספה בהצלחה! הקלד '/finish' כדי לשלוח את הסקר או הוסף שאלה נוספת." :
                "הגעת למספר המקסימלי של שאלות. הקלד '/finish' לסיום הסקר.");
    }

    private void askForDelay(Long chatId) {
        sendMessage(chatId, "האם תרצה לשלוח את הסקר עכשיו או עם עיכוב? הקלד '/now' לשליחה מיידית או הקלד מספר דקות לעיכוב (במלוא הדקות בלבד).");
        isWaitingForDelay = true;
    }

    private void handleDelayInput(Long chatId, String messageText) {
        if (!userSurveyInCreation.getOrDefault(chatId, false)) {
            sendMessage(chatId, "אין סקר פעיל כרגע.");
            return;
        }

        if (messageText.equalsIgnoreCase("/now")) {
            completeSurvey(chatId, 0);
        } else {
            try {
                int delayMinutes = Integer.parseInt(messageText);
                if (delayMinutes > 0) {
                    scheduleSurvey(chatId, delayMinutes);
                } else {
                    sendMessage(chatId, "אנא הכנס מספר חיובי במלוא הדקות לעיכוב.");
                }
            } catch (NumberFormatException e) {
                sendMessage(chatId, "אנא הכנס מספר חוקי במלוא הדקות לעיכוב.");
            }
        }
    }

    private void completeSurvey(Long chatId, int delayMinutes) {
        if (!surveyActive.get()) {
            if (delayMinutes == 0) {
                sendSurveyToCommunity();
            } else {
                scheduleSurvey(chatId, delayMinutes);
            }
            userSurveyInCreation.put(chatId, false);
            isWaitingForDelay = false;
            surveyActive.set(true);
            currentQuestionText = "";
            currentAnswers.clear();
            questionCount = 0;
            if (pollTimer != null) {
                pollTimer.cancel();
            }
            sendMessage(chatId, "הסקר נשלח בהצלחה!");
        } else {
            sendMessage(chatId, "סקר כבר פעיל.");
        }
    }

    private void scheduleSurvey(Long chatId, int delayMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, delayMinutes);
        Date scheduledTime = calendar.getTime();

        if (isScheduleConflicting(scheduledTime)) {
            sendMessage(chatId, "סקר אחר מתוכנן בעוד כמה דקות. נא לבחור זמן מאוחר יותר.");
            return;
        }

        pollTimer = new Timer();
        pollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendSurveyToCommunity();
                surveyActive.set(false);
                sendMessageToAllMembers("הסקר נסגר אוטומטית לאחר 5 דקות.");
            }
        }, delayMinutes * 60 * 1000);

        scheduledPolls.put(scheduledTime, chatId);
        userSurveyInCreation.put(chatId, false);
        isWaitingForDelay = false;
        surveyActive.set(true);
        sendMessage(chatId, "הסקר שלך יישלח בעוד " + delayMinutes + " דקות.");
    }


    private boolean isScheduleConflicting(Date scheduledTime) {
        return scheduledPolls.keySet().stream()
                .anyMatch(pollTime -> Math.abs(pollTime.getTime() - scheduledTime.getTime()) < 300000);
    }

    private void sendSurveyToCommunity() {
        communityAndSurveyManager.getCommunityMembers().forEach((memberId, memberName) -> {
            currentQuestions.forEach(question -> sendQuestionWithButtons(memberId, question));
        });

        new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000);
                sendSurveyResults();
                surveyActive.set(false);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendQuestionWithButtons(Long chatId, Question question) {
        try {
            SendMessage answerMessage = new SendMessage();
            answerMessage.setChatId(chatId.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            question.getOptions().forEach(option -> {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(option);
                button.setCallbackData(question.getText() + "::" + option);
                row.add(button);
                if (row.size() == 2) {
                    rows.add(new ArrayList<>(row));
                    row.clear();
                }
            });

            if (!row.isEmpty()) {
                rows.add(row);
            }

            markup.setKeyboard(rows);
            answerMessage.setText("שאלה: " + question.getText() + "\nבחר תשובה:");
            answerMessage.setReplyMarkup(markup);

            execute(answerMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSurveyResponse(Long chatId, Integer messageId, String username, String response) {
        String[] parts = response.split("::");
        String questionText = parts[0];
        String answerText = parts[1];

        if (surveyActive.get()) {
            respondedQuestions.putIfAbsent(chatId, new HashSet<>());
            if (!respondedQuestions.get(chatId).contains(questionText)) {
                responses.computeIfAbsent(questionText, k -> new HashMap<>())
                        .merge(answerText, 1, Integer::sum);
                respondedQuestions.get(chatId).add(questionText);
                editMessageText(chatId, messageId, "תשובה התקבלה לשאלה: " + questionText);

                if (allUsersAnsweredAllQuestions()) {
                    sendSurveyResults();
                }
            } else {
                sendMessage(chatId, "כבר ענית על השאלה הזאת.");
            }
        } else {
            editMessageText(chatId, messageId, "הסקר נסגר כבר.");
        }
    }

    private boolean allUsersAnsweredAllQuestions() {
        return communityAndSurveyManager.getCommunityMembers().keySet().stream()
                .allMatch(memberId -> currentQuestions.stream()
                        .allMatch(question -> respondedQuestions.getOrDefault(memberId, Collections.emptySet()).contains(question.getText())));
    }

    private void editMessageText(Long chatId, Integer messageId, String newText) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setText(newText);
        try {
            execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSurveyResults() {
        StringBuilder resultMessage = new StringBuilder("תוצאות הסקר:\n\n");

        currentQuestions.forEach(question -> {
            resultMessage.append("שאלה: *").append(question.getText()).append("*\n");

            Map<String, Integer> questionResponses = responses.getOrDefault(question.getText(), new HashMap<>());
            int totalResponses = questionResponses.values().stream().mapToInt(Integer::intValue).sum();

            question.getOptions().forEach(option -> questionResponses.putIfAbsent(option, 0));

            questionResponses.entrySet().stream()
                    .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                    .forEach(entry -> {
                        String answer = entry.getKey();
                        int votes = entry.getValue();
                        double percentage = totalResponses == 0 ? 0.0 : (votes / (double) totalResponses) * 100;
                        resultMessage.append("תשובה: ").append(answer)
                                .append(" - ").append(String.format("%.2f", percentage)).append("% (")
                                .append(votes).append(" הצבעות)\n");
                    });

            // Notify users who didn't respond
            communityAndSurveyManager.getCommunityMembers().keySet().forEach(memberId -> {
                if (!respondedQuestions.getOrDefault(memberId, Collections.emptySet()).contains(question.getText())) {
                    resultMessage.append("תשובתך לשאלה: ").append(question.getText()).append(" - לא התקבלה\n");
                }
            });
            resultMessage.append("\n");
        });

        communityAndSurveyManager.getCommunityMembers().keySet()
                .forEach(memberId -> sendMessage(memberId, resultMessage.toString()));

        communityAndSurveyManager.clearSurvey("current_survey");
        surveyActive.set(false);
    }

    private void sendWelcomeMessage(Long chatId, String username) {
        String instructions = "ברוך הבא לקהילה, " + username + "!\n\n"
                + "יש כרגע " + communityAndSurveyManager.getCommunitySize() + " חברים.\n"
                + "הנה איך זה עובד:\n"
                + "- צריך לפחות 3 חברים כדי ליצור סקר.\n"
                + "- כל סקר יכול לכלול 1-3 שאלות, וכל שאלה יכולה לכלול 2-4 תשובות אפשריות.\n"
                + "- כדי ליצור סקר חדש, הקלד /create_survey.\n"
                + "- לאחר יצירת השאלות, הקלד /finish כדי לסיים את הסקר.\n"
                + "- תוכל לשלוח את הסקר מיד או לעכב את השליחה במספר דקות מלאות.\n"
                + "- ברגע שסקר פעיל, כל החברים יקבלו אותו ויוכלו להצביע.\n"
                + "- הסקר יסתיים אוטומטית לאחר 5 דקות מתחילתו.\n"
                + "תודה שהצטרפת!";
        sendMessage(chatId, instructions);
    }


    private void notifyNewMember(String newMember) {
        String message = "חבר חדש הצטרף: " + newMember + ".\nסה\"כ חברים: " + communityAndSurveyManager.getCommunitySize();
        communityAndSurveyManager.getCommunityMembers().keySet()
                .forEach(memberId -> sendMessage(memberId, message));
    }

    private void sendMessageToAllMembers(String message) {
        communityAndSurveyManager.getCommunityMembers().keySet()
                .forEach(memberId -> sendMessage(memberId, message));
    }
}
