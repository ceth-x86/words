(ns my-app.import-test
  (:require [clojure.test :refer :all]
            [my-app.import :as imp]
            [my-app.db :as db]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Test data - новый формат markdown с вложенными списками
(def sample-markdown-content
  "# Words

- **apple** [ˈæpl]
  - **Meaning**: a round fruit with red skin
    - **Translation**: яблоко
    - **Examples**:
      - I eat an apple every day.
      - She picked apples from the tree.

- **banana** [bəˈnænə]
  - **Meaning**: a long curved fruit
    - **Translation**: банан
    - **Examples**:
      - Monkeys love bananas.")

;; Старый формат для обратной совместимости
(def sample-import-content
  "**apple** /ˈæpl/ - A round fruit
(яблоко)
- I ate an apple.
- She likes green apples.

**banana** /bəˈnænə/ - A long curved fruit
(банан)
- Monkeys eat bananas.")

(def sample-json-content
  "[
    {
      \"word\": \"apple\",
      \"transcription\": \"ˈæpl\",
      \"meanings\": [
        {
          \"description\": \"a round fruit with red skin\",
          \"translation\": \"яблоко\",
          \"examples\": [
            \"I eat an apple every day.\",
            \"She picked apples from the tree.\"
          ]
        }
      ]
    },
    {
      \"word\": \"banana\",
      \"transcription\": \"bəˈnænə\",
      \"meanings\": [
        {
          \"description\": \"a long curved fruit\",
          \"translation\": \"банан\",
          \"examples\": [
            \"Monkeys love bananas.\"
          ]
        }
      ]
    }
  ]")

(def sample-invalid-content
  "This is not a valid format
for importing words.")

;; Expected parsed data
(def expected-parsed-words
  [{:word "apple"
    :transcription "ˈæpl"
    :meanings [{:description "a round fruit with red skin"
                :translation "яблоко"
                :examples ["I eat an apple every day." "She picked apples from the tree."]}]}
   {:word "banana"
    :transcription "bəˈnænə"
    :meanings [{:description "a long curved fruit"
                :translation "банан"
                :examples ["Monkeys love bananas."]}]}])

;; Mock database function
(defn mock-batch-import-words! [words-data]
  (count words-data))

;; Create temporary files for testing
(defn create-temp-file-with-content [content file-extension]
  (let [temp-file (doto (File/createTempFile "import-test" file-extension)
                    (.deleteOnExit))]
    (spit temp-file content)
    temp-file))

;; Test fixture to setup mock database functions
(defn with-mock-db [f]
  (with-redefs [db/batch-import-words! mock-batch-import-words!]
    (f)))

(use-fixtures :each with-mock-db)

;; Тесты для старого формата, сохраняем для обратной совместимости
(deftest test-extract-word-and-transcription
  (testing "extract-word-and-transcription correctly extracts word, transcription and description"
    (let [line "**apple** /ˈæpl/ - A round fruit"
          result (imp/extract-word-and-transcription line)]
      (is (= "apple" (:word result)))
      (is (= "ˈæpl" (:transcription result)))
      (is (= "A round fruit" (:description result)))))
  
  (testing "extract-word-and-transcription handles missing parts"
    (let [line-no-desc "**apple** /ˈæpl/"
          result1 (imp/extract-word-and-transcription line-no-desc)]
      (is (= "apple" (:word result1)))
      (is (= "ˈæpl" (:transcription result1)))
      (is (nil? (:description result1))))
    
    (let [line-no-word "something /ˈæpl/ - A round fruit"
          result2 (imp/extract-word-and-transcription line-no-word)]
      (is (nil? (:word result2)))
      (is (= "ˈæpl" (:transcription result2))))))

(deftest test-extract-translation
  (testing "extract-translation correctly extracts translation"
    (let [line "(яблоко)"
          result (imp/extract-translation line)]
      (is (= "яблоко" result))))
  
  (testing "extract-translation handles whitespace"
    (let [line "( яблоко )"
          result (imp/extract-translation line)
          trimmed-result (when result (str/trim result))]
      (is (= "яблоко" trimmed-result))))
  
  (testing "extract-translation returns nil for invalid input"
    (is (nil? (imp/extract-translation "not a translation")))))

(deftest test-is-example-line?
  (testing "is-example-line? identifies example lines"
    (is (imp/is-example-line? "- This is an example"))
    (is (imp/is-example-line? "-This is also an example")))
  
  (testing "is-example-line? rejects non-example lines"
    (is (not (imp/is-example-line? "This is not an example")))
    (is (not (imp/is-example-line? "")))))

(deftest test-parse-meaning
  (testing "parse-meaning correctly parses a meaning entry"
    (let [lines ["**apple** /ˈæpl/ - A round fruit" 
                 "(яблоко)" 
                 "- Example 1" 
                 "- Example 2"]
          result (imp/parse-meaning lines)]
      (is (= "apple" (:word result)))
      (is (= "ˈæpl" (:transcription result)))
      (is (= "A round fruit" (:description result)))
      (is (= "яблоко" (:translation result)))
      (is (= ["Example 1" "Example 2"] (:examples result)))))
  
  (testing "parse-meaning handles entries without examples"
    (let [lines ["**apple** /ˈæpl/ - A round fruit" 
                 "(яблоко)"]
          result (imp/parse-meaning lines)]
      (is (= "apple" (:word result)))
      (is (= "яблоко" (:translation result)))
      (is (= [] (:examples result)))))
  
  (testing "parse-meaning handles empty input"
    (is (nil? (imp/parse-meaning [])))))

;; Новые тесты для формата markdown
(deftest test-parse-markdown-words-file
  (testing "parse-markdown-words-file correctly parses markdown content with multiple words"
    (let [result (imp/parse-markdown-words-file sample-markdown-content)]
      (is (= 2 (count result)) "Should find two words")
      (is (= "apple" (:word (first result))) "First word should be apple")
      (is (= "banana" (:word (second result))) "Second word should be banana")
      (is (= "ˈæpl" (:transcription (first result))) "Should extract correct transcription")
      (is (= 1 (count (:meanings (first result)))) "First word should have one meaning")
      (is (= "яблоко" (:translation (first (:meanings (first result))))) "Should have correct translation")
      (is (= 2 (count (:examples (first (:meanings (first result)))))) "First word should have two examples"))))

;; Тесты для JSON формата
(deftest test-parse-json-words-file
  (testing "parse-json-words-file correctly parses JSON content with multiple words"
    (let [result (imp/parse-json-words-file sample-json-content)]
      (is (= 2 (count result)) "Should find two words")
      (is (= "apple" (:word (first result))) "First word should be apple")
      (is (= "banana" (:word (second result))) "Second word should be banana"))))

;; Тесты для определения формата файла
(deftest test-detect-file-format
  (testing "detect-file-format correctly identifies file formats"
    (is (= :json (imp/detect-file-format "words.json")) "Should detect JSON format")
    (is (= :markdown (imp/detect-file-format "words.md")) "Should detect Markdown format")
    (is (= :unknown (imp/detect-file-format "words.xyz")) "Should return unknown for other formats")))

(deftest test-parse-words-file
  (testing "parse-words-file correctly parses different file formats"
    (let [md-result (imp/parse-words-file sample-markdown-content :markdown)
          json-result (imp/parse-words-file sample-json-content :json)]
      (is (= 2 (count md-result)) "Should parse two words from markdown")
      (is (= 2 (count json-result)) "Should parse two words from JSON"))))
  
(deftest test-import-words-from-file
  (testing "import-words-from-file correctly imports words from markdown file"
    (let [temp-file (create-temp-file-with-content sample-markdown-content ".md")
          imported-count (atom 0)]
      ;; Mock the database operations
      (with-redefs [db/insert-word! (fn [& args] true)
                    db/add-meaning-to-word! (fn [& args] true)
                    db/get-word-by-word (fn [word] nil)
                    db/delete-word! (fn [word] true)]
        (let [result (imp/import-words-from-file (.getPath temp-file))]
          (is (= 2 result))))))
  
  (testing "import-words-from-file correctly imports words from json file"
    (let [temp-file (create-temp-file-with-content sample-json-content ".json")
          imported-count (atom 0)]
      ;; Mock the database operations
      (with-redefs [db/insert-word! (fn [& args] true)
                    db/add-meaning-to-word! (fn [& args] true)
                    db/get-word-by-word (fn [word] nil)
                    db/delete-word! (fn [word] true)]
        (let [result (imp/import-words-from-file (.getPath temp-file))]
          (is (= 2 result))))))
  
  (testing "import-words-from-file handles invalid file content"
    (let [temp-file (create-temp-file-with-content sample-invalid-content ".txt")]
      ;; Mock the database operations
      (with-redefs [db/insert-word! (fn [& args] true)
                    db/add-meaning-to-word! (fn [& args] true)
                    db/get-word-by-word (fn [word] nil)
                    db/delete-word! (fn [word] true)]
        (let [result (imp/import-words-from-file (.getPath temp-file))]
          (is (= 0 result))))))
  
  (testing "import-words-from-file handles file not found error"
    ;; Mock the database operations
    (with-redefs [db/insert-word! (fn [& args] true)
                  db/add-meaning-to-word! (fn [& args] true)
                  db/get-word-by-word (fn [word] nil)
                  db/delete-word! (fn [word] true)]
      (let [result (imp/import-words-from-file "nonexistent-file.txt")]
        (is (= 0 result))))))

;; New test to verify that import preserves words not in the import file
(deftest test-import-preserves-non-imported-words
  (testing "Import only affects words in the import file, not other words in the database"
    (let [imported-words (atom [])
          deleted-words (atom [])
          inserted-words (atom [])
          meanings-added (atom [])
          get-word-mock (atom {})]
      
      ;; Mock functions for testing
      (with-redefs [db/delete-word! (fn [word] (swap! deleted-words conj word))
                    db/insert-word! (fn [word & args] (swap! inserted-words conj word) true)
                    db/add-meaning-to-word! (fn [word & args] (swap! meanings-added conj word) true)
                    db/get-word-by-word (fn [word] (get @get-word-mock word))]
        
        ;; 1. First add an existing word to the mock
        (swap! get-word-mock assoc "banana" {:id 1 :word "banana"})
        
        ;; 2. Create JSON with two words - one new, one existing
        (let [test-json "[{\"word\": \"apple\", \"transcription\": \"ˈæpl\", \"meanings\": [{\"description\": \"a fruit\", \"translation\": \"яблоко\", \"examples\": [\"Example\"]}]}, {\"word\": \"banana\", \"transcription\": \"bəˈnænə\", \"meanings\": [{\"description\": \"a fruit\", \"translation\": \"банан\", \"examples\": [\"Example\"]}]}]"
              temp-file (create-temp-file-with-content test-json ".json")
              result (imp/import-words-from-file (.getPath temp-file))]
          
          ;; Check that delete_word! wasn't called
          (is (empty? @deleted-words) "No words should be deleted in standard mode")
          
          ;; Check that insert_word! was called only for the new word "apple"
          (is (= ["apple"] @inserted-words) "Only new word 'apple' should be inserted")
          
          ;; Check that add_meaning_to_word! was called for "banana" (existing word)
          (is (= ["banana"] @meanings-added) "Only existing word 'banana' meaning should be added")
          
          ;; Check that the function returned the correct number of words
          (is (= 2 result) "Should return 2 words imported"))))))

;; Test to verify behavior with replace-meanings? option
(deftest test-import-with-replace-option
  (testing "Import with replace-meanings? option replaces all meanings for words in the import file"
    (let [deleted-words (atom [])
          inserted-words (atom [])]
      
      ;; Mock functions for testing
      (with-redefs [db/delete-word! (fn [word] (swap! deleted-words conj word))
                    db/insert-word! (fn [word & args] (swap! inserted-words conj word) true)]
        
        ;; Create JSON with one word
        (let [single-word-json "[{\"word\": \"apple\", \"transcription\": \"ˈæpl\", \"meanings\": [{\"description\": \"a fruit\", \"translation\": \"яблоко\", \"examples\": [\"Example\"]}]}]"
              temp-file (create-temp-file-with-content single-word-json ".json")
              result (imp/import-words-from-file (.getPath temp-file) true)] ;; Replace mode (replace-meanings? = true)
          
          ;; Check that "apple" was deleted and added
          (is (= ["apple"] @deleted-words) "Word 'apple' should be deleted when replace-meanings? is true")
          (is (= ["apple"] @inserted-words) "Word 'apple' should be inserted when replace-meanings? is true")
          (is (= 1 result) "Should return 1 word imported with replace mode"))))))

;; Test for standard behavior (preserving meanings)
(deftest test-standard-import-behavior
  (testing "Standard import behavior keeps existing meanings of words"
    (let [word-data (atom {})
          meaning-data (atom {})
          example-data (atom {})
          word-id-counter (atom 1)
          meaning-id-counter (atom 1)
          deleted-words (atom [])
          meaning-added (atom [])
          get-word-mock (atom {})]
      
      ;; Mock functions for DB
      (with-redefs [
        ;; Mock for word deletion
        db/delete-word! (fn [word] 
                          (swap! deleted-words conj word)
                          (swap! word-data dissoc word)
                          (println "Deleted word:" word))
        
        ;; Mock for word insertion with meaning
        db/insert-word! (fn [word transcription description translation examples]
                          (let [word-id @word-id-counter
                                meaning-id @meaning-id-counter]
                            (swap! word-id-counter inc)
                            (swap! meaning-id-counter inc)
                            (swap! word-data assoc word {:id word-id :word word})
                            (swap! meaning-data assoc meaning-id 
                                   {:id meaning-id 
                                    :word_id word-id 
                                    :transcription transcription 
                                    :description description
                                    :translation translation})
                            (when examples
                              (let [examples-list (clojure.string/split-lines examples)]
                                (swap! example-data assoc meaning-id examples-list)))
                            (println "Inserted word:" word "with meaning:" description)
                            true))
        
        ;; Mock for adding meaning
        db/add-meaning-to-word! (fn [word transcription description translation examples]
                                  (let [word-record (get @word-data word)
                                        word-id (if word-record 
                                                  (:id word-record)
                                                  (let [new-id @word-id-counter]
                                                    (swap! word-id-counter inc)
                                                    (swap! word-data assoc word {:id new-id :word word})
                                                    new-id))
                                        meaning-id @meaning-id-counter]
                                    (swap! meaning-id-counter inc)
                                    (swap! meaning-data assoc meaning-id 
                                           {:id meaning-id 
                                            :word_id word-id 
                                            :transcription transcription 
                                            :description description
                                            :translation translation})
                                    (when examples
                                      (let [examples-list (clojure.string/split-lines examples)]
                                        (swap! example-data assoc meaning-id examples-list)))
                                    (swap! meaning-added conj 
                                           {:word word :description description})
                                    (println "Added meaning to word:" word "description:" description)
                                    true))
        
        ;; Mock for getting a word
        db/get-word-by-word (fn [word] (get @word-data word))]
        
        ;; 1. First add "apple" word with two meanings
        (db/insert-word! "apple" "ˈæpl" "a fruit" "яблоко" "Example 1")
        (db/insert-word! "apple" "ˈæpl" "a computer" "яблоко (компьютер)" "Example 2")
        
        ;; Check that we have 2 meanings
        (is (= 2 (count @meaning-data)) "Should have 2 meanings before import")
        
        ;; 2. Import a new meaning for "apple" in standard mode (without replacing existing ones)
        (let [new-meaning-json "[{\"word\": \"apple\", \"transcription\": \"ˈæpl\", \"meanings\": [{\"description\": \"a company\", \"translation\": \"Эппл\", \"examples\": [\"Apple makes iPhones\"]}]}]"
              temp-file (create-temp-file-with-content new-meaning-json ".json")
              result (imp/import-words-from-file (.getPath temp-file))]
          
          ;; Check that delete-word! wasn't called
          (is (empty? @deleted-words) "No words should be deleted in standard mode")
          
          ;; Check that a new meaning was added
          (is (= 1 (count @meaning-added)) "Should add one new meaning")
          (is (= "a company" (:description (first @meaning-added))) "Should add the new company meaning")
          
          ;; After import there should be 3 meanings (2 old + 1 new)
          (is (= 3 (count @meaning-data)) "Should have 3 meanings after import (2 old + 1 new)")
          
          ;; Check that the function returned the correct word count
          (is (= 1 result) "Should return 1 word imported"))))))

;; Test to verify that meanings are not duplicated on repeated imports
(deftest test-import-does-not-duplicate-meanings
  (testing "Repeated import doesn't duplicate meanings with the same description and translation"
    (let [word-data (atom {})
          meaning-data (atom {})
          example-data (atom {})
          word-id-counter (atom 1)
          meaning-id-counter (atom 1)
          meaning-added-count (atom 0)]
      
      ;; Mock functions for DB
      (with-redefs [
        ;; Mock for getting a word with its meanings
        db/get-word-by-word (fn [word] 
                              (let [word-data (get @word-data word)]
                                (when word-data
                                  (let [word-id (:id word-data)
                                        meanings (filter #(= (:word_id (second %)) word-id) @meaning-data)]
                                    (assoc word-data 
                                           :meanings (map (fn [[id meaning]]
                                                          (dissoc meaning :word_id)) 
                                                        meanings))))))
        
        ;; Mock for word insertion
        db/insert-word! (fn [word transcription description translation examples]
                          (let [word-id @word-id-counter
                                meaning-id @meaning-id-counter]
                            (swap! word-id-counter inc)
                            (swap! meaning-id-counter inc)
                            (swap! word-data assoc word {:id word-id :word word})
                            (swap! meaning-data assoc meaning-id 
                                   {:id meaning-id 
                                    :word_id word-id 
                                    :transcription transcription 
                                    :description description
                                    :translation translation})
                            (when examples
                              (let [examples-list (clojure.string/split-lines examples)]
                                (swap! example-data assoc meaning-id examples-list)))
                            (swap! meaning-added-count inc)
                            (println "Inserted word:" word "with meaning:" description)
                            true))
        
        ;; Mock for adding a meaning
        db/add-meaning-to-word! (fn [word transcription description translation examples]
                                 (let [word-record (get @word-data word)
                                       word-id (if word-record 
                                                (:id word-record)
                                                (let [new-id @word-id-counter]
                                                  (swap! word-id-counter inc)
                                                  (swap! word-data assoc word {:id new-id :word word})
                                                  new-id))
                                       meaning-id @meaning-id-counter]
                                   (swap! meaning-id-counter inc)
                                   (swap! meaning-data assoc meaning-id 
                                          {:id meaning-id 
                                           :word_id word-id 
                                           :transcription transcription 
                                           :description description
                                           :translation translation})
                                   (swap! meaning-added-count inc)
                                   (println "Added meaning to word:" word "description:" description)
                                   true))]
        
        ;; Create JSON with one word
        (let [test-json "[{\"word\": \"apple\", \"transcription\": \"ˈæpl\", \"meanings\": [{\"description\": \"a fruit\", \"translation\": \"яблоко\", \"examples\": [\"Example\"]}]}]"
              temp-file (create-temp-file-with-content test-json ".json")]
          
          ;; 1. First import - should add a new word
          (let [result-1 (imp/import-words-from-file (.getPath temp-file))]
            (is (= 1 result-1) "First import should return 1 word")
            (is (= 1 @meaning-added-count) "First import should add 1 meaning"))
          
          ;; 2. Repeated import - meanings should not be duplicated
          (reset! meaning-added-count 0) ;; Reset counter
          (let [result-2 (imp/import-words-from-file (.getPath temp-file))]
            (is (= 1 result-2) "Second import should return 1 word")
            (is (= 0 @meaning-added-count) "Second import should not add any meanings because they already exist"))
          
          ;; 3. Add a new JSON with the same word but different meaning
          (let [new-json "[{\"word\": \"apple\", \"transcription\": \"ˈæpl\", \"meanings\": [{\"description\": \"a computer company\", \"translation\": \"Эппл\", \"examples\": [\"Example 2\"]}]}]"
                new-temp-file (create-temp-file-with-content new-json ".json")]
            
            (reset! meaning-added-count 0) ;; Reset counter
            (let [result-3 (imp/import-words-from-file (.getPath new-temp-file))]
              (is (= 1 result-3) "Third import with new meaning should return 1 word")
              (is (= 1 @meaning-added-count) "Third import should add 1 new meaning")
              
              ;; Check that the word now has 2 meanings
              (let [word-meanings (:meanings (db/get-word-by-word "apple"))]
                (is (= 2 (count word-meanings)) "Word should have 2 meanings after imports")
                (is (= #{"a fruit" "a computer company"} 
                       (set (map :description word-meanings)))
                    "Word should have both meanings"))))))))) 