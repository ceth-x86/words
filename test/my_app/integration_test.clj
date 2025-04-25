(ns my-app.integration-test
  (:require [clojure.test :refer :all]
            [my-app.db :as db]
            [my-app.import :as imp]
            [my-app.export :as exp]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Define a temporary file-based test database
(def test-db-file (str (System/getProperty "java.io.tmpdir") "/test_integration.db"))

;; Define the test database spec
(def test-db-spec
  {:dbtype "sqlite"
   :dbname test-db-file})

;; Path to the test words file
(def test-words-file "resources/test_words.md")

;; Test fixture to setup and teardown the test database
(defn with-test-db [f]
  ;; Delete any existing test database file
  (let [db-file (File. test-db-file)]
    (when (.exists db-file)
      (.delete db-file)))

  ;; Temporarily redefine db-spec to use our test database
  (with-redefs [db/db-spec test-db-spec]
    ;; Initialize the database
    (db/init-db!)
    
    ;; Run the tests
    (f)
    
    ;; Cleanup - delete the test database
    (let [db-file (File. test-db-file)]
      (when (.exists db-file)
        (.delete db-file)))))

(use-fixtures :each with-test-db)

;; Utility function to normalize whitespace
(defn normalize-whitespace [s]
  (-> s
      (str/replace #"\r\n" "\n")  ;; Convert Windows line endings
      (str/replace #"\r" "\n")    ;; Convert old Mac line endings
      (str/replace #"\n+" "\n")   ;; Normalize multiple newlines to single
      (str/replace #" +" " ")     ;; Normalize multiple spaces
      (str/trim)))                ;; Trim leading/trailing whitespace

;; Create temporary files for testing
(defn temp-file [prefix suffix]
  (doto (File/createTempFile prefix suffix)
    (.deleteOnExit)))

;; Test multiple meanings for a word
(deftest test-multiple-meanings
  (testing "Adding multiple meanings to a word"
    ;; Add a word with first meaning
    (db/insert-word! "run" "rʌn" "to move quickly on foot" "бежать" "I run every morning.\nShe runs faster than me.")
    
    ;; Verify the word exists with one meaning
    (let [word (db/get-word-by-word "run")]
      (is (= "run" (:word word)) "Word should be 'run'")
      (is (= 1 (count (:meanings word))) "Should have one meaning initially"))
    
    ;; Add a second meaning
    (db/add-meaning-to-word! "run" "rʌn" "to operate or function" "работать" "The machine runs smoothly.\nHow long has this program been running?")
    
    ;; Verify word now has two meanings
    (let [word (db/get-word-by-word "run")]
      (is (= 2 (count (:meanings word))) "Should have two meanings after adding one")
      (is (= "бежать" (:translation (first (:meanings word)))) "First meaning should be 'бежать'")
      (is (= "работать" (:translation (second (:meanings word)))) "Second meaning should be 'работать'"))))

;; Integration tests
(deftest test-import-export-cycle
  (testing "Full import-export cycle preserves word data"
    ;; 1. Import words from the test file
    (let [import-count (imp/import-words-from-file test-words-file)]
      (is (pos? import-count) "Should import some words")
      
      ;; Check that we have meanings and examples in the database
      (is (pos? (db/count-meanings)) "Should have meanings after import")
      (is (pos? (db/count-examples)) "Should have examples after import")
      
      ;; 2. Export words to a temporary file
      (let [temp-export-file (.getPath (temp-file "export-test" ".md"))
            export-count (exp/export-words-to-file temp-export-file)]
        
        ;; 3. Verify that we exported some words
        (is (pos? export-count) "Should export some words")
        
        ;; 4. Read exported file
        (let [exported-content (slurp temp-export-file)
              normalized-exported (normalize-whitespace exported-content)]
          
          ;; 5. Check that the header is present
          (is (str/includes? normalized-exported "# Words") "Should contain Words header")
          
          ;; 6. Check that translations are present
          (is (str/includes? normalized-exported "**Translation**:") "Should contain Translation field")
          
          ;; 7. Check that examples are present
          (is (str/includes? normalized-exported "**Examples**:") "Should contain Examples field"))))))

(deftest test-search-export-functionality
  (testing "Search and export functionality works correctly"
    ;; 1. Import words from the test file
    (let [import-count (imp/import-words-from-file test-words-file)]
      (is (pos? import-count) "Should import some words")
      
      ;; 2. Test searching and exporting apple
      (let [temp-apple-file (.getPath (temp-file "apple-export" ".md"))
            apple-count (exp/export-search-results-to-file "apple" temp-apple-file)]
        (is (pos? apple-count) "Should find and export word(s) for 'apple'")
        
        (let [apple-content (slurp temp-apple-file)]
          (is (str/includes? apple-content "**apple**") "Should contain apple"))))))

(deftest test-example-management
  (testing "Adding and retrieving examples"
    ;; Add a word with examples
    (db/insert-word! "test" "test" "a procedure for critical evaluation" "тест" "This is an example.\nThis is another example.")
    
    ;; Retrieve the word and check examples
    (let [word (db/get-word-by-word "test")
          first-meaning (first (:meanings word))
          examples (:examples first-meaning)]
      (is (= 2 (count examples)) "Should have two examples")
      
      ;; Add another example to the meaning
      (db/add-example-to-meaning! (:id first-meaning) "This is a third example.")
      
      ;; Verify the new example was added
      (let [updated-word (db/get-word-by-word "test")
            updated-meaning (first (:meanings updated-word))
            updated-examples (:examples updated-meaning)]
        (is (= 3 (count updated-examples)) "Should now have three examples"))))) 