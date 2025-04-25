(ns my-app.import-test
  (:require [clojure.test :refer :all]
            [my-app.import :as imp]
            [my-app.db :as db]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Test data
(def sample-import-content
  "**apple** /ˈæpl/ - A round fruit
(яблоко)
- I ate an apple.
- She likes green apples.

**banana** /bəˈnænə/ - A long curved fruit
(банан)
- Monkeys eat bananas.")

(def sample-invalid-content
  "This is not a valid format
for importing words.")

;; Expected parsed data
(def expected-parsed-words
  [{:word "apple"
    :transcription "ˈæpl"
    :description "A round fruit"
    :translation "яблоко"
    :examples "I ate an apple.\nShe likes green apples."}
   {:word "banana"
    :transcription "bəˈnænə"
    :description "A long curved fruit"
    :translation "банан"
    :examples "Monkeys eat bananas."}])

;; Mock database function
(defn mock-batch-import-words! [words-data]
  (count words-data))

;; Create temporary files for testing
(defn create-temp-file-with-content [content]
  (let [temp-file (doto (File/createTempFile "import-test" ".txt")
                    (.deleteOnExit))]
    (spit temp-file content)
    temp-file))

;; Test fixture to setup mock database functions
(defn with-mock-db [f]
  (with-redefs [db/batch-import-words! mock-batch-import-words!]
    (f)))

(use-fixtures :each with-mock-db)

;; Test extraction functions
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

(deftest test-parse-word-entry
  (testing "parse-word-entry correctly parses a word entry"
    (let [lines ["**apple** /ˈæpl/ - A round fruit" 
                 "(яблоко)" 
                 "- Example 1" 
                 "- Example 2"]
          result (imp/parse-word-entry lines)]
      (is (= "apple" (:word result)))
      (is (= "ˈæpl" (:transcription result)))
      (is (= "A round fruit" (:description result)))
      (is (= "яблоко" (:translation result)))
      (is (= "Example 1\nExample 2" (:examples result)))))
  
  (testing "parse-word-entry handles entries without examples"
    (let [lines ["**apple** /ˈæpl/ - A round fruit" 
                 "(яблоко)"]
          result (imp/parse-word-entry lines)]
      (is (= "apple" (:word result)))
      (is (= "яблоко" (:translation result)))
      (is (= "" (:examples result)))))
  
  (testing "parse-word-entry handles empty input"
    (is (nil? (imp/parse-word-entry [])))))

(deftest test-parse-words-file
  (testing "parse-words-file correctly parses file content with multiple words"
    (let [result (imp/parse-words-file sample-import-content)]
      (is (= 2 (count result)))
      (is (= "apple" (:word (first result))))
      (is (= "banana" (:word (second result))))
      (is (= "яблоко" (:translation (first result))))
      (is (= "I ate an apple.\nShe likes green apples." (:examples (first result))))))
  
  (testing "parse-words-file handles invalid content"
    (let [result (imp/parse-words-file sample-invalid-content)]
      (is (empty? result)))
    
    ;; Проверим, что parse-words-file возвращает пустой список для пустой строки
    (let [result (imp/parse-words-file "")]
      (is (empty? result)))))

(deftest test-import-words-from-file
  (testing "import-words-from-file correctly imports words from file"
    (let [temp-file (create-temp-file-with-content sample-import-content)
          result (imp/import-words-from-file (.getPath temp-file))]
      (is (= 2 result))))
  
  (testing "import-words-from-file handles invalid file content"
    (let [temp-file (create-temp-file-with-content sample-invalid-content)
          result (imp/import-words-from-file (.getPath temp-file))]
      (is (= 0 result))))
  
  (testing "import-words-from-file handles file not found error"
    (let [result (imp/import-words-from-file "nonexistent-file.txt")]
      (is (= 0 result))))) 