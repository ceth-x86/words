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
          result (imp/import-words-from-file (.getPath temp-file))]
      (is (= 2 result))))
  
  (testing "import-words-from-file correctly imports words from json file"
    (let [temp-file (create-temp-file-with-content sample-json-content ".json")
          result (imp/import-words-from-file (.getPath temp-file))]
      (is (= 2 result))))
  
  (testing "import-words-from-file handles invalid file content"
    (let [temp-file (create-temp-file-with-content sample-invalid-content ".txt")
          result (imp/import-words-from-file (.getPath temp-file))]
      (is (= 0 result))))
  
  (testing "import-words-from-file handles file not found error"
    (let [result (imp/import-words-from-file "nonexistent-file.txt")]
      (is (= 0 result))))) 