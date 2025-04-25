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
    
    ;; Add test data
    (db/insert-word! "apple" "ˈæpl" "A round fruit with red, yellow, or green skin" "яблоко" "I ate an apple for breakfast.")
    
    ;; Run the tests
    (f)
    
    ;; Cleanup - delete the test database
    (let [db-file (File. test-db-file)]
      (when (.exists db-file)
        (.delete db-file)))))

(use-fixtures :each with-test-db)

(deftest test-init-db!
  (testing "init-db! creates the words table"
    (db/init-db!) ;; Running again should be safe due to IF NOT EXISTS
    (let [ds (jdbc/get-datasource test-db-spec)
          tables (jdbc/execute! ds ["SELECT name FROM sqlite_master WHERE type='table'"]
                               {:builder-fn rs/as-unqualified-maps})]
      (is (some #(= "words" (:name %)) tables)))))

(deftest test-insert-word!
  (testing "insert-word! adds a new word to the database"
    (db/insert-word! "banana" "bəˈnænə" "A long curved fruit with a yellow skin" "банан" "Monkeys eat bananas.")
    (let [word (db/get-word-by-word "banana")]
      (is (= "banana" (:word word)))
      (is (= "банан" (:translation word))))))

(deftest test-batch-import-words!
  (testing "batch-import-words! adds multiple words to the database"
    (let [words-data [{:word "car" :transcription "kɑr" :description "A road vehicle" :translation "машина" :examples "I drive a car."}
                     {:word "dog" :transcription "dɔg" :description "A domestic animal" :translation "собака" :examples "I have a pet dog."}]]
      (db/batch-import-words! words-data)
      (let [ds (jdbc/get-datasource test-db-spec)
            results (jdbc/execute! ds ["SELECT * FROM words WHERE word IN (?, ?)" "car" "dog"]
                                  {:builder-fn rs/as-unqualified-maps})]
        (is (= 2 (count results)))
        (is (= #{"car" "dog"} (set (map :word results))))))))

(deftest test-get-all-words
  (testing "get-all-words returns all words from the database"
    (let [words (db/get-all-words)]
      (is (= 1 (count words)))
      (is (= "apple" (:word (first words)))))))

(deftest test-get-word-by-word
  (testing "get-word-by-word returns the correct word"
    (let [word (db/get-word-by-word "apple")]
      (is (= "apple" (:word word)))
      (is (= "яблоко" (:translation word)))))
  
  (testing "get-word-by-word returns nil for non-existent word"
    (is (nil? (db/get-word-by-word "nonexistent")))))

(deftest test-search-words
  (testing "search-words finds words with partial matches"
    ;; Add more test data for searching
    (db/insert-word! "application" "ˌæplɪˈkeɪʃən" "A program" "приложение" "I use this application daily.")
    
    (let [results (db/search-words "appl")]
      (is (= 2 (count results)))
      (is (= #{"apple" "application"} (set (map :word results))))))
  
  (testing "search-words returns empty list when no matches found"
    (let [results (db/search-words "xyz")]
      (is (empty? results)))))

(deftest test-update-word!
  (testing "update-word! updates an existing record"
    (db/update-word! "apple" "ˈæpl" "An updated description" "яблоко" "Updated example.")
    (let [word (db/get-word-by-word "apple")]
      (is (= "An updated description" (:description word)))
      (is (= "Updated example." (:examples word))))))

(deftest test-delete-word!
  (testing "delete-word! removes a word from the database"
    (db/delete-word! "apple")
    (is (nil? (db/get-word-by-word "apple"))))) 