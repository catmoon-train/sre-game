package net.exmo.sreGame.games.situationpuzzle;

import java.util.UUID;
import net.exmo.sreGame.ai.AnswerType;

/**
 * 海龟汤的一条提问与回答记录。
 */
public final class Question {
   private final UUID asker;
   private final String askerName;
   private final String question;
   private AnswerType answerType;

   public Question(UUID asker, String askerName, String question) {
      this.asker = asker;
      this.askerName = askerName;
      this.question = question;
   }

   public UUID asker() {
      return this.asker;
   }

   public String askerName() {
      return this.askerName;
   }

   public String question() {
      return this.question;
   }

   public AnswerType answerType() {
      return this.answerType;
   }

   public void setAnswerType(AnswerType answerType) {
      this.answerType = answerType;
   }

   public boolean isAnswered() {
      return this.answerType != null;
   }
}
