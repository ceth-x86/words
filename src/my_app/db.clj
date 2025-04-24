(ns my-app.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;; Define the database spec
(def db-spec
  {:dbtype "sqlite"
   :dbname "resources/english_words.db"})

;; Function to create a new database and table for English words
(defn init-db! []
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS words (
        word TEXT PRIMARY KEY NOT NULL,
        transcription TEXT,
        description TEXT,
        translation TEXT NOT NULL,
        examples TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    "])))

;; Function to insert a word into the words table
(defn insert-word! [word transcription description translation examples]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/execute! ds ["
        INSERT INTO words (word, transcription, description, translation, examples)
        VALUES (?, ?, ?, ?, ?)
      " word transcription description translation examples])
      (catch Exception e
        (println (str "Error adding word: '" word "'. Word might already exist."))
        (println (.getMessage e))))))

;; Function to batch import words
(defn batch-import-words! [words-data]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/with-transaction [tx ds]
        (doseq [{:keys [word transcription description translation examples]} words-data]
          (try
            (jdbc/execute! tx ["
              INSERT INTO words (word, transcription, description, translation, examples)
              VALUES (?, ?, ?, ?, ?)
            " word transcription description translation examples])
            (println (str "Added word: '" word "'"))
            (catch Exception e
              (println (str "Error adding word: '" word "'. Word might already exist."))
              (println (.getMessage e))))))
      (catch Exception e
        (println "Error importing words:")
        (println (.getMessage e))))))

;; Function to get all words from the table
(defn get-all-words []
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["SELECT * FROM words ORDER BY word"]
                  {:builder-fn rs/as-unqualified-maps})))

;; Function to get a specific word by word string
(defn get-word-by-word [word]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute-one! ds ["SELECT * FROM words WHERE word = ?" word]
                      {:builder-fn rs/as-unqualified-maps})))

;; Function to search words by English word (partial match)
(defn search-words [search-term]
  (let [ds (jdbc/get-datasource db-spec)
        pattern (str "%" search-term "%")]
    (jdbc/execute! ds ["SELECT * FROM words WHERE word LIKE ? OR translation LIKE ? ORDER BY word" 
                     pattern pattern]
                  {:builder-fn rs/as-unqualified-maps})))

;; Function to update a word
(defn update-word! [word transcription description translation examples]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      UPDATE words 
      SET transcription = ?, description = ?, translation = ?, examples = ? 
      WHERE word = ?
    " transcription description translation examples word])))

;; Function to delete a word
(defn delete-word! [word]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["DELETE FROM words WHERE word = ?" word]))) 