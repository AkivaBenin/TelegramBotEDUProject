package Bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new SurveyBot());
        } catch (Exception e) {
            if (e.getMessage().contains("Webhook")) {
                System.out.println("No webhook found to clear, continuing with long polling.");
            } else {
                e.printStackTrace();
            }
        }
    }
}
