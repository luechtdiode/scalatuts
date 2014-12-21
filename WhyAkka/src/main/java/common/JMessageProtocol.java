package common;

import java.io.Serializable;

import akka.actor.ActorRef;

public interface JMessageProtocol {
  public class Subscribe implements Serializable {
    private static final long serialVersionUID = 1L;
    public Subscribe(ActorRef sub) {
      this.sub = sub;
    }
    public final ActorRef sub;
    @Override
    public String toString() {
      return "Subscribe(" + sub.path() + ")";
    }
  }
  
  public class NewsMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    public NewsMessage(String news) {
      this.news = news;
    }
    public final String news;
    
    @Override
    public String toString() {
      return "NewsMessage(" + news + ")";
    }
    @Override
    public boolean equals(Object other) {
      return other instanceof NewsMessage && ((NewsMessage)other).news.equals(news);
    }
    @Override
    public int hashCode() {
      return news.hashCode();
    }
  }
  
  public class MessageProcessed implements Serializable {
    private static final long serialVersionUID = 1L;
    @Override
    public String toString() {
      return "MessageProcessed";
    }
    @Override
    public boolean equals(Object other) {
      return other instanceof MessageProcessed;
    }
    @Override
    public int hashCode() {
      return 1;
    }
  }
  
  MessageProcessed MESSAGE_PROCESSED = new MessageProcessed();
}