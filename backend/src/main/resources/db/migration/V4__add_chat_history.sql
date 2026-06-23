CREATE TABLE chat_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_subject VARCHAR(100) NOT NULL,
  title VARCHAR(300) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  question TEXT NOT NULL,
  answer LONGTEXT NOT NULL,
  sources LONGTEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id)
);

CREATE INDEX idx_chat_conversation_user ON chat_conversation(user_subject);
CREATE INDEX idx_chat_message_conversation ON chat_message(conversation_id);
