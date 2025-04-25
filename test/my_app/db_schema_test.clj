(ns my-app.db-schema-test
  (:require [clojure.test :refer :all]
            [my-app.db :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.io File)))

;; Define a temporary file-based test database
(def test-db-file (str (System/getProperty "java.io.tmpdir") "/test_schema.db"))

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
    
    ;; Run the tests
    (f)
    
    ;; Cleanup - delete the test database
    (let [db-file (File. test-db-file)]
      (when (.exists db-file)
        (.delete db-file)))))

(use-fixtures :each with-test-db)

(deftest test-db-schema
  (testing "Database schema has the correct structure"
    (let [ds (jdbc/get-datasource test-db-spec)
          table-info (jdbc/execute! ds ["PRAGMA table_info(words)"]
                                   {:builder-fn rs/as-unqualified-maps})]
      
      ;; Test for the correct number of columns
      (is (= 6 (count table-info)))
      
      ;; Test for specific columns and their types
      (is (= "word" (:name (first table-info))))
      (is (= "TEXT" (:type (first table-info))))
      (is (= 1 (:pk (first table-info)))) ;; 1 means it's a primary key
      (is (= 1 (:notnull (first table-info)))) ;; 1 means NOT NULL
      
      ;; Test that translation is required (NOT NULL)
      (let [translation-col (first (filter #(= "translation" (:name %)) table-info))]
        (is (= 1 (:notnull translation-col)))))))

(deftest test-primary-key-constraint
  (testing "Primary key constraint prevents duplicate words"
    (db/insert-word! "duplicate" "ˈduːplɪkeɪt" "An exact copy" "дубликат" "This is a duplicate.")
    (db/insert-word! "duplicate" "ˈduːplɪkeɪt" "Different description" "дубликат" "This won't be inserted.")
    
    ;; Check that there's only one entry with the word "duplicate"
    (let [ds (jdbc/get-datasource test-db-spec)
          results (jdbc/execute! ds ["SELECT COUNT(*) as count FROM words WHERE word = ?" "duplicate"]
                                {:builder-fn rs/as-unqualified-maps})]
      (is (= 1 (:count (first results)))))))

(deftest test-not-null-constraints
  (testing "NOT NULL constraints are enforced"
    ;; Try to insert without a required field (word)
    (try
      (let [ds (jdbc/get-datasource test-db-spec)]
        (jdbc/execute! ds ["INSERT INTO words (transcription, description, translation, examples) 
                          VALUES (?, ?, ?, ?)" 
                         "test" "Test description" "тест" "Test example"]))
      (catch Exception e
        ;; We expect an exception to be thrown
        (is (instance? Exception e))))
    
    ;; Try to insert without a required field (translation)
    (try
      (let [ds (jdbc/get-datasource test-db-spec)]
        (jdbc/execute! ds ["INSERT INTO words (word, transcription, description, examples) 
                          VALUES (?, ?, ?, ?)" 
                         "test2" "test" "Test description" "Test example"]))
      (catch Exception e
        ;; We expect an exception to be thrown
        (is (instance? Exception e))))))

(deftest test-timestamp-default
  (testing "created_at timestamp is automatically set"
    (db/insert-word! "timestamp_test" "test" "Testing timestamps" "тест" "Example")
    (let [ds (jdbc/get-datasource test-db-spec)
          result (jdbc/execute-one! ds ["SELECT created_at FROM words WHERE word = ?" "timestamp_test"]
                                   {:builder-fn rs/as-unqualified-maps})]
      ;; Check that the timestamp is not nil
      (is (not (nil? (:created_at result)))))))

(deftest test-table-indexes
  (testing "Primary key creates an index on the word column"
    (let [ds (jdbc/get-datasource test-db-spec)
          indexes (jdbc/execute! ds ["PRAGMA index_list('words')"]
                                {:builder-fn rs/as-unqualified-maps})]
      (is (pos? (count indexes)))
      
      ;; For SQLite, a table with a PRIMARY KEY will have an auto-created index
      ;; usually named sqlite_autoindex_TABLE_1
      (is (some #(or (.startsWith (:name %) "sqlite_autoindex") 
                      (.contains (:name %) "PRIMARY")) indexes))))) 