-- DEVELOPMENT ONLY. Not on the Flyway path, so it never runs in production.
-- Load AFTER seed_demo.sql (it attaches questions to the demo lessons):
--   docker compose exec -T postgres psql -U <DB_USER> -d <DB_NAME> < backend/src/main/resources/db/dev/seed_questions.sql
-- Safe to re-run: it removes the demo questions of those lessons first.
-- Includes draft / review / archived questions that must NEVER be visible to learners.

DO $$
DECLARE
  l_what  UUID;  -- 'What is Sandhi?'
  l_vowel UUID;  -- 'Vowel sandhi rules'
  l_pct   UUID;  -- 'Percentage basics'
  qid     UUID;
BEGIN
  SELECT id INTO l_what  FROM lessons WHERE title = 'What is Sandhi?' LIMIT 1;
  SELECT id INTO l_vowel FROM lessons WHERE title = 'Vowel sandhi rules' LIMIT 1;
  SELECT id INTO l_pct   FROM lessons WHERE title = 'Percentage basics' LIMIT 1;
  IF l_what IS NULL OR l_vowel IS NULL OR l_pct IS NULL THEN
    RAISE EXCEPTION 'Load seed_demo.sql first: the demo lessons were not found.';
  END IF;

  DELETE FROM questions WHERE lesson_id IN (l_what, l_vowel, l_pct);

  INSERT INTO tags (slug, name, name_bn) VALUES
    ('sandhi', 'Sandhi', 'সন্ধি'), ('grammar', 'Grammar', 'ব্যাকরণ'),
    ('percentage', 'Percentage', 'শতকরা'), ('basics', 'Basics', 'মৌলিক')
  ON CONFLICT (slug) DO NOTHING;

  -- 1. single choice, Bangla + English
  INSERT INTO questions (lesson_id, type, question_text, question_text_bn, explanation, explanation_bn, difficulty, marks, negative_marks, status, sort_order)
  VALUES (l_what, 'single_choice', 'What does "sandhi" mean?', '"সন্ধি" শব্দের অর্থ কী?',
          'Sandhi is the joining of two neighbouring sounds.', 'পাশাপাশি দুটি ধ্বনির মিলনকে সন্ধি বলে।', 'easy', 1, 0.25, 'published', 1)
  RETURNING id INTO qid;
  INSERT INTO question_options (question_id, option_text, option_text_bn, is_correct, sort_order) VALUES
    (qid, 'Joining of sounds', 'ধ্বনির মিলন', TRUE, 1), (qid, 'Splitting of words', 'শব্দের বিভাজন', FALSE, 2),
    (qid, 'A kind of poem', 'এক ধরনের কবিতা', FALSE, 3), (qid, 'A verb ending', 'ক্রিয়ার বিভক্তি', FALSE, 4);
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('sandhi', 'grammar', 'basics');

  -- 2. multiple select
  INSERT INTO questions (lesson_id, type, question_text, question_text_bn, explanation, difficulty, marks, negative_marks, status, sort_order)
  VALUES (l_what, 'multiple_select', 'Select all types of sandhi.', 'সন্ধির সব প্রকারভেদ নির্বাচন করো।',
          'The three types are vowel, consonant and visarga sandhi.', 'medium', 2, 0.5, 'published', 2)
  RETURNING id INTO qid;
  INSERT INTO question_options (question_id, option_text, option_text_bn, is_correct, sort_order) VALUES
    (qid, 'Vowel sandhi', 'স্বরসন্ধি', TRUE, 1), (qid, 'Consonant sandhi', 'ব্যঞ্জনসন্ধি', TRUE, 2),
    (qid, 'Visarga sandhi', 'বিসর্গসন্ধি', TRUE, 3), (qid, 'Tense sandhi', 'কালসন্ধি', FALSE, 4);
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('sandhi', 'grammar');

  -- 3. true / false
  INSERT INTO questions (lesson_id, type, question_text, question_text_bn, explanation, difficulty, marks, status, sort_order)
  VALUES (l_vowel, 'true_false', 'অ + অ = আ is an example of vowel sandhi.', 'অ + অ = আ স্বরসন্ধির উদাহরণ।',
          'Yes: two similar vowels merge into a long vowel.', 'easy', 1, 'published', 1)
  RETURNING id INTO qid;
  INSERT INTO question_options (question_id, option_text, option_text_bn, is_correct, sort_order) VALUES
    (qid, 'True', 'সত্য', TRUE, 1), (qid, 'False', 'মিথ্যা', FALSE, 2);
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('sandhi');

  -- 4. short answer (answer key is never returned to learners)
  INSERT INTO questions (lesson_id, type, question_text, explanation, difficulty, marks, status, sort_order)
  VALUES (l_pct, 'short_answer', 'What is 25% of 200?', '25/100 x 200 = 50.', 'easy', 1, 'published', 1)
  RETURNING id INTO qid;
  INSERT INTO question_answer_keys (question_id, answer) VALUES (qid, '50'), (qid, 'fifty');
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('percentage', 'basics');

  -- 5. written / descriptive (self-assessed against the model answer in the explanation; no auto key)
  INSERT INTO questions (lesson_id, type, question_text, explanation, difficulty, marks, status, sort_order)
  VALUES (l_pct, 'written', 'Explain how to convert the fraction 3/4 into a percentage.',
          'Multiply by 100: 3/4 x 100 = 75%.', 'medium', 3, 'published', 2)
  RETURNING id INTO qid;
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('percentage');

  -- 6. image based (placeholder image URLs; real uploads arrive with the admin backend)
  INSERT INTO questions (lesson_id, type, question_text, explanation, difficulty, marks, status, sort_order)
  VALUES (l_pct, 'image_based', 'The shaded part of the grid is what percentage of the whole?',
          '25 of 100 squares are shaded.', 'hard', 2, 'published', 3)
  RETURNING id INTO qid;
  INSERT INTO question_options (question_id, option_text, is_correct, sort_order) VALUES
    (qid, '15%', FALSE, 1), (qid, '25%', TRUE, 2), (qid, '40%', FALSE, 3), (qid, '75%', FALSE, 4);
  INSERT INTO question_images (question_id, kind, placement, url, alt_text, alt_text_bn, sort_order) VALUES
    (qid, 'diagram', 'question', 'https://example.com/demo/grid-25.png', 'A 10 by 10 grid with 25 squares shaded', '১০ x ১০ ছকে ২৫টি ঘর রঙিন', 1),
    (qid, 'image', 'explanation', 'https://example.com/demo/grid-25-solution.png', 'Counting the shaded squares', NULL, 1);
  INSERT INTO question_tags SELECT qid, id FROM tags WHERE slug IN ('percentage');

  -- Must never be visible: draft, review and archived
  INSERT INTO questions (lesson_id, type, question_text, difficulty, status, sort_order) VALUES
    (l_what, 'single_choice', 'HIDDEN draft question', 'easy', 'draft', 90),
    (l_what, 'single_choice', 'HIDDEN review question', 'easy', 'review', 91),
    (l_what, 'single_choice', 'HIDDEN archived question', 'easy', 'archived', 92);
  INSERT INTO question_options (question_id, option_text, is_correct, sort_order)
  SELECT id, 'Yes', TRUE, 1 FROM questions WHERE question_text LIKE 'HIDDEN %' AND lesson_id = l_what
  UNION ALL
  SELECT id, 'No', FALSE, 2 FROM questions WHERE question_text LIKE 'HIDDEN %' AND lesson_id = l_what;

  -- Version 1 snapshot for every demo question (the admin backend will write later versions).
  INSERT INTO question_versions (question_id, version, snapshot)
  SELECT q.id, q.current_version,
         jsonb_build_object(
           'type', q.type, 'text', q.question_text, 'textBn', q.question_text_bn,
           'explanation', q.explanation, 'difficulty', q.difficulty, 'marks', q.marks, 'negativeMarks', q.negative_marks,
           'options', COALESCE((SELECT jsonb_agg(jsonb_build_object('text', o.option_text, 'textBn', o.option_text_bn,
                                                                    'correct', o.is_correct, 'order', o.sort_order) ORDER BY o.sort_order)
                                FROM question_options o WHERE o.question_id = q.id), '[]'::jsonb),
           'answerKeys', COALESCE((SELECT jsonb_agg(k.answer) FROM question_answer_keys k WHERE k.question_id = q.id), '[]'::jsonb))
  FROM questions q WHERE q.lesson_id IN (l_what, l_vowel, l_pct);
END $$;

-- Expect zero rows:
SELECT * FROM question_integrity_issues;
