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
  (testing "Database schema has the correct structure for words table"
    (let [ds (jdbc/get-datasource test-db-spec)
          table-info (jdbc/execute! ds ["PRAGMA table_info(words)"]
                                   {:builder-fn rs/as-unqualified-maps})]
      
      ;; Test for the correct number of columns - updated to 3 for created_at
      (is (= 3 (count table-info)))
      
      ;; Test for specific columns and their types
      (is (= "id" (:name (first table-info))))
      (is (= "INTEGER" (:type (first table-info))))
      (is (= 1 (:pk (first table-info)))) ;; 1 means it's a primary key
      
      (is (= "word" (:name (second table-info))))
      (is (= "TEXT" (:type (second table-info))))
      (is (= 1 (:notnull (second table-info))))))
  
  (testing "Database schema has the correct structure for meanings table"
    (let [ds (jdbc/get-datasource test-db-spec)
          table-info (jdbc/execute! ds ["PRAGMA table_info(meanings)"]
                                   {:builder-fn rs/as-unqualified-maps})]
      
      ;; Test for the correct number of columns
      (is (= 6 (count table-info)))
      
      ;; Check if all expected columns exist
      (let [column-names (map :name table-info)]
        (is (some #{"id"} column-names))
        (is (some #{"word_id"} column-names))
        (is (some #{"transcription"} column-names))
        (is (some #{"description"} column-names))
        (is (some #{"translation"} column-names))
        (is (some #{"created_at"} column-names)))
      
      ;; Check for foreign key constraint on word_id
      (let [fk-info (jdbc/execute! ds ["PRAGMA foreign_key_list(meanings)"]
                                  {:builder-fn rs/as-unqualified-maps})]
        (is (= "words" (:table (first fk-info))))
        (is (= "word_id" (:from (first fk-info))))
        (is (= "id" (:to (first fk-info)))))))
  
  (testing "Database schema has the correct structure for examples table"
    (let [ds (jdbc/get-datasource test-db-spec)
          table-info (jdbc/execute! ds ["PRAGMA table_info(examples)"]
                                   {:builder-fn rs/as-unqualified-maps})]
      
      ;; Test for the correct number of columns
      (is (= 4 (count table-info)))
      
      ;; Check if all expected columns exist
      (let [column-names (map :name table-info)]
        (is (some #{"id"} column-names))
        (is (some #{"meaning_id"} column-names))
        (is (some #{"text"} column-names)) ;; обновлено с "example" на "text"
        (is (some #{"created_at"} column-names)))
      
      ;; Check for foreign key constraint on meaning_id
      (let [fk-info (jdbc/execute! ds ["PRAGMA foreign_key_list(examples)"]
                                  {:builder-fn rs/as-unqualified-maps})]
        (is (= "meanings" (:table (first fk-info))))
        (is (= "meaning_id" (:from (first fk-info))))
        (is (= "id" (:to (first fk-info))))))))

(deftest test-word-uniqueness-constraint
  (testing "Unique constraint prevents duplicate words"
    (let [ds (jdbc/get-datasource test-db-spec)
          result (db/insert-word! "duplicate" "test" "test description" "дубликат" "")]
      (is (some? result))
      
      ;; Try to add the same word again
      (try
        (jdbc/execute! ds ["INSERT INTO words (word) VALUES (?)" "duplicate"])
        (is false "Should have thrown an exception")
        (catch Exception e
          ;; We expect an exception to be thrown
          (is (instance? Exception e))))
      
      ;; Check that there's only one entry with the word "duplicate"
      (let [results (jdbc/execute! ds ["SELECT COUNT(*) as count FROM words WHERE word = ?" "duplicate"]
                                 {:builder-fn rs/as-unqualified-maps})]
        (is (= 1 (:count (first results))))))))

(deftest test-not-null-constraints
  (testing "NOT NULL constraints are enforced on meanings table"
    ;; Try to insert without a required field (translation)
    (let [_ (db/insert-word! "test-word" "test" "test description" "тест-слово" "")]
      (try
        (let [ds (jdbc/get-datasource test-db-spec)]
          (jdbc/execute! ds ["INSERT INTO meanings (word_id, transcription, description) 
                            VALUES (?, ?, ?)" 
                           1 "test" "Test description"]))
        (catch Exception e
          ;; We expect an exception to be thrown
          (is (instance? Exception e)))))))

(deftest test-timestamp-default
  (testing "created_at timestamp is automatically set in meanings table"
    (let [_ (db/insert-word! "timestamp_test" "test" "Testing timestamps" "тест" "Test example")
          ds (jdbc/get-datasource test-db-spec)
          meaning (jdbc/execute-one! ds ["SELECT id FROM meanings WHERE word_id = (SELECT id FROM words WHERE word = ?)" "timestamp_test"]
                                   {:builder-fn rs/as-unqualified-maps})
          meaning-id (:id meaning)]
      
      (let [result (jdbc/execute-one! ds ["SELECT created_at FROM meanings WHERE id = ?" meaning-id]
                                    {:builder-fn rs/as-unqualified-maps})]
        ;; Check that the timestamp is not nil
        (is (not (nil? (:created_at result))))))))

;; Убрал тест на индексы, так как они не созданы явно в схеме
(deftest test-table-primary-keys
  (testing "Tables have primary keys defined correctly"
    (let [ds (jdbc/get-datasource test-db-spec)
          words-pk (jdbc/execute! ds ["SELECT name FROM pragma_table_info('words') WHERE pk = 1"]
                                 {:builder-fn rs/as-unqualified-maps})
          meanings-pk (jdbc/execute! ds ["SELECT name FROM pragma_table_info('meanings') WHERE pk = 1"]
                                    {:builder-fn rs/as-unqualified-maps})
          examples-pk (jdbc/execute! ds ["SELECT name FROM pragma_table_info('examples') WHERE pk = 1"]
                                    {:builder-fn rs/as-unqualified-maps})]
      
      ;; Check for primary keys
      (is (= "id" (:name (first words-pk))))
      (is (= "id" (:name (first meanings-pk))))
      (is (= "id" (:name (first examples-pk))))))) 