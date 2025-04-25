(ns my-app.db-test
  (:require [clojure.test :refer :all]
            [my-app.db :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Define a temporary file-based test database
(def test-db-file (str (System/getProperty "java.io.tmpdir") "/test_english_words.db"))

;; Define the test database spec
(def test-db-spec
  {:dbtype "sqlite"
   :dbname test-db-file})

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
    
    ;; Insert word directly using the API
    (db/insert-word! "apple" "ˈæpl" "A round fruit with red, yellow, or green skin" "яблоко" "I ate an apple for breakfast.")
    
    ;; Run the tests
    (f)
    
    ;; Cleanup - delete the test database
    (let [db-file (File. test-db-file)]
      (when (.exists db-file)
        (.delete db-file)))))

(use-fixtures :each with-test-db)

(deftest test-init-db!
  (testing "init-db! creates the required tables"
    (db/init-db!) ;; Running again should be safe due to IF NOT EXISTS
    (let [ds (jdbc/get-datasource test-db-spec)
          tables (jdbc/execute! ds ["SELECT name FROM sqlite_master WHERE type='table'"]
                               {:builder-fn rs/as-unqualified-maps})]
      (is (some #(= "words" (:name %)) tables))
      (is (some #(= "meanings" (:name %)) tables))
      (is (some #(= "examples" (:name %)) tables)))))

(deftest test-insert-word!
  (testing "insert-word! adds a new word to the database"
    (db/insert-word! "banana" "bəˈnɑːnə" "A long curved fruit with a yellow skin" "банан" "Monkeys love bananas.")
    (let [word (db/get-word-by-word "banana")]
      (is (= "banana" (:word word)))
      (let [meaning (first (:meanings word))]
        (is (= "bəˈnɑːnə" (:transcription meaning)))
        (is (= "A long curved fruit with a yellow skin" (:description meaning)))
        (is (= "банан" (:translation meaning)))))))

(deftest test-add-meaning-to-word!
  (testing "add-meaning-to-word! adds a meaning to an existing word"
    (db/add-meaning-to-word! "apple" "ˈæpl" "A type of computer" "яблоко" "Apple makes computers and phones.")
    (let [word (db/get-word-by-word "apple")]
      (is (= 2 (count (:meanings word))))
      (is (some #(= "A type of computer" (:description %)) (:meanings word))))))

(deftest test-add-example!
  (testing "add-example-to-meaning! adds an example to a meaning"
    (let [word (db/get-word-by-word "apple")
          meaning (first (:meanings word))
          meaning-id (:id meaning)]
      (db/add-example-to-meaning! meaning-id "The apple fell from the tree.")
      (let [updated-word (db/get-word-by-word "apple")
            updated-meaning (first (:meanings updated-word))
            examples (get-in updated-word [:meanings 0 :examples])]
        (is (> (count examples) 1))
        (is (some #(= "The apple fell from the tree." (:text %)) examples))))))

(deftest test-get-all-words
  (testing "get-all-words returns all words with their meanings and examples"
    (let [words (db/get-all-words)]
      (is (>= (count words) 1))
      (is (some #(= "apple" (:word %)) words))
      (let [apple-word (first (filter #(= "apple" (:word %)) words))]
        (is (seq (:meanings apple-word)))))))

(deftest test-get-word-by-word
  (testing "get-word-by-word returns the correct word with meanings and examples"
    (let [word (db/get-word-by-word "apple")]
      (is (= "apple" (:word word)))
      (is (seq (:meanings word)))
      (is (= "яблоко" (:translation (first (:meanings word)))))))
  
  (testing "get-word-by-word returns nil for non-existent word"
    (is (nil? (db/get-word-by-word "nonexistent")))))

(deftest test-search-words
  (testing "search-words finds words with partial matches including their meanings"
    ;; Add more test data for searching
    (db/insert-word! "application" "ˌæplɪˈkeɪʃən" "A program" "приложение" "I use this application daily.")
    
    (let [results (db/search-words "appl")]
      (is (= 2 (count results)))
      (is (= #{"apple" "application"} (set (map :word results))))
      (is (seq (:meanings (first results))))))
  
  (testing "search-words returns empty list when no matches found"
    (let [results (db/search-words "xyz")]
      (is (empty? results)))))

(deftest test-delete-word!
  (testing "delete-word! removes a word and its associated meanings and examples from the database"
    (db/delete-word! "apple")
    (is (nil? (db/get-word-by-word "apple"))))) 