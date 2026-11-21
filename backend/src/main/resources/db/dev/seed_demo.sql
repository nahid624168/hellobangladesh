-- DEVELOPMENT ONLY. Not on the Flyway path, so it never runs in production.
-- Load with:  docker compose exec -T postgres psql -U <DB_USER> -d <DB_NAME> < backend/src/main/resources/db/dev/seed_demo.sql
-- Safe to re-run: it removes the demo subjects first (cascades to everything beneath them).
DELETE FROM subjects WHERE title IN ('Bangla Language', 'General Mathematics', 'Draft Subject (hidden)');

WITH s AS (
  INSERT INTO subjects (title, title_bn, description, description_bn, icon, is_featured, status, sort_order)
  VALUES ('Bangla Language', 'বাংলা ভাষা', 'Grammar, literature and vocabulary for exams.', 'পরীক্ষার জন্য ব্যাকরণ, সাহিত্য ও শব্দভান্ডার।', 'book', TRUE, 'published', 1)
  RETURNING id
), c AS (
  INSERT INTO categories (subject_id, title, title_bn, description, status, sort_order)
  SELECT id, 'Grammar', 'ব্যাকরণ', 'Core grammar topics.', 'published', 1 FROM s RETURNING id
), sc AS (
  INSERT INTO subcategories (category_id, title, title_bn, description, status, sort_order)
  SELECT id, 'Sandhi', 'সন্ধি', 'Joining of sounds.', 'published', 1 FROM c RETURNING id
)
INSERT INTO lessons (subcategory_id, title, title_bn, summary, summary_bn, content, content_bn, learning_objectives, estimated_minutes, status, sort_order)
SELECT id, 'What is Sandhi?', 'সন্ধি কী?', 'The basic idea of sandhi.', 'সন্ধির মূল ধারণা।',
  E'# What is Sandhi?\n\nSandhi is the **joining** of two neighbouring sounds.\n\n## Types\n- Vowel sandhi\n- Consonant sandhi\n- Visarga sandhi\n\n1. Split the word\n2. Find the meeting sounds\n3. Apply the rule',
  E'# সন্ধি কী?\n\nপাশাপাশি দুটি ধ্বনির **মিলনকে** সন্ধি বলে।\n\n## প্রকারভেদ\n- স্বরসন্ধি\n- ব্যঞ্জনসন্ধি\n- বিসর্গসন্ধি',
  ARRAY['Define sandhi', 'Name the three types'], 8, 'published', 1 FROM sc
UNION ALL
SELECT id, 'Vowel sandhi rules', 'স্বরসন্ধির নিয়ম', 'Rules when two vowels meet.', 'দুটি স্বর মিলিত হওয়ার নিয়ম।',
  E'## Vowel sandhi\n\n- অ + অ = আ\n- ই + ই = ঈ\n\nPractise with **ten** examples.', NULL,
  ARRAY['Apply basic vowel sandhi rules'], 12, 'published', 2 FROM sc;

WITH s AS (
  INSERT INTO subjects (title, title_bn, description, status, sort_order)
  VALUES ('General Mathematics', 'সাধারণ গণিত', 'Arithmetic and algebra practice.', 'published', 2) RETURNING id
), c AS (
  INSERT INTO categories (subject_id, title, status, sort_order) SELECT id, 'Arithmetic', 'published', 1 FROM s RETURNING id
), sc AS (
  INSERT INTO subcategories (category_id, title, status, sort_order) SELECT id, 'Percentages', 'published', 1 FROM c RETURNING id
)
INSERT INTO lessons (subcategory_id, title, summary, content, learning_objectives, estimated_minutes, status, sort_order)
SELECT id, 'Percentage basics', 'Converting between fractions and percentages.',
  E'## Percentage\n\nA percentage is a number out of **100**.', ARRAY['Convert fractions to percentages'], 6, 'published', 1 FROM sc;

INSERT INTO subjects (title, description, status, sort_order) VALUES ('Draft Subject (hidden)', 'Must never be visible to learners.', 'draft', 3);
