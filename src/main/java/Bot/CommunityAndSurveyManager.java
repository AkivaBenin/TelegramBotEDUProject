package Bot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommunityAndSurveyManager {

    private final Map<Long, String> communityMembers = new HashMap<>();
    private final Map<String, Survey> activeSurveys = new HashMap<>();
    private final Map<String, Map<String, String>> surveyResponses = new HashMap<>();
    private boolean isSurveyActive = false;

    public void addMember(String username, Long chatId) {
        communityMembers.putIfAbsent(chatId, username);
    }

    public int getCommunitySize() {
        return communityMembers.size();
    }

    public boolean isMember(String username) {
        return communityMembers.containsValue(username);
    }

    public Map<Long, String> getCommunityMembers() {
        return communityMembers;
    }

    public void deleteMemberHistory() {
        communityMembers.clear();
    }

    public boolean createSurvey(String creator, List<Question> questions) {
        if (getCommunitySize() < 2) {
            return false;
        }
        String surveyId = "survey_" + creator + "_" + System.currentTimeMillis();
        activeSurveys.put(surveyId, new Survey(creator, questions));
        return true;
    }

    public Survey getSurvey(String surveyId) {
        return activeSurveys.get(surveyId);
    }

    public boolean collectResponse(String surveyId, String username, String answer) {
        surveyResponses.putIfAbsent(surveyId, new HashMap<>());

        Map<String, String> responses = surveyResponses.get(surveyId);
        if (responses.containsKey(username)) {
            return false;
        }

        responses.put(username, answer);
        return true;
    }

    public Map<String, String> getResponses(String surveyId) {
        return surveyResponses.getOrDefault(surveyId, new HashMap<>());
    }

    public void clearSurvey(String surveyId) {
        surveyResponses.remove(surveyId);
    }

    public boolean isSurveyActive() {
        return isSurveyActive;
    }

    public void setSurveyActive(boolean active) {
        this.isSurveyActive = active;
    }
}
