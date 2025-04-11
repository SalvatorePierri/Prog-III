package app;

public class Message {
    private String firstLine;
    private String fullMessage;

    public Message(String fullMessage) {
        this.fullMessage = fullMessage;
        this.firstLine = fullMessage.split("\n")[0]; // Prima riga del messaggio
    }

    public String getFirstLine() {
        return firstLine;
    }

    public String getFullMessage() {
        return fullMessage;
    }

    @Override
    public String toString() {
        return firstLine;
    }
}
