ALTER TABLE knowledge_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE wiki_page ADD COLUMN deleted_at TIMESTAMP NULL;
