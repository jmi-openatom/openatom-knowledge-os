CREATE TABLE user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  subject VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(200),
  role VARCHAR(20) NOT NULL DEFAULT 'member',
  avatar VARCHAR(500),
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE knowledge_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(500) NOT NULL,
  extension VARCHAR(32) NOT NULL,
  content_type VARCHAR(150) NOT NULL,
  size BIGINT NOT NULL,
  object_key VARCHAR(700) NOT NULL UNIQUE,
  tags_csv VARCHAR(1000),
  extracted_text LONGTEXT,
  status VARCHAR(30) NOT NULL,
  created_by_subject VARCHAR(100) NOT NULL,
  created_by_name VARCHAR(100) NOT NULL,
  failure_message VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_knowledge_file_status ON knowledge_file(status);
CREATE INDEX idx_knowledge_file_updated_at ON knowledge_file(updated_at);

CREATE TABLE knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(300) NOT NULL,
  content LONGTEXT NOT NULL,
  version INT NOT NULL DEFAULT 1,
  updated_by_subject VARCHAR(100) NOT NULL,
  updated_by_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE document_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version INT NOT NULL,
  title VARCHAR(300) NOT NULL,
  content LONGTEXT NOT NULL,
  created_by_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
);

CREATE TABLE wiki_page (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT,
  title VARCHAR(300) NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'page',
  content LONGTEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  updated_by_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_wiki_parent FOREIGN KEY (parent_id) REFERENCES wiki_page(id)
);

CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_subject VARCHAR(100) NOT NULL,
  actor_name VARCHAR(100) NOT NULL,
  action VARCHAR(100) NOT NULL,
  resource_type VARCHAR(100) NOT NULL,
  resource_id VARCHAR(100),
  detail VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_created_at ON audit_log(created_at);
